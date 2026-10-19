package cg.creamgod.boarderless

internal actual fun browserDraftImportFixtureInstall(mode: String) {
    js("globalThis.boarderlessDraftImportFixture").install(mode)
}

internal actual fun browserDraftImportFixtureRestore() {
    js("globalThis.boarderlessDraftImportFixture").restore()
}

internal actual fun browserDraftImportFixtureInputs(): Int = js("globalThis.boarderlessDraftImportFixture").inputs().unsafeCast<Int>()

internal actual fun browserDraftImportFixtureRelease(done: () -> Unit) {
    js("globalThis.boarderlessDraftImportFixture").release(done)
}
