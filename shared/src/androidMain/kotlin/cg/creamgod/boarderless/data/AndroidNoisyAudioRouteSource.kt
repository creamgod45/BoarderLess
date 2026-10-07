package cg.creamgod.boarderless.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

/** Receives only the system's noisy-output event; no manifest/background receiver or permissions. */
internal class AndroidNoisyAudioRouteSource(
    context: Context,
) : VideoAudioRouteSource {
    private val application = context.applicationContext

    @Suppress("DEPRECATION")
    override fun subscribe(onNoisy: () -> Unit): AutoCloseable {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onNoisy()
                }
            }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(receiver, filter)
        }
        return AutoCloseable { application.unregisterReceiver(receiver) }
    }
}
