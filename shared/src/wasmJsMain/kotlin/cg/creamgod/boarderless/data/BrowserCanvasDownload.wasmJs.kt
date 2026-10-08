@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cg.creamgod.boarderless.data

internal actual fun browserDispatchCanvasDownload(
    name: String, mediaType: String, chunks: List<ByteArray>, canWrite: () -> Boolean,
) {
    dispatchBinary(name, mediaType, chunks.size, { index ->
        // Latin-1 transports exact byte values without UTF-8 decoding or Base64 expansion.
        val bytes = chunks[index]
        CharArray(bytes.size) { (bytes[it].toInt() and 255).toChar() }.concatToString()
    }, canWrite)
}

@JsFun("""(name, mediaType, count, chunkAt, canWrite) => {
    if (!canWrite() || document.hidden || !document.body) throw new Error('Download unavailable');
    const parts = [];
    for (let i = 0; i < count; i++) {
        if (!canWrite()) throw new Error('Download scope expired');
        const chunk = chunkAt(i), bytes = new Uint8Array(chunk.length);
        for (let j = 0; j < chunk.length; j++) bytes[j] = chunk.charCodeAt(j);
        parts.push(bytes);
    }
    if (!canWrite() || document.hidden || !document.body) throw new Error('Download unavailable');
    let url = null, timer = null, anchor = null;
    try {
        url = URL.createObjectURL(new Blob(parts, {type: mediaType}));
        const releaseUrl = url;
        anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = name;
        anchor.style.display = 'none';
        document.body.appendChild(anchor);
        timer = window.setTimeout(() => URL.revokeObjectURL(releaseUrl), 60000);
        if (!canWrite() || document.hidden) throw new Error('Download scope expired');
        anchor.click();
    } catch (error) {
        if (timer !== null) window.clearTimeout(timer);
        if (url !== null) URL.revokeObjectURL(url);
        throw error;
    } finally {
        if (anchor) anchor.remove();
    }
}""")
private external fun dispatchBinary(
    name: String, mediaType: String, count: Int, chunkAt: (Int) -> String, canWrite: () -> Boolean,
)
