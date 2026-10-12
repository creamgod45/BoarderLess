(() => {
    'use strict';
    const maxSize = 200 * 1024 * 1024, chunkSize = 1024 * 1024;
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
    const checksum = /^sha256:[0-9a-f]{64}$/;
    function create(databaseName, persistent) {
        const operations = new Map();
        const fail = () => new Error('Original storage failed');
        const integer = raw => { if (!/^(0|[1-9][0-9]*)$/.test(raw)) throw fail(); const n = Number(raw); if (!Number.isSafeInteger(n) || n > maxSize) throw fail(); return n; };
        function manifest(value) {
            if (!value || !uuid.test(value.token) || !Number.isSafeInteger(value.size) || value.size < 1 || value.size > maxSize ||
                !Number.isSafeInteger(value.next) || value.next < 0 || value.next > value.size || typeof value.sealed !== 'boolean' || typeof value.completionRequested !== 'boolean' ||
                (value.completionRequested ? !checksum.test(value.requestedChecksum) : value.requestedChecksum !== null) ||
                (value.sealed && (!value.completionRequested || value.requestedChecksum !== value.checksum)) ||
                (value.sealed ? value.next !== value.size || !checksum.test(value.checksum) : value.checksum !== null)) throw fail();
            return value;
        }
        function finish(op, value, error) {
            if (op.finished) return;
            op.finished = true; operations.delete(op.requestId);
            if (op.db) op.db.close();
            if (!op.cancelled) op.done(value, error ? 'Storage' : null);
        }
        function cancel(id) {
            const op = operations.get(id); if (!op || op.finished) return;
            op.cancelled = true;
            if (op.tx) { try { op.tx.abort(); } catch (_) {} }
            finish(op, null, true);
        }
        function encode(bytes) {
            let binary = '';
            for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
            return btoa(binary);
        }
        function decode(raw) {
            if (typeof raw !== 'string' || raw.length < 4 || raw.length > 4 * Math.ceil(chunkSize / 3)) throw fail();
            const binary = atob(raw); if (!binary.length || binary.length > chunkSize) throw fail();
            return Uint8Array.from(binary, c => c.charCodeAt(0));
        }
        async function begin(op) {
            if (!await persistent()) throw fail(); if (op.cancelled) return;
            const opening = indexedDB.open(databaseName, 1);
            opening.onupgradeneeded = () => { opening.result.createObjectStore('manifests'); opening.result.createObjectStore('chunks'); opening.result.createObjectStore('usedIds'); };
            opening.onerror = opening.onblocked = () => finish(op, null, true);
            opening.onsuccess = () => {
                const db = opening.result;
                if (op.finished || op.cancelled) { db.close(); return; }
                op.db = db; db.onversionchange = () => { cancel(op.requestId); db.close(); };
                try {
                    const write = op.action !== 'read' && op.action !== 'status';
                    const tx = db.transaction(['manifests', 'chunks', 'usedIds'], write ? 'readwrite' : 'readonly', write ? { durability: 'strict' } : undefined);
                    op.tx = tx;
                    tx.onerror = tx.onabort = () => finish(op, null, true);
                    let result = null; tx.oncomplete = () => finish(op, result, false);
                    if (write && tx.durability !== 'strict') { tx.abort(); return; }
                    const records = tx.objectStore('manifests'), chunks = tx.objectStore('chunks');
                    const get = records.get(op.assetId);
                    get.onsuccess = () => {
                        try {
                            const old = get.result === undefined ? null : manifest(get.result);
                            if (op.action === 'begin' || (op.action === 'resume' && !old)) {
                                if (old) throw fail();
                                // IDs are never recycled after abandon: a delayed seal cannot publish a replacement.
                                tx.objectStore('usedIds').add(true, op.assetId);
                                records.add({ token: op.token, size: op.size, next: 0, sealed: false, checksum: null, completionRequested: false, requestedChecksum: null }, op.assetId);
                                if (op.action === 'resume') result = '0';
                                return;
                            }
                            if (op.action === 'status') {
                                if (!old) { result = 'missing'; return; }
                                if (old.size !== op.size || (old.sealed && old.checksum !== op.data)) throw fail();
                                if (old.completionRequested && old.requestedChecksum !== op.data) throw fail();
                                result = old.sealed ? 'ready' : old.completionRequested ? 'recoverable' : 'pending'; return;
                            }
                            if (op.action === 'remove' && !old) { tx.objectStore('usedIds').put(true, op.assetId); return; }
                            if (!old || old.size !== op.size || (op.token !== null && old.token !== op.token)) throw fail();
                            if (op.action === 'resume') {
                                if (old.sealed || old.completionRequested) throw fail();
                                result = String(old.next); return;
                            }
                            if (op.action === 'write') {
                                if (old.sealed || old.completionRequested || old.next !== op.offset) throw fail();
                                const bytes = decode(op.data);
                                if (bytes.length > old.size - old.next) throw fail();
                                chunks.add(bytes, [op.assetId, op.offset]);
                                records.put({ ...old, next: old.next + bytes.length }, op.assetId);
                            } else if (op.action === 'read') {
                                if (op.token === null && (!old.sealed || old.checksum !== op.data)) throw fail();
                                if (op.offset >= old.next) throw fail();
                                const chunk = chunks.get([op.assetId, op.offset]);
                                chunk.onsuccess = () => {
                                    try {
                                        const bytes = chunk.result;
                                        if (!(bytes instanceof Uint8Array) || !bytes.length || bytes.length > chunkSize || bytes.length > old.next - op.offset) throw fail();
                                        result = encode(bytes);
                                    } catch (_) { tx.abort(); }
                                };
                            } else if (op.action === 'requestSeal') {
                                if (old.next !== old.size || !checksum.test(op.data) || (old.completionRequested && old.requestedChecksum !== op.data)) throw fail();
                                records.put({ ...old, completionRequested: true, requestedChecksum: op.data }, op.assetId);
                            } else if (op.action === 'seal') {
                                if (!old.completionRequested || old.requestedChecksum !== op.data || old.next !== old.size || !checksum.test(op.data) || (old.sealed && old.checksum !== op.data)) throw fail();
                                records.put({ ...old, sealed: true, checksum: op.data }, op.assetId);
                            } else if (op.action === 'remove') {
                                if (old.sealed || old.completionRequested) throw fail();
                                const cursor = chunks.openCursor(IDBKeyRange.bound([op.assetId, 0], [op.assetId, maxSize]));
                                cursor.onsuccess = () => { const row = cursor.result; if (row) { row.delete(); row.continue(); } };
                                records.delete(op.assetId);
                            }
                        } catch (_) { tx.abort(); }
                    };
                } catch (_) { finish(op, null, true); }
            };
        }
        return Object.freeze({
            operate(requestId, action, assetId, token, offset, size, data, done) {
                if (operations.has(requestId) || !uuid.test(requestId) || !uuid.test(assetId) ||
                    !['begin', 'resume', 'write', 'read', 'seal', 'remove', 'status', 'requestSeal'].includes(action) || typeof done !== 'function' ||
                    (['read', 'status'].includes(action) ? token !== null && !uuid.test(token) : !uuid.test(token))) throw fail();
                const op = { requestId, action, assetId, token, offset: integer(offset), size: integer(size), data, done, finished: false, cancelled: false, db: null, tx: null };
                if (op.size < 1 || op.offset > op.size) throw fail();
                operations.set(requestId, op); begin(op).catch(() => finish(op, null, true));
            }, cancel
        });
    }
    globalThis.boarderlessCreateOriginalStore = create;
    globalThis.boarderlessOriginalStore = create('BoarderLess.local-originals.v1', async () => {
        const storage = navigator.storage;
        if (!globalThis.isSecureContext || !storage || !storage.persisted || !storage.persist) return false;
        if (!await storage.persisted() && !await storage.persist()) return false;
        return await storage.persisted();
    });
})();
