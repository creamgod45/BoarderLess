package cg.creamgod.boarderless.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun mediaActivityAllowsPublication(activity: MediaPlaybackActivity, openedEpoch: Long): Boolean =
    activity.available && activity.epoch == openedEpoch

/** UI-thread owned lifecycle state. A quick hide/show retains an epoch change even if
 * StateFlow conflates the unavailable value. Focus changes alone must not update availability.
 */
class MediaActivityTracker {
    private val mutableActivity = MutableStateFlow(MediaPlaybackActivity(available = false))
    val activity = mutableActivity.asStateFlow()
    private var closed = false

    fun setAvailable(available: Boolean) {
        if (closed || mutableActivity.value.available == available) return
        val previous = mutableActivity.value
        mutableActivity.value = MediaPlaybackActivity(available, previous.epoch + if (available) 0 else 1)
    }

    fun close() {
        if (closed) return
        setAvailable(false)
        closed = true
    }
}
