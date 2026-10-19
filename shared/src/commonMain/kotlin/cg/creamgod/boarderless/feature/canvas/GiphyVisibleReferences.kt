package cg.creamgod.boarderless.feature.canvas

import androidx.compose.ui.unit.IntSize
import cg.creamgod.boarderless.data.MediaPlaybackActivity
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.media.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface GiphyReferenceResolution {
    data object Pending : GiphyReferenceResolution
    data class Ready(val item: GiphyItem) : GiphyReferenceResolution
    data object Missing : GiphyReferenceResolution
    data class Failed(val issue: GiphyIssue) : GiphyReferenceResolution
}

/** Owned by one workspace UI scope. Only currently visible metadata is retained, never persisted. */
internal class GiphyVisibleReferences {
    private var generation = 0L
    private var visible = emptySet<ExternalMediaReference>()
    private val mutableEntries = MutableStateFlow<Map<ExternalMediaReference, GiphyReferenceResolution>>(emptyMap())
    val entries = mutableEntries.asStateFlow()
    private val mutableRetry = MutableStateFlow(0L)
    val retryRevision = mutableRetry.asStateFlow()

    fun replaceVisible(references: Set<ExternalMediaReference>) {
        require(references.all { it.provider == ExternalMediaProvider.Giphy })
        if (references == visible) return
        generation++
        visible = references.toSet()
        mutableEntries.value = visible.associateWith { mutableEntries.value[it] ?: GiphyReferenceResolution.Pending }
    }

    fun retry(reference: ExternalMediaReference) {
        if (reference !in visible) return
        generation++
        mutableEntries.value = mutableEntries.value + (reference to GiphyReferenceResolution.Pending)
        mutableRetry.value++
    }

    suspend fun resolvePending(resolve: suspend (List<ExternalMediaReference>) -> List<GiphyItem>) =
        resolvePendingWhenActive({ true }, resolve)

    suspend fun resolvePendingWhenActive(canPublish: () -> Boolean, resolve: suspend (List<ExternalMediaReference>) -> List<GiphyItem>) {
        if (!canPublish()) { replaceVisible(emptySet()); return }
        val epoch = generation
        val pending = visible.filter { mutableEntries.value[it] == GiphyReferenceResolution.Pending }.sortedBy { it.id }
        for ((index, batch) in pending.chunked(100).withIndex()) {
            currentCoroutineContext().ensureActive()
            if (epoch != generation) return
            if (!canPublish()) { replaceVisible(emptySet()); return }
            try {
                val items = resolve(batch)
                currentCoroutineContext().ensureActive()
                if (epoch != generation) return
                if (!canPublish()) { replaceVisible(emptySet()); return }
                val ids = batch.map { it.id }.toSet()
                if (items.any { it.id !in ids } || items.map { it.id }.distinct().size != items.size) {
                    throw GiphyException(GiphyIssue.InvalidResponse)
                }
                val byId = items.associateBy { it.id }
                mutableEntries.value = mutableEntries.value + batch.associateWith { ref ->
                    byId[ref.id]?.let { GiphyReferenceResolution.Ready(it) } ?: GiphyReferenceResolution.Missing
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (epoch != generation) return
                if (!canPublish()) { replaceVisible(emptySet()); return }
                val issue = (error as? GiphyException)?.issue ?: GiphyIssue.Unavailable
                // Stop the whole wave on failure/rate limit. Only explicit retry can resume it.
                mutableEntries.value = mutableEntries.value + pending.drop(index * 100).associateWith { GiphyReferenceResolution.Failed(issue) }
                return
            }
        }
    }
}

internal fun visibleGiphyReferences(
    nodes: Collection<ExternalMediaNode>, viewport: Viewport, size: IntSize,
    previews: Map<CanvasObjectId, CanvasTransform>, activity: MediaPlaybackActivity = MediaPlaybackActivity(),
    dragPreviews: Map<CanvasObjectId, Vec2> = emptyMap(),
): Set<ExternalMediaReference> {
    if (!activity.available || size.width <= 0 || size.height <= 0) return emptySet()
    return nodes.filter { node ->
        val transform = previews[node.id] ?: node.transform
        val corners = rotatedTransformCorners(transform.copy(position = transform.position + (dragPreviews[node.id] ?: Vec2.Zero))).map(viewport::worldToScreen)
        corners.maxOf { it.x } >= 0 && corners.maxOf { it.y } >= 0 &&
            corners.minOf { it.x } <= size.width && corners.minOf { it.y } <= size.height
    }.mapTo(mutableSetOf()) { it.reference }
}
