(() => {
    let original, originalLibrary, api, database, mode, hold, putWaiter, committed, acknowledged, sawEarlySuccess, transactionMethod, putMethod;
    function open() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(database, 1);
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(new Error('fixture db'));
        });
    }
    globalThis.boarderlessRecordFixture = {
        install(value) {
            mode = value; database = 'boarderless-receipt-fixture-' + crypto.randomUUID();
            original = globalThis.boarderlessDurableRecords;
            originalLibrary = globalThis.boarderlessQuickSchemeRecords;
            committed = 0; acknowledged = 0; sawEarlySuccess = false; hold = mode === 'hold';
            transactionMethod = IDBDatabase.prototype.transaction;
            putMethod = IDBObjectStore.prototype.put;
            IDBDatabase.prototype.transaction = function (...args) {
                const tx = transactionMethod.apply(this, args);
                if (this.name === database && args[1] === 'readwrite') tx.addEventListener('complete', () => committed++);
                return tx;
            };
            IDBObjectStore.prototype.put = function (...args) {
                const request = putMethod.apply(this, args);
                if (this.transaction.db.name === database) {
                    const store = this, tx = this.transaction;
                    request.addEventListener('success', () => {
                        sawEarlySuccess = sawEarlySuccess || (acknowledged === 0 && committed === 0);
                        if (mode === 'abort' || (mode === 'abortConfirmation' && committed >= 1)) { tx.abort(); return; }
                        if (hold) {
                            const pin = () => {
                                if (!hold) return;
                                const get = store.get(args[1]);
                                get.onsuccess = pin;
                            };
                            pin();
                        }
                        if (putWaiter) { const waiter = putWaiter; putWaiter = null; waiter(); }
                    });
                }
                return request;
            };
            api = globalThis.boarderlessCreateDurableRecords(database, async () => mode !== 'denied', 4 * 1024 * 1024);
            globalThis.boarderlessDurableRecords = {
                operate(id, action, key, expected, next, done) {
                    api.operate(id, action, key, expected, next, (value, error) => {
                        if (action === 'cas' && !error) acknowledged++;
                        done(value, error);
                    });
                }, cancel: id => api.cancel(id)
            };
            globalThis.boarderlessQuickSchemeRecords = globalThis.boarderlessDurableRecords;
        },
        reopen() {
            api = globalThis.boarderlessCreateDurableRecords(database, async () => true, 4 * 1024 * 1024);
            globalThis.boarderlessDurableRecords = api;
            globalThis.boarderlessQuickSchemeRecords = api;
            hold = false; mode = 'allow';
        },
        waitForPut(done) { putWaiter = done; },
        value(name) { return String({ committed, acknowledged, sawEarlySuccess }[name]); },
        corrupt(key, done) {
            open().then(db => {
                const tx = db.transaction('records', 'readwrite', { durability: 'strict' });
                tx.objectStore('records').put('{invalid}', key);
                tx.oncomplete = () => { db.close(); done(); };
            });
        },
        restore() {
            hold = false;
            globalThis.boarderlessDurableRecords = original;
            globalThis.boarderlessQuickSchemeRecords = originalLibrary;
            IDBDatabase.prototype.transaction = transactionMethod;
            IDBObjectStore.prototype.put = putMethod;
            indexedDB.deleteDatabase(database);
        }
    };
})();
