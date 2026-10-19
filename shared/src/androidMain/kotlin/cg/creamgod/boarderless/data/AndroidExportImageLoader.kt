package cg.creamgod.boarderless.data

import android.graphics.*
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

class AndroidExportImageLoader(private val cacheDirectory: File) {
    private val permits = Semaphore(2)

    suspend fun load(gateway: AssetDownloadGateway, session: WorkspaceSession, assetId: String,
                     width: Int, height: Int): ImageBitmap = permits.withPermit {
        // Validate the output before authorization, download or native allocation.
        imageExportSize(1, 1, width, height)
        val ticket = gateway.authorize(session, assetId)
        currentCoroutineContext().ensureActive()
        require(ticket.asset.id == assetId && ticket.asset.workspaceId == session.workspace.id && ticket.asset.status == AssetStatus.Ready)
        require(ticket.asset.mediaType in setOf("image/png", "image/jpeg", "image/webp") && ticket.asset.byteSize in 1..MaxWorkspaceAssetBytes)
        withContext(Dispatchers.IO) {
            val directory = File(cacheDirectory, "asset-export-${UUID.randomUUID()}")
            check(directory.mkdir())
            try {
                val authorized = object : AssetDownloadGateway {
                    override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
                    override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
                }
                val local = AssetDownloadCoordinator(authorized).download(session, assetId,
                    AndroidFileAssetDownloadSink.create(directory, ticket.asset.mediaType))
                decodeAndroidExportImage(File(local.token), width, height).asImageBitmap()
            } finally {
                directory.listFiles().orEmpty().forEach { check(it.delete()) { "Cannot clear export original" } }
                check(directory.delete()) { "Cannot clear export directory" }
            }
        }
    }
}

internal suspend fun decodeAndroidExportImage(file: File, width: Int, height: Int): Bitmap {
    currentCoroutineContext().ensureActive()
    imageExportSize(1, 1, width, height)
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val size = imageExportSize(info.size.width, info.size.height, width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.setTargetSize(size.width, size.height)
        }
    } else decodeAndroidExportRegions(file, width, height)
    try {
        currentCoroutineContext().ensureActive()
        check(bitmap.width in 1..width && bitmap.height in 1..height && bitmap.width.toLong() * bitmap.height <= 4_194_304)
        return bitmap
    } catch (failure: Throwable) { bitmap.recycle(); throw failure }
}

/** API 24–27 fallback reads every bounded source region; no coarse-image underlay. */
@Suppress("DEPRECATION")
internal suspend fun decodeAndroidExportRegions(file: File, width: Int, height: Int): Bitmap {
    val decoder = checkNotNull(BitmapRegionDecoder.newInstance(file.path, false))
    try {
        val sw = decoder.width; val sh = decoder.height
        val size = imageExportSize(sw, sh, width, height)
        var sample = 1
        val ratio = min(sw.toDouble() / size.width, sh.toDouble() / size.height)
        while (sample * 2 <= ratio && sample < 128) sample *= 2
        val edge = 512 * sample
        val result = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
            for (top in 0 until sh step edge) for (left in 0 until sw step edge) {
                currentCoroutineContext().ensureActive()
                val right = min(left + edge, sw); val bottom = min(top + edge, sh)
                val tile = checkNotNull(decoder.decodeRegion(Rect(left, top, right, bottom), BitmapFactory.Options().apply {
                    inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false
                }))
                try {
                    check(tile.width in 1..512 && tile.height in 1..512)
                    canvas.drawBitmap(tile, null, Rect((left.toDouble() * size.width / sw).roundToInt(),
                        (top.toDouble() * size.height / sh).roundToInt(), (right.toDouble() * size.width / sw).roundToInt(),
                        (bottom.toDouble() * size.height / sh).roundToInt()), paint)
                } finally { tile.recycle() }
            }
            currentCoroutineContext().ensureActive()
            return result
        } catch (failure: Throwable) { result.recycle(); throw failure }
    } finally { decoder.recycle() }
}
