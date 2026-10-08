package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class CanvasExportLive(val session: WorkspaceSession?, val workspace: Workspace, val epoch: String, val ready: Boolean)

/** Export is a read of a frozen authorized view, never an editing operation. */
internal class CanvasExportCapture(owner: WorkspaceSession, baseline: Workspace, private val epoch: String,
    selection: Set<CanvasObjectId>? = null) {
    val owner = owner.copy(workspace = freeze(owner.workspace))
    val baseline = freeze(baseline)
    val scene: Workspace
    val selectionOnly = selection != null
    init {
        require(epoch.isNotBlank() && this.baseline.id == owner.workspace.id)
        require(this.baseline.hasValidSequenceBindings())
        val ids = if (selection == null) this.baseline.objects.keys else {
            require(selection.isNotEmpty() && selection.all(this.baseline.objects::containsKey))
            val groups = selection.filterTo(mutableSetOf()) { this.baseline.objects[it] is GroupFrame }
            selection + descendantObjectIds(this.baseline, groups)
        }
        require(ids.isNotEmpty()) { "Nothing to export" }
        val diagrams = this.baseline.sequenceDiagrams.filterValues { it.allObjectIds().any(ids::contains) }
        require(diagrams.values.all { ids.containsAll(it.allObjectIds()) }) { "Select the complete sequence diagram" }
        val objects = this.baseline.objects.filterKeys(ids::contains).mapValues { (_, obj) ->
            obj.withParentId(obj.parentId?.takeIf(ids::contains), version = obj.version)
        }
        scene = this.baseline.copy(objects = objects,
            relations = this.baseline.relations.filterValues { it.sourceObjectId in ids && it.targetObjectId in ids },
            sequenceDiagrams = diagrams)
        require(scene.hasValidSequenceBindings())
    }
    fun isCurrent(live: CanvasExportLive): Boolean = live.ready && live.epoch == epoch &&
        live.session == owner && live.workspace == baseline

    private companion object {
        private val snapshotJson = Json { allowStructuredMapKeys = true }
        fun freeze(workspace: Workspace): Workspace = snapshotJson.decodeFromString(snapshotJson.encodeToString(workspace))
    }
}
