package cg.creamgod.boarderless
internal actual fun browserOriginalFixtureInstall(mode: String) { js("globalThis.boarderlessOriginalFixture").install(mode) }
internal actual fun browserOriginalFixtureReopen() { js("globalThis.boarderlessOriginalFixture").reopen() }
internal actual fun browserOriginalFixtureRestore() { js("globalThis.boarderlessOriginalFixture").restore() }
internal actual fun browserOriginalFixtureValue(name: String): String = js("globalThis.boarderlessOriginalFixture").value(name) as String
internal actual fun browserOriginalFixtureWaitForPut(done: () -> Unit) { js("globalThis.boarderlessOriginalFixture").waitForPut(done) }
