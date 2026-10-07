package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.sequence.*

internal data class SequenceParticipantPlacement(
    val participant: SequenceParticipant,
    val headerOrigin: Vec2,
    val lifelineStart: Vec2,
    val lifelineEnd: Vec2,
)

internal data class SequenceMessagePlacement(
    val message: SequenceMessage,
    val points: List<Vec2>,
    val containingBranches: List<Pair<String, Int>>,
)

internal data class SequenceBranchPlacement(
    val label: String,
    val firstRow: Int,
    val lastRow: Int,
)

internal data class SequenceBlockPlacement(
    val blockId: String,
    val kind: SequenceBlockKind,
    val depth: Int,
    val top: Float,
    val bottom: Float,
    val branches: List<SequenceBranchPlacement>,
)

internal data class SequenceDiagramLayout(
    val size: CanvasSize,
    val participants: List<SequenceParticipantPlacement>,
    val messages: List<SequenceMessagePlacement>,
    val blocks: List<SequenceBlockPlacement>,
)

/** A visual row plan from the explicit semantic tree. Parallel branches have separate labeled
 * visual bands; their row positions are not an execution-order assertion. No ordinary graph layout. */
internal fun sequenceDiagramLayout(
    source: SequenceDiagramDraft,
    messageRowSpans: Map<String, Int> = emptyMap(),
    headerHeight: Float = 56f,
    branchRowSpans: Map<Pair<String, Int>, Int> = emptyMap(),
): SequenceDiagramLayout {
    require(headerHeight.isFinite() && headerHeight in 56f..100_000f)
    require(messageRowSpans.values.all { it in 1..128 } && branchRowSpans.values.all { it in 1..128 })
    val draft = source.validatedSnapshot()
    val columns = draft.participants.mapIndexed { index, p -> p.id to (112f + index * 240f) }.toMap()
    var row = 0

    fun y(index: Int) = 60f + headerHeight + index * 64f
    val messages = mutableListOf<SequenceMessagePlacement>()
    val blocks = mutableListOf<SequenceBlockPlacement>()

    fun visit(
        steps: List<SequenceStep>,
        containment: List<Pair<String, Int>>,
    ) {
        for (step in steps) {
            when (step) {
                is SequenceMessage -> {
                    val from = columns.getValue(step.sourceId)
                    val to = columns.getValue(step.targetId)
                    val span = messageRowSpans[step.id] ?: 1
                    val height = y(row + span - 1)
                    row += span
                    val points =
                        if (step.isSelfMessage) {
                            listOf(
                                Vec2(from, height),
                                Vec2(from + 96f, height),
                                Vec2(from + 96f, height + 24f),
                                Vec2(
                                    from,
                                    height + 24f,
                                ),
                            )
                        } else {
                            listOf(Vec2(from, height), Vec2(to, height))
                        }
                    messages.add(SequenceMessagePlacement(step, points, containment))
                }

                is SequenceBlock -> {
                    val top = y(row++) - 24f
                    val branches =
                        step.branches.mapIndexed { index, branch ->
                            val first = row
                            row += branchRowSpans[step.id to index] ?: 1
                            visit(branch.steps, containment + (step.id to index))
                            SequenceBranchPlacement(branch.label, first, row - 1)
                        }
                    blocks.add(SequenceBlockPlacement(step.id, step.kind, containment.size, top, y(row++) - 16f, branches))
                }
            }
        }
    }
    visit(draft.steps, emptyList())
    val bottom = y(row) + 24f
    return SequenceDiagramLayout(
        CanvasSize(224f + (draft.participants.size - 1) * 240f, bottom + 24f),
        draft.participants.map { p ->
            val x = columns.getValue(p.id)
            SequenceParticipantPlacement(p, Vec2(x - 80f, 24f), Vec2(x, 28f + headerHeight), Vec2(x, bottom))
        },
        messages,
        blocks,
    )
}
