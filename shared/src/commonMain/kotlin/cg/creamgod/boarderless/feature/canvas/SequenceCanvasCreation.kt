package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import kotlin.math.abs

/** Preparing a complete preview only allocates identities. Caller must revalidate live scope and
 * explicitly retain the exact transaction before publishing history/queue changes. */
internal class SequenceCanvasCreation(
    owner: WorkspaceSession,
    source: Workspace,
    val position: Vec2,
) {
    val baseline =
        source.copy(
            objects = source.objects.toMap(),
            relations = source.relations.toMap(),
            sequenceDiagrams = source.sequenceDiagrams.mapValues { it.value.validatedSnapshot() },
        )
    private val capture = PenCanvasCreation(owner.copy(workspace = baseline), baseline, position)

    init {
        require(source == owner.workspace && source.hasValidSequenceBindings())
    }

    fun isCurrent(
        session: WorkspaceSession?,
        workspace: Workspace,
    ) = capture.isCurrent(session, workspace)

    fun operation(
        source: SequenceDiagramDraft,
        title: String,
        layoutOverride: SequenceDiagramLayout? = null,
        newId: () -> String,
    ): TransactionOperation {
        val draft = source.validatedSnapshot()
        requireSequenceTextForCanvas(title)
        require(
            baseline.sequenceDiagrams.size < 64 && baseline.sequenceDiagramsVersion < MaximumCanvasVersion &&
                baseline.version < MaximumCanvasVersion,
        )
        val ids = mutableSetOf<String>()

        fun id() =
            newId().also { value ->
                require(value.isNotBlank() && value.length <= 128 && ids.add(value))
                require(baseline.objects.keys.none { it.value == value } && baseline.relations.keys.none { it.value == value })
            }
        val container = CanvasObjectId(id())
        val layout = layoutOverride ?: sequenceDiagramLayout(draft)
        require(layout.participants.map { it.participant } == draft.participants && layout.messages.map { it.message } == draft.messages())
        val participantIds = draft.participants.associate { it.id to CanvasObjectId(id()) }
        val messageIds = draft.messages().associate { it.id to RelationId(id()) }
        val blockIds = layout.blocks.associate { it.blockId to CanvasObjectId(id()) }
        val diagram = SequenceCanvasDiagram.bind(draft, container, participantIds, messageIds, blockIds)
        val objectCount = 1 + draft.participants.size + layout.blocks.size
        val highest = baseline.objects.values.maxOfOrNull { it.zIndex } ?: 0L
        require(highest <= Long.MAX_VALUE - objectCount)
        var z = highest

        fun transform(
            local: Vec2,
            size: CanvasSize,
        ): CanvasTransform {
            val point = position + local
            require(abs(point.x) <= 1_000_000f && abs(point.y) <= 1_000_000f && size.width in 1f..100_000f && size.height in 1f..100_000f)
            return CanvasTransform(point, size)
        }
        val objects =
            buildList<CanvasObject> {
                add(GroupFrame(container, transform = transform(Vec2.Zero, layout.size), zIndex = ++z, title = title))
                layout.participants.forEach { placement ->
                    add(
                        TextNode(
                            participantIds.getValue(placement.participant.id),
                            parentId = container,
                            zIndex = ++z,
                            transform =
                                transform(
                                    placement.headerOrigin,
                                    CanvasSize(160f, placement.lifelineStart.y - placement.headerOrigin.y - 4f),
                                ),
                            text = placement.participant.label,
                            shape = NodeShape.Rectangle,
                        ),
                    )
                }
                layout.blocks.forEach { placement ->
                    add(
                        GroupFrame(
                            blockIds.getValue(placement.blockId),
                            parentId = container,
                            zIndex = ++z,
                            transform =
                                transform(
                                    Vec2(16f, placement.top),
                                    CanvasSize(
                                        layout.size.width - 32f,
                                        placement.bottom - placement.top,
                                    ),
                                ),
                            title = placement.kind.name,
                        ),
                    )
                }
            }
        val relations =
            draft.messages().map { message ->
                Relation(
                    messageIds.getValue(message.id),
                    sourceObjectId = participantIds.getValue(message.sourceId),
                    targetObjectId = participantIds.getValue(message.targetId),
                    label = message.text,
                    direction = if (message.kind == SequenceMessageKind.Signal) RelationDirection.None else RelationDirection.Forward,
                    geometry = if (message.isSelfMessage) RelationGeometry(route = RelationRoute.Loop()) else null,
                )
            }
        val operation =
            TransactionOperation(
                id(),
                listOf(
                    CreateObjectsOperation(id(), objects),
                    CreateRelationsOperation(id(), relations),
                    UpdateSequenceDiagramsOperation(
                        id(),
                        baseline.sequenceDiagramsVersion,
                        baseline.sequenceDiagrams,
                        baseline.sequenceDiagrams + (container to diagram),
                    ),
                ),
            )
        require(operation.applyTo(baseline) is OperationResult.Applied)
        return operation
    }
}

private fun requireSequenceTextForCanvas(title: String) {
    require(title.isNotBlank() && title.encodeToByteArray().size <= 4096)
    title.encodeToByteArray(throwOnInvalidSequence = true)
}
