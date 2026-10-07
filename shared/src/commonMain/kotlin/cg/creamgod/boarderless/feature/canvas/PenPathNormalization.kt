package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*

internal data class NormalizedPenPath(
    val path: VectorPath,
    val origin: Vec2,
)

/** Conservative control-point/stroke bounds; every command keeps its local-unit geometry. */
internal fun normalizePenPath(path: VectorPath): NormalizedPenPath {
    val points =
        path.commands.flatMap { command ->
            when (command) {
                is VectorPathCommand.Move -> listOf(command.point)
                is VectorPathCommand.Line -> listOf(command.point)
                is VectorPathCommand.Quadratic -> listOf(command.control, command.point)
                is VectorPathCommand.Cubic -> listOf(command.control1, command.control2, command.point)
                VectorPathCommand.Close -> emptyList()
            }
        }
    val padding = if (path.style.strokeColorToken != null) path.style.strokeWidth / 2f else 0.5f
    val origin = Vec2(points.minOf { it.x } - padding, points.minOf { it.y } - padding)
    val box = CanvasSize(points.maxOf { it.x } - origin.x + padding, points.maxOf { it.y } - origin.y + padding)

    fun point(p: Vec2) = p - origin
    val normalized =
        path.copy(
            viewBox = box,
            commands =
                path.commands.map { command ->
                    when (command) {
                        is VectorPathCommand.Move -> {
                            command.copy(point = point(command.point))
                        }

                        is VectorPathCommand.Line -> {
                            command.copy(point = point(command.point))
                        }

                        is VectorPathCommand.Quadratic -> {
                            command.copy(control = point(command.control), point = point(command.point))
                        }

                        is VectorPathCommand.Cubic -> {
                            command.copy(
                                control1 = point(command.control1),
                                control2 = point(command.control2),
                                point = point(command.point),
                            )
                        }

                        VectorPathCommand.Close -> {
                            command
                        }
                    }
                },
        )
    val contours = normalized.flatten()
    require(contours.any { contour -> contour.points.zipWithNext().any { (a, b) -> a != b } }) { "Path has no segment" }
    if (normalized.style.strokeColorToken == null) {
        require(
            contours.any { contour ->
                val distinct = contour.points.distinct()
                if (distinct.size < 3) {
                    false
                } else {
                    val a = distinct[1] - distinct[0]
                    distinct.drop(2).any { p ->
                        val b = p - distinct[0]
                        kotlin.math.abs(a.x.toDouble() * b.y - a.y.toDouble() * b.x) > 0.000001
                    }
                }
            },
        ) { "Fill has no area" }
    }
    return NormalizedPenPath(normalized, origin)
}
