package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.i18n.Strings
import kotlin.math.*

internal data class SequenceMeasuredScene(
    val layout: SequenceDiagramLayout,
    val labels: Map<String, TextLayoutResult>,
    val headers: Map<String, TextLayoutResult>,
    val branchLabels: Map<Pair<String, Int>, TextLayoutResult>,
    val headerHeight: Float,
)

@Composable internal fun rememberSequenceScene(draft: SequenceDiagramDraft): SequenceMeasuredScene {
    val measurer = rememberTextMeasurer(cacheSize = 1024)
    val density = LocalDensity.current
    val colors = BoarderLessTheme.colors
    val style = TextStyle(fontSize = 14.sp, color = colors.contentText)
    return remember(draft, measurer, density.density, density.fontScale, colors.contentText) {
        val d = draft.validatedSnapshot()
        val columns = d.participants.mapIndexed { index, p -> p.id to index }.toMap()
        val labels =
            d.messages().associate { m ->
                val distance = abs(columns.getValue(m.sourceId) - columns.getValue(m.targetId))
                val width = if (m.isSelfMessage) 192 else distance * 240 - 24
                m.id to measurer.measure(m.text, style, constraints = Constraints(maxWidth = width), softWrap = true)
            }
        val headers =
            d.participants.associate {
                it.id to measurer.measure(it.label, style, constraints = Constraints(maxWidth = 144), softWrap = true)
            }
        val headerHeight = max(56f, (headers.values.maxOfOrNull { it.size.height } ?: 0) + 16f)
        val spans = labels.mapValues { ceil((it.value.size.height + 40f) / 64f).toInt().coerceIn(1, 128) }
        val initial = sequenceDiagramLayout(d, spans, headerHeight)
        val branchLabels =
            initial.blocks
                .flatMap { b ->
                    b.branches.mapIndexed { index, branch ->
                        (b.blockId to index) to
                            measurer.measure(
                                "${if (index == 0) {
                                    b.kind.name
                                } else if (b.kind == SequenceBlockKind.Alt) {
                                    "else"
                                } else {
                                    "and"
                                }}: ${branch.label}",
                                style,
                                constraints = Constraints(maxWidth = (initial.size.width - 48f).toInt()),
                                softWrap = true,
                            )
                    }
                }.toMap()
        val branchSpans = branchLabels.mapValues { ceil((it.value.size.height + 16f) / 64f).toInt().coerceIn(1, 128) }
        val layout = sequenceDiagramLayout(d, spans, headerHeight, branchSpans)
        SequenceMeasuredScene(layout, labels, headers, branchLabels, headerHeight)
    }
}

/** Rotation/scaling about the same container centre as normal canvas objects. */
internal fun sequenceSceneWorldPoint(
    transform: CanvasTransform,
    size: CanvasSize,
    point: Vec2,
): Vec2 {
    val relative =
        Vec2(
            (point.x - size.width / 2f) * transform.size.width / size.width,
            (point.y - size.height / 2f) * transform.size.height / size.height,
        )
    return transform.position +
        Vec2(
            transform.size.width / 2f,
            transform.size.height / 2f,
        ) + rotateVector(relative, transform.rotationDegrees)
}

@Composable internal fun SequenceDiagramScene(
    scene: SequenceMeasuredScene,
    transform: CanvasTransform,
    viewport: Viewport,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val colors = BoarderLessTheme.colors
    Canvas(
        modifier.semantics {
            contentDescription =
                Strings.sequence.sceneDescription(scene.layout.participants.size, scene.layout.messages.size)
        },
    ) {
        val origin = viewport.worldToScreen(transform.position)
        val width = transform.size.width * viewport.zoom
        val height = transform.size.height * viewport.zoom
        withTransform({
            translate(origin.x, origin.y)
            rotate(transform.rotationDegrees, Offset(width / 2f, height / 2f))
            scale(width / scene.layout.size.width, height / scene.layout.size.height, Offset.Zero)
        }) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            scene.layout.blocks.sortedBy { it.depth }.forEach { block ->
                drawRect(
                    colors.contentMuted.copy(alpha = .7f),
                    Offset(16f, block.top),
                    Size(scene.layout.size.width - 32f, block.bottom - block.top),
                    style = Stroke(1f),
                )
                block.branches.forEachIndexed { index, branch ->
                    val y = 60f + scene.headerHeight + branch.firstRow * 64f
                    if (index > 0) {
                        drawLine(
                            colors.contentMuted,
                            Offset(16f, y - 16f),
                            Offset(scene.layout.size.width - 16f, y - 16f),
                            pathEffect = dash,
                        )
                    }
                    drawText(scene.branchLabels.getValue(block.blockId to index), topLeft = Offset(24f, y - 8f))
                }
            }
            scene.layout.participants.forEach { p ->
                drawLine(
                    colors.contentMuted,
                    Offset(p.lifelineStart.x, p.lifelineStart.y),
                    Offset(p.lifelineEnd.x, p.lifelineEnd.y),
                    pathEffect = dash,
                )
                drawRect(colors.selection.copy(alpha = .12f), Offset(p.headerOrigin.x, p.headerOrigin.y), Size(160f, scene.headerHeight))
                drawRect(
                    colors.contentMuted,
                    Offset(p.headerOrigin.x, p.headerOrigin.y),
                    Size(160f, scene.headerHeight),
                    style = Stroke(1f),
                )
                drawText(scene.headers.getValue(p.participant.id), topLeft = Offset(p.headerOrigin.x + 8f, p.headerOrigin.y + 8f))
                if (p.participant.role == SequenceParticipantRole.Actor) {
                    val x = p.lifelineStart.x
                    val y = p.headerOrigin.y - 12f
                    drawCircle(colors.contentText, 3f, Offset(x, y))
                    drawLine(colors.contentText, Offset(x, y + 3), Offset(x, y + 10))
                    drawLine(colors.contentText, Offset(x - 6, y + 6), Offset(x + 6, y + 6))
                }
            }
            scene.layout.messages.forEach { placement ->
                val m = placement.message
                val points = placement.points
                points.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        colors.contentText,
                        Offset(a.x, a.y),
                        Offset(b.x, b.y),
                        strokeWidth = 1.5f,
                        pathEffect = if (m.dashed) dash else null,
                    )
                }
                if (m.kind != SequenceMessageKind.Signal) {
                    val end = points.last()
                    val delta = end - points[points.lastIndex - 1]
                    val length = sqrt(delta.x * delta.x + delta.y * delta.y)
                    val unit = delta / length
                    val tangent = Vec2(-unit.y, unit.x)
                    val a = end - unit * 10f + tangent * 5f
                    val b = end - unit * 10f - tangent * 5f
                    drawLine(colors.contentText, Offset(a.x, a.y), Offset(end.x, end.y), strokeWidth = 1.5f)
                    drawLine(colors.contentText, Offset(b.x, b.y), Offset(end.x, end.y), strokeWidth = 1.5f)
                    if (m.kind !=
                        SequenceMessageKind.Async
                    ) {
                        drawPath(
                            Path().apply {
                                moveTo(end.x, end.y)
                                lineTo(a.x, a.y)
                                lineTo(b.x, b.y)
                                close()
                            },
                            colors.contentText,
                        )
                    }
                }
                val label = scene.labels.getValue(m.id)
                val x = if (m.isSelfMessage) points.first().x - 80f else min(points.first().x, points.last().x) + 12f
                drawText(label, topLeft = Offset(x, points.first().y - label.size.height - 8f))
            }
        }
    }
}

@Composable internal fun WorkspaceSequenceScenes(
    workspace: Workspace,
    viewport: Viewport,
    dragPreviews: Map<CanvasObjectId, Vec2>,
    transformPreviews: Map<CanvasObjectId, CanvasTransform>,
) {
    workspace.sequenceDiagrams.values.forEach { diagram ->
        val root = workspace.objects[diagram.containerId] as? GroupFrame ?: return@forEach
        val draft = remember(diagram) { diagram.toDraft() }
        val scene = rememberSequenceScene(draft)
        val transform =
            transformPreviews[root.id] ?: root.transform.copy(position = root.transform.position + (dragPreviews[root.id] ?: Vec2.Zero))
        SequenceDiagramScene(scene, transform, viewport)
    }
}
