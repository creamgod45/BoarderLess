package cg.creamgod.boarderless.domain.model

import kotlinx.serialization.Serializable

internal const val MaximumCanvasVersion = 9_007_199_254_740_991L
private val HexCanvasColor = Regex("#[0-9a-f]{6}")

@Serializable
data class CanvasGridStyle(
    val pattern: String = "lines",
    val colorToken: String = "auto",
) {
    init {
        require(pattern in setOf("none", "lines", "dots")) { "Unsupported canvas grid pattern" }
        require(colorToken == "auto" || HexCanvasColor.matches(colorToken)) { "Invalid canvas grid color" }
    }
}

/** Document content. Visibility, snapping, viewport and theme remain personal preferences. */
@Serializable
data class CanvasStyle(
    val schemaVersion: Int = 1,
    val backgroundToken: String = "default",
    val gridStyle: CanvasGridStyle = CanvasGridStyle(),
) {
    init {
        require(schemaVersion == 1) { "Unsupported canvas style schema" }
        require(backgroundToken in setOf("default", "warm", "cool") || HexCanvasColor.matches(backgroundToken)) {
            "Invalid canvas background color"
        }
    }
}
