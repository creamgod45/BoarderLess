/* Actual OPFS and File bytes; only user picker/download dispatch and a timing barrier are controlled. */
globalThis.boarderlessBundleFixture = {
    install(mode) {
        if (this.state) throw new Error("Already installed");
        const s = this.state = { mode, owned: new Set(), downloads: 0, selected: new File([new Uint8Array(100).fill(17)], "private.blscheme"),
            operation: globalThis.boarderlessSchemeBundle.operation, pending: 0,
            inputClick: HTMLInputElement.prototype.click, anchorClick: HTMLAnchorElement.prototype.click,
            getFileHandle: FileSystemDirectoryHandle.prototype.getFileHandle,
            removeEntry: FileSystemDirectoryHandle.prototype.removeEntry,
            write: FileSystemWritableFileStream.prototype.write,
            createObjectURL: URL.createObjectURL, hidden: Object.getOwnPropertyDescriptor(document, "hidden") };
        globalThis.boarderlessSchemeBundle.operation = function(...args) {
            s.pending++;
            const done = args[7];
            args[7] = (...result) => { try { done(...result); } finally { if (--s.pending === 0) { const idle = s.idle; s.idle = null; idle?.(); } } };
            return s.operation(...args);
        };
        Object.defineProperty(document, "hidden", { configurable: true, value: false });
        FileSystemDirectoryHandle.prototype.getFileHandle = function(name, options) {
            if (name.startsWith("boarderless-bundle-") && options?.create) s.owned.add(name);
            return s.getFileHandle.call(this, name, options);
        };
        FileSystemDirectoryHandle.prototype.removeEntry = function(name, options) {
            if (s.failRemove && s.owned.has(name)) { s.failRemove = false; return Promise.reject(new DOMException("Fixture failure", "UnknownError")); }
            return s.removeEntry.call(this, name, options);
        };
        HTMLInputElement.prototype.click = function() {
            if (this.type !== "file") return s.inputClick.call(this);
            if (s.mode === "cancel") { this.dispatchEvent(new Event("cancel")); return; }
            Object.defineProperty(this, "files", { configurable: true, value: [s.selected] });
            this.dispatchEvent(new Event("change"));
        };
        URL.createObjectURL = function(blob) { s.download = blob; return s.createObjectURL.call(URL, blob); };
        HTMLAnchorElement.prototype.click = function() { s.downloads++; };
        FileSystemWritableFileStream.prototype.write = async function(bytes) {
            const value = await s.write.call(this, bytes);
            if (s.mode === "hold") {
                await new Promise(resolve => { s.release = resolve; s.waiter?.(); });
            }
            return value;
        };
    },
    failNextRemoval() { this.state.failRemove = true; },
    selectBytes(base64) {
        const binary = atob(base64);
        this.state.selected = new File([Uint8Array.from(binary, c => c.charCodeAt(0))], "desktop-original.blscheme");
    },
    selectDownload() { this.state.selected = this.state.download; },
    mutateDownload(done) {
        const s = this.state;
        void (async () => {
            const root = await (await navigator.storage.getDirectory()).getDirectoryHandle("boarderless-bundle-stages-v2", { create: true });
            const handle = await s.getFileHandle.call(root, s.download.name);
            const writer = await handle.createWritable();
            await writer.write(new Uint8Array(45).fill(99)); await writer.close();
            done();
        })();
    },
    value(name) { return String(this.state[name]); },
    waitForWrite(done) { const s = this.state; if (s.release) done(); else s.waiter = done; },
    waitForIdle(done) { const s = this.state; if (!s.pending) done(); else s.idle = done; },
    releaseWrite() { const s = this.state; s.mode = "normal"; s.release?.(); },
    audit(done) {
        const s = this.state;
        void (async () => {
            const root = await (await navigator.storage.getDirectory()).getDirectoryHandle("boarderless-bundle-stages-v2", { create: true }); let count = 0;
            for (const name of s.owned) {
                try { await s.getFileHandle.call(root, name); count++; } catch (e) { if (e.name !== "NotFoundError") throw e; }
            }
            done(String(count), null);
        })().catch(() => done(null, "Failed"));
    },
    exerciseLeases(done) {
        const s = this.state;
        void (async () => {
            const root = await (await navigator.storage.getDirectory()).getDirectoryHandle("boarderless-bundle-stages-v2", { create: true });
            const active = "boarderless-bundle-" + crypto.randomUUID();
            const idle = "boarderless-bundle-" + crypto.randomUUID();
            const published = "boarderless-bundle-" + crypto.randomUUID();
            const foreign = "fixture-unmanaged-" + crypto.randomUUID();
            const directory = "boarderless-bundle-" + crypto.randomUUID();
            for (const name of [active, idle, published, published + ".download", foreign, directory]) s.owned.add(name);
            const sentinel = await root.getFileHandle(foreign, { create: true });
            const writable = await sentinel.createWritable(); await writable.write("unchanged"); await writable.close();
            await root.getDirectoryHandle(directory, { create: true });
            await root.getFileHandle(idle, { create: true });
            await root.getFileHandle(published, { create: true });
            await root.getFileHandle(published + ".download", { create: true });
            const workerUrl = URL.createObjectURL(new Blob([`
                onmessage = event => {
                    const name = event.data;
                    navigator.locks.request("BoarderLess.bundle-stage.v2:" + name, async () => {
                        const root = await (await navigator.storage.getDirectory()).getDirectoryHandle("boarderless-bundle-stages-v2", { create: true });
                        const handle = await root.getFileHandle(name, { create: true });
                        const writer = await handle.createWritable(); await writer.write("active"); await writer.close();
                        postMessage("ready"); await new Promise(() => {});
                    }).catch(() => postMessage("failed"));
                };
            `], { type: "text/javascript" }));
            const worker = new Worker(workerUrl);
            const sweep = (action = "sweep") => new Promise((resolve, reject) => s.operation(crypto.randomUUID(), action, "", "", 0, 0, () => true,
                (value, error) => error ? reject(new Error("Sweep failed")) : resolve()));
            const exists = async name => { try { await s.getFileHandle.call(root, name); return true; } catch (e) { if (e.name === "NotFoundError") return false; throw e; } };
            try {
                await new Promise((resolve, reject) => {
                    worker.onmessage = event => event.data === "ready" ? resolve() : reject(new Error("Worker failed"));
                    worker.onerror = () => reject(new Error("Worker failed")); worker.postMessage(active);
                });
                await sweep();
                if (!await exists(active) || await exists(idle)) throw new Error("Lease exclusion failed");
                worker.terminate();
                // Poll the actual lock manager after worker termination; do not fake release.
                let released = false;
                for (let retry = 0; retry < 100 && !released; retry++) {
                    released = await navigator.locks.request("BoarderLess.bundle-stage.v2:" + active, { ifAvailable: true }, lock => !!lock);
                    if (!released) await new Promise(resolve => setTimeout(resolve, 10));
                }
                if (!released) throw new Error("Lease still held");
                await sweep();
                if (await exists(active) || !await exists(published) || (await (await sentinel.getFile()).text()) !== "unchanged") throw new Error("Recovery failed");
                await sweep("acknowledgeIdle");
                if (await exists(published)) throw new Error("Acknowledged cleanup failed");
                await root.getDirectoryHandle(directory); // Matching-name directory must never be recursively deleted.
                done("protected,reclaimed,foreign-preserved", null);
            } finally { worker.terminate(); URL.revokeObjectURL(workerUrl); }
        })().catch(() => done(null, "Failed"));
    },
    restore(done) {
        const s = this.state;
        globalThis.boarderlessSchemeBundle.operation = s.operation;
        HTMLInputElement.prototype.click = s.inputClick; HTMLAnchorElement.prototype.click = s.anchorClick;
        FileSystemDirectoryHandle.prototype.getFileHandle = s.getFileHandle;
        FileSystemDirectoryHandle.prototype.removeEntry = s.removeEntry;
        FileSystemWritableFileStream.prototype.write = s.write; URL.createObjectURL = s.createObjectURL;
        if (s.hidden) Object.defineProperty(document, "hidden", s.hidden); else delete document.hidden;
        s.release?.(); this.state = null;
        void (async () => {
            const root = await (await navigator.storage.getDirectory()).getDirectoryHandle("boarderless-bundle-stages-v2", { create: true });
            // Only exact files created by this fixture's operations, never another tab's stages.
            for (const name of s.owned) {
                if (name.endsWith(".download")) continue;
                await new Promise(resolve => s.operation(crypto.randomUUID(), "acknowledge", name, "", 0, 0, () => true, () => resolve()));
                await new Promise(resolve => s.operation(crypto.randomUUID(), "drop", name, "", 0, 0, () => true, () => resolve()));
            }
            for (const name of s.owned) { try { await root.removeEntry(name); } catch (_) {} }
            done();
        })();
    }
};
