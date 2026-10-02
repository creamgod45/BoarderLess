package cg.creamgod.boarderless

import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.sound.sampled.AudioFormat
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** Offline packaging diagnostic; no window, backend, audio device or user asset is opened. */
internal object DesktopMediaRuntimeCheck {
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty()) { "This check does not accept asset paths or server credentials" }
        FFmpegFrameGrabber.tryLoad()
        val codecs = linkedMapOf("h264" to AV_CODEC_ID_H264, "aac" to AV_CODEC_ID_AAC,
            "mpeg4" to AV_CODEC_ID_MPEG4, "vp8" to AV_CODEC_ID_VP8, "vp9" to AV_CODEC_ID_VP9,
            "opus" to AV_CODEC_ID_OPUS, "vorbis" to AV_CODEC_ID_VORBIS)
        codecs.forEach { (name, id) ->
            val decoder = avcodec_find_decoder(id)
            check(decoder != null && !decoder.isNull) { "Packaged decoder unavailable: $name" }
        }
        Image.makeRaster(ImageInfo(1, 1, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
            byteArrayOf(51, 102, 153.toByte(), 255.toByte()), 4).use { image ->
            val pixel = IntArray(1)
            image.toComposeImageBitmap().readPixels(pixel)
            check(pixel[0] == 0xff336699.toInt()) { "Packaged Skia pixel conversion failed" }
        }
        // Verify java.desktop/audio format classes without acquiring a speaker or microphone.
        check(AudioFormat(48000f, 16, 2, true, false).frameSize == 4)
        println("MEDIA_RUNTIME_OK decoders=${codecs.keys.joinToString(",")} skia=rgba audio=pcm16-stereo")
    }
}
