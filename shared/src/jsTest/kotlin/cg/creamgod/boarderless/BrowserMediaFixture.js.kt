package cg.creamgod.boarderless

internal actual fun recordBrowserVideoFixture(done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessMediaFixture").recordVideo(done)
}

internal actual fun browserVideoAccounting(reset: Boolean): String =
    js("globalThis.boarderlessMediaFixture").videoAccounting(reset) as String

internal actual fun hideBrowserVideoPage() {
    js("globalThis.boarderlessMediaFixture").hideVideoPage()
}

internal actual fun rejectNextBrowserVideoPlay() {
    js("globalThis.boarderlessMediaFixture").rejectNextVideoPlay()
}

internal actual fun installBrowserMediaFixture(mode: String) {
    js("globalThis.boarderlessMediaFixture").install(mode)
}

internal actual fun browserMediaFixtureInputRemoved(): Boolean = js("globalThis.boarderlessMediaFixture").inputRemoved() as Boolean

internal actual fun browserMediaFixtureNativeUploadUsed(): Boolean = js("globalThis.boarderlessMediaFixture").nativeUploadUsed() as Boolean
