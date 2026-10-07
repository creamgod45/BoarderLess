package cg.creamgod.boarderless.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.math.abs

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("mode")
sealed interface RelationRoute {
    @Serializable
    @SerialName("auto")
    data object Auto : RelationRoute

    @Serializable
    @SerialName("manual")
    data class Manual(
        val waypoints: List<Vec2>,
    ) : RelationRoute {
        init {
            require(
                waypoints.size in 1..32 &&
                    waypoints.all { it.x.isFinite() && it.y.isFinite() && abs(it.x) <= 10_000_000f && abs(it.y) <= 10_000_000f },
            )
        }
    }

    @Serializable
    @SerialName("loop")
    data class Loop(
        val side: String = "right",
        val extent: Float = 80f,
    ) : RelationRoute {
        init {
            require(side in setOf("top", "right", "bottom", "left") && extent.isFinite() && extent in 16f..4096f)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("mode")
sealed interface RelationLabelPlacement {
    @Serializable
    @SerialName("auto")
    data object Auto : RelationLabelPlacement

    @Serializable
    @SerialName("manual")
    data class Manual(
        val pathFraction: Float,
        val tangentOffset: Float = 0f,
        val normalOffset: Float = 24f,
    ) : RelationLabelPlacement {
        init {
            require(
                pathFraction.isFinite() && pathFraction in 0f..1f && tangentOffset.isFinite() && normalOffset.isFinite() &&
                    abs(tangentOffset) <= 4096f &&
                    abs(normalOffset) <= 4096f,
            )
        }
    }
}

@Serializable
data class RelationGeometry(
    val schema: String = "boarderless.relation-geometry.v1",
    val route: RelationRoute = RelationRoute.Auto,
    val labelPlacement: RelationLabelPlacement = RelationLabelPlacement.Auto,
) {
    init {
        require(schema == "boarderless.relation-geometry.v1")
    }

    fun allowsEndpoints(
        source: CanvasObjectId,
        target: CanvasObjectId,
    ): Boolean = if (source == target) route !is RelationRoute.Manual else route !is RelationRoute.Loop

    fun translated(delta: Vec2): RelationGeometry =
        copy(
            route =
                when (val path = route) {
                    is RelationRoute.Manual -> path.copy(waypoints = path.waypoints.map { it + delta })
                    else -> path
                },
        )
}
