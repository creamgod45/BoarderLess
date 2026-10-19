package cg.creamgod.boarderless

internal actual fun browserExportFixtureOriginal(done: (String) -> Unit) {
    js("globalThis.boarderlessImageExportFixture").original(done)
}

internal actual fun browserExportFixtureActive(): Int {
    return js("globalThis.boarderlessImageExportFixture").active()
}

internal actual fun browserExportFixtureInstallDelay() {
    js("globalThis.boarderlessImageExportFixture").installDelay()
}

internal actual fun browserExportFixtureWaiting(): Boolean {
    return js("globalThis.boarderlessImageExportFixture").waiting()
}

internal actual fun browserExportFixtureCompleteDelay(done: () -> Unit) {
    js("globalThis.boarderlessImageExportFixture").completeDelay(done)
}

internal actual fun browserExportFixtureCloses(): Int {
    return js("globalThis.boarderlessImageExportFixture").closes()
}

internal actual fun browserExportFixtureRestore() {
    js("globalThis.boarderlessImageExportFixture").restore()
}


internal actual fun browserExportFixtureJpeg(done: (String) -> Unit) {
    js("globalThis.boarderlessImageExportFixture").jpeg(done)
}

internal actual fun browserExportFixtureTiny(done: (String) -> Unit) {
    js("globalThis.boarderlessImageExportFixture").tiny(done)
}
