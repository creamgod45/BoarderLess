(function () {
    "use strict";
    const records = new Map();
    const maxBytes = 200 * 1024 * 1024;
    function get(id) { const record = records.get(id); if (!record) throw new Error("Video is released"); return record; }
    function state(record, extra = {}) {
        const video = record.video;
        return JSON.stringify({
            durationMs: video && Number.isFinite(video.duration) ? Math.round(video.duration * 1000) : 0,
            positionMs: video && Number.isFinite(video.currentTime) ? Math.round(video.currentTime * 1000) : 0,
            width: video?.videoWidth || 0, height: video?.videoHeight || 0,
            playing: !!record.playAccepted && !!video && !video.paused && !video.ended, muted: video?.muted ?? true,
            ended: video?.ended || false, failed: !!record.failed || !!video?.error, released: false, ...extra,
        });
    }
    function update(record, extra) {
        if (extra?.failed) record.failed = true;
        record.update?.(state(record, extra));
    }
    function release(id) {
        const record = records.get(id);
        if (!record) return;
        records.delete(id);
        record.closed = true;
        for (const [name, handler] of record.listeners) record.video?.removeEventListener(name, handler);
        for (const cancel of record.frames.values()) cancel("Video is released");
        record.frames.clear();
        record.pendingOpen?.("Video is released");
        record.pendingOpen = null;
        if (record.video) {
            record.video.pause();
            record.video.removeAttribute("src");
            record.video.load();
        }
        if (record.url) URL.revokeObjectURL(record.url);
        record.parts = [];
        record.canvas = null;
        update(record, { playing: false, released: true });
        record.update = null;
        record.video = null;
    }
    function create(id, mime) {
        if (records.has(id) || !["video/mp4", "video/webm"].includes(mime) || document.hidden) throw new Error("Video is unavailable");
        records.set(id, { mime, parts: [], size: 0, sealed: false, listeners: [], frames: new Map(), closed: false, attached: false });
    }
    function append(id, encoded) {
        const record = get(id);
        if (record.sealed) throw new Error("Video is sealed");
        const binary = atob(encoded);
        if (!binary.length || binary.length > 1024 * 1024 || record.size + binary.length > maxBytes) throw new Error("Invalid video chunk");
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        record.parts.push(bytes); record.size += bytes.length;
    }
    function open(id, onState, done) {
        const record = get(id);
        if (record.sealed || !record.size) throw new Error("Invalid video state");
        // Kotlin calls this only after actual byte size and SHA-256 have been verified.
        record.sealed = true;
        record.update = onState;
        const video = document.createElement("video");
        record.video = video;
        video.muted = true; video.playsInline = true; video.preload = "auto";
        video.controls = false; video.autoplay = false; video.loop = false;
        record.url = URL.createObjectURL(new Blob(record.parts, { type: record.mime }));
        record.parts = [];
        let timer;
        const finish = error => {
            if (!record.pendingOpen) return;
            record.pendingOpen = null;
            clearTimeout(timer);
            done(error);
        };
        record.pendingOpen = finish;
        const listen = (name, handler) => { record.listeners.push([name, handler]); video.addEventListener(name, handler); };
        const ready = () => {
            if (video.duration === Infinity && !record.probed) {
                // Browser-recorded WebM may omit Duration: a seek discovers the finite file end.
                record.probed = true; record.probing = true; video.currentTime = 1e10; return;
            }
            if (!Number.isFinite(video.duration) || video.duration <= 0 || !video.videoWidth || !video.videoHeight || video.videoWidth * video.videoHeight > 12000000) {
                update(record, { failed: true, playing: false }); finish("Invalid video metadata"); return;
            }
            update(record); finish(null);
        };
        listen("loadeddata", ready);
        listen("seeked", () => {
            if (record.probing && Number.isFinite(video.duration)) {
                record.probing = false; record.resetting = true; video.currentTime = 0;
            } else if (record.resetting) { record.resetting = false; ready(); }
        });
        listen("error", () => { update(record, { failed: true, playing: false }); finish("Video cannot be decoded"); });
        for (const name of ["playing", "pause", "ended", "seeked", "volumechange", "timeupdate"]) listen(name, () => {
            if (name === "playing") record.playAccepted = true;
            if (name === "pause" || name === "ended") record.playAccepted = false;
            update(record);
            if (name === "pause" || name === "ended") for (const cancel of [...record.frames.values()]) cancel(null);
        });
        timer = setTimeout(() => { update(record, { failed: true, playing: false }); finish("Video preparation timed out"); }, 30000);
        video.src = record.url;
        video.load();
    }
    function attached(id, value) {
        const record = get(id);
        record.attached = value;
        if (!value) { record.playAccepted = false; record.video?.pause(); }
        else if (record.wantsPlay) record.video.play().then(() => {
            if (!record.closed) { record.playAccepted = !record.video.paused; update(record); }
        }).catch(() => { if (!record.closed) update(record, { failed: true, playing: false }); });
        update(record);
    }
    async function command(id, action, value, done) {
        try {
            const record = get(id), video = record.video;
            if (!video || !record.sealed) throw new Error("Video is not prepared");
            if (action === "play") {
                record.wantsPlay = !!value;
                if (!value) { record.playAccepted = false; video.pause(); }
                else if (record.attached) { if (video.ended) video.currentTime = 0; await video.play(); record.playAccepted = !video.paused; }
            } else if (action === "muted") video.muted = !!value;
            else if (action === "seek") video.currentTime = Math.max(0, Math.min(video.duration, value / 1000));
            else throw new Error("Unknown video command");
            if (record.closed) throw new Error("Video is released");
            update(record); done(null);
        } catch (_) { done("Video command failed"); }
    }
    function frame(id, request, done) {
        const record = get(id), video = record.video;
        let nativeRequest = null, completed = false, capturing = false, reader = null;
        const finish = (data, error) => {
            if (completed) return;
            completed = true; record.frames.delete(request); done(data, error);
        };
        const capture = () => {
            if (completed || capturing) return;
            capturing = true;
            if (record.closed) { finish(null, "Video is released"); return; }
            try {
                const canvas = record.canvas ||= document.createElement("canvas");
                canvas.width = video.videoWidth; canvas.height = video.videoHeight;
                canvas.getContext("2d").drawImage(video, 0, 0);
                canvas.toBlob(blob => {
                    if (completed) return;
                    if (!blob || record.closed) { finish(null, "Video frame is unavailable"); return; }
                    reader = new FileReader();
                    reader.onload = () => finish(record.closed ? null : String(reader.result).split(",")[1], record.closed ? "Video is released" : null);
                    reader.onerror = () => finish(null, "Video frame cannot be read");
                    reader.readAsDataURL(blob);
                }, "image/png");
            } catch (_) { finish(null, "Video frame cannot be captured"); }
        };
        record.frames.set(request, error => {
            if (nativeRequest !== null) video.cancelVideoFrameCallback?.(nativeRequest);
            if (error) { reader?.abort(); finish(null, error); } else capture();
        });
        if (!video || video.readyState < 2) { finish(null, "Video frame is unavailable"); return; }
        if (!video.paused && !video.ended && video.requestVideoFrameCallback) nativeRequest = video.requestVideoFrameCallback(capture);
        else capture();
    }
    function cancelFrame(id, request) { records.get(id)?.frames.get(request)?.("Video frame cancelled"); }
    document.addEventListener("visibilitychange", () => { if (document.hidden) for (const id of [...records.keys()]) release(id); });
    globalThis.addEventListener("pagehide", () => { for (const id of [...records.keys()]) release(id); });
    globalThis.boarderlessVideo = { create, append, open, command, frame, cancelFrame, attached, release };
})();
