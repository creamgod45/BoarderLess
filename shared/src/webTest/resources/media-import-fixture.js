globalThis.boarderlessMediaFixture = {
    recordVideo(done) {
        // Historical bridge name: this returns a fixed codec fixture, NOT a live recording.
        // 32x32 VP8, 10fps: 3 white frames then 6 blue frames. Streaming WebM deliberately
        // omits Duration, retaining the native player's duration-probe/reset coverage.
        // Timer-driven canvas capture requests do not acknowledge encoded frames; keeping
        // the input fixed prevents recorder scheduling from changing the pixel oracle.
        const encoded = "GkXfo59ChoEBQveBAULygQRC84EIQoKEd2VibUKHgQJChYECGFOAZwH/////////EU2bdKtNu4tTq4QVSalmU6yBoU27i1OrhBZUrmtTrIHLTbuMU6uEElTDZ1OsggEY7AEAAAAAAABoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVSalmpSrXsYMPQkBNgIxMYXZmNjMuMS4xMDJXQYxMYXZmNjMuMS4xMDIWVK5ryK4BAAAAAAAAP9eBAXPFiPSq4ziT6508nIEAIrWcg3VuZIiBAIaFVl9WUDiDgQEj44OEBfXhAOCQsIEguoEgmoECVbCEVbmBARJUw2fWc3OfY8CAZ8iZRaOHRU5DT0RFUkSHjExhdmY2My4xLjEwMnNzsWPAi2PFiPSq4ziT6508Z8igRaOHRU5DT0RFUkSHk0xhdmM2My4xLjEwMiBsaWJ2cHgfQ7Z1QTHngQCjpIEAAIAwAgCdASogACAAAEcIhYWIhYSIAgIAB5DzycD+/6PeAKOVgQBkALEBAAUQrAAYABhYL/QACHAAo5WBAMgAsQEABRCsABgAGFgv9AAIcACjvYEBLIDQAgCdASogACAAAEcIhYWIhYSIAgICdaoD+AIIIQg9AP7/TRL//FhX8WFfxYV/8WFf/PzO7cX85gCjlYEBkACxAQAFEKwAGAAYWC/0AAhwAKOVgQH0ALEBAAUQrAAYABhYL/QACHAAo72BAliA0AIAnQEqIAAgAABHCIWFiIWEiAICAnWqA/gCCCEIPQD+/00S//xYV/FhX8WFf/FhX/z8zu3F/OYAo5WBArwAsQEABRCsABgAGFgv9AAIcACjlYEDIACxAQAFEKwAGAAYWC/0AAhwAA==";
        setTimeout(() => done(encoded, null), 0);
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
