package cg.creamgod.boarderless.domain.history

import kotlinx.serialization.Serializable

import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationId
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.withTransform
import cg.creamgod.boarderless.domain.model.withParentId

sealed interface OperationResult {
    data class Applied(val workspace: Workspace) : OperationResult

    data class Rejected(val error: OperationError) : OperationResult
}

sealed interface OperationError {
    data class DuplicateObject(val objectId: CanvasObjectId) : OperationError

    data class MissingObject(val objectId: CanvasObjectId) : OperationError

    data class VersionConflict(
        val objectId: CanvasObjectId,
        val expected: Long,
        val actual: Long,
    ) : OperationError

    data class StateConflict(val objectId: CanvasObjectId) : OperationError

    data class UnsupportedObject(val objectId: CanvasObjectId) : OperationError

    data class HierarchyCycle(val objectId: CanvasObjectId) : OperationError

    data class HierarchyCascadeMismatch(
        val missingObjectIds: Set<CanvasObjectId>,
    ) : OperationError

    data class DuplicateRelation(val relationId: RelationId) : OperationError

    data class MissingRelation(val relationId: RelationId) : OperationError

    data class RelationStateConflict(val relationId: RelationId) : OperationError

    data class RelationCascadeMismatch(
        val missingRelationIds: Set<RelationId>,
        val unexpectedRelationIds: Set<RelationId>,
    ) : OperationError

    data class ChildOperationRejected(
        val operationId: String,
        val cause: OperationError,
    ) : OperationError
}

@Serializable
sealed interface WorkspaceOperation {
    val operationId: String

    fun applyTo(workspace: Workspace): OperationResult

    fun inverse(): WorkspaceOperation
}

@Serializable
data class CreateObjectsOperation(
    override val operationId: String,
    val objects: List<CanvasObject>,
) : WorkspaceOperation {
    init {
        require(objects.isNotEmpty()) { "Create operation needs at least one object" }
        require(objects.map { it.id }.distinct().size == objects.size) {
            "Create operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        objects.firstOrNull { workspace.objects.containsKey(it.id) }?.let {
            return OperationResult.Rejected(OperationError.DuplicateObject(it.id))
        }

        val prospectiveObjects = workspace.objects + objects.associateBy { it.id }
        for (canvasObject in objects) {
            var parentId = canvasObject.parentId
            val visited = mutableSetOf(canvasObject.id)
            while (parentId != null) {
                val parent = prospectiveObjects[parentId]
                    ?: return OperationResult.Rejected(OperationError.MissingObject(parentId))
                if (parent !is GroupFrame) {
                    return OperationResult.Rejected(OperationError.UnsupportedObject(parentId))
                }
                if (!visited.add(parentId)) {
                    return OperationResult.Rejected(OperationError.HierarchyCycle(canvasObject.id))
                }
                parentId = parent.parentId
            }
        }

        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                objects = prospectiveObjects,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = DeleteObjectsOperation(
        operationId = "$operationId:inverse",
        objects = objects,
    )
}

@Serializable
data class DeleteObjectsOperation(
    override val operationId: String,
    val objects: List<CanvasObject>,
    val relations: List<Relation> = emptyList(),
) : WorkspaceOperation {
    init {
        require(objects.isNotEmpty()) { "Delete operation needs at least one object" }
        require(objects.map { it.id }.distinct().size == objects.size) {
            "Delete operation cannot contain duplicate ids"
        }
        require(relations.map { it.id }.distinct().size == relations.size) {
            "Delete operation cannot contain duplicate relation ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        for (snapshot in objects) {
            val current = workspace.objects[snapshot.id]
                ?: return OperationResult.Rejected(OperationError.MissingObject(snapshot.id))
            if (current.version != snapshot.version) {
                return OperationResult.Rejected(
                    OperationError.VersionConflict(snapshot.id, snapshot.version, current.version),
                )
            }
            if (current != snapshot) {
                return OperationResult.Rejected(OperationError.StateConflict(snapshot.id))
            }
        }

        val deletedObjectIds = objects.mapTo(mutableSetOf()) { it.id }
        val missingDescendantIds = workspace.objects.values
            .filterTo(mutableListOf()) { canvasObject ->
                canvasObject.id !in deletedObjectIds && canvasObject.parentId in deletedObjectIds
            }
            .mapTo(mutableSetOf()) { it.id }
        if (missingDescendantIds.isNotEmpty()) {
            return OperationResult.Rejected(
                OperationError.HierarchyCascadeMismatch(missingDescendantIds),
            )
        }

        val expectedRelationIds = workspace.relations.values
            .filterTo(mutableListOf()) { relation ->
                relation.sourceObjectId in deletedObjectIds || relation.targetObjectId in deletedObjectIds
            }
            .mapTo(mutableSetOf()) { it.id }
        val suppliedRelationIds = relations.mapTo(mutableSetOf()) { it.id }
        if (expectedRelationIds != suppliedRelationIds) {
            return OperationResult.Rejected(
                OperationError.RelationCascadeMismatch(
                    missingRelationIds = expectedRelationIds - suppliedRelationIds,
                    unexpectedRelationIds = suppliedRelationIds - expectedRelationIds,
                ),
            )
        }

        for (snapshot in relations) {
            val current = workspace.relations[snapshot.id]
                ?: return OperationResult.Rejected(OperationError.MissingRelation(snapshot.id))
            if (current != snapshot) {
                return OperationResult.Rejected(OperationError.RelationStateConflict(snapshot.id))
            }
        }

        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                objects = workspace.objects - deletedObjectIds,
                relations = workspace.relations - suppliedRelationIds,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation {
        val createObjects = CreateObjectsOperation(
            operationId = "$operationId:inverse:objects",
            objects = objects,
        )
        return if (relations.isEmpty()) {
            createObjects
        } else {
            TransactionOperation(
                operationId = "$operationId:inverse",
                operations = listOf(
                    createObjects,
                    CreateRelationsOperation("$operationId:inverse:relations", relations),
                ),
            )
        }
    }
}

@Serializable
data class CreateRelationsOperation(
    override val operationId: String,
    val relations: List<Relation>,
) : WorkspaceOperation {
    init {
        require(relations.isNotEmpty()) { "Create relation operation needs at least one relation" }
        require(relations.map { it.id }.distinct().size == relations.size) {
            "Create relation operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        relations.firstOrNull { workspace.relations.containsKey(it.id) }?.let {
            return OperationResult.Rejected(OperationError.DuplicateRelation(it.id))
        }
        for (relation in relations) {
            if (relation.sourceObjectId == relation.targetObjectId) {
                return OperationResult.Rejected(OperationError.RelationStateConflict(relation.id))
            }
            if (!workspace.objects.containsKey(relation.sourceObjectId)) {
                return OperationResult.Rejected(OperationError.MissingObject(relation.sourceObjectId))
            }
            if (!workspace.objects.containsKey(relation.targetObjectId)) {
                return OperationResult.Rejected(OperationError.MissingObject(relation.targetObjectId))
            }
        }
        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                relations = workspace.relations + relations.associateBy { it.id },
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = DeleteRelationsOperation(
        operationId = "$operationId:inverse",
        relations = relations,
    )
}

@Serializable
data class DeleteRelationsOperation(
    override val operationId: String,
    val relations: List<Relation>,
) : WorkspaceOperation {
    init {
        require(relations.isNotEmpty()) { "Delete relation operation needs at least one relation" }
        require(relations.map { it.id }.distinct().size == relations.size) {
            "Delete relation operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        for (snapshot in relations) {
            val current = workspace.relations[snapshot.id]
                ?: return OperationResult.Rejected(OperationError.MissingRelation(snapshot.id))
            if (current != snapshot) {
                return OperationResult.Rejected(OperationError.RelationStateConflict(snapshot.id))
            }
        }
        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                relations = workspace.relations - relations.map { it.id }.toSet(),
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = CreateRelationsOperation(
        operationId = "$operationId:inverse",
        relations = relations,
    )
}

@Serializable
data class RelationAttributes(
    val direction: cg.creamgod.boarderless.domain.model.RelationDirection,
    val intent: String?,
    val label: String?,
    val colorToken: String,
)

@Serializable
data class RelationAttributesChange(
    val relationId: RelationId,
    val expectedVersion: Long,
    val before: RelationAttributes,
    val after: RelationAttributes,
) {
    fun inverse(): RelationAttributesChange = copy(
        expectedVersion = expectedVersion + 1,
        before = after,
        after = before,
    )
}

@Serializable
data class UpdateRelationAttributesOperation(
    override val operationId: String,
    val changes: List<RelationAttributesChange>,
) : WorkspaceOperation {
    init {
        require(changes.isNotEmpty()) { "Relation attribute operation needs at least one change" }
        require(changes.map { it.relationId }.distinct().size == changes.size) {
            "Relation attribute operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        val replacements = mutableMapOf<RelationId, Relation>()
        for (change in changes) {
            val current = workspace.relations[change.relationId]
                ?: return OperationResult.Rejected(OperationError.MissingRelation(change.relationId))
            if (current.version != change.expectedVersion) {
                return OperationResult.Rejected(OperationError.RelationStateConflict(change.relationId))
            }
            val currentAttributes = RelationAttributes(
                direction = current.direction,
                intent = current.intent,
                label = current.label,
                colorToken = current.colorToken,
            )
            if (currentAttributes != change.before) {
                return OperationResult.Rejected(OperationError.RelationStateConflict(change.relationId))
            }
            replacements[current.id] = current.copy(
                version = current.version + 1,
                direction = change.after.direction,
                intent = change.after.intent,
                label = change.after.label,
                colorToken = change.after.colorToken,
            )
        }
        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                relations = workspace.relations + replacements,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = UpdateRelationAttributesOperation(
        operationId = "$operationId:inverse",
        changes = changes.map(RelationAttributesChange::inverse),
    )
}

@Serializable
data class TransformChange(
    val objectId: CanvasObjectId,
    val expectedVersion: Long,
    val before: CanvasTransform,
    val after: CanvasTransform,
) {
    fun inverse(): TransformChange = copy(
        expectedVersion = expectedVersion + 1,
        before = after,
        after = before,
    )
}

@Serializable
data class TransformObjectsOperation(
    override val operationId: String,
    val changes: List<TransformChange>,
) : WorkspaceOperation {
    init {
        require(changes.isNotEmpty()) { "Transform operation needs at least one change" }
        require(changes.map { it.objectId }.distinct().size == changes.size) {
            "Transform operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        val replacements = mutableMapOf<CanvasObjectId, CanvasObject>()
        for (change in changes) {
            val current = workspace.objects[change.objectId]
                ?: return OperationResult.Rejected(OperationError.MissingObject(change.objectId))
            if (current.version != change.expectedVersion) {
                return OperationResult.Rejected(
                    OperationError.VersionConflict(
                        objectId = change.objectId,
                        expected = change.expectedVersion,
                        actual = current.version,
                    ),
                )
            }
            if (current.transform != change.before) {
                return OperationResult.Rejected(OperationError.StateConflict(change.objectId))
            }
            replacements[change.objectId] = current.withTransform(change.after)
        }

        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                objects = workspace.objects + replacements,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = TransformObjectsOperation(
        operationId = "$operationId:inverse",
        changes = changes.map(TransformChange::inverse),
    )
}

@Serializable
data class ParentChange(
    val objectId: CanvasObjectId,
    val expectedVersion: Long,
    val before: CanvasObjectId?,
    val after: CanvasObjectId?,
) {
    fun inverse(): ParentChange = copy(
        expectedVersion = expectedVersion + 1,
        before = after,
        after = before,
    )
}

@Serializable
data class ReparentObjectsOperation(
    override val operationId: String,
    val changes: List<ParentChange>,
) : WorkspaceOperation {
    init {
        require(changes.isNotEmpty()) { "Reparent operation needs at least one change" }
        require(changes.map { it.objectId }.distinct().size == changes.size) {
            "Reparent operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        val replacements = mutableMapOf<CanvasObjectId, CanvasObject>()
        for (change in changes) {
            val current = workspace.objects[change.objectId]
                ?: return OperationResult.Rejected(OperationError.MissingObject(change.objectId))
            if (current.version != change.expectedVersion) {
                return OperationResult.Rejected(
                    OperationError.VersionConflict(change.objectId, change.expectedVersion, current.version),
                )
            }
            if (current.parentId != change.before) {
                return OperationResult.Rejected(OperationError.StateConflict(change.objectId))
            }
            change.after?.let { parentId ->
                val parent = workspace.objects[parentId]
                if (parentId == current.id || parent == null) {
                    return OperationResult.Rejected(OperationError.MissingObject(parentId))
                }
                if (parent !is GroupFrame) {
                    return OperationResult.Rejected(OperationError.UnsupportedObject(parentId))
                }
            }
            replacements[current.id] = current.withParentId(change.after)
        }
        val prospectiveObjects = workspace.objects + replacements
        for (changedObject in replacements.values) {
            val visited = mutableSetOf<CanvasObjectId>()
            var parentId = changedObject.parentId
            while (parentId != null) {
                if (parentId == changedObject.id || !visited.add(parentId)) {
                    return OperationResult.Rejected(OperationError.HierarchyCycle(changedObject.id))
                }
                parentId = prospectiveObjects[parentId]?.parentId
            }
        }
        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                objects = workspace.objects + replacements,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = ReparentObjectsOperation(
        operationId = "$operationId:inverse",
        changes = changes.map(ParentChange::inverse),
    )
}

@Serializable
data class TextChange(
    val objectId: CanvasObjectId,
    val expectedVersion: Long,
    val before: String,
    val after: String,
) {
    fun inverse(): TextChange = copy(
        expectedVersion = expectedVersion + 1,
        before = after,
        after = before,
    )
}

@Serializable
data class EditTextOperation(
    override val operationId: String,
    val changes: List<TextChange>,
) : WorkspaceOperation {
    init {
        require(changes.isNotEmpty()) { "Text operation needs at least one change" }
        require(changes.map { it.objectId }.distinct().size == changes.size) {
            "Text operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        val replacements = mutableMapOf<CanvasObjectId, CanvasObject>()
        for (change in changes) {
            val current = workspace.objects[change.objectId]
                ?: return OperationResult.Rejected(OperationError.MissingObject(change.objectId))
            if (current !is TextNode) {
                return OperationResult.Rejected(OperationError.UnsupportedObject(change.objectId))
            }
            if (current.version != change.expectedVersion) {
                return OperationResult.Rejected(
                    OperationError.VersionConflict(
                        objectId = change.objectId,
                        expected = change.expectedVersion,
                        actual = current.version,
                    ),
                )
            }
            if (current.text != change.before) {
                return OperationResult.Rejected(OperationError.StateConflict(change.objectId))
            }
            replacements[change.objectId] = current.copy(
                version = current.version + 1,
                text = change.after,
            )
        }

        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                objects = workspace.objects + replacements,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = EditTextOperation(
        operationId = "$operationId:inverse",
        changes = changes.map(TextChange::inverse),
    )
}

@Serializable
data class TextNodeAttributes(
    val zIndex: Long,
    val locked: Boolean,
    val colorToken: String,
    val shape: NodeShape = NodeShape.RoundedRectangle,
)

@Serializable
data class TextNodeAttributesChange(
    val objectId: CanvasObjectId,
    val expectedVersion: Long,
    val before: TextNodeAttributes,
    val after: TextNodeAttributes,
) {
    fun inverse(): TextNodeAttributesChange = copy(
        expectedVersion = expectedVersion + 1,
        before = after,
        after = before,
    )
}

@Serializable
data class UpdateTextNodeAttributesOperation(
    override val operationId: String,
    val changes: List<TextNodeAttributesChange>,
) : WorkspaceOperation {
    init {
        require(changes.isNotEmpty()) { "Attribute operation needs at least one change" }
        require(changes.map { it.objectId }.distinct().size == changes.size) {
            "Attribute operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        val replacements = mutableMapOf<CanvasObjectId, CanvasObject>()
        for (change in changes) {
            val current = workspace.objects[change.objectId]
                ?: return OperationResult.Rejected(OperationError.MissingObject(change.objectId))
            if (current !is TextNode) {
                return OperationResult.Rejected(OperationError.UnsupportedObject(change.objectId))
            }
            if (current.version != change.expectedVersion) {
                return OperationResult.Rejected(
                    OperationError.VersionConflict(change.objectId, change.expectedVersion, current.version),
                )
            }
            val currentAttributes = TextNodeAttributes(
                zIndex = current.zIndex,
                locked = current.locked,
                colorToken = current.colorToken,
                shape = current.shape,
            )
            if (currentAttributes != change.before) {
                return OperationResult.Rejected(OperationError.StateConflict(change.objectId))
            }
            replacements[change.objectId] = current.copy(
                version = current.version + 1,
                zIndex = change.after.zIndex,
                locked = change.after.locked,
                colorToken = change.after.colorToken,
                shape = change.after.shape,
            )
        }

        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                objects = workspace.objects + replacements,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = UpdateTextNodeAttributesOperation(
        operationId = "$operationId:inverse",
        changes = changes.map(TextNodeAttributesChange::inverse),
    )
}

@Serializable
data class GroupFrameAttributes(
    val zIndex: Long,
    val locked: Boolean,
    val colorToken: String,
    val title: String,
)

@Serializable
data class GroupFrameAttributesChange(
    val objectId: CanvasObjectId,
    val expectedVersion: Long,
    val before: GroupFrameAttributes,
    val after: GroupFrameAttributes,
) {
    fun inverse(): GroupFrameAttributesChange = copy(
        expectedVersion = expectedVersion + 1,
        before = after,
        after = before,
    )
}

@Serializable
data class UpdateGroupFrameAttributesOperation(
    override val operationId: String,
    val changes: List<GroupFrameAttributesChange>,
) : WorkspaceOperation {
    init {
        require(changes.isNotEmpty()) { "Group attribute operation needs at least one change" }
        require(changes.map { it.objectId }.distinct().size == changes.size) {
            "Group attribute operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        val replacements = mutableMapOf<CanvasObjectId, CanvasObject>()
        for (change in changes) {
            val current = workspace.objects[change.objectId]
                ?: return OperationResult.Rejected(OperationError.MissingObject(change.objectId))
            if (current !is GroupFrame) {
                return OperationResult.Rejected(OperationError.UnsupportedObject(change.objectId))
            }
            if (current.version != change.expectedVersion) {
                return OperationResult.Rejected(
                    OperationError.VersionConflict(change.objectId, change.expectedVersion, current.version),
                )
            }
            val currentAttributes = GroupFrameAttributes(
                current.zIndex,
                current.locked,
                current.colorToken,
                current.title,
            )
            if (currentAttributes != change.before) {
                return OperationResult.Rejected(OperationError.StateConflict(change.objectId))
            }
            replacements[current.id] = current.copy(
                version = current.version + 1,
                zIndex = change.after.zIndex,
                locked = change.after.locked,
                colorToken = change.after.colorToken,
                title = change.after.title,
            )
        }
        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                objects = workspace.objects + replacements,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = UpdateGroupFrameAttributesOperation(
        operationId = "$operationId:inverse",
        changes = changes.map(GroupFrameAttributesChange::inverse),
    )
}

@Serializable
data class MediaNodeAttributes(
    val zIndex: Long,
    val locked: Boolean,
    val altText: String,
)

@Serializable
data class MediaNodeAttributesChange(
    val objectId: CanvasObjectId,
    val expectedVersion: Long,
    val before: MediaNodeAttributes,
    val after: MediaNodeAttributes,
) {
    fun inverse(): MediaNodeAttributesChange = copy(
        expectedVersion = expectedVersion + 1,
        before = after,
        after = before,
    )
}

@Serializable
data class UpdateMediaNodeAttributesOperation(
    override val operationId: String,
    val changes: List<MediaNodeAttributesChange>,
) : WorkspaceOperation {
    init {
        require(changes.isNotEmpty()) { "Media attribute operation needs at least one change" }
        require(changes.map { it.objectId }.distinct().size == changes.size) {
            "Media attribute operation cannot contain duplicate ids"
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        val replacements = mutableMapOf<CanvasObjectId, CanvasObject>()
        for (change in changes) {
            val current = workspace.objects[change.objectId]
                ?: return OperationResult.Rejected(OperationError.MissingObject(change.objectId))
            if (current !is MediaNode) {
                return OperationResult.Rejected(OperationError.UnsupportedObject(change.objectId))
            }
            if (current.version != change.expectedVersion) {
                return OperationResult.Rejected(
                    OperationError.VersionConflict(change.objectId, change.expectedVersion, current.version),
                )
            }
            val currentAttributes = MediaNodeAttributes(current.zIndex, current.locked, current.altText)
            if (currentAttributes != change.before) {
                return OperationResult.Rejected(OperationError.StateConflict(change.objectId))
            }
            replacements[current.id] = current.copy(
                version = current.version + 1,
                zIndex = change.after.zIndex,
                locked = change.after.locked,
                altText = change.after.altText,
            )
        }
        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                objects = workspace.objects + replacements,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation = UpdateMediaNodeAttributesOperation(
        operationId = "$operationId:inverse",
        changes = changes.map(MediaNodeAttributesChange::inverse),
    )
}

@Serializable
data class TransactionOperation(
    override val operationId: String,
    val operations: List<WorkspaceOperation>,
) : WorkspaceOperation {
    init {
        require(operations.isNotEmpty()) { "Transaction needs at least one operation" }
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        var current = workspace
        for (operation in operations) {
            when (val result = operation.applyTo(current)) {
                is OperationResult.Applied -> current = result.workspace
                is OperationResult.Rejected -> return OperationResult.Rejected(
                    OperationError.ChildOperationRejected(operation.operationId, result.error),
                )
            }
        }
        // Child operations run against each other's object/relation versions, but the transaction is
        // one atomic Workspace revision. This mirrors the backend contract, which advances
        // currentVersion once per accepted transaction rather than once per expanded operation.
        return OperationResult.Applied(current.copy(version = workspace.version + 1))
    }

    override fun inverse(): WorkspaceOperation = TransactionOperation(
        operationId = "$operationId:inverse",
        operations = operations.asReversed().map(WorkspaceOperation::inverse),
    )
}
