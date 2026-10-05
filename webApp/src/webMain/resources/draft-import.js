// Explicit local JSON selection only. No storage, upload, URL creation or file-handle retention.
(() => {
    const pending = new Map();
    globalThis.boarderlessDraftImport = {
        cancel(id) { const cancel = pending.get(id); if (cancel) cancel(); },
        pick(id, canRead, done) {
            if (pending.has(id) || !canRead() || document.hidden || !document.body) {
                done(null, 'Draft selection unavailable'); return;
            }
            const input = document.createElement('input');
            input.type = 'file'; input.accept = '.json,application/json'; input.multiple = false;
            input.style.display = 'none';
            let terminal = false, reading = false, focusTimer = null, deadline = null;
            const cleanup = () => {
                pending.delete(id);
                input.removeEventListener('change', change);
                input.removeEventListener('cancel', cancel);
                window.removeEventListener('focus', focus);
                document.removeEventListener('visibilitychange', visibility);
                if (focusTimer !== null) window.clearTimeout(focusTimer);
                if (deadline !== null) window.clearTimeout(deadline);
                input.value = ''; input.remove();
            };
            const finish = (content, error, silent = false) => {
                if (terminal) return;
                terminal = true; cleanup();
                if (!silent) done(content, error);
            };
            const cancel = () => finish(null, null);
            const visibility = () => { if (document.hidden) finish(null, 'Draft selection unavailable'); };
            const focus = () => {
                if (focusTimer !== null) window.clearTimeout(focusTimer);
                focusTimer = window.setTimeout(() => {
                    focusTimer = null;
                    if (!reading && (!input.files || !input.files.length)) cancel();
                }, 400);
            };
            const change = async () => {
                if (terminal || reading) return;
                reading = true;
                const file = input.files && input.files[0];
                input.value = '';
                if (!file) { cancel(); return; }
                try {
                    if (!canRead() || document.hidden || file.size < 1 || file.size > 4194304)
                        throw new Error('Unavailable');
                    // File is an immutable Blob snapshot; do not decode oversized inputs.
                    const buffer = await file.arrayBuffer();
                    if (terminal) return;
                    if (!canRead() || document.hidden || buffer.byteLength !== file.size || buffer.byteLength > 4194304)
                        throw new Error('Unavailable');
                    const text = new TextDecoder('utf-8', {fatal: true}).decode(buffer);
                    if (!canRead() || document.hidden) throw new Error('Unavailable');
                    finish(text, null);
                } catch (_) { finish(null, 'Draft file could not be read'); }
            };
            pending.set(id, () => finish(null, null, true));
            input.addEventListener('change', change);
            input.addEventListener('cancel', cancel);
            window.addEventListener('focus', focus);
            document.addEventListener('visibilitychange', visibility);
            deadline = window.setTimeout(() => finish(null, 'Draft selection timed out'), 120000);
            try { document.body.appendChild(input); input.click(); }
            catch (_) { finish(null, 'Draft selection unavailable'); }
        }
    };
})();
