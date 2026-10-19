@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless
@JsFun("(mode) => globalThis.boarderlessRecordFixture.install(mode)")
internal actual external fun browserRecordFixtureInstall(mode: String)
@JsFun("() => globalThis.boarderlessRecordFixture.reopen()")
internal actual external fun browserRecordFixtureReopen()
@JsFun("() => globalThis.boarderlessRecordFixture.restore()")
internal actual external fun browserRecordFixtureRestore()
@JsFun("(name) => globalThis.boarderlessRecordFixture.value(name)")
internal actual external fun browserRecordFixtureValue(name: String): String
@JsFun("(done) => globalThis.boarderlessRecordFixture.waitForPut(done)")
internal actual external fun browserRecordFixtureWaitForPut(done: () -> Unit)
@JsFun("(key, done) => globalThis.boarderlessRecordFixture.corrupt(key, done)")
internal actual external fun browserRecordFixtureCorrupt(key: String, done: () -> Unit)
