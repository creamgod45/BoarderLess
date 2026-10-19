@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cg.creamgod.boarderless

@JsFun("""(done) => globalThis.boarderlessDraftDownloadFixture.hex(done)""")
internal actual external fun browserBackupFixtureHex(done: (String) -> Unit)
