package cg.creamgod.boarderless.data

internal interface IosVideoAudioSessionPort {
    fun activate(onLoss: () -> Unit): Boolean

    fun deactivate()
}

/** Main-dispatcher owned. AVAudioSession is global, so only its current owner may deactivate it. */
internal class IosVideoAudioSession(
    private val port: IosVideoAudioSessionPort,
) {
    private var owner: Any? = null
    private var onLoss: (() -> Unit)? = null
    private var epoch = 0L

    fun acquire(
        candidate: Any,
        loss: () -> Unit,
    ): Boolean {
        if (owner === candidate) return true
        interruptCurrent()
        val acquired = ++epoch
        owner = candidate
        onLoss = loss
        try {
            val granted =
                port.activate {
                    if (epoch == acquired && owner === candidate) interruptCurrent()
                }
            if (!granted || epoch != acquired || owner !== candidate) {
                release(candidate)
                return false
            }
            return true
        } catch (error: Exception) {
            try {
                release(candidate)
            } catch (cleanup: Exception) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    fun release(candidate: Any) {
        if (owner !== candidate) return
        owner = null
        onLoss = null
        epoch++
        port.deactivate()
    }

    private fun interruptCurrent() {
        if (owner == null) return
        val callback = onLoss
        owner = null
        onLoss = null
        epoch++
        // Pause/mute the former AVPlayer before deactivating the process-wide session.
        try {
            callback?.invoke()
        } finally {
            port.deactivate()
        }
    }
}
