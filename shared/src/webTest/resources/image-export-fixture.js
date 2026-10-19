globalThis.boarderlessImageExportFixture = {
    async tiny(done) {
        const canvas = new OffscreenCanvas(16, 8), context = canvas.getContext('2d');
        context.fillStyle = 'rgba(0,255,0,0.5019607843137255)'; context.fillRect(0, 0, 16, 8);
        const blob = await canvas.convertToBlob({type:'image/png'});
        const bytes = new Uint8Array(await blob.arrayBuffer());
        canvas.width = canvas.height = 0; done(btoa(String.fromCharCode(...bytes)));
    },
    async jpeg(done) {
        const canvas = new OffscreenCanvas(200, 100), context = canvas.getContext('2d');
        context.fillStyle = 'red'; context.fillRect(0, 0, 100, 100);
        context.fillStyle = 'blue'; context.fillRect(100, 0, 100, 100);
        const blob = await canvas.convertToBlob({type:'image/jpeg', quality:0.95});
        const bytes = new Uint8Array(await blob.arrayBuffer());
        const exif = Uint8Array.of(255,225,0,34,69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0);
        const metadata = new TextEncoder().encode('http://ns.adobe.com/xap/1.0/\0<fixture/>');
        const xmp = new Uint8Array(metadata.length + 4); xmp.set([255,225,(metadata.length + 2) >> 8,(metadata.length + 2) & 255]); xmp.set(metadata,4);
        const output = new Uint8Array(bytes.length + exif.length + xmp.length);
        output.set(bytes.subarray(0,2)); output.set(exif,2); output.set(xmp,2 + exif.length); output.set(bytes.subarray(2),2 + exif.length + xmp.length);
        canvas.width = canvas.height = 0; done(btoa(String.fromCharCode(...output)));
    },
    async original(done) {
        const canvas = new OffscreenCanvas(6000, 2400), context = canvas.getContext('2d');
        context.fillStyle = 'rgba(0,255,0,0.5019607843137255)'; context.fillRect(0, 0, 6000, 2400);
        context.fillStyle = 'red'; context.fillRect(0, 0, 64, 64);
        context.fillStyle = 'blue'; context.fillRect(0, 2336, 64, 64);
        for (let stripe = 0; stripe < 16; stripe++) {
            context.fillStyle = stripe % 2 ? 'white' : 'black'; context.fillRect(5872 + stripe * 8, 2272, 8, 128);
        }
        const blob = await canvas.convertToBlob({type:'image/png'});
        const bytes = new Uint8Array(await blob.arrayBuffer());
        let text = ''; for (let p = 0; p < bytes.length; p += 32768) text += String.fromCharCode(...bytes.subarray(p, p + 32768));
        canvas.width = canvas.height = 0; done(btoa(text));
    },
    active() { return globalThis.boarderlessImageExport.activeCount(); },
    installDelay() {
        if (this.delay) throw new Error('Already installed');
        const saved = globalThis.createImageBitmap, state = this.delay = {saved, waiting: null, resolve: null, closes: 0};
        globalThis.createImageBitmap = (...args) => new Promise((resolve, reject) => {
            state.waiting = {args, reject}; state.resolve = resolve;
        });
    },
    waiting() { return !!(this.delay && this.delay.waiting); },
    async completeDelay(done) {
        const state = this.delay, bitmap = await state.saved.call(globalThis, ...state.waiting.args);
        const close = bitmap.close.bind(bitmap);
        bitmap.close = () => { state.closes++; close(); };
        state.resolve(bitmap); queueMicrotask(() => done());
    },
    closes() { return this.delay ? this.delay.closes : 0; },
    restore() { if (this.delay) { globalThis.createImageBitmap = this.delay.saved; this.delay = null; } }
};
