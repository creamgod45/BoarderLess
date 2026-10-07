package cg.creamgod.boarderless.data

internal fun interface VideoAudioRouteSource {
    fun subscribe(onNoisy: () -> Unit): AutoCloseable
}

/** Main-thread owned subscription. Old receiver callbacks cannot affect a later playback lease. */
internal class VideoAudioRouteMonitor(
    private val source: VideoAudioRouteSource,
) {
    private var subscription: AutoCloseable? = null
    private var epoch = 0L

    fun start(onNoisy: () -> Unit) {
        stop()
        val acquired = ++epoch
        subscription = source.subscribe { if (epoch == acquired && subscription != null) onNoisy() }
    }

    fun stop() {
        epoch++
        val old = subscription
        subscription = null
        old?.close()
    }
}
