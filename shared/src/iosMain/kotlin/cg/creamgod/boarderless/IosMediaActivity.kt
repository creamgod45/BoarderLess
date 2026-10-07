package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.MediaActivityTracker
import cg.creamgod.boarderless.data.ControllerMediaActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIViewController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.UIKit.UISceneDidEnterBackgroundNotification
import platform.UIKit.UISceneDidDisconnectNotification

/** One binding per Compose controller; main-queue callbacks and explicit disposal. */
internal fun bindIosMediaActivity(
    tracker: MediaActivityTracker,
    lifecycle: Lifecycle,
    controller: () -> UIViewController,
): () -> Unit {
    val center = NSNotificationCenter.defaultCenter
    val scoped = ControllerMediaActivity(tracker)
    var appActive = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
    var disposed = false
    var inactiveSceneId: String? = null
    fun ownScene() = controller().viewIfLoaded?.window?.windowScene
    fun refresh(sceneActiveOverride: Boolean? = null) {
        if (disposed) return
        val scene = ownScene()
        val sceneId = scene?.session?.persistentIdentifier
        // WillDeactivate may precede the property transition. Keep its negative signal until
        // this same scene explicitly activates; an unrelated app activation cannot undo it.
        if (sceneActiveOverride == false) inactiveSceneId = sceneId
        else if (sceneActiveOverride == true && inactiveSceneId == sceneId) inactiveSceneId = null
        scoped.update(appActive, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            sceneActiveOverride ?: (scene?.activationState == UISceneActivationStateForegroundActive && sceneId != inactiveSceneId),
            sceneId)
    }
    val lifecycleObserver = LifecycleEventObserver { _, _ -> refresh() }
    val observers = listOf(
        center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
            appActive = false
            refresh()
        },
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
            appActive = true
            refresh()
        },
        center.addObserverForName(UISceneDidActivateNotification, null, NSOperationQueue.mainQueue) { notification ->
            val scene = ownScene()
            if (scene != null && notification?.`object` == scene) refresh(true)
        },
        center.addObserverForName(UISceneWillDeactivateNotification, null, NSOperationQueue.mainQueue) { notification ->
            val scene = ownScene()
            if (scene != null && notification?.`object` == scene) refresh(false)
        },
        center.addObserverForName(UISceneDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { notification ->
            val scene = ownScene()
            if (scene != null && notification?.`object` == scene) refresh(false)
        },
        center.addObserverForName(UISceneDidDisconnectNotification, null, NSOperationQueue.mainQueue) { notification ->
            val scene = ownScene()
            if (scene != null && notification?.`object` == scene) refresh(false)
        },
    )
    lifecycle.addObserver(lifecycleObserver)
    refresh()
    return {
        disposed = true
        lifecycle.removeObserver(lifecycleObserver)
        observers.forEach { center.removeObserver(it) }
        scoped.close()
    }
}
