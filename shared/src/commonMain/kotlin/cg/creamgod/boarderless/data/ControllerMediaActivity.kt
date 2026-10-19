package cg.creamgod.boarderless.data

/** All three gates must agree; application-wide activation cannot reactivate a hidden scene.
 * Scene identity remains device-local. Moving the controller to another scene resets consent
 * via an epoch even if both scenes are active, without relying on an intermediate UI frame.
 */
internal class ControllerMediaActivity(
    private val tracker: MediaActivityTracker,
) {
    private var sceneId: String? = null
    private var closed = false

    fun update(
        appActive: Boolean,
        controllerResumed: Boolean,
        sceneActive: Boolean,
        sceneId: String?,
    ) {
        if (closed) return
        if (sceneId != this.sceneId) tracker.setAvailable(false)
        this.sceneId = sceneId
        tracker.setAvailable(appActive && controllerResumed && sceneActive && !sceneId.isNullOrBlank())
    }

    fun close() {
        if (closed) return
        closed = true
        tracker.close()
    }
}
