/* Private OPFS snapshots/stages. Original selected files are never written or removed. */
(() => {
    const chunkBytes = 1024 * 1024, maximumBytes = 1024 * 1024 * 1024;
    const handles = new Map(), operations = new Map();
    // A new namespace avoids sweeping files used by older clients without this lease protocol.
    const directoryName = "boarderless-bundle-stages-v2";
    const managedName = /^boarderless-bundle-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
    const lockName = token => "BoarderLess.bundle-stage.v2:" + token;
    async function managedRoot(state) {
        requireState(state);
        if (!globalThis.isSecureContext || !navigator.storage?.getDirectory || !navigator.locks?.request) throw new Error("Unavailable");
        const storage = await navigator.storage.getDirectory(); requireState(state);
        const root = await storage.getDirectoryHandle(directoryName, { create: true }); requireState(state);
        return root;
    }
    async function sweep(root, state, confirmedDownloads = false) {
        for await (const [entryName, entry] of root.entries()) {
            requireState(state);
            const isMarker = entryName.endsWith(".download");
            const name = isMarker ? entryName.slice(0, -9) : entryName;
            if (entry.kind !== "file" || !managedName.test(name)) continue;
            // Never queue behind a live tab or steal its lease. Hold exclusion through deletion.
            await navigator.locks.request(lockName(name), { ifAvailable: true }, async lock => {
                if (!lock) return;
                requireState(state);
                if (isMarker) {
                    try { await root.getFileHandle(name); }
                    catch (error) {
                        if (error.name !== "NotFoundError") throw error;
                        try { await root.removeEntry(entryName); } catch (missing) { if (missing.name !== "NotFoundError") throw missing; }
                        return;
                    }
                }
                // Downloads can outlive their tab. A persisted publication marker requires human ACK.
                try { await root.getFileHandle(name + ".download"); if (!confirmedDownloads) return; }
                catch (error) { if (error.name !== "NotFoundError") throw error; }
                try { await root.removeEntry(name); } catch (error) { if (error.name !== "NotFoundError") throw error; }
                if (confirmedDownloads) {
                    try { await root.removeEntry(name + ".download"); } catch (error) { if (error.name !== "NotFoundError") throw error; }
                }
            });
        }
    }
    async function acquire(item) {
        let granted, failed;
        const acquired = new Promise((resolve, reject) => { granted = resolve; failed = reject; });
        const held = new Promise(resolve => { item.unlock = resolve; });
        item.lease = navigator.locks.request(lockName(item.token), { ifAvailable: true }, async lock => {
            if (!lock) throw new Error("Unavailable");
            granted(); await held;
        });
        item.lease.catch(failed);
        await acquired; item.acquired = true;
    }
    const requireState = state => {
        if (state.cancelled || !state.guard() || document.hidden) throw new Error("Unavailable");
    };
    function discard(item, confirmed = false) {
        if (!item || (item.dispatched && !confirmed)) return Promise.resolve();
        if (item.cleanup) return item.cleanup;
        item.removed = true;
        item.cleanup = (async () => {
            if (item.writer) { try { await item.writer.abort(); } catch (_) {} item.writer = null; }
            if (item.acquired) {
                try { await item.root.removeEntry(item.token); }
                catch (error) { if (error.name !== "NotFoundError") throw error; }
                try { await item.root.removeEntry(item.token + ".download"); }
                catch (error) { if (error.name !== "NotFoundError") throw error; }
            }
            item.unlock?.(); try { await item.lease; } catch (_) {}
            handles.delete(item.token);
        })();
        // A failed removal keeps its lease and handle for an explicit cleanup retry.
        item.cleanup.catch(() => { item.cleanup = null; });
        return item.cleanup;
    }
    async function create(state) {
        requireState(state);
        const root = await managedRoot(state);
        await sweep(root, state); requireState(state);
        const token = "boarderless-bundle-" + crypto.randomUUID();
        const item = { root, token, writer: null, file: null, bytes: 0, busy: false, removed: false };
        state.created = item; // Own even a late getFileHandle result.
        await acquire(item); requireState(state);
        item.handle = await root.getFileHandle(token, { create: true }); requireState(state);
        item.writer = await item.handle.createWritable(); requireState(state);
        handles.set(token, item);
        return item;
    }
    function select(state) {
        return new Promise((resolve, reject) => {
            requireState(state);
            const input = document.createElement("input");
            input.type = "file"; input.accept = ".blscheme,application/octet-stream";
            input.style.display = "none";
            let settled = false;
            const finish = (file, error) => {
                if (settled) return; settled = true;
                input.remove(); window.removeEventListener("focus", focus);
                clearTimeout(timer); state.stopSelection = null;
                error ? reject(error) : resolve(file);
            };
            const focus = () => { timer = setTimeout(() => { if (!input.files?.length) finish(null); }, 500); };
            let timer;
            state.stopSelection = () => finish(null, new Error("Cancelled"));
            input.addEventListener("change", () => finish(input.files?.[0] || null), { once: true });
            input.addEventListener("cancel", () => finish(null), { once: true });
            window.addEventListener("focus", focus);
            document.body.appendChild(input);
            try { input.click(); } catch (e) { finish(null, e); }
        });
    }
    const decode = text => {
        if (text.length > Math.ceil(chunkBytes / 3) * 4) throw new Error("Invalid");
        const raw = atob(text), bytes = new Uint8Array(raw.length);
        if (raw.length < 1 || raw.length > chunkBytes) throw new Error("Invalid");
        for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
        return bytes;
    };
    const encode = bytes => {
        let text = "";
        for (let i = 0; i < bytes.length; i += 8192) text += String.fromCharCode(...bytes.subarray(i, i + 8192));
        return btoa(text);
    };
    async function run(state, action, token, argument, offset, limit) {
        if (action === "sweep" || action === "acknowledgeIdle") { const root = await managedRoot(state); await sweep(root, state, action === "acknowledgeIdle"); return ""; }
        if (action === "pick") {
            const file = await select(state); requireState(state);
            if (!file) return null;
            if (!Number.isSafeInteger(file.size) || file.size < 45 || file.size > maximumBytes) throw new Error("Invalid");
            const item = await create(state);
            for (let at = 0; at < file.size; at += chunkBytes) {
                requireState(state);
                const bytes = await file.slice(at, Math.min(at + chunkBytes, file.size)).arrayBuffer(); requireState(state);
                if (bytes.byteLength !== Math.min(chunkBytes, file.size - at)) throw new Error("Invalid");
                await item.writer.write(bytes); requireState(state);
            }
            await item.writer.close(); item.writer = null; requireState(state);
            item.file = await item.handle.getFile(); requireState(state);
            if (item.file.size !== file.size) throw new Error("Invalid");
            item.bytes = file.size;
            return item.token;
        }
        if (action === "create") {
            if (!/^[A-Za-z0-9_-]+\.blscheme$/.test(argument)) throw new Error("Invalid");
            const item = await create(state); item.name = argument; return item.token;
        }
        const item = handles.get(token);
        if (action === "drop" || action === "acknowledge") {
            if (action === "acknowledge" && item && !item.dispatched) throw new Error("Invalid");
            await discard(item, action === "acknowledge"); return "";
        }
        if (!item || item.removed || item.busy) throw new Error("Unavailable");
        item.busy = true; state.item = item;
        try {
            requireState(state);
            if (action === "size") { if (!item.file) throw new Error("Invalid"); return String(item.bytes); }
            if (action === "read") {
                if (!item.file || !Number.isSafeInteger(offset) || offset < 0 || !Number.isInteger(limit) ||
                    limit < 1 || limit > chunkBytes || offset + limit > item.bytes) throw new Error("Invalid");
                const bytes = new Uint8Array(await item.file.slice(offset, offset + limit).arrayBuffer()); requireState(state);
                if (bytes.length !== limit) throw new Error("Invalid");
                return encode(bytes);
            }
            if (action === "append") {
                if (!item.writer || item.dispatched) throw new Error("Invalid");
                const bytes = decode(argument);
                if (item.bytes + bytes.length > maximumBytes) throw new Error("Invalid");
                await item.writer.write(bytes); requireState(state);
                item.bytes += bytes.length; return "";
            }
            if (action === "commit") {
                if (!item.writer || item.bytes < 45 || item.dispatched) throw new Error("Invalid");
                await item.writer.close(); item.writer = null; requireState(state);
                const file = await item.handle.getFile(); requireState(state);
                if (file.size !== item.bytes) throw new Error("Invalid");
                // Persist intent before the possibly irreversible anchor dispatch.
                const marker = await item.root.getFileHandle(item.token + ".download", { create: true }); requireState(state);
                const markerWriter = await marker.createWritable();
                try { await markerWriter.write("download-requested"); await markerWriter.close(); }
                catch (error) { try { await markerWriter.abort(); } catch (_) {} throw error; }
                requireState(state);
                const url = URL.createObjectURL(file);
                try {
                    const anchor = document.createElement("a"); anchor.download = item.name; anchor.href = url;
                    anchor.style.display = "none"; document.body.appendChild(anchor);
                    try { requireState(state); item.dispatched = true; anchor.click(); }
                    finally { anchor.remove(); }
                    // A download request is not an acknowledgement that the browser saved to disk.
                    // Retain backing file while this page lives: a large browser download may
                    // still be reading after its URL has been revoked. There is no save ACK.
                    setTimeout(() => { URL.revokeObjectURL(url); }, 60000);
                } catch (e) { URL.revokeObjectURL(url); throw e; }
                return "";
            }
            throw new Error("Invalid");
        } finally { item.busy = false; }
    }
    globalThis.boarderlessSchemeBundle = {
        operation(id, action, token, argument, offset, limit, guard, done) {
            if (operations.has(id)) { done(null, "Unavailable"); return; }
            const state = { guard, cancelled: false }; operations.set(id, state);
            void (async () => {
                try {
                    const value = await run(state, action, token, argument, offset, limit);
                    if (action !== "drop" && action !== "acknowledge") requireState(state);
                    done(value, null);
                } catch (_) {
                    try { await discard(state.created); } catch (_) {}
                    if (action === "append" || action === "commit") { try { await discard(state.item); } catch (_) {} }
                    done(null, "Unavailable");
                } finally { operations.delete(id); }
            })();
        },
        cancel(id) { const state = operations.get(id); if (state) { state.cancelled = true; state.stopSelection?.(); } },
        release(token) { const item = handles.get(token); if (item) void discard(item).catch(() => {}); }
    };
})();
