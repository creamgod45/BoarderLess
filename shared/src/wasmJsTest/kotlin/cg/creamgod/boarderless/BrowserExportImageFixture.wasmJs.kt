@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cg.creamgod.boarderless

@JsFun("(done) => globalThis.boarderlessImageExportFixture.original(done)")
internal actual external fun browserExportFixtureOriginal(done: (String) -> Unit)

@JsFun("() => globalThis.boarderlessImageExportFixture.active()")
internal actual external fun browserExportFixtureActive(): Int

@JsFun("() => globalThis.boarderlessImageExportFixture.installDelay()")
internal actual external fun browserExportFixtureInstallDelay()

@JsFun("() => globalThis.boarderlessImageExportFixture.waiting()")
internal actual external fun browserExportFixtureWaiting(): Boolean

@JsFun("(done) => globalThis.boarderlessImageExportFixture.completeDelay(done)")
internal actual external fun browserExportFixtureCompleteDelay(done: () -> Unit)

@JsFun("() => globalThis.boarderlessImageExportFixture.closes()")
internal actual external fun browserExportFixtureCloses(): Int

@JsFun("() => globalThis.boarderlessImageExportFixture.restore()")
internal actual external fun browserExportFixtureRestore()


@JsFun("(done) => globalThis.boarderlessImageExportFixture.jpeg(done)")
internal actual external fun browserExportFixtureJpeg(done: (String) -> Unit)

@JsFun("(done) => globalThis.boarderlessImageExportFixture.tiny(done)")
internal actual external fun browserExportFixtureTiny(done: (String) -> Unit)
