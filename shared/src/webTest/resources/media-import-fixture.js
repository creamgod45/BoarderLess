globalThis.boarderlessMediaFixture = {
    recordVideo(done) {
        const canvas = document.createElement("canvas");
        canvas.width = 32; canvas.height = 32;
        const paint = color => { const context = canvas.getContext("2d"); context.fillStyle = color; context.fillRect(0, 0, 32, 32); };
        paint("white");
        const stream = canvas.captureStream(10);
        const recorder = new MediaRecorder(stream, { mimeType: "video/webm;codecs=vp8" });
        const parts = []; let timer;
        recorder.ondataavailable = event => { if (event.data.size) parts.push(event.data); };
        recorder.onerror = () => { clearInterval(timer); stream.getTracks().forEach(track => track.stop()); done(null, "Recorder failed"); };
        recorder.onstop = () => {
            clearInterval(timer); stream.getTracks().forEach(track => track.stop());
            const reader = new FileReader();
            reader.onload = () => done(String(reader.result).split(",")[1], null);
            reader.readAsDataURL(new Blob(parts, { type: "video/webm" }));
        };
        const started = performance.now();
        recorder.start();
        timer = setInterval(() => { paint(performance.now() - started < 300 ? "white" : "blue"); if (performance.now() - started > 800) recorder.stop(); }, 100);
    },
    videoAccounting(reset) {
        if (!this.videoStats) {
            this.videoStats = { created: 0, revoked: 0 };
            const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL);
            URL.createObjectURL = blob => { this.videoStats.created++; return create(blob); };
            URL.revokeObjectURL = url => { this.videoStats.revoked++; return revoke(url); };
        }
        if (reset) { this.videoStats.created = 0; this.videoStats.revoked = 0; }
        return JSON.stringify(this.videoStats);
    },
    hideVideoPage() { globalThis.dispatchEvent(new Event("pagehide")); },
    rejectNextVideoPlay() {
        const original = HTMLMediaElement.prototype.play;
        HTMLMediaElement.prototype.play = function () {
            HTMLMediaElement.prototype.play = original;
            return Promise.reject(new DOMException("Playback denied", "NotAllowedError"));
        };
    },
    install(mode) {
        if (mode === "upload") {
            const originalXHR = globalThis.XMLHttpRequest;
            globalThis.XMLHttpRequest = class {
                upload = {};
                status = 204;
                headers = {};
                open(method, url) { this.method = method; this.url = url; }
                setRequestHeader(name, value) { this.headers[name] = value; }
                send(file) {
                    globalThis.XMLHttpRequest = originalXHR;
                    globalThis.boarderlessMediaFixture.nativeUpload = file instanceof File && file.size === 3 &&
                        this.method === "PUT" && this.headers["x-storage-token"] === "secret" &&
                        this.headers["Content-Type"] === "image/png";
                    queueMicrotask(() => { this.upload.onprogress({ loaded: 2 }); this.onload(); });
                }
                abort() { this.onabort(); }
            };
        }
        const original = HTMLInputElement.prototype.click;
        HTMLInputElement.prototype.click = function () {
            HTMLInputElement.prototype.click = original;
            if (mode === "cancel") {
                this.dispatchEvent(new Event("cancel"));
            } else {
                const transfer = new DataTransfer();
                transfer.items.add(new File([new Uint8Array([97, 98, 99])], "abc.png", { type: "image/png" }));
                this.files = transfer.files;
                this.dispatchEvent(new Event("change"));
            }
        };
    },
    inputRemoved() { return !document.querySelector("input[type=file]"); },
    nativeUploadUsed() { return this.nativeUpload === true; }
};
