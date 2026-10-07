package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.min

@Composable internal fun QuickSchemePreview(
    payload: ClipboardPayload,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val workspace =
        remember(payload) {
            if (payload.sequenceDiagrams.isEmpty()) {
                null
            } else {
                runCatching {
                    require(validateClipboardPayload(payload) == null)
                    val ids =
                        payload.groups.map { it.originalId } + payload.nodes.map { it.originalId } + payload.media.map { it.originalId }
                    clipboardMaterialization(
                        payload,
                        Vec2.Zero,
                        ids.associateWith(::CanvasObjectId),
                        payload.relations.map { RelationId(requireNotNull(it.originalId)) },
                        0,
                    )
                }.getOrNull()
            }
        }
    val managed =
        workspace
            ?.sequenceDiagrams
            ?.values
            ?.flatMap { it.allObjectIds() }
            ?.map { it.value }
            ?.toSet()
            .orEmpty()
    val edges =
        workspace
            ?.sequenceDiagrams
            ?.values
            ?.flatMap { it.allRelationIds() }
            ?.map { it.value }
            ?.toSet()
            .orEmpty()
    val ordinary =
        if (workspace == null) {
            payload
        } else {
            payload.copy(
                nodes = payload.nodes.filter { it.originalId !in managed },
                groups = payload.groups.filter { it.originalId !in managed },
                relations = payload.relations.filter { it.originalId !in edges },
                sequenceDiagrams = emptyList(),
            )
        }
    Box(modifier.onSizeChanged { size = it }.clip(RoundedCornerShape(12.dp))) {
        QuickSchemeObjectsPreview(ordinary, payload, Modifier.fillMaxSize())
        if (workspace != null && size.width > 0 && size.height > 0) {
            WorkspaceSequenceScenes(
                workspace,
                clipboardPreviewViewport(payload, size.width.toFloat(), size.height.toFloat(), density),
                emptyMap(),
                emptyMap(),
            )
        }
    }
}

/** Preview projects ordinary geometry and authoritative sequence scenes with one viewport. */
internal fun clipboardPreviewViewport(
    payload: ClipboardPayload,
    width: Float,
    height: Float,
    density: Float,
): Viewport {
    val routes = clipboardGeometryRoutes(payload).values.flatten()
    val lefts = payload.nodes.map { it.x } + payload.groups.map { it.x } + payload.media.map { it.x }
    val tops = payload.nodes.map { it.y } + payload.groups.map { it.y } + payload.media.map { it.y }
    val rights = payload.nodes.map { it.x + it.width } + payload.groups.map { it.x + it.width } + payload.media.map { it.x + it.width }
    val bottoms = payload.nodes.map { it.y + it.height } + payload.groups.map { it.y + it.height } + payload.media.map { it.y + it.height }
    if (lefts.isEmpty()) return Viewport()
    val roots = payload.sequenceDiagrams.map { it.containerId.value }.toSet()
    val rotatedRoots = payload.groups.filter { it.originalId in roots }.flatMap {
        rotatedTransformCorners(CanvasTransform(Vec2(it.x, it.y), CanvasSize(it.width, it.height), it.rotationDegrees))
    }
    val minX = (lefts + routes.map { it.x } + rotatedRoots.map { it.x }).min()
    val minY = (tops + routes.map { it.y } + rotatedRoots.map { it.y }).min()
    val contentWidth = ((rights + routes.map { it.x } + rotatedRoots.map { it.x }).max() - minX).coerceAtLeast(1f)
    val contentHeight = ((bottoms + routes.map { it.y } + rotatedRoots.map { it.y }).max() - minY).coerceAtLeast(1f)
    val padding = 12f * density
    val scale = min((width - padding * 2).coerceAtLeast(1f) / contentWidth, (height - padding * 2).coerceAtLeast(1f) / contentHeight)
    return Viewport(Vec2((width - contentWidth * scale) / 2 - minX * scale, (height - contentHeight * scale) / 2 - minY * scale), scale)
}

@Composable
private fun QuickSchemeObjectsPreview(
    payload: ClipboardPayload,
    boundsPayload: ClipboardPayload,
    modifier: Modifier = Modifier,
) {
    val colors = BoarderLessTheme.colors
    Canvas(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.canvas.copy(alpha = 0.72f))
                .border(1.dp, colors.contentBorder, RoundedCornerShape(12.dp)),
    ) {
        if (payload.nodes.isEmpty() && payload.groups.isEmpty() && payload.media.isEmpty()) return@Canvas
        val geometryRoutes = clipboardGeometryRoutes(payload)
        val viewport = clipboardPreviewViewport(boundsPayload, size.width, size.height, density)
        val scale = viewport.zoom

        fun project(
            x: Float,
            y: Float,
        ): Offset = viewport.worldToScreen(Vec2(x, y)).let { Offset(it.x, it.y) }

        payload.groups.forEach { group ->
            val topLeft = project(group.x, group.y)
            val groupSize = Size(group.width * scale, group.height * scale)
            val groupColor =
                when (group.colorToken) {
                    "lilac" -> colors.nodeLilac
                    "amber" -> colors.nodeAmber
                    "mint" -> colors.nodeMint
                    else -> colors.selection
                }
            val radius = 6f * density
            drawRoundRect(
                color = groupColor.copy(alpha = 0.13f),
                topLeft = topLeft,
                size = groupSize,
                cornerRadius = CornerRadius(radius, radius),
            )
            drawRoundRect(
                color = groupColor.copy(alpha = 0.75f),
                topLeft = topLeft,
                size = groupSize,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 1.25f * density),
            )
        }

        val centersById =
            payload.nodes.associate { it.originalId to project(it.x + it.width / 2f, it.y + it.height / 2f) } +
                payload.media.associate { it.originalId to project(it.x + it.width / 2f, it.y + it.height / 2f) }
        payload.relations.forEach { relation ->
            val start = centersById[relation.sourceId] ?: return@forEach
            val end = centersById[relation.targetId] ?: return@forEach
            val path = geometryRoutes[relation]?.map { project(it.x, it.y) } ?: listOf(start, end)
            path.zipWithNext().forEach { (a, b) ->
                drawLine(
                    color =
                        when (relation.intent) {
                            "conflicts" -> colors.danger
                            "supports" -> colors.selection
                            else -> colors.contentMuted
                        }.copy(alpha = 0.82f),
                    start = a,
                    end = b,
                    strokeWidth = 1.5f * density,
                )
            }
        }

        payload.nodes.forEach { node ->
            val topLeft = project(node.x, node.y)
            val nodeSize =
                Size(
                    width = (node.width * scale).coerceAtLeast(8f * density),
                    height = (node.height * scale).coerceAtLeast(6f * density),
                )
            val nodeShape = NodeShape.fromToken(node.shapeToken) ?: NodeShape.RoundedRectangle
            val nodeColor =
                if (nodeShape == NodeShape.PlainText) {
                    nodeTextColors(node.colorToken, colors, nodeShape).text
                } else {
                    nodeFillColor(node.colorToken, colors)
                }
            if (node.vectorPath != null) {
                withTransform({
                    translate(topLeft.x, topLeft.y)
                    rotate(node.rotationDegrees, Offset(nodeSize.width / 2f, nodeSize.height / 2f))
                    scale(nodeSize.width / node.vectorPath.viewBox.width, nodeSize.height / node.vectorPath.viewBox.height, Offset.Zero)
                }) {
                    drawVectorPath(node.vectorPath) { token -> if (token == "ink") colors.contentText else nodeFillColor(token, colors) }
                }
            } else {
                drawNodePreviewShape(
                    shape = nodeShape,
                    color = nodeColor,
                    borderColor = colors.contentBorder,
                    topLeft = topLeft,
                    size = nodeSize,
                    cornerRadius = 4f * density,
                    borderWidth = 1f * density,
                )
            }
        }
        payload.media.forEach { media ->
            val topLeft = project(media.x, media.y)
            val mediaSize =
                Size(
                    width = (media.width * scale).coerceAtLeast(8f * density),
                    height = (media.height * scale).coerceAtLeast(6f * density),
                )
            drawRoundRect(
                color = colors.canvas,
                topLeft = topLeft,
                size = mediaSize,
                cornerRadius = CornerRadius(4f * density),
            )
            drawRoundRect(
                color = colors.accent,
                topLeft = topLeft,
                size = mediaSize,
                cornerRadius = CornerRadius(4f * density),
                style = Stroke(1f * density),
            )
        }
    }
}
