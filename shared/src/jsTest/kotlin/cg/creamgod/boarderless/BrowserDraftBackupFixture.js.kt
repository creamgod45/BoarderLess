package cg.creamgod.boarderless

internal actual fun browserBackupFixtureInstall() {
    val invoke = js("""(() => globalThis.boarderlessDraftDownloadFixture.install())""")
    invoke()
}

internal actual fun browserBackupFixtureRestore() {
    val invoke = js("""(() => globalThis.boarderlessDraftDownloadFixture.restore())""")
    invoke()
}

internal actual fun browserBackupFixtureExpire() {
    val invoke = js("""(() => globalThis.boarderlessDraftDownloadFixture.expire())""")
    invoke()
}

internal actual fun browserBackupFixtureFailClick() {
    val invoke = js("""(() => globalThis.boarderlessDraftDownloadFixture.failClick())""")
    invoke()
}

internal actual fun browserBackupFixtureValue(field: String): String {
    val invoke = js("""((field) => globalThis.boarderlessDraftDownloadFixture.value(field))""")
    return invoke(field).unsafeCast<String>()
}

internal actual fun browserBackupFixtureText(done: (String) -> Unit) {
    val invoke = js("""((done) => globalThis.boarderlessDraftDownloadFixture.text(done))""")
    invoke(done)
}
