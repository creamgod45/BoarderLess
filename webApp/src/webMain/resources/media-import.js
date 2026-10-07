// Ephemeral browser handles shared by Kotlin/JS and Kotlin/Wasm. Nothing is persisted here.
(() => {
    const files = new Map();
    const pickers = new Map();
    const uploads = new Map();
    const mediaTypes = new Set(["image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/webm"]);
    const extensions = { png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", webp: "image/webp", gif: "image/gif", mp4: "video/mp4", webm: "video/webm" };
    const maximumSize = 200 * 1024 * 1024;
    const maximumChunk = 1024 * 1024;

    globalThis.boarderlessMedia = {
        pick(id, done) {
            const input = document.createElement("input");
            input.type = "file";
            input.accept = [...mediaTypes].join(",");
            input.multiple = false;
            input.style.display = "none";
            let finished = false;
            let deadline;
            const finish = (file, error = null) => {
                if (finished) return;
                finished = true;
                clearTimeout(deadline);
                input.removeEventListener("change", onChange);
                input.removeEventListener("cancel", onCancel);
                pickers.delete(id);
                input.value = "";
                input.remove();
                if (!file || error) { done(null, error); return; }
                const mediaType = file.type.toLowerCase() || extensions[file.name.split(".").pop().toLowerCase()];
                if (!mediaTypes.has(mediaType) || file.size <= 0 || file.size > maximumSize) {
                    done(null, "Selected file type or size is unsupported");
                    return;
                }
                const fileId = id + ".file";
                files.set(fileId, file);
                done(JSON.stringify({ fileId, name: file.name, mediaType, byteSize: file.size }), null);
            };
            const onChange = () => finish(input.files?.[0] ?? null);
            const onCancel = () => finish(input.files?.[0] ?? null);
            // Native focus can arrive before a local/cloud File provider delivers change.
            // Empty files on focus never prove cancellation. Legacy browsers without cancel
            // remain pending until explicit UI cancellation or a separately reported deadline.
            input.addEventListener("change", onChange);
            input.addEventListener("cancel", onCancel);
            pickers.set(id, () => finish(null));
            deadline = setTimeout(() => finish(null, "Media selection timed out; choose the file again"), 120000);
            try { document.body.appendChild(input); input.click(); }
            catch (_) { finish(null, "Media selection is unavailable"); }
        },
        async read(id, offset, maximum, done) {
            try {
                const file = files.get(id);
                if (!file) throw new Error("Selected file is no longer available");
                if (!Number.isSafeInteger(offset) || offset < 0 || !Number.isInteger(maximum) || maximum < 1 || maximum > maximumChunk) {
                    throw new Error("Invalid file chunk request");
                }
                const bytes = new Uint8Array(await file.slice(offset, offset + maximum).arrayBuffer());
                if (!files.has(id)) throw new Error("File selection was released");
                let binary = "";
                for (let start = 0; start < bytes.length; start += 16384) {
                    binary += String.fromCharCode(...bytes.subarray(start, start + 16384));
                }
                done(btoa(binary), null);
            } catch (error) { done(null, String(error)); }
        },
        upload(id, url, headers, progress, done) {
            const file = files.get(id);
            if (!file || uploads.has(id)) { done("Selected file is unavailable or already uploading"); return; }
            const xhr = new XMLHttpRequest();
            let finished = false;
            const finish = error => {
                if (finished) return;
                finished = true;
                uploads.delete(id);
                if (!error) progress(file.size);
                done(error);
            };
            uploads.set(id, xhr);
            xhr.upload.onprogress = event => { if (!finished) progress(Math.min(event.loaded, file.size)); };
            xhr.onload = () => finish(xhr.status >= 200 && xhr.status < 300 ? null : `Upload failed (HTTP ${xhr.status})`);
            xhr.onerror = () => finish("Upload connection failed");
            xhr.onabort = () => finish("Upload cancelled");
            xhr.ontimeout = () => finish("Upload timed out");
            try {
                xhr.open("PUT", url);
                xhr.timeout = 5 * 60 * 1000;
                for (const [name, value] of Object.entries(JSON.parse(headers))) xhr.setRequestHeader(name, value);
                xhr.send(file);
            } catch (error) { finish(String(error)); }
        },
        abortUpload(id) { uploads.get(id)?.abort(); },
        release(id) { uploads.get(id)?.abort(); files.delete(id); },
        cancel(id) { pickers.get(id)?.(); }
    };
})();
