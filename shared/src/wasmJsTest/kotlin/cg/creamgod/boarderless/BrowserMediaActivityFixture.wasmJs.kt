@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package cg.creamgod.boarderless

@JsFun("""(type) => { (type === 'freeze' || type === 'resume' ? document : window).dispatchEvent(new Event(type)); }""")
internal actual external fun browserActivityEvent(type: String)
@JsFun("""(hidden) => { Object.defineProperty(document, 'hidden', {configurable: true, value: hidden}); document.dispatchEvent(new Event('visibilitychange')); }""")
internal actual external fun browserActivityHidden(hidden: Boolean)
@JsFun("""() => { delete document.hidden; document.dispatchEvent(new Event('visibilitychange')); }""")
internal actual external fun browserActivityRestoreVisibility()
