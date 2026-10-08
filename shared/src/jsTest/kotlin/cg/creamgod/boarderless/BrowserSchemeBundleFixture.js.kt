package cg.creamgod.boarderless
internal actual fun browserBundleFixtureInstall(mode: String) { js("globalThis.boarderlessBundleFixture").install(mode) }
internal actual fun browserBundleFixtureSelectDownload() { js("globalThis.boarderlessBundleFixture").selectDownload() }
internal actual fun browserBundleFixtureValue(name: String): String = js("globalThis.boarderlessBundleFixture").value(name) as String
internal actual fun browserBundleFixtureWaitForWrite(done: () -> Unit) { js("globalThis.boarderlessBundleFixture").waitForWrite(done) }
internal actual fun browserBundleFixtureReleaseWrite() { js("globalThis.boarderlessBundleFixture").releaseWrite() }
internal actual fun browserBundleFixtureAudit(done: (String?, String?) -> Unit) { js("globalThis.boarderlessBundleFixture").audit(done) }
internal actual fun browserBundleFixtureRestore(done: () -> Unit) { js("globalThis.boarderlessBundleFixture").restore(done) }

internal actual fun browserBundleFixtureMutateDownload(done: () -> Unit) { js("globalThis.boarderlessBundleFixture").mutateDownload(done) }

internal actual fun browserBundleFixtureWaitForIdle(done: () -> Unit) { js("globalThis.boarderlessBundleFixture").waitForIdle(done) }

internal actual fun browserBundleFixtureExerciseLeases(done: (String?, String?) -> Unit) { js("globalThis.boarderlessBundleFixture").exerciseLeases(done) }

internal actual fun browserBundleFixtureFailNextRemoval() { js("globalThis.boarderlessBundleFixture").failNextRemoval() }
