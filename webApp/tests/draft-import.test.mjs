import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const code = readFileSync(new URL('../src/webMain/resources/draft-import.js', import.meta.url), 'utf8');
function setup(file) {
    const timers = new Map(); let serial = 0;
    const window = new EventTarget();
    window.setTimeout = (fn, ms) => { timers.set(++serial, {fn, ms}); return serial; };
    window.clearTimeout = id => timers.delete(id);
    const document = new EventTarget(); document.hidden = false;
    const children = new Set();
    document.body = {appendChild(input) { children.add(input); }};
    let input;
    document.createElement = () => {
        input = new EventTarget(); input.style = {}; input.files = file ? [file] : [];
        input.remove = () => children.delete(input); input.click = () => {};
        return input;
    };
    const context = vm.createContext({window, document, TextDecoder, Map});
    vm.runInContext(code, context);
    const results = [];
    return { api: context.boarderlessDraftImport, document, window, timers, children, results,
        input: () => input, pick(guard = () => true) { context.boarderlessDraftImport.pick('id', guard, (s, e) => results.push([s, e])); },
        fire(ms) { const [id, timer] = [...timers.entries()].find(([, timer]) => timer.ms === ms); timers.delete(id); timer.fn(); },
        clean() { assert.equal(children.size, 0); assert.equal(timers.size, 0); } };
}
const settle = () => new Promise(resolve => setImmediate(resolve));

test('reads real Blob Unicode/BOM bytes, publishes once and releases DOM/timers', async () => {
    const text = '{"text":"私密🙂"}'; const fixture = setup(new Blob(['\uFEFF', text])); fixture.pick();
    assert.equal(fixture.input().accept, '.json,application/json');
    fixture.input().dispatchEvent(new Event('change')); await settle();
    assert.deepEqual(fixture.results, [[text, null]]); fixture.clean();
    fixture.input().dispatchEvent(new Event('change')); assert.equal(fixture.results.length, 1);
});
test('size and strict UTF8 failures are bounded and sanitized', async () => {
    for (const file of [new Blob([]), new Blob([new Uint8Array([0xC3, 0x28])]),
        {size: 4194305, arrayBuffer() { throw new Error('private filename must not be read'); }}]) {
        const f = setup(file); f.pick(); f.input().dispatchEvent(new Event('change')); await settle();
        assert.deepEqual(f.results, [[null, 'Draft file could not be read']]); f.clean();
    }
});
test('cancel event, legacy focus fallback, hidden and deadline release selection', () => {
    for (const mode of ['cancel', 'focus', 'hidden', 'deadline']) {
        const f = setup(null); f.pick();
        if (mode === 'cancel') f.input().dispatchEvent(new Event('cancel'));
        if (mode === 'focus') { f.window.dispatchEvent(new Event('focus')); f.fire(400); }
        if (mode === 'hidden') { f.document.hidden = true; f.document.dispatchEvent(new Event('visibilitychange')); }
        if (mode === 'deadline') f.fire(120000);
        assert.equal(f.results.length, 1); assert.equal(f.results[0][0], null); f.clean();
    }
});
test('coroutine cancel and scope expiry reject delayed file read', async () => {
    for (const mode of ['cancel', 'scope']) {
        let resolve, active = true;
        const f = setup({size: 2, arrayBuffer: () => new Promise(r => { resolve = r; })}); f.pick(() => active);
        f.input().dispatchEvent(new Event('change'));
        if (mode === 'cancel') f.api.cancel('id'); else active = false;
        resolve(new TextEncoder().encode('{}').buffer); await settle();
        if (mode === 'cancel') assert.equal(f.results.length, 0);
        else assert.deepEqual(f.results, [[null, 'Draft file could not be read']]);
        f.clean();
    }
});
test('unavailable scope never opens picker', () => {
    const f = setup(null); f.pick(() => false); assert.equal(f.input(), undefined);
    assert.deepEqual(f.results, [[null, 'Draft selection unavailable']]); f.clean();
});
