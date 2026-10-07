@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cg.creamgod.boarderless.data

@JsFun(
    """(filename, json, canWrite) => {
    if (!canWrite() || document.hidden || !document.body) throw new Error('Download unavailable');
    let url = null, timer = null, anchor = null;
    try {
        url = URL.createObjectURL(new Blob([json], {type: 'application/json;charset=utf-8'}));
        const releaseUrl = url;
        anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.style.display = 'none';
        document.body.appendChild(anchor);
        // Keep the object alive long enough for the browser to consume the download.
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
}""",
)
internal actual external fun browserDispatchDraftDownload(
    filename: String,
    json: String,
    canWrite: () -> Boolean,
)
