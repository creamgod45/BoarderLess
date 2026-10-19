package cg.creamgod.boarderless.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** BoarderLess commands, not SVG markup, URLs or executable content. Coordinates are local units. */
@Serializable
sealed interface VectorPathCommand {
    @Serializable
    @SerialName("move")
    data class Move(
        val point: Vec2,
    ) : VectorPathCommand

    @Serializable
    @SerialName("line")
    data class Line(
        val point: Vec2,
    ) : VectorPathCommand

    @Serializable
    @SerialName("quadratic")
    data class Quadratic(
        val control: Vec2,
        val point: Vec2,
    ) : VectorPathCommand

    @Serializable
    @SerialName("cubic")
    data class Cubic(
        val control1: Vec2,
        val control2: Vec2,
        val point: Vec2,
    ) : VectorPathCommand

    @Serializable
    @SerialName("close")
    data object Close : VectorPathCommand
}

@Serializable enum class VectorFillRule { NonZero, EvenOdd }

@Serializable
data class VectorPathStyle(
    val fillColorToken: String? = "paper",
    val strokeColorToken: String? = "ink",
    val strokeWidth: Float = 2f,
    val fillRule: VectorFillRule = VectorFillRule.NonZero,
) {
    init {
        require(fillColorToken != null || strokeColorToken != null)
        require(listOfNotNull(fillColorToken, strokeColorToken).all { it.matches(Regex("[A-Za-z0-9#_-]{1,64}")) })
        require(strokeWidth.isFinite() && strokeWidth > 0f && strokeWidth <= 128f)
    }
}

@Serializable
data class VectorPath(
    val viewBox: CanvasSize,
    val commands: List<VectorPathCommand>,
    val style: VectorPathStyle = VectorPathStyle(),
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1)
        require(viewBox.width in 0.001f..100_000f && viewBox.height in 0.001f..100_000f)
        require(commands.size in 2..4096)
        var open = false
        var segments = 0
        var subpaths = 0
        commands.forEach { command ->
            command.points().forEach(::requireVectorCoordinate)
            when (command) {
                is VectorPathCommand.Move -> {
                    require(!open || segments > 0) // No empty subpath, including consecutive moves.
                    require(++subpaths <= 128)
                    open = true
                    segments = 0
                }

                VectorPathCommand.Close -> {
                    require(open && segments > 0)
                    open = false
                }

                else -> {
                    require(open)
                    segments++
                }
            }
        }
        require(!open || segments > 0)
    }
}

internal fun requireVectorCoordinate(point: Vec2) {
    require(abs(point.x) <= 1_000_000f && abs(point.y) <= 1_000_000f)
}

internal fun VectorPathCommand.points(): List<Vec2> =
    when (this) {
        is VectorPathCommand.Move -> listOf(point)
        is VectorPathCommand.Line -> listOf(point)
        is VectorPathCommand.Quadratic -> listOf(control, point)
        is VectorPathCommand.Cubic -> listOf(control1, control2, point)
        VectorPathCommand.Close -> emptyList()
    }
