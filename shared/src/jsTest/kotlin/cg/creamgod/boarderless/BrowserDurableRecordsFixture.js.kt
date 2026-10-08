package cg.creamgod.boarderless
internal actual fun browserRecordFixtureInstall(mode: String) { js("globalThis.boarderlessRecordFixture").install(mode) }
internal actual fun browserRecordFixtureReopen() { js("globalThis.boarderlessRecordFixture").reopen() }
internal actual fun browserRecordFixtureRestore() { js("globalThis.boarderlessRecordFixture").restore() }
internal actual fun browserRecordFixtureValue(name: String): String = js("globalThis.boarderlessRecordFixture").value(name) as String
internal actual fun browserRecordFixtureWaitForPut(done: () -> Unit) { js("globalThis.boarderlessRecordFixture").waitForPut(done) }
internal actual fun browserRecordFixtureCorrupt(key: String, done: () -> Unit) { js("globalThis.boarderlessRecordFixture").corrupt(key, done) }
