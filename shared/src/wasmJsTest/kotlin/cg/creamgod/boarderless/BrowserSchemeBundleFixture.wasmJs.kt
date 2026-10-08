@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless
@JsFun("(mode) => globalThis.boarderlessBundleFixture.install(mode)")
internal actual external fun browserBundleFixtureInstall(mode: String)
@JsFun("() => globalThis.boarderlessBundleFixture.selectDownload()")
internal actual external fun browserBundleFixtureSelectDownload()
@JsFun("(name) => globalThis.boarderlessBundleFixture.value(name)")
internal actual external fun browserBundleFixtureValue(name: String): String
@JsFun("(done) => globalThis.boarderlessBundleFixture.waitForWrite(done)")
internal actual external fun browserBundleFixtureWaitForWrite(done: () -> Unit)
@JsFun("() => globalThis.boarderlessBundleFixture.releaseWrite()")
internal actual external fun browserBundleFixtureReleaseWrite()
@JsFun("(done) => globalThis.boarderlessBundleFixture.audit(done)")
internal actual external fun browserBundleFixtureAudit(done: (String?, String?) -> Unit)
@JsFun("(done) => globalThis.boarderlessBundleFixture.restore(done)")
internal actual external fun browserBundleFixtureRestore(done: () -> Unit)

@JsFun("(done) => globalThis.boarderlessBundleFixture.mutateDownload(done)")
internal actual external fun browserBundleFixtureMutateDownload(done: () -> Unit)

@JsFun("(done) => globalThis.boarderlessBundleFixture.waitForIdle(done)")
internal actual external fun browserBundleFixtureWaitForIdle(done: () -> Unit)

@JsFun("(done) => globalThis.boarderlessBundleFixture.exerciseLeases(done)")
internal actual external fun browserBundleFixtureExerciseLeases(done: (String?, String?) -> Unit)

@JsFun("() => globalThis.boarderlessBundleFixture.failNextRemoval()")
internal actual external fun browserBundleFixtureFailNextRemoval()
