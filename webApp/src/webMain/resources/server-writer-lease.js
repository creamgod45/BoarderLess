(() => {
    'use strict';
    const pending = new Map(), owners = new Set();
    let requesting = false, held = null, disposed = false;
    const name = 'boarderless.backend-writer.v1';
    function notify(done, accepted) { try { done(accepted); } catch (_) {} }
    function failPending() {
        const callbacks = [...pending.values()]; pending.clear();
        callbacks.forEach(done => notify(done, false));
    }
    function release(id) {
        const done = pending.get(id); pending.delete(id);
        owners.delete(id);
        if (done) notify(done, false);
        if (held && owners.size === 0) { held.active = false; held.release(); }
    }
    function acquire(id, done) {
        if (disposed || typeof id !== 'string' || !/^[0-9a-f-]{36}$/.test(id) ||
            pending.has(id) || owners.has(id) || pending.size + owners.size >= 256 ||
            !globalThis.isSecureContext || !globalThis.navigator?.locks?.request) {
            notify(done, false); return;
        }
        if (held?.active) { owners.add(id); notify(done, true); return; }
        pending.set(id, done);
        if (requesting) return;
        requesting = true;
        try {
            navigator.locks.request(name, { mode: 'exclusive', ifAvailable: true }, async lock => {
                requesting = false;
                if (!lock || disposed) { failPending(); return; }
                let resolve;
                const waiting = new Promise(r => { resolve = r; });
                const mine = { active: true, release: resolve }; held = mine;
                const entries = [...pending.entries()];
                for (const [token, callback] of entries) {
                    if (!pending.has(token)) continue;
                    pending.delete(token);
                    if (!mine.active || disposed) notify(callback, false);
                    else { owners.add(token); notify(callback, true); }
                }
                if (owners.size === 0) { mine.active = false; mine.release(); }
                try { await waiting; }
                finally { if (held === mine) { mine.active = false; held = null; owners.clear(); } }
            }).catch(() => {
                requesting = false;
                if (held) { held.active = false; held.release(); held = null; }
                owners.clear(); failPending();
            });
        } catch (_) { requesting = false; failPending(); }
    }
    globalThis.boarderlessServerWriterLease = Object.freeze({
        acquire, release, isHeld: id => !disposed && !!held?.active && owners.has(id)
    });
    globalThis.addEventListener('pagehide', () => {
        disposed = true; failPending(); owners.clear();
        if (held) { held.active = false; held.release(); }
    }); // Restored BFCache handles stay invalid; do not silently reacquire or resend.
})();
