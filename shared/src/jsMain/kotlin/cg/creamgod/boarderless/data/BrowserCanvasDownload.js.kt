package cg.creamgod.boarderless.data

internal actual fun browserDispatchCanvasDownload(
    name: String, mediaType: String, chunks: List<ByteArray>, canWrite: () -> Boolean,
) {
    val dispatch = js("""((name, mediaType, parts, canWrite) => {
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
})""")
    dispatch(name, mediaType, chunks.toTypedArray(), canWrite)
}
