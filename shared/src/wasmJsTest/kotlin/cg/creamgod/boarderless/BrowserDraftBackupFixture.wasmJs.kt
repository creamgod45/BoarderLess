@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless

@JsFun("""() => globalThis.boarderlessDraftDownloadFixture.install()""")
internal actual external fun browserBackupFixtureInstall()

@JsFun("""() => globalThis.boarderlessDraftDownloadFixture.restore()""")
internal actual external fun browserBackupFixtureRestore()

@JsFun("""() => globalThis.boarderlessDraftDownloadFixture.expire()""")
internal actual external fun browserBackupFixtureExpire()

@JsFun("""() => globalThis.boarderlessDraftDownloadFixture.failClick()""")
internal actual external fun browserBackupFixtureFailClick()

@JsFun("""(field) => globalThis.boarderlessDraftDownloadFixture.value(field)""")
internal actual external fun browserBackupFixtureValue(field: String): String

@JsFun("""(done) => globalThis.boarderlessDraftDownloadFixture.text(done)""")
internal actual external fun browserBackupFixtureText(done: (String) -> Unit)
