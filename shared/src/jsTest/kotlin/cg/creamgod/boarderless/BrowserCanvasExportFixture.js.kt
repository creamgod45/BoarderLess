package cg.creamgod.boarderless

internal actual fun browserBackupFixtureHex(done: (String) -> Unit) {
    val invoke = js("""((done) => globalThis.boarderlessDraftDownloadFixture.hex(done))""")
    invoke(done)
}
