(() => {
    'use strict';
    // The factory permits isolated-origin tests with an explicit persistence-grant fixture.
    // Production uses the actual StorageManager grant and never falls back to volatile storage.
    function createRecords(databaseName, persistent, maximumBytes = 1024 * 1024) {
        const operations = new Map();
        const fail = () => new Error('Durable storage failed');
        function finish(op, value, error) {
            if (op.finished) return;
            op.finished = true;
            operations.delete(op.id);
            if (op.db) op.db.close();
            // Notify terminal settlement even after Kotlin cancellation, so its writer
            // accounting cannot release the Web Lock before this transaction finishes.
            op.done(value, error || op.cancelled ? 'Storage' : null);
        }
        function validate(raw) {
            if (typeof raw !== 'string' || raw.length > 2 * maximumBytes + 4096) throw fail();
            const value = JSON.parse(raw);
            if (!value || Object.keys(value).sort().join(',') !== 'checksum,generation,payload' ||
                !Number.isSafeInteger(value.generation) || value.generation < 1 ||
                typeof value.payload !== 'string' || typeof value.checksum !== 'string' || !/^[0-9a-f]{64}$/.test(value.checksum)) throw fail();
            return value;
        }
        function cancel(id) {
            const op = operations.get(id);
            if (!op || op.finished) return;
            op.cancelled = true;
            if (op.tx) {
                try { op.tx.abort(); } catch (_) {} // Commit may already be in progress.
                return; // Only onabort/oncomplete can settle a started transaction.
            }
            finish(op, null, true);
        }
        async function begin(op) {
            if (!await persistent()) throw fail();
            if (op.cancelled) return;
            const opening = indexedDB.open(databaseName, 1);
            opening.onupgradeneeded = () => opening.result.createObjectStore('records');
            opening.onerror = opening.onblocked = () => finish(op, null, true);
            opening.onsuccess = () => {
                const db = opening.result;
                if (op.finished || op.cancelled) { db.close(); return; }
                op.db = db;
                db.onversionchange = () => { cancel(op.id); db.close(); };
                try {
                    const write = op.action === 'cas';
                    const tx = db.transaction('records', write ? 'readwrite' : 'readonly', write ? { durability: 'strict' } : undefined);
                    op.tx = tx;
                    tx.onabort = () => finish(op, null, true);
                    tx.onerror = () => { try { tx.abort(); } catch (_) {} };
                    let result = null;
                    tx.oncomplete = () => finish(op, result, false);
                    if (write && tx.durability !== 'strict') { tx.abort(); return; }
                    const records = tx.objectStore('records');
                    const get = records.get(op.key);
                    get.onsuccess = () => {
                        try {
                            const previous = get.result;
                            if (previous !== undefined) validate(previous);
                            if (!write) { result = previous === undefined ? null : previous; return; }
                            if ((previous === undefined ? null : previous) !== op.expected) throw fail();
                            const candidate = validate(op.next);
                            const oldGeneration = previous === undefined ? 0 : validate(previous).generation;
                            if (op.next === previous ? candidate.generation !== oldGeneration : candidate.generation !== oldGeneration + 1) throw fail();
                            records.put(op.next, op.key);
                            // No success callback here: only tx.oncomplete acknowledges durable publication.
                        } catch (_) { tx.abort(); }
                    };
                } catch (_) {
                    if (op.tx) { try { op.tx.abort(); } catch (_) {} }
                    else finish(op, null, true);
                }
            };
        }
        return Object.freeze({
            operate(id, action, key, expected, next, done) {
                if (operations.has(id) || !/^[A-Za-z0-9_-]{1,128}$/.test(id) || !/^[a-f0-9]{64}$/.test(key) ||
                    !['read', 'cas'].includes(action) || typeof done !== 'function') throw fail();
                const op = { id, action, key, expected, next, done, finished: false, cancelled: false, tx: null, db: null };
                operations.set(id, op);
                begin(op).catch(() => finish(op, null, true));
            }, cancel
        });
    }
    globalThis.boarderlessCreateDurableRecords = createRecords;
    const persistenceGranted = async () => {
        const storage = navigator.storage;
        if (!globalThis.isSecureContext || !storage || !storage.persisted || !storage.persist) return false;
        if (!await storage.persisted() && !await storage.persist()) return false;
        return await storage.persisted();
    };
    globalThis.boarderlessDurableRecords = createRecords('BoarderLess.bundle-receipts.v1', persistenceGranted);
    globalThis.boarderlessAssetCatalogRecords = createRecords('BoarderLess.local-asset-catalog.v1', persistenceGranted, 4 * 1024 * 1024);
    globalThis.boarderlessDocumentRecords = createRecords('BoarderLess.local-documents.v1', persistenceGranted, 4 * 1024 * 1024);
    globalThis.boarderlessQuickSchemeRecords = createRecords('BoarderLess.quick-schemes.v1', persistenceGranted, 4 * 1024 * 1024);
})();
