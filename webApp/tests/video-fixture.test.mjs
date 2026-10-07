import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";

await import("../../shared/src/webTest/resources/media-import-fixture.js");

test("codec fixture is fixed, asynchronous and independent of recorder/canvas availability", async () => {
    let synchronous = true;
    const get = () => new Promise((resolve, reject) => {
        globalThis.boarderlessMediaFixture.recordVideo((encoded, error) => {
            try {
                assert.equal(synchronous, false);
                assert.equal(error, null);
                resolve(Buffer.from(encoded, "base64"));
            } catch (failure) { reject(failure); }
        });
    });
    const requests = [get(), get()];
    synchronous = false;
    const [first, second] = await Promise.all(requests);
    assert.deepEqual(first, second);
    assert.equal(first.length, 730);
    assert.equal(createHash("sha256").update(first).digest("hex"), "ee13b6c257d11c080e0489977bfa9779ad1220f2552189ca8052dec11b7431ab");
    assert.equal(first.subarray(0, 4).toString("hex"), "1a45dfa3");
});
