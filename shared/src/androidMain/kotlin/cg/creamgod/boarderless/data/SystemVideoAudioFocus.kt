package cg.creamgod.boarderless.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

internal fun videoAudioAttributes(): AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

/** One request instance per acquisition; queued callbacks from old requests cannot pause a new one. */
internal class SystemVideoAudioFocus(
    context: Context,
) : VideoAudioFocusPort {
    private val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var epoch = 0L
    private var listener: AudioManager.OnAudioFocusChangeListener? = null
    private var request: AudioFocusRequest? = null
    private val routes = VideoAudioRouteMonitor(AndroidNoisyAudioRouteSource(context))

    @Suppress("DEPRECATION")
    override fun request(onLoss: () -> Unit): Boolean {
        abandon()
        val acquired = ++epoch
        val callback =
            AudioManager.OnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                ) {
                    handler.post { if (epoch == acquired) onLoss() }
                }
            }
        listener = callback
        val result =
            if (Build.VERSION.SDK_INT >= 26) {
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(videoAudioAttributes())
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(callback, handler)
                    .build()
                    .also { request = it }
                    .let(manager::requestAudioFocus)
            } else {
                manager.requestAudioFocus(callback, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) routes.start { handler.post { if (epoch == acquired) onLoss() } }
        return granted
    }

    @Suppress("DEPRECATION")
    override fun abandon() {
        epoch++
        val oldRequest = request
        val oldListener = listener
        request = null
        listener = null
        try {
            routes.stop()
        } finally {
            if (Build.VERSION.SDK_INT >= 26 && oldRequest != null) {
                manager.abandonAudioFocusRequest(oldRequest)
            } else if (oldListener != null) {
                manager.abandonAudioFocus(oldListener)
            }
        }
    }
}
