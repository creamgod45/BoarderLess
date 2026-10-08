package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Rectangle
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.*

/** Verified originals live only in bounded, disposable device-local files. Pixels are sampled by region. */
internal class DesktopViewportImageLoader(
    private val cacheRoot: Path? = null,
) {
    private data class Key(
        val user: String,
        val workspace: String,
        val asset: WorkspaceAsset,
    )

    private data class FileEntry(
        val directory: Path,
        val file: Path,
        val bytes: Long,
    )

    private val mutex = Mutex()
    private val files = LinkedHashMap<Key, FileEntry>()
    private var totalBytes = 0L

    suspend fun clear() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                files.values.forEach(::remove)
                files.clear()
                totalBytes = 0
            }
        }

    suspend fun load(
        gateway: AssetDownloadGateway,
        session: WorkspaceSession,
        assetId: String,
        request: ViewportImageRequest,
        completeImage: Boolean = false,
        emit: suspend (ImagePreviewTile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Authorization is refreshed even on a file-cache hit, before any cached pixels are exposed.
            val ticket = gateway.authorize(session, assetId)
            currentCoroutineContext().ensureActive()
            require(
                ticket.asset.id == assetId && ticket.asset.workspaceId == session.workspace.id && ticket.asset.status == AssetStatus.Ready,
            )
            require(ticket.asset.mediaType in setOf("image/jpeg", "image/png", "image/webp"))
            require(ticket.asset.byteSize in 1..MaxWorkspaceAssetBytes)
            val key = Key(session.userId, session.workspace.id.value, ticket.asset)
            val entry =
                files.remove(key)?.also { files[key] = it } ?: run {
                    while (files.isNotEmpty() && (files.size >= 4 || totalBytes + ticket.asset.byteSize > 256L * 1024 * 1024)) {
                        val oldest = files.remove(files.keys.first())!!
                        totalBytes -= oldest.bytes
                        remove(oldest)
                    }
                    val dir =
                        if (cacheRoot == null) {
                            Files.createTempDirectory("boarderless-image-")
                        } else {
                            Files.createTempDirectory(cacheRoot, "image-")
                        }
                    dir.toFile().deleteOnExit()
                    try {
                        val sink = JvmFileAssetDownloadSink.create(dir.toString(), "original", ticket.asset.mediaType)
                        val authorized =
                            object : AssetDownloadGateway {
                                override suspend fun authorize(
                                    session: WorkspaceSession,
                                    assetId: String,
                                ) = ticket

                                override suspend fun download(
                                    ticket: AssetDownloadTicket,
                                    onChunk: suspend (ByteArray) -> Unit,
                                ) = gateway.download(ticket, onChunk)
                            }
                        val local = AssetDownloadCoordinator(authorized).download(session, assetId, sink)
                        java.nio.file.Paths
                            .get(local.token)
                            .toFile()
                            .deleteOnExit()
                        currentCoroutineContext().ensureActive()
                        FileEntry(
                            dir,
                            java.nio.file.Paths
                                .get(local.token),
                            ticket.asset.byteSize,
                        ).also {
                            files[key] = it
                            totalBytes += it.bytes
                        }
                    } catch (error: Throwable) {
                        remove(FileEntry(dir, dir, 0))
                        throw error
                    }
                }
            decode(entry.file, request, completeImage, emit)
        }
    }

    suspend fun loadExportImage(
        gateway: AssetDownloadGateway, session: WorkspaceSession, assetId: String,
        displayWidth: Int, displayHeight: Int,
    ): ImageBitmap {
        require(displayWidth > 0 && displayHeight > 0)
        val tiles = mutableListOf<ImagePreviewTile>()
        load(gateway, session, assetId, ViewportImageRequest(0f, 0f, 1f, 1f, displayWidth, displayHeight), true) { tiles += it }
        return withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            val first = checkNotNull(tiles.firstOrNull())
            val fit = minOf(displayWidth.toDouble() / first.sourceWidth, displayHeight.toDouble() / first.sourceHeight, 1.0)
            val width = floor(first.sourceWidth * fit).toInt().coerceAtLeast(1)
            val height = floor(first.sourceHeight * fit).toInt().coerceAtLeast(1)
            val result = ImageBitmap(width, height)
            val canvas = Canvas(result)
            val paint = Paint().apply { blendMode = BlendMode.Src; filterQuality = FilterQuality.Medium }
            for (tile in tiles) {
                currentCoroutineContext().ensureActive()
                check(tile.sourceWidth == first.sourceWidth && tile.sourceHeight == first.sourceHeight)
                val left = (tile.left * width).roundToInt(); val top = (tile.top * height).roundToInt()
                val right = (tile.right * width).roundToInt(); val bottom = (tile.bottom * height).roundToInt()
                if (right > left && bottom > top) canvas.drawImageRect(tile.bitmap,
                    dstOffset = IntOffset(left, top), dstSize = IntSize(right - left, bottom - top), paint = paint)
            }
            result
        }
    }

    private fun remove(entry: FileEntry) {
        // Only this loader's newly-created directory is enumerated; never the picker source.
        Files.newDirectoryStream(entry.directory).use { paths -> paths.forEach { Files.deleteIfExists(it) } }
        Files.deleteIfExists(entry.directory)
    }

    private suspend fun decode(
        file: Path,
        request: ViewportImageRequest,
        completeImage: Boolean,
        emit: suspend (ImagePreviewTile) -> Unit,
    ) {
        ImageIO.createImageInputStream(file.toFile()).use { input ->
            requireNotNull(input)
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                // WebP has no bundled ImageIO region reader. Keep its existing bounded Skia path.
                require(Files.size(file) <= AssetPreviewPolicy.MaxEncodedBytes)
                val bitmap = DesktopAssetPreviewLoader.decodePreview(Files.readAllBytes(file))
                currentCoroutineContext().ensureActive()
                emit(ImagePreviewTile(bitmap, 0f, 0f, 1f, 1f, bitmap.width, bitmap.height))
                return
            }
            val reader = readers.next()
            try {
                reader.setInput(input, false, true)
                val encodedWidth = reader.getWidth(0)
                val encodedHeight = reader.getHeight(0)
                val orientation = desktopJpegOrientation(file)
                val width = if (orientation >= 5) encodedHeight else encodedWidth
                val height = if (orientation >= 5) encodedWidth else encodedHeight
                require(width in 1..65_535 && height in 1..65_535 && width.toLong() * height <= 268_435_456L) {
                    "Image source dimensions exceed the header safety limit"
                }

                suspend fun region(
                    rect: Rectangle,
                    sample: Int,
                ): ImagePreviewTile {
                    currentCoroutineContext().ensureActive()
                    val param =
                        reader.defaultReadParam.apply {
                            sourceRegion = encodedImageRegion(rect, encodedWidth, encodedHeight, orientation)
                            setSourceSubsampling(sample, sample, 0, 0)
                        }
                    // Validate output before asking the decoder to allocate pixels.
                    AssetPreviewPolicy.validateDimensions((rect.width + sample - 1) / sample, (rect.height + sample - 1) / sample)
                    val buffered = reader.read(0, param)
                    var upright: java.awt.image.BufferedImage? = null
                    try {
                        currentCoroutineContext().ensureActive()
                        upright = uprightImageTile(buffered, orientation)
                        val encoded = ByteArrayOutputStream().also { check(ImageIO.write(upright, "png", it)) }.toByteArray()
                        val bitmap = DesktopAssetPreviewLoader.decodePreview(encoded)
                        return ImagePreviewTile(
                            bitmap,
                            rect.x.toFloat() / width,
                            rect.y.toFloat() / height,
                            (rect.x + rect.width).toFloat() / width,
                            (rect.y + rect.height).toFloat() / height,
                            width,
                            height,
                        )
                    } finally {
                        if (upright !== buffered) upright?.flush()
                        buffered.flush()
                    }
                }
                val baseEdge = min(512, max(request.displayWidth, request.displayHeight)).coerceAtLeast(64)
                val baseSample = ceil(max(width, height).toDouble() / baseEdge).toInt().coerceAtLeast(1)
                emit(region(Rectangle(0, 0, width, height), baseSample))
                val plan = planImageTiles(width, height, request, completeImage)
                for ((rect, sample) in plan) {
                    currentCoroutineContext().ensureActive()
                    emit(region(rect, sample))
                    yield()
                }
            } finally {
                reader.dispose()
            }
        }
    }
}

/** Viewport: at most 16 visible 512px tiles, center first, with stable source-grid boundaries.
 * Export: aspect-fit sampling and full coverage; reject excessive tile counts, never truncate. */
internal fun planImageTiles(
    width: Int,
    height: Int,
    request: ViewportImageRequest,
    completeImage: Boolean = false,
): List<Pair<Rectangle, Int>> {
    require(width > 0 && height > 0)
    if (completeImage) require(request.left == 0f && request.top == 0f && request.right == 1f && request.bottom == 1f)
    val ratio = if (completeImage) max(width.toDouble() / request.displayWidth, height.toDouble() / request.displayHeight)
        else min(width.toDouble() / request.displayWidth, height.toDouble() / request.displayHeight)
    var sample = 1
    while (sample * 2 <= ratio && sample < 128) sample *= 2
    val edge = 512 * sample
    val left = floor(request.left * width / edge).toInt()
    val top = floor(request.top * height / edge).toInt()
    val right = ceil(request.right * width / edge).toInt()
    val bottom = ceil(request.bottom * height / edge).toInt()
    val cx = (left + right - 1) / 2.0
    val cy = (top + bottom - 1) / 2.0
    return (top until bottom)
        .flatMap { y -> (left until right).map { x -> x to y } }
        .sortedBy { (x, y) -> (x - cx).pow(2) + (y - cy).pow(2) }
        .let { if (completeImage) it else it.take(16) }
        .map { (x, y) -> Rectangle(x * edge, y * edge, min(edge, width - x * edge), min(edge, height - y * edge)) to sample }
}
