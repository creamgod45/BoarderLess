import { test } from "node:test";
import assert from "node:assert/strict";

// Event-order regression only: native codec/pixel coverage stays in BrowserVideoPlaybackTest.
class Video extends EventTarget {
    duration = Infinity;
    currentTime = 0;
    videoWidth = 32;
    videoHeight = 32;
    paused = true;
    ended = false;
    pause() { this.paused = true; }
    load() {}
    removeAttribute() {}
    fire(name) { this.dispatchEvent(new Event(name)); }
}
let currentVideo;
globalThis.document = {
    hidden: false,
    createElement(type) {
        assert.equal(type, "video");
        return currentVideo = new Video();
    },
    addEventListener() {},
};
globalThis.addEventListener = () => {};
await import("../src/webMain/resources/video-playback.js");
const bridge = globalThis.boarderlessVideo;

function prepare(id) {
    bridge.create(id, "video/webm");
    bridge.append(id, Buffer.from("fixture-only-not-a-codec").toString("base64"));
    const completions = [], states = [];
    bridge.open(id, value => states.push(JSON.parse(value)), error => completions.push(error));
    return { video: currentVideo, completions, states };
}

test("WebM loadeddata cannot finish preparation while probing or resetting the initial frame", () => {
    const id = "duration-race";
    const { video, completions, states } = prepare(id);
    try {
        video.fire("loadeddata");
        assert.equal(video.currentTime, 1e10);
        assert.deepEqual(completions, []);
        // The end seek discovers duration, then a second loadeddata precedes seeked.
        video.duration = 1;
        video.fire("loadeddata");
        assert.deepEqual(completions, [], "Duration discovered is not initial-frame ready");
        video.fire("seeked");
        assert.equal(video.currentTime, 0);
        video.fire("loadeddata");
        assert.deepEqual(completions, [], "Resetting seek must finish before publication");
        video.fire("seeked");
        assert.deepEqual(completions, [null]);
        assert.equal(states.at(-1).positionMs, 0);
        assert.equal(states.at(-1).playing, false);
        video.fire("loadeddata");
        assert.deepEqual(completions, [null], "Preparation completes once");
    } finally { bridge.release(id); }
});

test("finite metadata prepares normally without a duration-discovery seek", () => {
    const id = "finite";
    const { video, completions } = prepare(id);
    try {
        video.duration = 2;
        video.fire("loadeddata");
        assert.equal(video.currentTime, 0);
        assert.deepEqual(completions, [null]);
    } finally { bridge.release(id); }
});

test("release during duration discovery fails pending preparation exactly once", () => {
    const id = "cancel-probe";
    const { video, completions } = prepare(id);
    video.fire("loadeddata");
    bridge.release(id);
    assert.deepEqual(completions, ["Video is released"]);
    video.duration = 1;
    video.fire("loadeddata");
    video.fire("seeked");
    bridge.release(id);
    assert.deepEqual(completions, ["Video is released"]);
});
