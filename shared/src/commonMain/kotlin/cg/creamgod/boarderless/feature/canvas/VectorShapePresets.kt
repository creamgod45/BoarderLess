package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import kotlin.math.cos
import kotlin.math.sin

/** Editable native paths, not SVG markup or new backend shape tokens. */
internal data class VectorShapePreset(val id: String, val title: String, val path: VectorPath)

internal fun vectorShapePresets(): List<VectorShapePreset> {
    fun move(x: Float, y: Float) = VectorPathCommand.Move(Vec2(x, y))
    fun line(x: Float, y: Float) = VectorPathCommand.Line(Vec2(x, y))
    fun cubic(a: Float, b: Float, c: Float, d: Float, x: Float, y: Float) =
        VectorPathCommand.Cubic(Vec2(a, b), Vec2(c, d), Vec2(x, y))
    fun path(vararg commands: VectorPathCommand) = VectorPath(CanvasSize(100f, 100f), commands.toList())
    fun radial(count: Int, radius: (Int) -> Int): VectorPath {
        val points = (0 until count).map { index ->
            val angle = index * kotlin.math.PI * 2 / count - kotlin.math.PI / 2
            Vec2((50 + cos(angle) * radius(index)).toFloat(), (50 + sin(angle) * radius(index)).toFloat())
        }
        return VectorPath(CanvasSize(100f, 100f), listOf(VectorPathCommand.Move(points.first())) +
            points.drop(1).map { VectorPathCommand.Line(it) } + VectorPathCommand.Close)
    }
    return listOf(
        VectorShapePreset("vector-tag", Strings.library.vectorTag(), path(move(8f, 22f), line(68f, 22f),
            line(94f, 50f), line(68f, 78f), line(8f, 78f), VectorPathCommand.Close)),
        VectorShapePreset("vector-bookmark", Strings.library.vectorBookmark(), path(move(22f, 8f), line(78f, 8f),
            line(78f, 92f), line(50f, 72f), line(22f, 92f), VectorPathCommand.Close)),
        VectorShapePreset("vector-cloud", Strings.library.vectorCloud(), path(move(23f, 79f),
            cubic(0f, 79f, 0f, 45f, 22f, 44f), cubic(12f, 12f, 51f, 4f, 63f, 29f),
            cubic(81f, 15f, 99f, 35f, 89f, 50f), cubic(99f, 65f, 90f, 81f, 76f, 79f),
            line(23f, 79f), VectorPathCommand.Close)),
        VectorShapePreset("vector-speech", Strings.library.vectorSpeech(), path(move(10f, 15f), line(90f, 15f),
            line(90f, 70f), line(49f, 70f), line(24f, 91f), line(28f, 70f), line(10f, 70f), VectorPathCommand.Close)),
        VectorShapePreset("vector-heart", Strings.library.vectorHeart(), path(move(50f, 91f),
            cubic(37f, 77f, 4f, 55f, 7f, 31f), cubic(10f, 7f, 38f, 7f, 50f, 29f),
            cubic(62f, 7f, 90f, 7f, 93f, 31f), cubic(96f, 55f, 63f, 77f, 50f, 91f), VectorPathCommand.Close)),
        VectorShapePreset("vector-gear", Strings.library.vectorGear(), radial(32) { if (it % 4 in 1..2) 46 else 36 }),
        VectorShapePreset("vector-burst", Strings.library.vectorBurst(), radial(24) { if (it % 2 == 0) 46 else 29 }),
        VectorShapePreset("vector-banner", Strings.library.vectorBanner(), path(move(6f, 27f), line(94f, 27f),
            line(80f, 50f), line(94f, 73f), line(6f, 73f), line(20f, 50f), VectorPathCommand.Close)),
    )
}
