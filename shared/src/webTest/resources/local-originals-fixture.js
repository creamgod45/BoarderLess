(() => {
    let original, database, mode, hold, waiter, addMethod, putMethod, committed, acknowledged, sawEarlySuccess, transactionMethod;
    function replace() {
        const api = globalThis.boarderlessCreateOriginalStore(database, async () => mode !== 'denied');
        globalThis.boarderlessOriginalStore = {
            operate(id, action, asset, token, offset, size, data, done) {
                api.operate(id, action, asset, token, offset, size, data, (value, error) => {
                    if (action === 'write' && !error) acknowledged++;
                    done(value, action === "requestSeal" && mode === "unknownIntentAck" && !error ? "Storage" : error);
                });
            }, cancel: id => api.cancel(id)
        };
    }
    globalThis.boarderlessOriginalFixture = {
        install(value) {
            original = globalThis.boarderlessOriginalStore;
            database = 'boarderless-original-fixture-' + crypto.randomUUID();
            mode = value; hold = mode === 'hold'; committed = acknowledged = 0; sawEarlySuccess = false;
            transactionMethod = IDBDatabase.prototype.transaction;
            addMethod = IDBObjectStore.prototype.add; putMethod = IDBObjectStore.prototype.put;
            IDBDatabase.prototype.transaction = function (...args) {
                const tx = transactionMethod.apply(this, args);
                if (this.name === database && args[1] === 'readwrite') tx.addEventListener('complete', () => committed++);
                return tx;
            };
            IDBObjectStore.prototype.add = function (...args) {
                const request = addMethod.apply(this, args);
                if (this.transaction.db.name === database && this.name === 'chunks') {
                    const store = this, tx = this.transaction;
                    request.addEventListener('success', () => {
                        sawEarlySuccess = sawEarlySuccess || acknowledged === 0;
                        if (mode === 'abort') { tx.abort(); return; }
                        if (hold) {
                            const pin = () => { if (!hold) return; const get = store.get(args[1]); get.onsuccess = pin; };
                            pin();
                        }
                        if (waiter) { const done = waiter; waiter = null; done(); }
                    });
                }
                return request;
            };
            IDBObjectStore.prototype.put = function (...args) {
                const request = putMethod.apply(this, args);
                if (this.transaction.db.name === database && this.name === 'manifests' && args[0].sealed && mode === 'abortSeal')
                    request.addEventListener('success', () => this.transaction.abort());
                return request;
            };
            replace();
        },
        reopen() { hold = false; mode = 'allow'; replace(); },
        waitForPut(done) { waiter = done; },
        value(name) { return String({ committed, acknowledged, sawEarlySuccess }[name]); },
        restore() {
            hold = false;
            globalThis.boarderlessOriginalStore = original;
            IDBDatabase.prototype.transaction = transactionMethod;
            IDBObjectStore.prototype.add = addMethod; IDBObjectStore.prototype.put = putMethod;
            indexedDB.deleteDatabase(database);
        }
    };
})();
