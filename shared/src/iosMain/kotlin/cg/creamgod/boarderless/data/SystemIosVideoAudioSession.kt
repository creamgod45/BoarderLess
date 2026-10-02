@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless.data

import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.NSObjectProtocol

internal val systemIosVideoAudioSession by lazy { IosVideoAudioSession(SystemIosVideoAudioSessionPort()) }

/** Only used on Main; notifications are explicitly delivered to the main operation queue. */
internal class SystemIosVideoAudioSessionPort(
    private val session: AVAudioSession = AVAudioSession.sharedInstance(),
    private val configure: () -> Boolean = {
        session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0uL, null)
    },
    private val setActive: (Boolean) -> Boolean = { active ->
        session.setActive(active, withOptions = if (active) 0uL else AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    },
) : IosVideoAudioSessionPort {
    private val observers = mutableListOf<NSObjectProtocol>()
    private var epoch = 0L
    private var activationAttempted = false

    override fun activate(onLoss: () -> Unit): Boolean {
        check(observers.isEmpty() && !activationAttempted)
        val acquired = ++epoch
        fun interrupted() {
            if (epoch == acquired) {
                // Native notification callbacks must not throw through Foundation.
                try { onLoss() } catch (_: Exception) { }
            }
        }
        val center = NSNotificationCenter.defaultCenter
        observers += center.addObserverForName(AVAudioSessionInterruptionNotification, session, NSOperationQueue.mainQueue) {
            val type = (it?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongLongValue
            if (type == AVAudioSessionInterruptionTypeBegan) interrupted()
            // Ended / shouldResume never authorizes automatic playback.
        }
        observers += center.addObserverForName(AVAudioSessionRouteChangeNotification, session, NSOperationQueue.mainQueue) {
            val reason = (it?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongLongValue
            if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) interrupted()
        }
        observers += center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, NSOperationQueue.mainQueue) {
            interrupted()
        }
        if (!configure()) return false
        if (epoch != acquired) return false
        activationAttempted = true
        return setActive(true)
    }

    override fun deactivate() {
        epoch++
        val previous = observers.toList()
        observers.clear()
        try {
            previous.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        } finally {
            if (activationAttempted) {
                activationAttempted = false
                check(setActive(false)) {
                    "Video audio session deactivation failed"
                }
            }
        }
    }
}
