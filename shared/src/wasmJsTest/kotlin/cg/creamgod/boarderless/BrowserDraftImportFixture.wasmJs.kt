@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless

@JsFun("(mode) => globalThis.boarderlessDraftImportFixture.install(mode)")
internal actual external fun browserDraftImportFixtureInstall(mode: String)
@JsFun("() => globalThis.boarderlessDraftImportFixture.restore()")
internal actual external fun browserDraftImportFixtureRestore()
@JsFun("() => globalThis.boarderlessDraftImportFixture.inputs()")
internal actual external fun browserDraftImportFixtureInputs(): Int
@JsFun("(done) => globalThis.boarderlessDraftImportFixture.release(done)")
internal actual external fun browserDraftImportFixtureRelease(done: () -> Unit)
