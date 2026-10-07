package cg.creamgod.boarderless.data

internal interface VideoAudioFocusPort {
    fun request(onLoss: () -> Unit): Boolean
    fun abandon()
}

/** Main-thread policy: mute/pause releases focus; loss never auto-resumes on a later gain. */
internal class VideoAudioFocusSession(private val port: VideoAudioFocusPort, private val onLoss: () -> Unit) {
    private var held = false
    private var closed = false
    private var epoch = 0L

    fun update(audible: Boolean): Boolean {
        check(!closed)
        if (!audible) { abandon(); return true }
        if (held) return true
        val acquired = ++epoch
        try { held = port.request {
            if (held && !closed && epoch == acquired) {
                held = false
                epoch++
                try { port.abandon() } catch (_: Exception) { }
                onLoss()
            }
        } } catch (error: Exception) {
            epoch++
            try { port.abandon() } catch (cleanup: Exception) { if (cleanup !== error) error.addSuppressed(cleanup) }
            throw error
        }
        if (!held) { epoch++; port.abandon() }
        return held
    }

    private fun abandon() {
        if (held) { held = false; epoch++; port.abandon() }
    }

    fun release() {
        if (closed) return
        closed = true
        epoch++
        held = false
        port.abandon()
    }
}

internal object UnavailableVideoAudioFocus : VideoAudioFocusPort {
    override fun request(onLoss: () -> Unit) = false
    override fun abandon() = Unit
}
