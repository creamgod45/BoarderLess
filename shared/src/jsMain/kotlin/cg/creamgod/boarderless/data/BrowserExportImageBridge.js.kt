package cg.creamgod.boarderless.data

internal actual fun browserExportImageBegin(id: String, type: String, expected: Double) {
    js("globalThis.boarderlessImageExport").begin(id, type, expected)
}

internal actual fun browserExportImageAppend(id: String, base64: String) {
    js("globalThis.boarderlessImageExport").append(id, base64)
}

internal actual fun browserExportImageDecode(id: String, width: Int, height: Int, done: (String?, String?) -> Unit) {
    js("globalThis.boarderlessImageExport").decode(id, width, height, done)
}

internal actual fun browserExportImageRelease(id: String) {
    js("globalThis.boarderlessImageExport").release(id)
}

