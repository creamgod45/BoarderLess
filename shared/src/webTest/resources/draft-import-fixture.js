// Real input/File/Blob/decoder; intercept click so no OS picker opens in automated tests.
globalThis.boarderlessDraftImportFixture = {
    install(mode) {
        if (this.state) throw new Error('Fixture already installed');
        const state = this.state = {mode, click: HTMLInputElement.prototype.click,
            hidden: Object.getOwnPropertyDescriptor(document, 'hidden'), inputs: [], release: null};
        Object.defineProperty(document, 'hidden', {configurable: true, value: false});
        HTMLInputElement.prototype.click = function() {
            if (this.type !== 'file') return state.click.call(this);
            state.inputs.push(this);
            if (mode === 'cancel') { this.dispatchEvent(new Event('cancel')); return; }
            let file;
            if (mode === 'oversized') file = {size: 4194305, arrayBuffer() { throw new Error('Must not read'); }};
            else if (mode === 'invalid') file = new File([new Uint8Array([0xC3, 0x28])], 'private.json');
            else if (mode === 'held') file = {size: 2, arrayBuffer() {
                return new Promise(resolve => { state.release = () => new Blob(['{}']).arrayBuffer().then(resolve); });
            }};
            else file = new File(['\uFEFF{"text":"中文🙂"}'], 'private.json', {type: 'application/json'});
            Object.defineProperty(this, 'files', {configurable: true, value: [file]});
            this.dispatchEvent(new Event('change'));
        };
    },
    inputs() { return this.state.inputs.filter(input => input.isConnected).length; },
    release(done) { this.state.release().then(() => window.setTimeout(done, 0)); },
    restore() {
        const s = this.state;
        if (!s) return;
        HTMLInputElement.prototype.click = s.click;
        s.inputs.forEach(input => input.remove());
        if (s.hidden) Object.defineProperty(document, 'hidden', s.hidden); else delete document.hidden;
        this.state = null;
    }
};
