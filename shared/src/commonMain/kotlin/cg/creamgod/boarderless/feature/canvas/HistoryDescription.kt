package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.CreateRelationsOperation
import cg.creamgod.boarderless.domain.history.DeleteObjectsOperation
import cg.creamgod.boarderless.domain.history.DeleteRelationsOperation
import cg.creamgod.boarderless.domain.history.EditTextOperation
import cg.creamgod.boarderless.domain.history.ReparentObjectsOperation
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.history.TransformObjectsOperation
import cg.creamgod.boarderless.domain.history.UpdateGroupFrameAttributesOperation
import cg.creamgod.boarderless.domain.history.UpdateMediaNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.UpdateMediaReferenceOperation
import cg.creamgod.boarderless.domain.history.UpdateRelationAttributesOperation
import cg.creamgod.boarderless.domain.history.UpdateTextNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.i18n.Strings

internal enum class HistoryDirection { Undo, Redo }

internal fun describeServerOperationType(operationType: String): String =
    when (operationType) {
        "update_canvas_style", "UpdateCanvasStyleOperation" -> Strings.canvasBackground.historyChanged()
        "create_object" -> Strings.history.operation.createdObject()
        "delete_objects" -> Strings.history.operation.deletedObjects()
        "update_object" -> Strings.history.operation.updatedObject()
        "move_objects", "transform_objects" -> Strings.history.operation.transformedObjects()
        "reparent_objects" -> Strings.history.operation.changedGroupMembership()
        "create_relation" -> Strings.history.operation.createdConnection()
        "delete_relations" -> Strings.history.operation.deletedConnections()
        "update_relation" -> Strings.history.operation.updatedConnection()
        else -> operationType.replace('_', ' ').replaceFirstChar(Char::uppercase)
    }

internal fun describeHistoryOperation(
    operation: WorkspaceOperation,
    direction: HistoryDirection,
): String {
    val undo = direction == HistoryDirection.Undo
    return when (operation) {
        is CreateObjectsOperation -> {
            describeObjectCount(created = !undo, operation.objects.size)
        }

        is DeleteObjectsOperation -> {
            describeObjectCount(created = undo, operation.objects.size)
        }

        is CreateRelationsOperation -> {
            describeConnectionCount(created = !undo, operation.relations.size)
        }

        is DeleteRelationsOperation -> {
            describeConnectionCount(created = undo, operation.relations.size)
        }

        is TransformObjectsOperation -> {
            Strings.history.transformedObjects(operation.changes.size)
        }

        is ReparentObjectsOperation -> {
            Strings.history.changedGroupForObjects(operation.changes.size)
        }

        is EditTextOperation -> {
            Strings.history.editedThoughts(operation.changes.size)
        }

        is UpdateTextNodeAttributesOperation -> {
            Strings.history.changedStyleForObjects(operation.changes.size)
        }

        is UpdateGroupFrameAttributesOperation -> {
            Strings.history.changedGroupSettingsForGroups(operation.changes.size)
        }

        is UpdateMediaNodeAttributesOperation -> {
            Strings.history.changedStyleForObjects(operation.changes.size)
        }

        is UpdateMediaReferenceOperation -> {
            Strings.history.changedItems(operation.changes.size)
        }

        is UpdateRelationAttributesOperation -> {
            Strings.history.changedConnectionSettingsForConnections(operation.changes.size)
        }

        is cg.creamgod.boarderless.domain.history.UpdateSequenceDiagramsOperation -> {
            Strings.sequence.historyChanged()
        }

        is cg.creamgod.boarderless.domain.history.UpdateCanvasStyleOperation -> {
            Strings.canvasBackground.historyChanged()
        }

        is TransactionOperation -> {
            if ("move-reparent-" in operation.operationId) {
                Strings.history.operation.movedObjectAndChangedGroup()
            } else {
                Strings.history.changedItems(operation.operations.size)
            }
        }
    }
}

private fun describeObjectCount(
    created: Boolean,
    count: Int,
): String =
    if (created) {
        Strings.history.createdObjects(count)
    } else {
        Strings.history.deletedObjects(count)
    }

private fun describeConnectionCount(
    created: Boolean,
    count: Int,
): String =
    if (created) {
        Strings.history.createdConnections(count)
    } else {
        Strings.history.deletedConnections(count)
    }
