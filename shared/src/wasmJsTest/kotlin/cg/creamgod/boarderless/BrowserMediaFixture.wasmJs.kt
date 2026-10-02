@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cg.creamgod.boarderless
@JsFun("(done) => globalThis.boarderlessMediaFixture.recordVideo(done)")
internal actual external fun recordBrowserVideoFixture(done: (String?, String?) -> Unit)
@JsFun("(reset) => globalThis.boarderlessMediaFixture.videoAccounting(reset)")
internal actual external fun browserVideoAccounting(reset: Boolean): String
@JsFun("() => globalThis.boarderlessMediaFixture.hideVideoPage()")
internal actual external fun hideBrowserVideoPage()
@JsFun("() => globalThis.boarderlessMediaFixture.rejectNextVideoPlay()")
internal actual external fun rejectNextBrowserVideoPlay()

@JsFun("(mode) => globalThis.boarderlessMediaFixture.install(mode)")
internal actual external fun installBrowserMediaFixture(mode: String)
@JsFun("() => globalThis.boarderlessMediaFixture.inputRemoved()")
internal actual external fun browserMediaFixtureInputRemoved(): Boolean
@JsFun("() => globalThis.boarderlessMediaFixture.nativeUploadUsed()")
internal actual external fun browserMediaFixtureNativeUploadUsed(): Boolean
