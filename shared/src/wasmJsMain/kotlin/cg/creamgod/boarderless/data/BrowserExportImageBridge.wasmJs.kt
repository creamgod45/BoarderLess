@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cg.creamgod.boarderless.data

@JsFun("(id, type, expected) => globalThis.boarderlessImageExport.begin(id, type, expected)")
internal actual external fun browserExportImageBegin(id: String, type: String, expected: Double)

@JsFun("(id, base64) => globalThis.boarderlessImageExport.append(id, base64)")
internal actual external fun browserExportImageAppend(id: String, base64: String)

@JsFun("(id, width, height, done) => globalThis.boarderlessImageExport.decode(id, width, height, done)")
internal actual external fun browserExportImageDecode(id: String, width: Int, height: Int, done: (String?, String?) -> Unit)

@JsFun("(id) => globalThis.boarderlessImageExport.release(id)")
internal actual external fun browserExportImageRelease(id: String)

