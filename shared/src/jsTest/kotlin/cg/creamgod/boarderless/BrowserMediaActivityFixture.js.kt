package cg.creamgod.boarderless

internal actual fun browserActivityEvent(type: String) {
    val emit = js("""(function(type) { (type === 'freeze' || type === 'resume' ? document : window).dispatchEvent(new Event(type)); })""")
    emit(type)
}

internal actual fun browserActivityHidden(hidden: Boolean) {
    val emit =
        js(
            """(function(hidden) { Object.defineProperty(document, 'hidden', {configurable: true, value: hidden}); document.dispatchEvent(new Event('visibilitychange')); })""",
        )
    emit(hidden)
}

internal actual fun browserActivityRestoreVisibility() {
    js("delete document.hidden; document.dispatchEvent(new Event('visibilitychange'))")
}
