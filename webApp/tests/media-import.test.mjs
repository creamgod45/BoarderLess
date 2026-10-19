import { test } from "node:test";
import assert from "node:assert/strict";

class Input extends EventTarget {
    style = {};
    files = [];
    remove() { this.removed = true; }
    click() { globalThis.selectedInput = this; }
}
globalThis.window = new EventTarget();
globalThis.document = { createElement: () => new Input(), body: { appendChild() {} } };
await import("../src/webMain/resources/media-import.js");
const bridge = globalThis.boarderlessMedia;

function choose(id, file) {
    const result = new Promise(resolve => bridge.pick(id, (value, error) => resolve({ value, error })));
    selectedInput.files = [file];
    selectedInput.dispatchEvent(new Event("change"));
    return result;
}
function read(id, offset, length) {
    return new Promise(resolve => bridge.read(id, offset, length, (value, error) => resolve({ value, error })));
}

test("selected file metadata and bounded slices retain exact binary contents", async () => {
    const file = new File([new Uint8Array([0, 128, 255, 1])], "test.gif", { type: "image/gif" });
    const result = await choose("slice", file);
    const metadata = JSON.parse(result.value);
    assert.equal(metadata.byteSize, 4);
    assert.equal(metadata.mediaType, "image/gif");
    assert.deepEqual([...Buffer.from((await read(metadata.fileId, 1, 2)).value, "base64")], [128, 255]);
    assert.equal((await read(metadata.fileId, 4, 1)).value, "");
    bridge.release(metadata.fileId);
    assert.match((await read(metadata.fileId, 0, 1)).error, /no longer available/);
});

test("native picker cancellation completes once and removes the hidden input", () => {
    let calls = 0;
    bridge.pick("cancel", (value, error) => { calls++; assert.equal(value, null); assert.equal(error, null); });
    const input = selectedInput;
    input.dispatchEvent(new Event("cancel"));
    input.dispatchEvent(new Event("change"));
    bridge.cancel("cancel");
    assert.equal(calls, 1);
    assert.equal(input.removed, true);
});

test("focus before delayed file change must not cancel a successful selection", async () => {
    const originalSetTimeout = globalThis.setTimeout;
    const originalClearTimeout = globalThis.clearTimeout;
    const timers = new Map();
    let nextTimer = 0;
    globalThis.setTimeout = (callback, delay) => { const id = ++nextTimer; timers.set(id, { callback, delay }); return id; };
    globalThis.clearTimeout = id => timers.delete(id);
    const results = [];
    try {
        bridge.pick("delayed-change", (value, error) => results.push({ value, error }));
        const input = selectedInput;
        window.dispatchEvent(new Event("focus"));
        window.dispatchEvent(new Event("focus"));
        // Native focus may precede file-provider resolution by more than the former 250ms.
        for (const [id, timer] of [...timers]) {
            if (timer.delay <= 1000) { timers.delete(id); timer.callback(); }
        }
        assert.equal(results.length, 0, "Focus with no File is not evidence of cancellation");
        assert.notEqual(input.removed, true);
        input.files = [new File(["abc"], "delayed.png", { type: "image/png" })];
        input.dispatchEvent(new Event("change"));
        assert.equal(results.length, 1);
        assert.equal(JSON.parse(results[0].value).name, "delayed.png");
        assert.equal(results[0].error, null);
        assert.equal(input.removed, true);
        assert.equal(timers.size, 0);
        bridge.release("delayed-change.file");
    } finally {
        bridge.cancel("delayed-change");
        globalThis.setTimeout = originalSetTimeout;
        globalThis.clearTimeout = originalClearTimeout;
    }
});

test("legacy picker deadline is an error, not a false user cancellation, and cleans up", () => {
    const originalSetTimeout = globalThis.setTimeout;
    const originalClearTimeout = globalThis.clearTimeout;
    const timers = new Map();
    globalThis.setTimeout = (callback, delay) => { timers.set(1, { callback, delay }); return 1; };
    globalThis.clearTimeout = id => timers.delete(id);
    const results = [];
    try {
        bridge.pick("deadline", (value, error) => results.push({ value, error }));
        const input = selectedInput;
        window.dispatchEvent(new Event("focus"));
        assert.equal(results.length, 0);
        const timer = timers.get(1);
        assert.equal(timer.delay, 120000);
        timer.callback();
        assert.equal(results.length, 1);
        assert.equal(results[0].value, null);
        assert.match(results[0].error, /timed out/);
        assert.equal(input.removed, true);
        assert.equal(timers.size, 0);
        input.dispatchEvent(new Event("change"));
        bridge.cancel("deadline");
        assert.equal(results.length, 1);
    } finally {
        bridge.cancel("deadline");
        globalThis.setTimeout = originalSetTimeout;
        globalThis.clearTimeout = originalClearTimeout;
    }
});

test("explicit UI cancel prevents delayed change and releases deadline", () => {
    let calls = 0;
    bridge.pick("ui-cancel", (value, error) => { calls++; assert.equal(value, null); assert.equal(error, null); });
    const input = selectedInput;
    window.dispatchEvent(new Event("focus"));
    bridge.cancel("ui-cancel");
    input.files = [new File(["abc"], "late.png", { type: "image/png" })];
    input.dispatchEvent(new Event("change"));
    assert.equal(calls, 1);
    assert.equal(input.removed, true);
});

test("unsupported and oversized files never obtain a retained file handle", async () => {
    for (const [id, file] of [
        ["wrong-type", new File(["abc"], "x.txt", { type: "text/plain" })],
        ["empty", new File([], "x.png", { type: "image/png" })],
        ["large", { name: "x.mp4", type: "video/mp4", size: 200 * 1024 * 1024 + 1 }]
    ]) {
        const result = await choose(id, file);
        assert.equal(result.value, null);
        assert.match(result.error, /unsupported/);
        assert.ok((await read(id + ".file", 0, 1)).error);
    }
});

test("absent browser MIME falls back to a supported extension", async () => {
    const result = await choose("no-mime", new File(["abc"], "image.PNG"));
    assert.equal(JSON.parse(result.value).mediaType, "image/png");
    bridge.release("no-mime.file");
});

test("invalid or oversized chunk requests are rejected", async () => {
    await choose("limits", new File(["abc"], "x.mp4", { type: "video/mp4" }));
    for (const [offset, maximum] of [[-1, 1], [0.5, 1], [0, 0], [0, 1024 * 1024 + 1]]) {
        assert.match((await read("limits.file", offset, maximum)).error, /Invalid file chunk/);
    }
    bridge.release("limits.file");
});

test("release during an in-flight read prevents later delivery of bytes", async () => {
    let finishRead;
    const file = { name: "x.webp", type: "image/webp", size: 1,
        slice: () => ({ arrayBuffer: () => new Promise(resolve => { finishRead = resolve; }) }) };
    await choose("in-flight", file);
    const result = read("in-flight.file", 0, 1);
    bridge.release("in-flight.file");
    finishRead(new Uint8Array([1]).buffer);
    assert.match((await result).error, /released/);
});

test("signed upload sends native File and forwards headers and progress", async () => {
    const file = new File(["abc"], "x.png", { type: "image/png" });
    await choose("native-upload", file);
    let xhr;
    globalThis.XMLHttpRequest = class {
        upload = {};
        headers = {};
        constructor() { xhr = this; }
        open(method, url) { this.method = method; this.url = url; }
        setRequestHeader(key, value) { this.headers[key] = value; }
        send(body) { this.body = body; }
        abort() { this.onabort(); }
    };
    const progress = [];
    const result = new Promise(resolve => bridge.upload("native-upload.file", "https://storage.invalid/signed", '{"x-token":"secret"}', value => progress.push(value), resolve));
    assert.equal(xhr.body, file);
    assert.equal(xhr.method, "PUT");
    assert.equal(xhr.headers["x-token"], "secret");
    xhr.upload.onprogress({ loaded: 2 });
    xhr.status = 204;
    xhr.onload();
    assert.equal(await result, null);
    assert.deepEqual(progress, [2, 3]);
    bridge.release("native-upload.file");
});

test("native upload abort and HTTP error do not report full completion", async () => {
    let xhr;
    globalThis.XMLHttpRequest = class {
        upload = {};
        constructor() { xhr = this; }
        open() {}
        setRequestHeader() {}
        send() {}
        abort() { this.onabort(); }
    };
    for (const mode of ["abort", "reject", "timeout"]) {
        await choose(mode, new File(["abc"], "x.gif", { type: "image/gif" }));
        const progress = [];
        const result = new Promise(resolve => bridge.upload(mode + ".file", "https://storage.invalid/signed", "{}", value => progress.push(value), resolve));
        if (mode === "abort") bridge.abortUpload(mode + ".file");
        else if (mode === "timeout") xhr.ontimeout();
        else { xhr.status = 403; xhr.onload(); }
        assert.ok(await result);
        assert.deepEqual(progress, []);
        bridge.release(mode + ".file");
    }
});
