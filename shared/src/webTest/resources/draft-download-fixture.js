// Intercept only this explicit download: real Blob/object URLs, never real disk downloads.
globalThis.boarderlessDraftDownloadFixture = {
    install() {
        if (this.state) throw new Error('Fixture already installed');
        const state = this.state = {
            create: URL.createObjectURL, revoke: URL.revokeObjectURL,
            click: HTMLAnchorElement.prototype.click,
            timeout: window.setTimeout, clearTimeout: window.clearTimeout,
            hidden: Object.getOwnPropertyDescriptor(document, 'hidden'),
            urls: new Set(), timers: new Map(), created: 0, revoked: 0, clicks: 0,
            serial: -10000, filename: '', blob: null, failClick: false
        };
        Object.defineProperty(document, 'hidden', {configurable: true, value: false});
        URL.createObjectURL = blob => {
            const url = state.create.call(URL, blob);
            state.blob = blob; state.created++; state.urls.add(url); return url;
        };
        URL.revokeObjectURL = url => {
            if (state.urls.delete(url)) state.revoked++;
            state.revoke.call(URL, url);
        };
        HTMLAnchorElement.prototype.click = function() {
            if (state.failClick) throw new Error('Fixture blocked dispatch');
            if (!this.isConnected || !this.href.startsWith('blob:')) throw new Error('Invalid download anchor');
            state.clicks++; state.filename = this.download;
        };
        window.setTimeout = (callback, delay, ...args) => {
            if (delay !== 60000) return state.timeout.call(window, callback, delay, ...args);
            const id = state.serial--; state.timers.set(id, callback); return id;
        };
        window.clearTimeout = id => {
            if (!state.timers.delete(id)) state.clearTimeout.call(window, id);
        };
    },
    value(field) {
        const s = this.state;
        if (field === 'type') return s.blob ? s.blob.type : '';
        if (field === 'urls') return String(s.urls.size);
        if (field === 'timers') return String(s.timers.size);
        if (field === 'anchors') return String(document.querySelectorAll('a[download][href^="blob:"]').length);
        return String(s[field]);
    },
    text(done) { this.state.blob.text().then(done, () => done('FIXTURE_READ_FAILED')); },
    failClick() { this.state.failClick = true; },
    expire() {
        const callbacks = Array.from(this.state.timers.values()); this.state.timers.clear();
        callbacks.forEach(callback => callback());
    },
    restore() {
        const s = this.state;
        if (!s) return;
        s.urls.forEach(url => s.revoke.call(URL, url));
        URL.createObjectURL = s.create; URL.revokeObjectURL = s.revoke;
        HTMLAnchorElement.prototype.click = s.click;
        window.setTimeout = s.timeout; window.clearTimeout = s.clearTimeout;
        if (s.hidden) Object.defineProperty(document, 'hidden', s.hidden); else delete document.hidden;
        this.state = null;
    }
};
