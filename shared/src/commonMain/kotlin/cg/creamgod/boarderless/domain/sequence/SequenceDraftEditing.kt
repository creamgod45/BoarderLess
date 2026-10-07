package cg.creamgod.boarderless.domain.sequence

/** A location in the semantic tree, never a canvas y-coordinate. Null block means top level. */
data class SequenceStepLocation(
    val blockId: String? = null,
    val branchIndex: Int = 0,
) {
    init {
        require(branchIndex >= 0 && (blockId != null || branchIndex == 0))
        blockId?.let(::requireSequenceId)
    }
}

fun SequenceDiagramDraft.reorderParticipants(ids: List<String>): SequenceDiagramDraft {
    val snapshot = validatedSnapshot()
    require(ids.size == snapshot.participants.size && ids.toSet() == snapshot.participants.map { it.id }.toSet())
    return snapshot.copy(participants = ids.map { id -> snapshot.participants.single { it.id == id } })
}

/** Insert/replace/remove in one named branch. Constructors verify IDs, endpoints and block shape. */
fun SequenceDiagramDraft.editSteps(
    location: SequenceStepLocation,
    edit: (List<SequenceStep>) -> List<SequenceStep>,
): SequenceDiagramDraft {
    val snapshot = validatedSnapshot()
    var matched = false

    fun visit(items: List<SequenceStep>): List<SequenceStep> =
        items.map { step ->
            if (step !is SequenceBlock) {
                step
            } else if (step.id == location.blockId) {
                require(location.branchIndex in step.branches.indices)
                matched = true
                step.copy(
                    branches =
                        step.branches.mapIndexed { index, branch ->
                            if (index == location.branchIndex) branch.copy(steps = edit(branch.steps)) else branch
                        },
                )
            } else {
                step.copy(branches = step.branches.map { it.copy(steps = visit(it.steps)) })
            }
        }
    val after =
        if (location.blockId == null) {
            matched = true
            edit(snapshot.steps)
        } else {
            visit(snapshot.steps)
        }
    require(matched) { "Missing sequence block" }
    return snapshot.copy(steps = after).validatedSnapshot()
}

fun SequenceDiagramDraft.insertMessage(
    location: SequenceStepLocation,
    index: Int,
    message: SequenceMessage,
): SequenceDiagramDraft =
    editSteps(location) { items ->
        require(index in 0..items.size)
        items.toMutableList().also { it.add(index, message) }
    }

fun SequenceDiagramDraft.moveStep(
    location: SequenceStepLocation,
    stepId: String,
    index: Int,
): SequenceDiagramDraft =
    editSteps(location) { items ->
        val source = items.indexOfFirst { it.id == stepId }
        require(source >= 0 && index in items.indices)
        items.toMutableList().also {
            val value = it.removeAt(source)
            it.add(index, value)
        }
    }
