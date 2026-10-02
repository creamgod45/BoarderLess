package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.MediaPlaybackActivity
import java.awt.Frame
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Window visibility, not keyboard focus: opening another panel must not stop playback. */
internal class DesktopMediaWindowLifecycle {
    private val mutableActivity = MutableStateFlow(MediaPlaybackActivity(available = false))
    val activity = mutableActivity.asStateFlow()
    private var closed = false

    @Synchronized fun setAvailable(available: Boolean) {
        if (closed || available == activity.value.available) return
        val old = activity.value
        mutableActivity.value = MediaPlaybackActivity(available, old.epoch + if (available) 0 else 1)
    }

    @Synchronized fun close() {
        if (closed) return
        setAvailable(false)
        closed = true
    }

    /** The close button only hides a window that [hidesWindow] (macOS), so it can be shown again. */
    fun closeRequested(hidesWindow: Boolean) = if (hidesWindow) setAvailable(false) else close()

    /**
     * Bind/unbind on the Compose desktop UI thread. Disposing the window is terminal; closing it is
     * too unless [closeHidesWindow].
     */
    fun bind(window: Window, closeHidesWindow: Boolean = false): AutoCloseable {
        fun refresh() = setAvailable(window.isVisible &&
            (window !is Frame || window.extendedState and Frame.ICONIFIED == 0))
        val windowListener = object : WindowAdapter() {
            override fun windowIconified(event: WindowEvent) = setAvailable(false)
            override fun windowDeiconified(event: WindowEvent) = refresh()
            override fun windowClosing(event: WindowEvent) = closeRequested(closeHidesWindow)
            override fun windowClosed(event: WindowEvent) = close()
        }
        val componentListener = object : ComponentAdapter() {
            override fun componentHidden(event: ComponentEvent) = setAvailable(false)
            override fun componentShown(event: ComponentEvent) = refresh()
        }
        window.addWindowListener(windowListener)
        window.addComponentListener(componentListener)
        refresh()
        return AutoCloseable {
            window.removeWindowListener(windowListener)
            window.removeComponentListener(componentListener)
            close()
        }
    }
}
