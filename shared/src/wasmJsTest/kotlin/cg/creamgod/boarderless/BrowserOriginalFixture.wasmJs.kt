@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless
@JsFun("(mode) => globalThis.boarderlessOriginalFixture.install(mode)")
internal actual external fun browserOriginalFixtureInstall(mode: String)
@JsFun("() => globalThis.boarderlessOriginalFixture.reopen()")
internal actual external fun browserOriginalFixtureReopen()
@JsFun("() => globalThis.boarderlessOriginalFixture.restore()")
internal actual external fun browserOriginalFixtureRestore()
@JsFun("(name) => globalThis.boarderlessOriginalFixture.value(name)")
internal actual external fun browserOriginalFixtureValue(name: String): String
@JsFun("(done) => globalThis.boarderlessOriginalFixture.waitForPut(done)")
internal actual external fun browserOriginalFixtureWaitForPut(done: () -> Unit)
