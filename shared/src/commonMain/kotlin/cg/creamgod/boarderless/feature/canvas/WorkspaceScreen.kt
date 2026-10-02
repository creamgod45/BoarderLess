package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.i18n.Strings
import cg.creamgod.boarderless.data.RecentWorkspaceStore
import cg.creamgod.boarderless.data.RecentWorkspacesPublisher
import cg.creamgod.boarderless.data.WorkspaceLaunchRequests
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.designsystem.BoarderLessColors
import cg.creamgod.boarderless.designsystem.GlassSurface
import cg.creamgod.boarderless.designsystem.ButtonGap
import cg.creamgod.boarderless.designsystem.ShellButton
import cg.creamgod.boarderless.designsystem.horizontalScrollWithMouseWheel
import cg.creamgod.boarderless.designsystem.ShellIcon
import cg.creamgod.boarderless.designsystem.color.ColorPickerWindow
import cg.creamgod.boarderless.designsystem.color.ColorSwatchButton
import cg.creamgod.boarderless.designsystem.color.ColorSwatchOption
import cg.creamgod.boarderless.designsystem.color.RgbColor
import cg.creamgod.boarderless.data.persistence.UiPreferences
import cg.creamgod.boarderless.i18n.LanguagePreference
import cg.creamgod.boarderless.i18n.Localization
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.WorkspaceSummary
import cg.creamgod.boarderless.data.WorkspaceActivity
import cg.creamgod.boarderless.data.WorkspaceMember
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.MediaPlaybackActivity
import cg.creamgod.boarderless.data.VideoPlayback
import cg.creamgod.boarderless.data.VideoPlaybackLease
import cg.creamgod.boarderless.data.workspaceCatchUpRequests
import cg.creamgod.boarderless.data.VideoPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import cg.creamgod.boarderless.data.AssetImportCoordinator
import cg.creamgod.boarderless.data.AssetImportStage
import cg.creamgod.boarderless.data.AssetTransferSource
import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.data.remote.AssetTransferUnavailableException
import cg.creamgod.boarderless.data.WorkspaceSubmissionQueue
import cg.creamgod.boarderless.data.WorkspaceSubmissionStep
import cg.creamgod.boarderless.data.canEditContent
import cg.creamgod.boarderless.data.hasRemoteChangesComparedTo
import cg.creamgod.boarderless.data.submitNextWorkspaceChange
import cg.creamgod.boarderless.data.remote.BackendWorkspaceRepository
import cg.creamgod.boarderless.data.remote.BackendHttpException
import cg.creamgod.boarderless.data.remote.BackendContractException
import cg.creamgod.boarderless.data.remote.isWorkspaceAccessLoss
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.data.persistence.CanvasPreferences
import cg.creamgod.boarderless.data.persistence.CanvasDisplaySettings
import cg.creamgod.boarderless.data.persistence.QuickSchemeStore
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.CreateRelationsOperation
import cg.creamgod.boarderless.domain.history.DeleteObjectsOperation
import cg.creamgod.boarderless.domain.history.DeleteRelationsOperation
import cg.creamgod.boarderless.domain.history.EditTextOperation
import cg.creamgod.boarderless.domain.history.TextChange
import cg.creamgod.boarderless.domain.history.TransformChange
import cg.creamgod.boarderless.domain.history.TransformObjectsOperation
import cg.creamgod.boarderless.domain.history.TextNodeAttributes
import cg.creamgod.boarderless.domain.history.TextNodeAttributesChange
import cg.creamgod.boarderless.domain.history.UpdateTextNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.RelationAttributes
import cg.creamgod.boarderless.domain.history.RelationAttributesChange
import cg.creamgod.boarderless.domain.history.UpdateRelationAttributesOperation
import cg.creamgod.boarderless.domain.history.GroupFrameAttributes
import cg.creamgod.boarderless.domain.history.GroupFrameAttributesChange
import cg.creamgod.boarderless.domain.history.UpdateGroupFrameAttributesOperation
import cg.creamgod.boarderless.domain.history.MediaNodeAttributes
import cg.creamgod.boarderless.domain.history.MediaNodeAttributesChange
import cg.creamgod.boarderless.domain.history.UpdateMediaNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import cg.creamgod.boarderless.domain.history.HistoryResult
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.history.ParentChange
import cg.creamgod.boarderless.domain.history.ReparentObjectsOperation
import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Viewport
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.RelationId
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.MediaKind
import cg.creamgod.boarderless.domain.model.MediaNode
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import cg.creamgod.boarderless.data.GifAnimation
import cg.creamgod.boarderless.data.playGifFrames
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val GridSize = 32f

private data class Marquee(
    val start: Vec2,
    val end: Vec2,
)

private data class AlignmentGuides(
    val verticalWorldX: Float? = null,
    val horizontalWorldY: Float? = null,
)

private data class ConnectionDragPreview(
    val sourceId: CanvasObjectId,
    val startScreen: Vec2,
    val endScreen: Vec2,
    val targetId: CanvasObjectId?,
)

internal data class AlignmentSnapResult(
    val delta: Vec2,
    val verticalWorldX: Float? = null,
    val horizontalWorldY: Float? = null,
)

internal fun selectionAfterObjectTap(
    selectedIds: Set<CanvasObjectId>,
    objectId: CanvasObjectId,
    additive: Boolean,
): Set<CanvasObjectId> = when {
    !additive -> setOf(objectId)
    objectId in selectedIds -> selectedIds - objectId
    else -> selectedIds + objectId
}

internal fun hierarchyAwareSelectionAfterObjectTap(
    selectedIds: Set<CanvasObjectId>,
    objectId: CanvasObjectId,
    additive: Boolean,
    objectsById: Map<CanvasObjectId, CanvasObject>,
): Set<CanvasObjectId> {
    val next = selectionAfterObjectTap(selectedIds, objectId, additive)
    if (!additive || objectId !in next) return next

    fun hasAncestor(objectToCheck: CanvasObjectId, possibleAncestor: CanvasObjectId): Boolean {
        val visited = mutableSetOf<CanvasObjectId>()
        var parentId = objectsById[objectToCheck]?.parentId
        while (parentId != null && visited.add(parentId)) {
            if (parentId == possibleAncestor) return true
            parentId = objectsById[parentId]?.parentId
        }
        return false
    }

    return next.filterTo(linkedSetOf()) { selectedId ->
        selectedId == objectId ||
            (!hasAncestor(selectedId, objectId) && !hasAncestor(objectId, selectedId))
    }
}

internal fun topLevelSelectionIds(objects: Collection<CanvasObject>): Set<CanvasObjectId> =
    objects.filterTo(mutableListOf()) { it.parentId == null }
        .mapTo(linkedSetOf(), CanvasObject::id)

internal fun usesCompactCanvasLayout(widthPixels: Int, heightPixels: Int, density: Float): Boolean {
    if (widthPixels <= 0 || heightPixels <= 0 || density <= 0f) return false
    val widthDp = widthPixels / density
    val heightDp = heightPixels / density
    return widthDp < 720f || heightDp < 600f
}

internal fun shouldShowDesktopCanvasToolbar(widthPixels: Int, heightPixels: Int, density: Float): Boolean =
    widthPixels > 0 && heightPixels > 0 && density > 0f &&
        !usesCompactCanvasLayout(widthPixels, heightPixels, density)

internal fun transformHandleTouchTargetDp(largeTouchTargets: Boolean): Float =
    if (largeTouchTargets) 44f else 18f

internal fun transformHandleVisualSizeDp(largeTouchTargets: Boolean): Float =
    if (largeTouchTargets) 22f else 18f

internal fun compactInspectorWidthDp(widthPixels: Int, density: Float): Float =
    if (widthPixels <= 0 || density <= 0f) 284f else min(284f, max(160f, widthPixels / density - 24f))

internal fun compactBottomSheetWidthDp(widthPixels: Int, density: Float): Float =
    if (widthPixels <= 0 || density <= 0f) 320f else min(560f, max(120f, widthPixels / density - 16f))

internal fun emptyCanvasCardWidthDp(widthPixels: Int, density: Float, compactLayout: Boolean): Float = when {
    !compactLayout || widthPixels <= 0 || density <= 0f -> 360f
    else -> min(360f, max(120f, widthPixels / density - 24f))
}

internal fun compactStatusMaxWidthDp(widthPixels: Int, density: Float): Float =
    if (widthPixels <= 0 || density <= 0f) 320f else min(360f, max(120f, widthPixels / density - 32f))

internal fun compactSheetExpandedAfterDrag(
    expanded: Boolean,
    dragYPixels: Float,
    density: Float,
): Boolean {
    if (!dragYPixels.isFinite() || !density.isFinite() || density <= 0f) return expanded
    val threshold = 32f * density
    return when {
        dragYPixels <= -threshold -> true
        dragYPixels >= threshold -> false
        else -> expanded
    }
}

internal fun shouldShowStatusOverlay(
    compactModalVisible: Boolean,
    commandPaletteVisible: Boolean,
): Boolean = !compactModalVisible && !commandPaletteVisible

internal fun shouldShowCompactMenuEntry(
    compactLayout: Boolean,
    compactModalVisible: Boolean,
    commandPaletteVisible: Boolean,
): Boolean = compactLayout && !compactModalVisible && !commandPaletteVisible

internal data class WorkspaceChangePolicy(
    val sessionAvailable: Boolean,
    val connectionFailed: Boolean = false,
    val switchInProgress: Boolean = false,
    val syncInProgress: Boolean = false,
    val pendingSaveCount: Int = 0,
    val nodeEditing: Boolean = false,
    val contentInspectorEditing: Boolean = false,
    val pendingNodeDraft: Boolean = false,
    val objectDragActive: Boolean = false,
    val transformActive: Boolean = false,
    val connectionDragActive: Boolean = false,
    val marqueeActive: Boolean = false,
    val libraryDragActive: Boolean = false,
    val mediaImportActive: Boolean = false,
) {
    init {
        require(pendingSaveCount >= 0) { "Pending save count cannot be negative" }
    }

    val allowed: Boolean
        get() = sessionAvailable &&
            !connectionFailed &&
            !switchInProgress &&
            !syncInProgress &&
            pendingSaveCount == 0 &&
            !nodeEditing &&
            !contentInspectorEditing &&
            !pendingNodeDraft &&
            !objectDragActive &&
            !transformActive &&
            !connectionDragActive &&
            !marqueeActive &&
            !libraryDragActive &&
            !mediaImportActive
}

@Serializable
internal data class ClipboardPayload(
    val format: String = "boarderless/selection",
    val version: Int = 4,
    val nodes: List<ClipboardNode>,
    val groups: List<ClipboardGroup> = emptyList(),
    val media: List<ClipboardMedia> = emptyList(),
    val relations: List<ClipboardRelation> = emptyList(),
)

@Serializable
internal data class ClipboardNode(
    val originalId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
    val text: String,
    val colorToken: String,
    val shapeToken: String = NodeShape.RoundedRectangle.token,
    val parentOriginalId: String? = null,
    val locked: Boolean = false,
    val zOffset: Long = 0,
)

@Serializable
internal data class ClipboardGroup(
    val originalId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
    val title: String,
    val colorToken: String,
    val parentOriginalId: String? = null,
    val locked: Boolean = false,
    val zOffset: Long = 0,
)

@Serializable
internal data class ClipboardMedia(
    val originalId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
    val assetId: String,
    val mediaKind: String,
    val altText: String = "",
    val thumbnailAssetId: String? = null,
    val parentOriginalId: String? = null,
    val locked: Boolean = false,
    val zOffset: Long = 0,
)

@Serializable
internal data class ClipboardRelation(
    val sourceId: String,
    val targetId: String,
    val direction: String,
    val intent: String? = null,
    val label: String? = null,
)

private data class BuiltInComponent(
    val entry: ComponentLibraryEntry,
    val payload: ClipboardPayload,
)

private data class LibraryDragPreview(
    val title: String,
    val screenPosition: Vec2,
)

private val ClipboardJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal enum class ClipboardPayloadIssue {
    UnsupportedFormat,
    UnsupportedVersion,
    Empty,
    InvalidObjectId,
    DuplicateObjectId,
    InvalidTransform,
    UnsupportedShape,
    InvalidMedia,
    InvalidParent,
    ParentCycle,
    InvalidRelation,
}

internal fun validateClipboardPayload(payload: ClipboardPayload): ClipboardPayloadIssue? {
    if (payload.format != "boarderless/selection") return ClipboardPayloadIssue.UnsupportedFormat
    if (payload.version !in 1..4) return ClipboardPayloadIssue.UnsupportedVersion
    if (payload.nodes.isEmpty() && payload.groups.isEmpty() && payload.media.isEmpty()) {
        return ClipboardPayloadIssue.Empty
    }

    val objectIds = payload.groups.map(ClipboardGroup::originalId) +
        payload.nodes.map(ClipboardNode::originalId) +
        payload.media.map(ClipboardMedia::originalId)
    if (objectIds.any(String::isBlank)) return ClipboardPayloadIssue.InvalidObjectId
    if (objectIds.toSet().size != objectIds.size) return ClipboardPayloadIssue.DuplicateObjectId

    fun validTransform(x: Float, y: Float, width: Float, height: Float, rotation: Float): Boolean =
        x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() && rotation.isFinite() &&
            kotlin.math.abs(x) <= 1_000_000f && kotlin.math.abs(y) <= 1_000_000f &&
            width > 0f && height > 0f && width <= 100_000f && height <= 100_000f

    if (payload.nodes.any {
            !validTransform(it.x, it.y, it.width, it.height, it.rotationDegrees)
        } || payload.groups.any {
            !validTransform(it.x, it.y, it.width, it.height, it.rotationDegrees)
        } || payload.media.any {
            !validTransform(it.x, it.y, it.width, it.height, it.rotationDegrees)
        }
    ) {
        return ClipboardPayloadIssue.InvalidTransform
    }
    if (payload.nodes.any { NodeShape.fromToken(it.shapeToken) == null }) {
        return ClipboardPayloadIssue.UnsupportedShape
    }
    if (payload.media.any {
            it.assetId.isBlank() || MediaKind.fromToken(it.mediaKind) == null ||
                it.thumbnailAssetId?.isBlank() == true
        }
    ) {
        return ClipboardPayloadIssue.InvalidMedia
    }

    val groupIds = payload.groups.mapTo(mutableSetOf(), ClipboardGroup::originalId)
    if (payload.nodes.any { it.parentOriginalId != null && it.parentOriginalId !in groupIds } ||
        payload.media.any { it.parentOriginalId != null && it.parentOriginalId !in groupIds } ||
        payload.groups.any {
            it.parentOriginalId != null &&
                (it.parentOriginalId !in groupIds || it.parentOriginalId == it.originalId)
        }
    ) {
        return ClipboardPayloadIssue.InvalidParent
    }
    val parentByGroupId = payload.groups.associate { it.originalId to it.parentOriginalId }
    val visited = mutableSetOf<String>()
    val visiting = mutableSetOf<String>()
    fun hasParentCycle(groupId: String): Boolean {
        if (groupId in visited) return false
        if (!visiting.add(groupId)) return true
        val cyclic = parentByGroupId[groupId]?.let(::hasParentCycle) == true
        visiting.remove(groupId)
        visited += groupId
        return cyclic
    }
    if (groupIds.any(::hasParentCycle)) return ClipboardPayloadIssue.ParentCycle

    val nodeIds = payload.nodes.mapTo(mutableSetOf(), ClipboardNode::originalId)
    if (payload.relations.any { relation ->
            relation.sourceId !in nodeIds || relation.targetId !in nodeIds ||
                relation.sourceId == relation.targetId ||
                runCatching { RelationDirection.valueOf(relation.direction) }.isFailure
        }
    ) {
        return ClipboardPayloadIssue.InvalidRelation
    }
    return null
}

/** One button of the compact canvas menu. */
private data class CompactMenuItem(val label: String, val icon: ShellIcon, val action: () -> Unit)

private fun relationIntentLabel(intent: String): String = when (intent) {
    "relates" -> Strings.objects.relates()
    "supports" -> Strings.objects.supports()
    "conflicts" -> Strings.objects.conflicts()
    else -> intent.replaceFirstChar { it.uppercase() }
}

private fun workspaceRoleLabel(role: String): String = when (role) {
    "owner" -> Strings.roles.owner()
    "editor" -> Strings.roles.editor()
    "commenter" -> Strings.roles.commenter()
    "viewer" -> Strings.roles.viewer()
    "current" -> Strings.roles.current()
    else -> role
}

private fun builtInComponents(density: Float): List<BuiltInComponent> {
    val nodeWidth = 240f * density
    val nodeHeight = 116f * density
    val gapX = 72f * density
    val gapY = 84f * density
    fun node(
        id: String,
        x: Float,
        y: Float,
        text: String,
        color: String = "paper",
        shape: NodeShape = NodeShape.RoundedRectangle,
        parentId: String? = null,
        zOffset: Long = 0,
        width: Float = nodeWidth,
        height: Float = nodeHeight,
    ) = ClipboardNode(
        originalId = id,
        x = x,
        y = y,
        width = width,
        height = height,
        rotationDegrees = 0f,
        text = text,
        colorToken = color,
        shapeToken = shape.token,
        parentOriginalId = parentId,
        zOffset = zOffset,
    )
    fun relation(source: String, target: String, intent: String = "relates") = ClipboardRelation(
        sourceId = source,
        targetId = target,
        direction = RelationDirection.Forward.name,
        intent = intent,
    )
    fun shapeComponent(
        id: String,
        title: String,
        description: String,
        text: String,
        color: String,
        shape: NodeShape,
    ) = BuiltInComponent(
        entry = ComponentLibraryEntry(
            id = id,
            title = title,
            description = description,
            colorToken = color,
            shape = shape,
        ),
        payload = ClipboardPayload(
            nodes = listOf(node(id, 0f, 0f, text, color, shape)),
        ),
    )
    return listOf(
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "thought",
                title = Strings.library.thoughtCard(),
                description = Strings.library.cleanTextNodeForNew(),
                colorToken = "paper",
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    ClipboardNode("thought", 0f, 0f, nodeWidth, nodeHeight, 0f, Strings.content.newThought(), "paper"),
                ),
            ),
        ),
        shapeComponent(
            id = "process-node",
            title = Strings.library.processNode(),
            description = Strings.library.standardActionOrProcessStep(),
            text = Strings.objects.process(),
            color = "lilac",
            shape = NodeShape.Rectangle,
        ),
        shapeComponent(
            id = "start-end-node",
            title = Strings.library.startEndNode(),
            description = Strings.library.flowStartTerminalStateOr(),
            text = Strings.objects.startEnd(),
            color = "mint",
            shape = NodeShape.Pill,
        ),
        shapeComponent(
            id = "decision-node",
            title = Strings.library.decisionNode(),
            description = Strings.library.branchingConditionOrDecisionPoint(),
            text = Strings.objects.decision(),
            color = "amber",
            shape = NodeShape.Diamond,
        ),
        shapeComponent(
            id = "input-output-node",
            title = Strings.library.inputOutputNode(),
            description = Strings.library.dataEnteringOrLeavingProcess(),
            text = Strings.objects.inputOutput(),
            color = "paper",
            shape = NodeShape.Parallelogram,
        ),
        shapeComponent(
            id = "ellipse-node",
            title = Strings.library.ellipseNode(),
            description = Strings.library.entityEventOrRelatedConcept(),
            text = Strings.library.entity(),
            color = "mint",
            shape = NodeShape.Ellipse,
        ),
        shapeComponent(
            id = "system-node",
            title = Strings.library.systemNode(),
            description = Strings.library.serviceSubsystemOrTopologyDevice(),
            text = Strings.objects.system(),
            color = "lilac",
            shape = NodeShape.Hexagon,
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "highlight",
                title = Strings.library.keyIdea(),
                description = Strings.library.amberNodeForEmphasisOr(),
                colorToken = "amber",
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    ClipboardNode("highlight", 0f, 0f, nodeWidth, nodeHeight, 0f, Strings.library.keyIdea(), "amber"),
                ),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "document-node",
                title = Strings.library.documentNode(),
                description = Strings.library.flowchartDocumentReportOrGenerated(),
                colorToken = "paper",
                shape = NodeShape.Document,
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    node("document", 0f, 0f, Strings.objects.document(), "paper", NodeShape.Document),
                ),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "database-node",
                title = Strings.library.databaseNode(),
                description = Strings.library.persistentDataStoreOrDatabase(),
                colorToken = "amber",
                shape = NodeShape.Database,
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    node("database", 0f, 0f, Strings.objects.database(), "amber", NodeShape.Database),
                ),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "support-pair",
                title = Strings.library.supportingPair(),
                description = Strings.library.twoThoughtsConnectedBySupport(),
                colorToken = "lilac",
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    ClipboardNode("claim", 0f, 0f, nodeWidth, nodeHeight, 0f, Strings.library.mainThought(), "lilac"),
                    ClipboardNode(
                        "support",
                        nodeWidth + 72f * density,
                        0f,
                        nodeWidth,
                        nodeHeight,
                        0f,
                        Strings.library.supportingEvidence(),
                        "mint",
                    ),
                ),
                relations = listOf(
                    ClipboardRelation(
                        sourceId = "support",
                        targetId = "claim",
                        direction = RelationDirection.Forward.name,
                        intent = "supports",
                    ),
                ),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "comparison",
                title = Strings.library.comparisonPair(),
                description = Strings.library.twoAlternativesConnectedAsConflict(),
                colorToken = "mint",
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    ClipboardNode("option-a", 0f, 0f, nodeWidth, nodeHeight, 0f, Strings.library.option(), "lilac"),
                    ClipboardNode(
                        "option-b",
                        nodeWidth + 72f * density,
                        0f,
                        nodeWidth,
                        nodeHeight,
                        0f,
                        Strings.library.optionB(),
                        "amber",
                    ),
                ),
                relations = listOf(
                    ClipboardRelation(
                        sourceId = "option-a",
                        targetId = "option-b",
                        direction = RelationDirection.Both.name,
                        intent = "conflicts",
                    ),
                ),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "flowchart-starter",
                title = Strings.library.flowchartStarter(),
                description = Strings.library.startProcessAndDecisionShapes(),
                colorToken = "lilac",
                shape = NodeShape.Diamond,
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    node("flow-start", 0f, 0f, Strings.library.start(), "mint", NodeShape.Pill, zOffset = 0),
                    node("flow-process", nodeWidth + gapX, 0f, Strings.objects.process(), "lilac", NodeShape.Rectangle, zOffset = 1),
                    node("flow-decision", (nodeWidth + gapX) * 2f, 0f, Strings.library.decision(), "amber", NodeShape.Diamond, zOffset = 2),
                ),
                relations = listOf(
                    relation("flow-start", "flow-process"),
                    relation("flow-process", "flow-decision"),
                ),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "swimlane-starter",
                title = Strings.library.swimlaneStarter(),
                description = Strings.library.twoResponsibilityLanesWithHandoff(),
                colorToken = "mint",
                shape = NodeShape.Rectangle,
            ),
            payload = ClipboardPayload(
                groups = listOf(
                    ClipboardGroup(
                        originalId = "lane-a",
                        x = 0f,
                        y = 0f,
                        width = nodeWidth * 2f + gapX,
                        height = nodeHeight + gapY,
                        rotationDegrees = 0f,
                        title = Strings.library.team(),
                        colorToken = "lilac",
                        zOffset = 0,
                    ),
                    ClipboardGroup(
                        originalId = "lane-b",
                        x = 0f,
                        y = nodeHeight + gapY + 32f * density,
                        width = nodeWidth * 2f + gapX,
                        height = nodeHeight + gapY,
                        rotationDegrees = 0f,
                        title = Strings.library.teamB(),
                        colorToken = "mint",
                        zOffset = 1,
                    ),
                ),
                nodes = listOf(
                    node("lane-request", 28f * density, 50f * density, Strings.library.request(), "lilac", NodeShape.Rectangle, "lane-a", 2),
                    node(
                        "lane-handoff",
                        nodeWidth + gapX - 28f * density,
                        nodeHeight + gapY + 82f * density,
                        Strings.library.handoff(),
                        "mint",
                        NodeShape.Rectangle,
                        "lane-b",
                        3,
                    ),
                ),
                relations = listOf(relation("lane-request", "lane-handoff", "handoff")),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "org-chart-starter",
                title = Strings.library.organizationChart(),
                description = Strings.library.leaderAndTwoReportingBranches(),
                colorToken = "lilac",
                shape = NodeShape.Rectangle,
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    node("org-lead", nodeWidth / 2f + gapX / 2f, 0f, Strings.library.lead(), "amber", NodeShape.Rectangle, zOffset = 0),
                    node("org-a", 0f, nodeHeight + gapY, Strings.library.team(), "lilac", NodeShape.Rectangle, zOffset = 1),
                    node("org-b", nodeWidth + gapX, nodeHeight + gapY, Strings.library.teamB(), "mint", NodeShape.Rectangle, zOffset = 2),
                ),
                relations = listOf(relation("org-lead", "org-a", "reports"), relation("org-lead", "org-b", "reports")),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "architecture-starter",
                title = Strings.library.architectureDiagram(),
                description = Strings.library.clientServiceAndDataBoundary(),
                colorToken = "amber",
                shape = NodeShape.Hexagon,
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    node("arch-client", 0f, 0f, Strings.library.client(), "paper", NodeShape.Ellipse, zOffset = 0),
                    node("arch-service", nodeWidth + gapX, 0f, Strings.library.service(), "lilac", NodeShape.Hexagon, zOffset = 1),
                    node("arch-data", (nodeWidth + gapX) * 2f, 0f, Strings.library.dataStore(), "amber", NodeShape.Database, zOffset = 2),
                ),
                relations = listOf(relation("arch-client", "arch-service"), relation("arch-service", "arch-data")),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "relationship-map",
                title = Strings.library.relationshipMap(),
                description = Strings.library.centralConceptWithThreeRelated(),
                colorToken = "mint",
                shape = NodeShape.Ellipse,
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    node("rel-center", nodeWidth + gapX, nodeHeight + gapY, Strings.library.coreIdea(), "amber", NodeShape.Ellipse, zOffset = 0),
                    node("rel-a", 0f, 0f, Strings.library.related(), "lilac", NodeShape.Ellipse, zOffset = 1),
                    node("rel-b", (nodeWidth + gapX) * 2f, 0f, Strings.library.relatedB(), "mint", NodeShape.Ellipse, zOffset = 2),
                    node("rel-c", nodeWidth + gapX, (nodeHeight + gapY) * 2f, Strings.library.relatedC(), "paper", NodeShape.Ellipse, zOffset = 3),
                ),
                relations = listOf(
                    relation("rel-center", "rel-a"),
                    relation("rel-center", "rel-b"),
                    relation("rel-center", "rel-c"),
                ),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "topology-starter",
                title = Strings.library.topologyDiagram(),
                description = Strings.library.coreSystemConnectedToEdge(),
                colorToken = "lilac",
                shape = NodeShape.Hexagon,
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    node("top-core", nodeWidth + gapX, nodeHeight + gapY, Strings.library.core(), "amber", NodeShape.Hexagon, zOffset = 0),
                    node("top-a", 0f, 0f, Strings.library.node(), "mint", NodeShape.Hexagon, zOffset = 1),
                    node("top-b", (nodeWidth + gapX) * 2f, 0f, Strings.library.nodeB(), "lilac", NodeShape.Hexagon, zOffset = 2),
                    node("top-c", nodeWidth + gapX, (nodeHeight + gapY) * 2f, Strings.library.nodeC(), "paper", NodeShape.Hexagon, zOffset = 3),
                ),
                relations = listOf(
                    relation("top-core", "top-a", "link"),
                    relation("top-core", "top-b", "link"),
                    relation("top-core", "top-c", "link"),
                ),
            ),
        ),
        BuiltInComponent(
            entry = ComponentLibraryEntry(
                id = "tree-starter",
                title = Strings.library.treeDiagram(),
                description = Strings.library.rootBranchesAndLeaves(),
                colorToken = "mint",
                shape = NodeShape.Pill,
            ),
            payload = ClipboardPayload(
                nodes = listOf(
                    node("tree-root", nodeWidth / 2f + gapX / 2f, 0f, Strings.library.root(), "amber", NodeShape.Pill, zOffset = 0),
                    node("tree-a", 0f, nodeHeight + gapY, Strings.library.branch(), "lilac", zOffset = 1),
                    node("tree-b", nodeWidth + gapX, nodeHeight + gapY, Strings.library.branchB(), "mint", zOffset = 2),
                    node("tree-a1", 0f, (nodeHeight + gapY) * 2f, Strings.library.leafA1(), "paper", NodeShape.Ellipse, zOffset = 3),
                    node("tree-b1", nodeWidth + gapX, (nodeHeight + gapY) * 2f, Strings.library.leafB1(), "paper", NodeShape.Ellipse, zOffset = 4),
                ),
                relations = listOf(
                    relation("tree-root", "tree-a"),
                    relation("tree-root", "tree-b"),
                    relation("tree-a", "tree-a1"),
                    relation("tree-b", "tree-b1"),
                ),
            ),
        ),
    )
}

private fun orderClipboardGroups(groups: List<ClipboardGroup>): List<ClipboardGroup> {
    val byId = groups.associateBy(ClipboardGroup::originalId)
    fun depth(group: ClipboardGroup, visited: Set<String> = emptySet()): Int {
        if (group.originalId in visited) return 0
        val parent = group.parentOriginalId?.let(byId::get) ?: return 0
        return 1 + depth(parent, visited + group.originalId)
    }
    return groups.sortedWith(compareBy<ClipboardGroup>({ depth(it) }, ClipboardGroup::zOffset))
}

@Composable
private fun QuickSchemePreview(
    payload: ClipboardPayload,
    modifier: Modifier = Modifier,
) {
    val colors = BoarderLessTheme.colors
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.canvas.copy(alpha = 0.72f))
            .border(1.dp, colors.contentBorder, RoundedCornerShape(12.dp)),
    ) {
        if (payload.nodes.isEmpty() && payload.groups.isEmpty() && payload.media.isEmpty()) return@Canvas
        val padding = 12f * density
        val lefts = payload.nodes.map { it.x } + payload.groups.map { it.x } + payload.media.map { it.x }
        val tops = payload.nodes.map { it.y } + payload.groups.map { it.y } + payload.media.map { it.y }
        val rights = payload.nodes.map { it.x + it.width } + payload.groups.map { it.x + it.width } +
            payload.media.map { it.x + it.width }
        val bottoms = payload.nodes.map { it.y + it.height } + payload.groups.map { it.y + it.height } +
            payload.media.map { it.y + it.height }
        val minX = lefts.min()
        val minY = tops.min()
        val maxX = rights.max()
        val maxY = bottoms.max()
        val contentWidth = (maxX - minX).coerceAtLeast(1f)
        val contentHeight = (maxY - minY).coerceAtLeast(1f)
        val scale = min(
            (size.width - padding * 2f).coerceAtLeast(1f) / contentWidth,
            (size.height - padding * 2f).coerceAtLeast(1f) / contentHeight,
        )
        val offsetX = (size.width - contentWidth * scale) / 2f
        val offsetY = (size.height - contentHeight * scale) / 2f
        fun project(x: Float, y: Float) = Offset(
            x = offsetX + (x - minX) * scale,
            y = offsetY + (y - minY) * scale,
        )

        payload.groups.forEach { group ->
            val topLeft = project(group.x, group.y)
            val groupSize = Size(group.width * scale, group.height * scale)
            val groupColor = when (group.colorToken) {
                "lilac" -> colors.nodeLilac
                "amber" -> colors.nodeAmber
                "mint" -> colors.nodeMint
                else -> colors.selection
            }
            val radius = 6f * density
            drawRoundRect(
                color = groupColor.copy(alpha = 0.13f),
                topLeft = topLeft,
                size = groupSize,
                cornerRadius = CornerRadius(radius, radius),
            )
            drawRoundRect(
                color = groupColor.copy(alpha = 0.75f),
                topLeft = topLeft,
                size = groupSize,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 1.25f * density),
            )
        }

        val nodesById = payload.nodes.associateBy { it.originalId }
        payload.relations.forEach { relation ->
            val source = nodesById[relation.sourceId] ?: return@forEach
            val target = nodesById[relation.targetId] ?: return@forEach
            val start = project(source.x + source.width / 2f, source.y + source.height / 2f)
            val end = project(target.x + target.width / 2f, target.y + target.height / 2f)
            drawLine(
                color = when (relation.intent) {
                    "conflicts" -> colors.danger
                    "supports" -> colors.selection
                    else -> colors.contentMuted
                }.copy(alpha = 0.82f),
                start = start,
                end = end,
                strokeWidth = 1.5f * density,
            )
        }

        payload.nodes.forEach { node ->
            val topLeft = project(node.x, node.y)
            val nodeSize = Size(
                width = (node.width * scale).coerceAtLeast(8f * density),
                height = (node.height * scale).coerceAtLeast(6f * density),
            )
            val nodeColor = nodeFillColor(node.colorToken, colors)
            drawNodePreviewShape(
                shape = NodeShape.fromToken(node.shapeToken) ?: NodeShape.RoundedRectangle,
                color = nodeColor,
                borderColor = colors.contentBorder,
                topLeft = topLeft,
                size = nodeSize,
                cornerRadius = 4f * density,
                borderWidth = 1f * density,
            )
        }
        payload.media.forEach { media ->
            val topLeft = project(media.x, media.y)
            val mediaSize = Size(
                width = (media.width * scale).coerceAtLeast(8f * density),
                height = (media.height * scale).coerceAtLeast(6f * density),
            )
            drawRoundRect(
                color = colors.canvas,
                topLeft = topLeft,
                size = mediaSize,
                cornerRadius = CornerRadius(4f * density),
            )
            drawRoundRect(
                color = colors.accent,
                topLeft = topLeft,
                size = mediaSize,
                cornerRadius = CornerRadius(4f * density),
                style = Stroke(1f * density),
            )
        }
    }
}

@Composable
fun WorkspaceScreen(
    mediaImportRuntime: MediaImportRuntime = MediaImportRuntime.Unavailable,
    reduceTransparency: Boolean = false,
    onReduceTransparencyChange: (Boolean) -> Unit = {},
    reduceMotion: Boolean = false,
    onReduceMotionChange: (Boolean) -> Unit = {},
    languagePreference: LanguagePreference = LanguagePreference.System,
    onLanguagePreferenceChange: (LanguagePreference) -> Unit = {},
    menuBridge: WorkspaceMenuBridge? = null,
    recentWorkspacesPublisher: RecentWorkspacesPublisher = RecentWorkspacesPublisher.None,
) {
    val colors = BoarderLessTheme.colors
    val density = LocalDensity.current.density
    val focusManager = LocalFocusManager.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val uiScope = rememberCoroutineScope()
    val canvasFocusRequester = remember { FocusRequester() }
    val repository = remember { BackendWorkspaceRepository() }
    val recentWorkspaces = remember { RecentWorkspaceStore() }
    fun publishRecentWorkspaces() = recentWorkspacesPublisher.publish(recentWorkspaces.items)
    LaunchedEffect(recentWorkspacesPublisher) { publishRecentWorkspaces() }
    val assetGateway = remember(mediaImportRuntime) {
        if (mediaImportRuntime.selectSource != null) BackendAssetTransferGateway() else null
    }
    DisposableEffect(assetGateway) {
        onDispose { assetGateway?.close() }
    }
    var mediaImportBusy by remember { mutableStateOf(false) }
    var mediaImportJob by remember { mutableStateOf<Job?>(null) }
    var mediaImportLabel by remember { mutableStateOf("") }
    val canvasPreferences = remember { CanvasPreferences() }
    val quickSchemeStore = remember { QuickSchemeStore() }
    DisposableEffect(repository) {
        onDispose { repository.close() }
    }
    var history by remember {
        mutableStateOf(
            WorkspaceHistory(
                Workspace(
                    id = WorkspaceId("00000000-0000-0000-0000-000000000000"),
                    title = Strings.content.loadingSpace(),
                ),
            ),
        )
    }
    var viewport by remember(density) {
        mutableStateOf(Viewport(pan = Vec2(120f * density, 96f * density)))
    }
    var displaySettings by remember { mutableStateOf(CanvasDisplaySettings()) }
    var workspaceSummaries by remember { mutableStateOf<List<WorkspaceSummary>>(emptyList()) }
    var showWorkspaceSwitcher by remember { mutableStateOf(false) }
    var workspaceNameDraft by remember { mutableStateOf(Strings.content.newThinkingSpace()) }
    var workspaceRenameDraft by remember { mutableStateOf("") }
    var workspaceDeleteArmed by remember { mutableStateOf(false) }
    var workspaceSwitchInProgress by remember { mutableStateOf(false) }
    var areaSelectionMode by remember { mutableStateOf(false) }
    var multiSelectionMode by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedIds by remember { mutableStateOf<Set<CanvasObjectId>>(emptySet()) }
    var selectedRelationId by remember { mutableStateOf<RelationId?>(null) }
    var inspectorTextEditing by remember { mutableStateOf(false) }
    val uiPreferences = remember { UiPreferences() }
    var colorPickerModel by remember { mutableStateOf(uiPreferences.colorPickerModel) }
    var collapsedInspectorSections by remember {
        mutableStateOf(uiPreferences.collapsedInspectorSections)
    }
    var collapsedPanelSections by remember {
        mutableStateOf(uiPreferences.collapsedPanelSections)
    }
    // The nodes the open color picker edits, and the token it is previewing on them.
    var colorPickerTargets by remember { mutableStateOf<Set<CanvasObjectId>?>(null) }
    var colorPickerInitialToken by remember { mutableStateOf("paper") }
    var colorPreviewToken by remember { mutableStateOf<String?>(null) }
    var showCanvasBackgroundPicker by remember { mutableStateOf(false) }
    var canvasBackgroundPreview by remember { mutableStateOf<String?>(null) }
    var workspaceFormEditing by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<CanvasObjectId?>(null) }
    var pendingNewNode by remember { mutableStateOf<TextNode?>(null) }
    var dragPreviews by remember { mutableStateOf<Map<CanvasObjectId, Vec2>>(emptyMap()) }
    var transformPreviews by remember { mutableStateOf<Map<CanvasObjectId, CanvasTransform>>(emptyMap()) }
    var connectionDragPreview by remember { mutableStateOf<ConnectionDragPreview?>(null) }
    var marquee by remember { mutableStateOf<Marquee?>(null) }
    var alignmentGuides by remember { mutableStateOf(AlignmentGuides()) }
    var nextNodeNumber by remember { mutableStateOf(1) }
    var session by remember { mutableStateOf<WorkspaceSession?>(null) }
    LaunchedEffect(mediaImportRuntime, session?.userId, session?.workspace?.id) {
        mediaImportRuntime.clearPreviewCache?.invoke()
    }
    var submissionQueue by remember { mutableStateOf(WorkspaceSubmissionQueue()) }
    var syncInProgress by remember { mutableStateOf(false) }
    var connectionFailed by remember { mutableStateOf(false) }
    var connectionAttempt by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf(Strings.status.connectingToWorkspace()) }
    var schemes by remember { mutableStateOf(quickSchemeStore.list()) }
    var showSchemeLibrary by remember { mutableStateOf(false) }
    var showComponentLibrary by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var showHistoryPanel by remember { mutableStateOf(false) }
    var showMembersPanel by remember { mutableStateOf(false) }
    var workspaceMembers by remember { mutableStateOf<List<WorkspaceMember>>(emptyList()) }
    var workspaceAssets by remember { mutableStateOf<Map<String, WorkspaceAsset>>(emptyMap()) }
    var workspaceAssetsLoading by remember { mutableStateOf(false) }
    var workspaceAssetsUnavailable by remember { mutableStateOf(false) }
    var workspaceAssetsRefreshAttempt by remember { mutableStateOf(0) }
    var workspaceAssetsLoadEpoch by remember { mutableStateOf(0L) }
    var membersLoading by remember { mutableStateOf(false) }
    var membersError by remember { mutableStateOf<String?>(null) }
    var membersRefreshAttempt by remember { mutableStateOf(0) }
    var memberUserIdDraft by remember { mutableStateOf("") }
    var memberRoleDraft by remember { mutableStateOf(WorkspaceMemberRole.Editor) }
    var memberRemoveArmed by remember { mutableStateOf<String?>(null) }
    var memberMutationInProgress by remember { mutableStateOf(false) }
    var recentWorkspaceActivity by remember { mutableStateOf<List<WorkspaceActivity>>(emptyList()) }
    var activityLoading by remember { mutableStateOf(false) }
    var activityError by remember { mutableStateOf<String?>(null) }
    var activityRefreshAttempt by remember { mutableStateOf(0) }
    var showCompactMenu by remember { mutableStateOf(false) }
    var compactInspectorExpanded by remember { mutableStateOf(false) }
    var libraryDragPreview by remember { mutableStateOf<LibraryDragPreview?>(null) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var commandPaletteQuery by remember { mutableStateOf("") }
    var selectedSchemeId by remember { mutableStateOf<Int?>(schemes.lastOrNull()?.id) }
    var schemeNameDraft by remember { mutableStateOf(schemes.lastOrNull()?.name.orEmpty()) }
    val canvasInteractionBlocked = session == null || connectionFailed || workspaceSwitchInProgress
    val inputBlocked = canvasInteractionBlocked || session?.canEditContent != true || mediaImportBusy
    val libraryComponents = remember(density, Localization.language) { builtInComponents(density) }
    val compactLayout = usesCompactCanvasLayout(canvasSize.width, canvasSize.height, density)
    val inspectorWidth = if (compactLayout && canvasSize.width > 0) {
        compactInspectorWidthDp(canvasSize.width, density).dp
    } else {
        284.dp
    }
    val compactBottomSheetWidth = compactBottomSheetWidthDp(canvasSize.width, density).dp
    val compactInspectorMaxHeight = min(
        520f,
        max(260f, canvasSize.height.takeIf { it > 0 }?.div(density)?.times(0.62f) ?: 420f),
    ).dp

    fun toggleInspectorSection(section: InspectorSection) {
        val next = toggleInspectorSection(collapsedInspectorSections, section)
        collapsedInspectorSections = next
        uiPreferences.collapsedInspectorSections = next
    }

    fun togglePanelSection(section: PanelSection) {
        val next = togglePanelSection(collapsedPanelSections, section)
        collapsedPanelSections = next
        uiPreferences.collapsedPanelSections = next
    }

    LaunchedEffect(compactLayout, selectedIds, selectedRelationId, editingId) {
        if (!compactLayout || editingId != null || (selectedIds.isEmpty() && selectedRelationId == null)) {
            compactInspectorExpanded = false
        }
        if (!compactLayout || editingId != null) multiSelectionMode = false
    }
    val latestSession by rememberUpdatedState(session)
    val remoteCatchUpAllowed by rememberUpdatedState(
        session != null &&
            !connectionFailed &&
            !workspaceSwitchInProgress &&
            !mediaImportBusy &&
            !syncInProgress &&
            submissionQueue.isEmpty &&
            editingId == null &&
            !inspectorTextEditing &&
            !workspaceFormEditing &&
            dragPreviews.isEmpty() &&
            transformPreviews.isEmpty() &&
            connectionDragPreview == null &&
            marquee == null &&
            libraryDragPreview == null,
    )

    val workspaceChangeAllowed = WorkspaceChangePolicy(
        sessionAvailable = session != null,
        connectionFailed = connectionFailed,
        switchInProgress = workspaceSwitchInProgress,
        syncInProgress = syncInProgress,
        pendingSaveCount = submissionQueue.size,
        nodeEditing = editingId != null,
        contentInspectorEditing = inspectorTextEditing,
        pendingNodeDraft = pendingNewNode != null,
        objectDragActive = dragPreviews.isNotEmpty(),
        transformActive = transformPreviews.isNotEmpty(),
        connectionDragActive = connectionDragPreview != null,
        marqueeActive = marquee != null,
        libraryDragActive = libraryDragPreview != null,
        mediaImportActive = mediaImportBusy,
    ).allowed
    val currentWorkspaceCanRename = session?.canEditContent == true
    val currentWorkspaceCanDelete = session?.role == WorkspaceMemberRole.Owner
    val currentWorkspaceCanManageMembers = session?.role == WorkspaceMemberRole.Owner

    fun adoptWorkspace(opened: WorkspaceSession, message: String) {
        session = opened
        history = WorkspaceHistory(opened.workspace)
        submissionQueue = submissionQueue.clear()
        selectedIds = emptySet()
        selectedRelationId = null
        editingId = null
        inspectorTextEditing = false
        workspaceFormEditing = false
        pendingNewNode = null
        dragPreviews = emptyMap()
        transformPreviews = emptyMap()
        connectionDragPreview = null
        marquee = null
        alignmentGuides = AlignmentGuides()
        areaSelectionMode = false
        multiSelectionMode = false
        viewport = Viewport(pan = Vec2(120f * density, 96f * density))
        displaySettings = CanvasDisplaySettings()
        nextNodeNumber = opened.workspace.objects.size + 1
        workspaceRenameDraft = opened.workspace.title
        workspaceDeleteArmed = false
        recentWorkspaceActivity = emptyList()
        activityError = null
        workspaceMembers = emptyList()
        workspaceAssets = emptyMap()
        membersError = null
        memberUserIdDraft = ""
        memberRoleDraft = WorkspaceMemberRole.Editor
        memberRemoveArmed = null
        memberMutationInProgress = false
        showMembersPanel = false
        compactInspectorExpanded = false
        connectionFailed = false
        statusMessage = message
        recentWorkspaces.recordOpened(opened.workspace.id, opened.workspace.title)
        publishRecentWorkspaces()
    }

    suspend fun refreshWorkspaceSummaries(activeSession: WorkspaceSession) {
        workspaceSummaries = try {
            repository.listWorkspaces(activeSession).also { available ->
                recentWorkspaces.reconcile(available)
                publishRecentWorkspaces()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            listOf(
                WorkspaceSummary(
                    id = activeSession.workspace.id,
                    title = activeSession.workspace.title,
                    role = "current",
                    workspaceVersion = activeSession.workspaceVersion,
                    lastServerSeq = activeSession.lastServerSeq,
                ),
            )
        }
    }

    fun switchWorkspace(summary: WorkspaceSummary) {
        val activeSession = session ?: return
        if (summary.id == activeSession.workspace.id) {
            showWorkspaceSwitcher = false
            return
        }
        if (workspaceSwitchInProgress || !workspaceChangeAllowed) {
            statusMessage = Strings.status.finishEditingAndWaitFor()
            return
        }
        focusManager.clearFocus()
        workspaceFormEditing = false
        workspaceSwitchInProgress = true
        uiScope.launch {
            statusMessage = Strings.status.opening(summary.title)
            try {
                val opened = repository.openWorkspace(activeSession, summary.id)
                adoptWorkspace(opened, Strings.status.connectedAllChangesSaved(opened.workspace.title))
                refreshWorkspaceSummaries(opened)
                showWorkspaceSwitcher = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                statusMessage = Strings.status.couldNotOpen(summary.title, error.message ?: Strings.common.unknownError())
            } finally {
                workspaceSwitchInProgress = false
            }
        }
    }

    fun createWorkspace() {
        val activeSession = session ?: return
        val title = workspaceNameDraft.trim()
        if (title.isEmpty()) return
        if (workspaceSwitchInProgress || !workspaceChangeAllowed) {
            statusMessage = Strings.status.waitForAllChangesTo()
            return
        }
        focusManager.clearFocus()
        workspaceFormEditing = false
        workspaceSwitchInProgress = true
        uiScope.launch {
            statusMessage = Strings.status.creating(title)
            try {
                val opened = repository.createWorkspace(activeSession, title)
                adoptWorkspace(opened, Strings.status.createdAllChangesSaved(opened.workspace.title))
                refreshWorkspaceSummaries(opened)
                workspaceNameDraft = Strings.content.newThinkingSpace()
                showWorkspaceSwitcher = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                statusMessage = Strings.status.couldNotCreateWorkspace(error.message ?: Strings.common.unknownError())
            } finally {
                workspaceSwitchInProgress = false
            }
        }
    }

    fun renameCurrentWorkspace() {
        val activeSession = session ?: return
        val title = workspaceRenameDraft.trim()
        if (title.isEmpty() || title == activeSession.workspace.title) return
        if (!currentWorkspaceCanRename) {
            statusMessage = Strings.status.onlyOwnersAndEditorsCan()
            return
        }
        if (workspaceSwitchInProgress || !workspaceChangeAllowed) {
            statusMessage = Strings.status.waitForAllChangesToSave()
            return
        }
        focusManager.clearFocus()
        workspaceFormEditing = false
        workspaceSwitchInProgress = true
        uiScope.launch {
            statusMessage = Strings.status.renamingWorkspace()
            try {
                val renamed = repository.renameWorkspace(activeSession, activeSession.workspace.id, title)
                val renamedWorkspace = activeSession.workspace.copy(title = renamed.title)
                session = activeSession.copy(workspace = renamedWorkspace)
                history = history.copy(workspace = history.workspace.copy(title = renamed.title))
                workspaceSummaries = workspaceSummaries.map { summary ->
                    if (summary.id == renamed.id) renamed else summary
                }
                workspaceRenameDraft = renamed.title
                workspaceDeleteArmed = false
                recentWorkspaces.rename(renamed.id, renamed.title)
                publishRecentWorkspaces()
                statusMessage = Strings.status.renamedAllChangesSaved(renamed.title)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                statusMessage = Strings.status.couldNotRenameWorkspace(error.message ?: Strings.common.unknownError())
            } finally {
                workspaceSwitchInProgress = false
            }
        }
    }

    fun deleteCurrentWorkspace() {
        val activeSession = session ?: return
        if (!currentWorkspaceCanDelete) {
            statusMessage = Strings.status.onlyOwnerCanDeleteThis()
            return
        }
        if (!workspaceDeleteArmed) {
            workspaceDeleteArmed = true
            statusMessage = Strings.status.pressConfirmDeleteToPermanently(activeSession.workspace.title)
            return
        }
        if (workspaceSwitchInProgress || !workspaceChangeAllowed) {
            statusMessage = Strings.status.waitForAllChangesToSaveBefore()
            return
        }
        focusManager.clearFocus()
        workspaceFormEditing = false
        workspaceSwitchInProgress = true
        uiScope.launch {
            val deletedTitle = activeSession.workspace.title
            var deletionAccepted = false
            statusMessage = Strings.status.deleting(deletedTitle)
            try {
                repository.deleteWorkspace(activeSession, activeSession.workspace.id)
                deletionAccepted = true
                recentWorkspaces.remove(activeSession.workspace.id)
                publishRecentWorkspaces()
                val remaining = repository.listWorkspaces(activeSession)
                val opened = remaining.firstOrNull()?.let { repository.openWorkspace(activeSession, it.id) }
                    ?: repository.createWorkspace(activeSession, Strings.content.myThinkingSpace())
                adoptWorkspace(opened, Strings.status.deletedOpened(deletedTitle, opened.workspace.title))
                refreshWorkspaceSummaries(opened)
                showWorkspaceSwitcher = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (deletionAccepted) connectionFailed = true
                statusMessage = if (deletionAccepted) {
                    Strings.status.workspaceDeletedButReplacementCould()
                } else {
                    Strings.status.couldNotDeleteWorkspace(error.message ?: Strings.common.unknownError())
                }
            } finally {
                workspaceSwitchInProgress = false
            }
        }
    }

    fun saveWorkspaceMember() {
        val activeSession = session ?: return
        if (!currentWorkspaceCanManageMembers || memberMutationInProgress) return
        val targetUserId = normalizedMemberUserId(memberUserIdDraft)
        if (targetUserId == null) {
            statusMessage = Strings.members.enterValidUserId()
            return
        }
        if (targetUserId == activeSession.userId.lowercase()) {
            statusMessage = Strings.members.workspaceOwnerRoleCannotBe()
            return
        }
        focusManager.clearFocus()
        workspaceFormEditing = false
        memberMutationInProgress = true
        memberRemoveArmed = null
        uiScope.launch {
            statusMessage = Strings.members.updatingWorkspaceMember()
            try {
                repository.setWorkspaceMemberRole(activeSession, targetUserId, memberRoleDraft)
                workspaceMembers = repository.listWorkspaceMembers(activeSession)
                memberUserIdDraft = ""
                statusMessage = Strings.members.workspaceMemberUpdated()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                membersError = error.message ?: Strings.common.unknownError()
                statusMessage = Strings.members.couldNotUpdateWorkspaceMember(membersError.orEmpty())
            } finally {
                memberMutationInProgress = false
            }
        }
    }

    fun removeWorkspaceMember(member: WorkspaceMember) {
        val activeSession = session ?: return
        if (!currentWorkspaceCanManageMembers || member.role == WorkspaceMemberRole.Owner || memberMutationInProgress) {
            return
        }
        if (memberRemoveArmed != member.userId) {
            memberRemoveArmed = member.userId
            statusMessage = Strings.members.pressRemoveAgainToRevoke(member.displayName)
            return
        }
        memberMutationInProgress = true
        uiScope.launch {
            statusMessage = Strings.members.removingWorkspaceMember()
            try {
                repository.removeWorkspaceMember(activeSession, member.userId)
                workspaceMembers = repository.listWorkspaceMembers(activeSession)
                memberRemoveArmed = null
                statusMessage = Strings.members.workspaceMemberRemoved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                membersError = error.message ?: Strings.common.unknownError()
                statusMessage = Strings.members.couldNotRemoveWorkspaceMember(membersError.orEmpty())
            } finally {
                memberMutationInProgress = false
            }
        }
    }

    LaunchedEffect(repository, connectionAttempt) {
        connectionFailed = false
        statusMessage = Strings.status.connectingToWorkspace()
        try {
            val opened = repository.openOrCreateWorkspace(WorkspaceLaunchRequests.pending.value)
            adoptWorkspace(opened, Strings.status.connectedLiveCatchUpAll(opened.workspace.title))
            refreshWorkspaceSummaries(opened)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            connectionFailed = true
            statusMessage = Strings.status.couldNotConnect(error.message ?: Strings.common.unknownError())
        }
    }

    // Opens workspaces requested from outside the app (Dock menu, jump list, launcher shortcut).
    val launchRequest by WorkspaceLaunchRequests.pending.collectAsState()
    LaunchedEffect(launchRequest, session?.workspace?.id, workspaceSwitchInProgress) {
        val requested = launchRequest ?: return@LaunchedEffect
        val activeSession = session ?: return@LaunchedEffect
        if (workspaceSwitchInProgress) return@LaunchedEffect
        WorkspaceLaunchRequests.consume(requested)
        if (requested == activeSession.workspace.id) return@LaunchedEffect
        val title = workspaceSummaries.firstOrNull { it.id == requested }?.title
            ?: recentWorkspaces.items.firstOrNull { it.id == requested.value }?.title
            ?: requested.value
        switchWorkspace(WorkspaceSummary(requested, title, role = "", workspaceVersion = 0, lastServerSeq = 0))
    }

    LaunchedEffect(session?.workspace?.id) {
        val workspaceId = session?.workspace?.id?.value ?: return@LaunchedEffect
        canvasPreferences.loadViewport(workspaceId)?.let { viewport = it }
        displaySettings = canvasPreferences.loadDisplaySettings(workspaceId)
        launch {
            snapshotFlow { viewport }
                .collectLatest {
                    delay(300)
                    canvasPreferences.saveViewport(workspaceId, it)
                }
        }
        launch {
            snapshotFlow { displaySettings }
                .collect { canvasPreferences.saveDisplaySettings(workspaceId, it) }
        }
    }

    LaunchedEffect(session?.userId, session?.clientId, session?.workspace?.id, session?.lastServerSeq, workspaceAssetsRefreshAttempt) {
        val activeSession = session ?: return@LaunchedEffect
        val loadEpoch = ++workspaceAssetsLoadEpoch
        workspaceAssetsLoading = true
        workspaceAssetsUnavailable = false
        workspaceAssets = try {
            val listed = repository.listAssets(activeSession)
            coroutineContext.ensureActive()
            listed.associateBy(WorkspaceAsset::id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Editing remains available when metadata is temporarily unreachable. Media cards
            // show an explicit unavailable state instead of pretending that a preview loaded.
            workspaceAssetsUnavailable = true
            emptyMap()
        } finally {
            if (loadEpoch == workspaceAssetsLoadEpoch) workspaceAssetsLoading = false
        }
    }

    LaunchedEffect(
        showHistoryPanel,
        session?.workspace?.id,
        session?.lastServerSeq,
        activityRefreshAttempt,
    ) {
        val activeSession = session
        if (!showHistoryPanel || activeSession == null) return@LaunchedEffect
        activityLoading = true
        activityError = null
        try {
            recentWorkspaceActivity = repository.listRecentActivity(activeSession, limit = 100)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            activityError = error.message ?: Strings.common.unknownError()
        } finally {
            activityLoading = false
        }
    }

    LaunchedEffect(
        showMembersPanel,
        session?.workspace?.id,
        membersRefreshAttempt,
    ) {
        val activeSession = session
        if (!showMembersPanel || activeSession == null) return@LaunchedEffect
        membersLoading = true
        membersError = null
        try {
            workspaceMembers = repository.listWorkspaceMembers(activeSession)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            membersError = error.message ?: Strings.common.unknownError()
        } finally {
            membersLoading = false
        }
    }

    LaunchedEffect(repository, session?.userId, session?.clientId, session?.workspace?.id, connectionAttempt) {
        val observedSession = session ?: return@LaunchedEffect
        var consecutiveFailures = 0
        workspaceCatchUpRequests(observedSession, repository.observeRemoteChanges(observedSession)).collect {
            if (!remoteCatchUpAllowed) return@collect
            val requestedFrom = latestSession ?: return@collect
            try {
                val refreshed = repository.refresh(requestedFrom)
                val current = latestSession
                if (
                    remoteCatchUpAllowed &&
                    current != null &&
                    current.workspaceVersion == requestedFrom.workspaceVersion &&
                    refreshed.hasRemoteChangesComparedTo(current)
                ) {
                    session = refreshed
                    history = WorkspaceHistory(refreshed.workspace)
                    workspaceSummaries = workspaceSummaries.map { summary ->
                        if (summary.id == refreshed.workspace.id) {
                            summary.copy(
                                title = refreshed.workspace.title,
                                role = refreshed.role.token,
                                workspaceVersion = refreshed.workspaceVersion,
                                lastServerSeq = refreshed.lastServerSeq,
                            )
                        } else {
                            summary
                        }
                    }
                    selectedIds = selectedIds.filterTo(mutableSetOf()) {
                        refreshed.workspace.objects.containsKey(it)
                    }
                    selectedRelationId = selectedRelationId?.takeIf {
                        refreshed.workspace.relations.containsKey(it)
                    }
                    nextNodeNumber = max(nextNodeNumber, refreshed.workspace.objects.size + 1)
                    workspaceDeleteArmed = false
                    statusMessage = Strings.status.receivedRemoteChangesUndoHistory()
                } else if (consecutiveFailures >= 3 && remoteCatchUpAllowed) {
                    statusMessage = Strings.status.connectedLiveCatchUpResumed()
                }
                consecutiveFailures = 0
            } catch (error: CancellationException) {
                throw error
            } catch (error: BackendContractException) {
                connectionFailed = true
                statusMessage = Strings.status.workspaceContainsContentThisApp(error.message ?: Strings.status.unsupportedProjection())
            } catch (error: BackendHttpException) {
                if (error.isWorkspaceAccessLoss) {
                    connectionFailed = true
                    showMembersPanel = false
                    workspaceDeleteArmed = false
                    memberRemoveArmed = null
                    statusMessage = Strings.members.workspaceAccessWasRevokedOr()
                } else {
                    consecutiveFailures += 1
                    if (consecutiveFailures == 3 && remoteCatchUpAllowed) {
                        statusMessage = Strings.status.connectedLiveCatchUpPaused()
                    }
                }
            } catch (_: Throwable) {
                consecutiveFailures += 1
                if (consecutiveFailures == 3 && remoteCatchUpAllowed) {
                    statusMessage = Strings.status.connectedLiveCatchUpPaused()
                }
            }
        }
    }

    LaunchedEffect(repository, session?.workspace?.id, submissionQueue.first?.token) {
        if (submissionQueue.first == null) return@LaunchedEffect
        val activeSession = session ?: return@LaunchedEffect
        syncInProgress = true
        statusMessage = Strings.status.savingChanges(submissionQueue.size)
        try {
            when (val step = submitNextWorkspaceChange(repository, activeSession, submissionQueue)) {
                WorkspaceSubmissionStep.Idle -> Unit

                is WorkspaceSubmissionStep.Accepted -> {
                    session = step.session
                    submissionQueue = submissionQueue.accept(step.acceptedToken)
                    statusMessage = if (submissionQueue.isEmpty) {
                        Strings.status.connectedLiveCatchUpAllChanges()
                    } else {
                        Strings.status.savingChanges(submissionQueue.size)
                    }
                }

                is WorkspaceSubmissionStep.Conflict -> {
                    session = step.session
                    history = WorkspaceHistory(step.session.workspace)
                    submissionQueue = submissionQueue.clear()
                    selectedIds = emptySet()
                    selectedRelationId = null
                    editingId = null
                    pendingNewNode = null
                    dragPreviews = emptyMap()
                    transformPreviews = emptyMap()
                    connectionDragPreview = null
                    alignmentGuides = AlignmentGuides()
                    statusMessage = Strings.status.updatedFromServerUnconfirmedLocal()
                }

                is WorkspaceSubmissionStep.Recovered -> {
                    session = step.session
                    history = WorkspaceHistory(step.session.workspace)
                    submissionQueue = submissionQueue.clear()
                    connectionFailed = false
                    selectedIds = emptySet()
                    selectedRelationId = null
                    editingId = null
                    pendingNewNode = null
                    dragPreviews = emptyMap()
                    transformPreviews = emptyMap()
                    connectionDragPreview = null
                    alignmentGuides = AlignmentGuides()
                    statusMessage = Strings.status.saveResponseFailedReconciledWith()
                }

                is WorkspaceSubmissionStep.Failed -> {
                    session = step.session
                    history = WorkspaceHistory(step.session.workspace)
                    submissionQueue = submissionQueue.clear()
                    connectionFailed = step.submitError !is BackendHttpException
                    selectedIds = emptySet()
                    selectedRelationId = null
                    editingId = null
                    pendingNewNode = null
                    dragPreviews = emptyMap()
                    transformPreviews = emptyMap()
                    connectionDragPreview = null
                    alignmentGuides = AlignmentGuides()
                    statusMessage = Strings.status.saveFailedUnconfirmedChangesReverted((step.submitError.message ?: Strings.common.unknownError()).take(180))
                }
            }
        } finally {
            syncInProgress = false
        }
    }

    fun synchronize(result: HistoryResult) {
        history = result.history
        val operation = result.appliedOperation
        if (!result.succeeded || operation == null || session == null) {
            statusMessage = if (result.succeeded) statusMessage else Strings.status.thatChangeCouldNotBe()
            return
        }
        workspaceDeleteArmed = false
        submissionQueue = submissionQueue.enqueue(operation, result.history.workspace)
        statusMessage = Strings.status.queuedChanges(submissionQueue.size)
    }

    fun execute(operation: WorkspaceOperation): Boolean {
        if (session == null || connectionFailed) return false
        val result = history.execute(operation)
        synchronize(result)
        return result.succeeded && result.appliedOperation != null
    }

    fun addTextNode(
        screenPosition: Vec2,
        shape: NodeShape = NodeShape.RoundedRectangle,
    ) {
        if (inputBlocked || pendingNewNode != null) return
        val id = CanvasObjectId(randomUuid())
        nextNodeNumber += 1
        val worldPosition = viewport.screenToWorld(screenPosition)
        val node = TextNode(
            id = id,
            transform = CanvasTransform(
                position = worldPosition,
                size = CanvasSize(width = 260f * density, height = 132f * density),
            ),
            text = "",
            zIndex = nextNodeNumber.toLong(),
            shape = shape,
        )
        pendingNewNode = node
        multiSelectionMode = false
        selectedIds = setOf(id)
        selectedRelationId = null
        editingId = id
        statusMessage = Strings.status.draftingThoughtNotSavedYet()
    }

    fun addTextNodeAtCenter(shape: NodeShape = NodeShape.RoundedRectangle) {
        addTextNode(
            Vec2(
                x = canvasSize.width / 2f - 130f * density * viewport.zoom,
                y = canvasSize.height / 2f - 66f * density * viewport.zoom,
            ),
            shape = shape,
        )
    }

    fun insertStoredMedia(entry: MediaAssetLibraryEntry) {
        val openedSession = session ?: return
        if (inputBlocked || !workspaceChangeAllowed || connectionFailed || !entry.insertable) return
        mediaImportBusy = true
        mediaImportLabel = Strings.media.checkingAsset()
        mediaImportJob = uiScope.launch {
            try {
                val asset = repository.getAsset(openedSession, entry.asset.id)
                coroutineContext.ensureActive()
                val current = latestSession
                if (!canInsertMediaInSession(openedSession, current, connectionFailed)) return@launch
                checkNotNull(current)
                require(asset.id == entry.asset.id && asset.workspaceId == current.workspace.id)
                workspaceAssets = workspaceAssets + (asset.id to asset)
                val node = mediaNodeFromReadyAsset(asset, current.workspace.id, CanvasObjectId(randomUuid()),
                    viewport.screenToWorld(Vec2(canvasSize.width / 2f, canvasSize.height / 2f)), density,
                    (history.workspace.objects.values.maxOfOrNull { it.zIndex } ?: 0L) + 1, entry.title, requestedAssetId = entry.asset.id)
                if (execute(CreateObjectsOperation("reuse-media-${node.id.value}", listOf(node)))) {
                    selectedIds = setOf(node.id)
                    selectedRelationId = null
                    multiSelectionMode = false
                } else statusMessage = Strings.media.assetInsertFailed()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { statusMessage = Strings.media.assetInsertFailed() }
            finally {
                mediaImportBusy = false
                mediaImportJob = null
                mediaImportLabel = ""
            }
        }
    }

    fun startMediaImport() {
        val picker = mediaImportRuntime.selectSource ?: return
        val gateway = assetGateway ?: return
        val importSession = session ?: return
        if (inputBlocked || !workspaceChangeAllowed) return
        workspaceDeleteArmed = false
        mediaImportBusy = true
        mediaImportLabel = Strings.media.selecting()
        mediaImportJob = uiScope.launch {
            var selectedSource: AssetTransferSource? = null
            try {
                val source = picker()
                selectedSource = source
                if (source == null) {
                    statusMessage = Strings.media.importCancelled()
                    return@launch
                }
                val imported = AssetImportCoordinator(gateway).import(importSession, source) { stage ->
                    mediaImportLabel = when (stage) {
                        AssetImportStage.Validating, AssetImportStage.Preparing -> Strings.media.preparing()
                        is AssetImportStage.Uploading -> Strings.media.uploading(
                            (stage.uploadedBytes * 100 / stage.totalBytes).toString(),
                        )
                        AssetImportStage.Confirming -> Strings.media.confirming()
                        is AssetImportStage.Processing -> Strings.media.processing()
                        is AssetImportStage.Ready -> Strings.media.confirming()
                    }
                }
                coroutineContext.ensureActive()
                val current = latestSession
                if (!canInsertMediaInSession(importSession, current, connectionFailed)) {
                    statusMessage = Strings.media.importNotInserted()
                    return@launch
                }
                checkNotNull(current)
                val asset = imported.asset
                val center = viewport.screenToWorld(Vec2(canvasSize.width / 2f, canvasSize.height / 2f))
                val node = mediaNodeFromReadyAsset(asset, current.workspace.id, CanvasObjectId(randomUuid()), center, density,
                    (history.workspace.objects.values.maxOfOrNull { it.zIndex } ?: 0L) + 1, source.displayName,
                    imported.thumbnailAssetId ?: asset.thumbnailAssetId)
                workspaceAssets = workspaceAssets + (asset.id to asset)
                if (execute(CreateObjectsOperation("import-${node.id.value}", listOf(node)))) {
                    selectedIds = setOf(node.id)
                    selectedRelationId = null
                    multiSelectionMode = false
                } else {
                    statusMessage = Strings.media.importNotInserted()
                }
            } catch (error: CancellationException) {
                statusMessage = Strings.media.importCancelled()
                throw error
            } catch (_: AssetTransferUnavailableException) {
                statusMessage = Strings.media.transferUnavailable()
            } catch (_: Exception) {
                statusMessage = Strings.media.importFailed()
            } finally {
                withContext(NonCancellable) {
                    runCatching { selectedSource?.release() }
                }
                mediaImportBusy = false
                mediaImportJob = null
                mediaImportLabel = ""
            }
        }
    }

    fun startEditingSelectedNode(): Boolean {
        val node = selectedIds.singleOrNull()
            ?.let(history.workspace::objectById) as? TextNode
            ?: return false
        if (node.locked || inputBlocked) return false
        selectedRelationId = null
        compactInspectorExpanded = false
        multiSelectionMode = false
        editingId = node.id
        return true
    }

    fun undo() {
        if (inputBlocked || !history.canUndo) return
        synchronize(history.undo())
        selectedIds = emptySet()
        selectedRelationId = null
        editingId = null
        dragPreviews = emptyMap()
        transformPreviews = emptyMap()
        alignmentGuides = AlignmentGuides()
    }

    fun redo() {
        if (inputBlocked || !history.canRedo) return
        synchronize(history.redo())
        selectedIds = emptySet()
        selectedRelationId = null
        editingId = null
        dragPreviews = emptyMap()
        transformPreviews = emptyMap()
        alignmentGuides = AlignmentGuides()
    }

    fun deleteSelection() {
        if (inputBlocked) return
        selectedRelationId?.let { relationId ->
            history.workspace.relationById(relationId)?.let { relation ->
                execute(DeleteRelationsOperation("delete-relation-${relation.id.value}", listOf(relation)))
            }
            selectedRelationId = null
            return
        }
        val selectedGroupIds = selectedIds.filterTo(mutableSetOf()) {
            history.workspace.objectById(it) is GroupFrame
        }
        val effectiveIds = selectedIds + descendantObjectIds(history.workspace, selectedGroupIds)
        val selectedObjects = effectiveIds.mapNotNull(history.workspace::objectById)
            .sortedBy { if (it is GroupFrame) 0 else 1 }
        if (selectedObjects.any { it.locked }) {
            statusMessage = Strings.status.unlockSelectedObjectsBeforeDeleting()
            return
        }
        if (selectedObjects.isNotEmpty()) {
            val touchingRelations = history.workspace.relations.values.filter { relation ->
                relation.sourceObjectId in effectiveIds || relation.targetObjectId in effectiveIds
            }
            execute(
                DeleteObjectsOperation(
                    operationId = "delete-selection-${history.workspace.version}",
                    objects = selectedObjects,
                    relations = touchingRelations,
                ),
            )
        }
        selectedIds = emptySet()
        editingId = null
    }

    fun updateSelectedRelation(update: (RelationAttributes) -> RelationAttributes) {
        val relation = selectedRelationId?.let(history.workspace::relationById) ?: return
        val before = RelationAttributes(
            direction = relation.direction,
            intent = relation.intent,
            label = relation.label,
            colorToken = relation.colorToken,
        )
        val after = update(before)
        if (before == after) return
        execute(
            UpdateRelationAttributesOperation(
                operationId = "update-relation-${relation.id.value}-${history.workspace.version}",
                changes = listOf(
                    RelationAttributesChange(relation.id, relation.version, before, after),
                ),
            ),
        )
    }

    fun fitContent() {
        val bounds = canvasObjectBounds(history.workspace.objects.values)
        viewport = if (bounds == null || canvasSize == IntSize.Zero) {
            Viewport(pan = Vec2(120f * density, 96f * density))
        } else {
            viewport.fit(
                worldTopLeft = bounds.topLeft,
                worldBottomRight = bounds.bottomRight,
                screenSize = CanvasSize(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
                padding = 64f * density,
            )
        }
    }

    fun zoomCanvas(factor: Float) {
        if (canvasSize == IntSize.Zero) return
        val screenCenter = Vec2(canvasSize.width / 2f, canvasSize.height / 2f)
        viewport = viewport.zoomAt(screenCenter, factor)
    }

    fun resetCanvasZoom() {
        if (canvasSize == IntSize.Zero) return
        zoomCanvas(1f / viewport.zoom)
    }

    fun updateSelectedNodes(
        operationName: String,
        update: (node: TextNode, index: Int) -> TextNodeAttributes,
    ) {
        val nodes = selectedIds.mapNotNull { history.workspace.objectById(it) as? TextNode }
            .sortedBy { it.zIndex }
        val changes = nodes.mapIndexedNotNull { index, node ->
            val before = TextNodeAttributes(node.zIndex, node.locked, node.colorToken, node.shape)
            val after = update(node, index)
            if (before == after) null else TextNodeAttributesChange(node.id, node.version, before, after)
        }
        if (changes.isNotEmpty()) {
            execute(
                UpdateTextNodeAttributesOperation(
                    operationId = "$operationName-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun updateSelectedGroups(
        operationName: String,
        update: (group: GroupFrame, index: Int) -> GroupFrameAttributes,
    ) {
        val groups = selectedIds.mapNotNull { history.workspace.objectById(it) as? GroupFrame }
            .sortedBy { it.zIndex }
        val changes = groups.mapIndexedNotNull { index, group ->
            val before = GroupFrameAttributes(group.zIndex, group.locked, group.colorToken, group.title)
            val after = update(group, index)
            if (before == after) null else GroupFrameAttributesChange(group.id, group.version, before, after)
        }
        if (changes.isNotEmpty()) {
            execute(
                UpdateGroupFrameAttributesOperation(
                    operationId = "$operationName-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun updateSelectedMedia(
        operationName: String,
        update: (node: MediaNode, index: Int) -> MediaNodeAttributes,
    ) {
        val media = selectedIds.mapNotNull { history.workspace.objectById(it) as? MediaNode }
            .sortedBy { it.zIndex }
        val changes = media.mapIndexedNotNull { index, node ->
            val before = MediaNodeAttributes(node.zIndex, node.locked, node.altText)
            val after = update(node, index)
            if (before == after) null else MediaNodeAttributesChange(node.id, node.version, before, after)
        }
        if (changes.isNotEmpty()) {
            execute(
                UpdateMediaNodeAttributesOperation(
                    operationId = "$operationName-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun reorderSelectedNodeLayers(move: LayerMove) {
        val nodes = history.workspace.objects.values.filterIsInstance<TextNode>()
        val selectedNodeIds = selectedIds.filterTo(mutableSetOf()) { id ->
            history.workspace.objectById(id) is TextNode
        }
        val updates = layerZIndexUpdates(nodes, selectedNodeIds, move)
        val changes = nodes.mapNotNull { node ->
            val nextZIndex = updates[node.id] ?: return@mapNotNull null
            TextNodeAttributesChange(
                objectId = node.id,
                expectedVersion = node.version,
                before = TextNodeAttributes(node.zIndex, node.locked, node.colorToken, node.shape),
                after = TextNodeAttributes(nextZIndex, node.locked, node.colorToken, node.shape),
            )
        }
        if (changes.isNotEmpty()) {
            execute(
                UpdateTextNodeAttributesOperation(
                    operationId = "layer-node-${move.name.lowercase()}-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun reorderSelectedGroupLayers(move: LayerMove) {
        val groups = history.workspace.objects.values.filterIsInstance<GroupFrame>()
        val selectedGroupIds = selectedIds.filterTo(mutableSetOf()) { id ->
            history.workspace.objectById(id) is GroupFrame
        }
        val updates = layerZIndexUpdates(groups, selectedGroupIds, move)
        val changes = groups.mapNotNull { group ->
            val nextZIndex = updates[group.id] ?: return@mapNotNull null
            GroupFrameAttributesChange(
                objectId = group.id,
                expectedVersion = group.version,
                before = GroupFrameAttributes(group.zIndex, group.locked, group.colorToken, group.title),
                after = GroupFrameAttributes(nextZIndex, group.locked, group.colorToken, group.title),
            )
        }
        if (changes.isNotEmpty()) {
            execute(
                UpdateGroupFrameAttributesOperation(
                    operationId = "layer-group-${move.name.lowercase()}-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun reorderSelectedMediaLayers(move: LayerMove) {
        val media = history.workspace.objects.values.filterIsInstance<MediaNode>()
        val selectedMediaIds = selectedIds.filterTo(mutableSetOf()) { id ->
            history.workspace.objectById(id) is MediaNode
        }
        val updates = layerZIndexUpdates(media, selectedMediaIds, move)
        val changes = media.mapNotNull { node ->
            val nextZIndex = updates[node.id] ?: return@mapNotNull null
            MediaNodeAttributesChange(
                objectId = node.id,
                expectedVersion = node.version,
                before = MediaNodeAttributes(node.zIndex, node.locked, node.altText),
                after = MediaNodeAttributes(nextZIndex, node.locked, node.altText),
            )
        }
        if (changes.isNotEmpty()) {
            execute(
                UpdateMediaNodeAttributesOperation(
                    operationId = "layer-media-${move.name.lowercase()}-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun transformSelectedNodesAsSelection(
        operationName: String,
        update: (Map<CanvasObjectId, CanvasTransform>) -> Map<CanvasObjectId, CanvasTransform>,
    ) {
        val nodesById = selectedIds.mapNotNull { id ->
            val node = history.workspace.objectById(id) as? TextNode ?: return@mapNotNull null
            if (node.locked) null else node.id to node
        }.toMap()
        val afterById = update(nodesById.mapValues { it.value.transform })
        val changes = nodesById.mapNotNull { (id, node) ->
            val after = afterById[id] ?: return@mapNotNull null
            if (after == node.transform) null else TransformChange(id, node.version, node.transform, after)
        }
        if (changes.isNotEmpty()) {
            execute(
                TransformObjectsOperation(
                    operationId = "$operationName-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun connectNodes(
        source: TextNode,
        target: TextNode,
        intent: String,
        direction: RelationDirection,
    ): RelationId? {
        if (inputBlocked || source.id == target.id) return null
        val duplicate = history.workspace.relations.values.any {
            it.sourceObjectId == source.id && it.targetObjectId == target.id && it.intent == intent
        }
        if (duplicate) {
            statusMessage = Strings.status.thoseNodesAlreadyHaveConnection(relationIntentLabel(intent).lowercase())
            return null
        }
        val relationId = RelationId(randomUuid())
        val applied = execute(
            CreateRelationsOperation(
                operationId = "connect-${history.workspace.version}",
                relations = listOf(
                    Relation(
                        id = relationId,
                        sourceObjectId = source.id,
                        targetObjectId = target.id,
                        direction = direction,
                        intent = intent,
                    ),
                ),
            ),
        )
        return relationId.takeIf { applied }
    }

    fun connectSelected(intent: String, direction: RelationDirection) {
        val nodes = selectedIds.mapNotNull { history.workspace.objectById(it) as? TextNode }
            .sortedBy { it.zIndex }
        if (nodes.size != 2) return
        connectNodes(nodes[0], nodes[1], intent, direction)?.let { relationId ->
            selectedIds = emptySet()
            selectedRelationId = relationId
        }
    }

    fun selectionClipboardPayload(): ClipboardPayload? {
        val selectedGroupIds = selectedIds.filterTo(mutableSetOf()) {
            history.workspace.objectById(it) is GroupFrame
        }
        val includedIds = selectedIds + descendantObjectIds(history.workspace, selectedGroupIds)
        val objects = includedIds.mapNotNull(history.workspace::objectById)
        if (objects.isEmpty()) return null
        val nodes = objects.filterIsInstance<TextNode>()
        val groups = objects.filterIsInstance<GroupFrame>()
        val media = objects.filterIsInstance<MediaNode>()
        val minimumZ = objects.minOf(CanvasObject::zIndex)
        val includedIdSet = objects.mapTo(mutableSetOf()) { it.id }
        val nodeIds = nodes.mapTo(mutableSetOf()) { it.id }
        val relations = history.workspace.relations.values.filter {
            it.sourceObjectId in nodeIds && it.targetObjectId in nodeIds
        }
        return ClipboardPayload(
            nodes = nodes.map { node ->
                ClipboardNode(
                    originalId = node.id.value,
                    x = node.transform.position.x,
                    y = node.transform.position.y,
                    width = node.transform.size.width,
                    height = node.transform.size.height,
                    rotationDegrees = node.transform.rotationDegrees,
                    text = node.text,
                    colorToken = node.colorToken,
                    shapeToken = node.shape.token,
                    parentOriginalId = node.parentId?.takeIf { it in includedIdSet }?.value,
                    locked = node.locked,
                    zOffset = node.zIndex - minimumZ,
                )
            },
            groups = groups.map { group ->
                ClipboardGroup(
                    originalId = group.id.value,
                    x = group.transform.position.x,
                    y = group.transform.position.y,
                    width = group.transform.size.width,
                    height = group.transform.size.height,
                    rotationDegrees = group.transform.rotationDegrees,
                    title = group.title,
                    colorToken = group.colorToken,
                    parentOriginalId = group.parentId?.takeIf { it in includedIdSet }?.value,
                    locked = group.locked,
                    zOffset = group.zIndex - minimumZ,
                )
            },
            media = media.map { node ->
                ClipboardMedia(
                    originalId = node.id.value,
                    x = node.transform.position.x,
                    y = node.transform.position.y,
                    width = node.transform.size.width,
                    height = node.transform.size.height,
                    rotationDegrees = node.transform.rotationDegrees,
                    assetId = node.assetId,
                    mediaKind = node.mediaKind.token,
                    altText = node.altText,
                    thumbnailAssetId = node.thumbnailAssetId,
                    parentOriginalId = node.parentId?.takeIf { it in includedIdSet }?.value,
                    locked = node.locked,
                    zOffset = node.zIndex - minimumZ,
                )
            },
            relations = relations.map { relation ->
                ClipboardRelation(
                    sourceId = relation.sourceObjectId.value,
                    targetId = relation.targetObjectId.value,
                    direction = relation.direction.name,
                    intent = relation.intent,
                    label = relation.label,
                )
            },
        )
    }

    fun copySelection(): ClipboardPayload? {
        val payload = selectionClipboardPayload() ?: return null
        @Suppress("DEPRECATION")
        clipboardManager.setText(AnnotatedString(ClipboardJson.encodeToString(payload)))
        val objectCount = payload.nodes.size + payload.groups.size + payload.media.size
        statusMessage = Strings.status.copiedObjects(objectCount)
        return payload
    }

    fun cutSelection() {
        if (inputBlocked) return
        if (copySelection() != null) deleteSelection()
    }

    fun pastePayload(
        payload: ClipboardPayload,
        targetWorldPosition: Vec2? = null,
    ) {
        when (validateClipboardPayload(payload)) {
            null -> Unit
            ClipboardPayloadIssue.UnsupportedVersion -> {
                statusMessage = Strings.status.clipboardUsesUnsupportedBoarderlessVersion()
                return
            }
            ClipboardPayloadIssue.UnsupportedShape -> {
                statusMessage = Strings.library.thisComponentUsesUnsupportedNode()
                return
            }
            else -> {
                statusMessage = Strings.status.clipboardSelectionIsInvalid()
                return
            }
        }
        val nodeShapes = payload.nodes.associate { copied ->
            copied.originalId to checkNotNull(NodeShape.fromToken(copied.shapeToken))
        }
        val idMap = (
            payload.groups.map { it.originalId } +
                payload.nodes.map { it.originalId } +
                payload.media.map { it.originalId }
            )
            .associateWith { CanvasObjectId(randomUuid()) }
        val top = history.workspace.objects.values.maxOfOrNull { it.zIndex } ?: 0L
        val allPositions = payload.groups.map { Vec2(it.x, it.y) } +
            payload.nodes.map { Vec2(it.x, it.y) } +
            payload.media.map { Vec2(it.x, it.y) }
        val sourceOrigin = Vec2(
            x = allPositions.minOf(Vec2::x),
            y = allPositions.minOf(Vec2::y),
        )
        val offset = targetWorldPosition?.let { it - sourceOrigin } ?: Vec2(32f * density, 32f * density)
        val zOrder = (
            payload.groups.map { it.originalId to it.zOffset } +
                payload.nodes.map { it.originalId to it.zOffset }
                + payload.media.map { it.originalId to it.zOffset }
            ).sortedBy { (_, zOffset) -> zOffset }
            .mapIndexed { index, (id, _) -> id to (top + index + 1L) }
            .toMap()
        val groups = orderClipboardGroups(payload.groups).map { copied ->
            GroupFrame(
                id = idMap.getValue(copied.originalId),
                parentId = copied.parentOriginalId?.let(idMap::get),
                zIndex = zOrder.getValue(copied.originalId),
                locked = copied.locked,
                transform = CanvasTransform(
                    position = Vec2(copied.x + offset.x, copied.y + offset.y),
                    size = CanvasSize(copied.width, copied.height),
                    rotationDegrees = copied.rotationDegrees,
                ),
                title = copied.title,
                colorToken = copied.colorToken,
            )
        }
        val nodes = payload.nodes.map { copied ->
            TextNode(
                id = idMap.getValue(copied.originalId),
                parentId = copied.parentOriginalId?.let(idMap::get),
                zIndex = zOrder.getValue(copied.originalId),
                locked = copied.locked,
                transform = CanvasTransform(
                    position = Vec2(copied.x + offset.x, copied.y + offset.y),
                    size = CanvasSize(copied.width, copied.height),
                    rotationDegrees = copied.rotationDegrees,
                ),
                text = copied.text,
                colorToken = copied.colorToken,
                shape = nodeShapes.getValue(copied.originalId),
            )
        }
        val media = payload.media.map { copied ->
            MediaNode(
                id = idMap.getValue(copied.originalId),
                parentId = copied.parentOriginalId?.let(idMap::get),
                zIndex = zOrder.getValue(copied.originalId),
                locked = copied.locked,
                transform = CanvasTransform(
                    position = Vec2(copied.x + offset.x, copied.y + offset.y),
                    size = CanvasSize(copied.width, copied.height),
                    rotationDegrees = copied.rotationDegrees,
                ),
                assetId = copied.assetId,
                mediaKind = checkNotNull(MediaKind.fromToken(copied.mediaKind)),
                altText = copied.altText,
                thumbnailAssetId = copied.thumbnailAssetId,
            )
        }
        val relations = payload.relations.mapNotNull { copied ->
            val sourceId = idMap[copied.sourceId] ?: return@mapNotNull null
            val targetId = idMap[copied.targetId] ?: return@mapNotNull null
            Relation(
                id = RelationId(randomUuid()),
                sourceObjectId = sourceId,
                targetObjectId = targetId,
                direction = RelationDirection.valueOf(copied.direction),
                intent = copied.intent,
                label = copied.label,
            )
        }
        val objects = groups + nodes + media
        val createObjects = CreateObjectsOperation("paste-objects-${history.workspace.version}", objects)
        val operation = if (relations.isEmpty()) {
            createObjects
        } else {
            TransactionOperation(
                operationId = "paste-${history.workspace.version}",
                operations = listOf(
                    createObjects,
                    CreateRelationsOperation("paste-relations-${history.workspace.version}", relations),
                ),
            )
        }
        execute(operation)
        selectedIds = objects.mapTo(mutableSetOf()) { it.id }
        selectedRelationId = null
        editingId = null
    }

    fun pasteFromClipboard() {
        if (inputBlocked) return
        @Suppress("DEPRECATION")
        val text = clipboardManager.getText()?.text
        if (text.isNullOrBlank()) {
            statusMessage = Strings.status.clipboardIsEmpty()
            return
        }
        val payload = runCatching { ClipboardJson.decodeFromString<ClipboardPayload>(text) }.getOrNull()
        if (payload == null) {
            statusMessage = Strings.status.clipboardDoesNotContainBoarderless()
        } else {
            pastePayload(payload)
        }
    }

    fun duplicateSelection() {
        if (inputBlocked) return
        selectionClipboardPayload()?.let(::pastePayload)
    }

    fun saveQuickScheme() {
        val payload = selectionClipboardPayload() ?: return
        val saved = quickSchemeStore.save(
            payload = ClipboardJson.encodeToString(payload),
            schemaVersion = payload.version,
        )
        schemes = quickSchemeStore.list()
        selectedSchemeId = saved.id
        schemeNameDraft = saved.name
        statusMessage = Strings.status.saved(saved.name)
    }

    fun insertScheme(scheme: QuickScheme) {
        val payload = runCatching {
            ClipboardJson.decodeFromString<ClipboardPayload>(scheme.payload)
        }.getOrNull()
        if (payload == null) {
            statusMessage = Strings.status.quickSchemeCouldNotBe()
        } else {
            pastePayload(payload)
        }
    }

    fun insertLibraryComponent(entry: ComponentLibraryEntry, screenPosition: Vec2) {
        val component = libraryComponents.firstOrNull { it.entry.id == entry.id } ?: return
        pastePayload(component.payload, viewport.screenToWorld(screenPosition))
        statusMessage = Strings.status.inserted(entry.title)
    }

    fun finishLibraryDrag(entry: ComponentLibraryEntry, screenPosition: Vec2) {
        libraryDragPreview = null
        val droppedOnCanvas = isLibraryDropOnCanvas(
            screenPosition = screenPosition,
            canvasSize = CanvasSize(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
            density = density,
        )
        if (droppedOnCanvas) {
            insertLibraryComponent(entry, screenPosition)
        } else {
            statusMessage = Strings.status.componentDragCancelled()
        }
    }

    fun renameSelectedScheme() {
        val id = selectedSchemeId ?: return
        val renamed = quickSchemeStore.rename(id, schemeNameDraft) ?: return
        schemes = quickSchemeStore.list()
        schemeNameDraft = renamed.name
        statusMessage = Strings.status.renamed(renamed.name)
    }

    fun deleteSelectedScheme() {
        val id = selectedSchemeId ?: return
        val deletedName = schemes.firstOrNull { it.id == id }?.name ?: return
        if (!quickSchemeStore.delete(id)) return
        schemes = quickSchemeStore.list()
        selectedSchemeId = schemes.lastOrNull()?.id
        schemeNameDraft = schemes.lastOrNull()?.name.orEmpty()
        statusMessage = Strings.status.deleted(deletedName)
    }

    fun groupSelection() {
        val selectedObjects = selectedIds.mapNotNull(history.workspace::objectById)
        if (selectedObjects.size < 2 || inputBlocked) return
        if (selectedObjects.any { it.locked }) {
            statusMessage = Strings.status.unlockObjectsBeforeGroupingThem()
            return
        }
        if (selectedObjects.any { it.parentId in selectedIds }) {
            statusMessage = Strings.status.selectGroupOrItsChild()
            return
        }
        val parentIds = selectedObjects.map { it.parentId }.distinct()
        if (parentIds.size > 1) {
            statusMessage = Strings.status.objectsMustShareSameParent()
            return
        }
        val padding = 40f * density
        val groupTransform = groupFrameTransform(selectedObjects, padding) ?: return
        val group = GroupFrame(
            id = CanvasObjectId(randomUuid()),
            parentId = parentIds.single(),
            zIndex = selectedObjects.minOf { it.zIndex } - 1L,
            transform = groupTransform,
            title = Strings.content.group(history.workspace.objects.values.count { it is GroupFrame } + 1),
        )
        execute(
            TransactionOperation(
                operationId = "group-${history.workspace.version}",
                operations = listOf(
                    CreateObjectsOperation("create-group-${group.id.value}", listOf(group)),
                    ReparentObjectsOperation(
                        operationId = "parent-group-${group.id.value}",
                        changes = selectedObjects.map { canvasObject ->
                            ParentChange(canvasObject.id, canvasObject.version, canvasObject.parentId, group.id)
                        },
                    ),
                ),
            ),
        )
        if (history.workspace.objectById(group.id) !is GroupFrame) return
        selectedIds = setOf(group.id)
        selectedRelationId = null
        multiSelectionMode = false
    }

    fun ungroup(group: GroupFrame) {
        if (inputBlocked) return
        val children = history.workspace.objects.values.filter { it.parentId == group.id }
        val touchingRelations = history.workspace.relations.values.filter { relation ->
            relation.sourceObjectId == group.id || relation.targetObjectId == group.id
        }
        val operations = buildList {
            if (children.isNotEmpty()) {
                add(
                    ReparentObjectsOperation(
                        operationId = "unparent-${group.id.value}",
                        changes = children.map { child ->
                            ParentChange(child.id, child.version, group.id, group.parentId)
                        },
                    ),
                )
            }
            add(
                DeleteObjectsOperation(
                    operationId = "delete-group-${group.id.value}",
                    objects = listOf(group),
                    relations = touchingRelations,
                ),
            )
        }
        execute(
            if (operations.size == 1) operations.single() else {
                TransactionOperation("ungroup-${history.workspace.version}", operations)
            },
        )
        selectedIds = children.mapTo(mutableSetOf()) { it.id }
        selectedRelationId = null
    }

    fun fitGroupToContents(group: GroupFrame) {
        if (inputBlocked || group.locked) return
        val contents = descendantObjectIds(history.workspace, setOf(group.id))
            .mapNotNull(history.workspace::objectById)
        val after = fittedGroupFrameTransform(group, contents, padding = 40f * density)
        if (after == null) {
            statusMessage = Strings.common.groupHasNoContentsTo()
            return
        }
        if (after == group.transform) return
        execute(
            TransformObjectsOperation(
                operationId = "fit-group-${group.id.value}-${history.workspace.version}",
                changes = listOf(
                    TransformChange(group.id, group.version, group.transform, after),
                ),
            ),
        )
    }

    fun nudgeSelection(delta: Vec2) {
        if (inputBlocked || selectedIds.isEmpty()) return
        val movingIds = movableSelectionObjectIds(history.workspace, selectedIds)
        if (movingIds.isEmpty()) {
            statusMessage = Strings.status.unlockSelectionAndGroupedContents()
            return
        }
        val changes = movingIds.mapNotNull { id ->
            history.workspace.objectById(id)?.let { movingObject ->
                TransformChange(
                    objectId = id,
                    expectedVersion = movingObject.version,
                    before = movingObject.transform,
                    after = movingObject.transform.copy(position = movingObject.transform.position + delta),
                )
            }
        }
        if (changes.isNotEmpty()) {
            execute(
                TransformObjectsOperation(
                    operationId = "nudge-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun selectedNodesForAutoLayout(): List<TextNode> = selectedIds
        .mapNotNull { history.workspace.objectById(it) as? TextNode }
        .sortedWith(compareBy(TextNode::zIndex, { it.id.value }))

    fun canAutoLayoutSelection(): Boolean {
        val selectedNodes = selectedNodesForAutoLayout()
        return selectedNodes.size >= 2 &&
            selectedNodes.size == selectedIds.size &&
            selectedNodes.none(TextNode::locked) &&
            selectedNodes.map(TextNode::parentId).distinct().size == 1 &&
            !inputBlocked
    }

    fun autoLayoutSelection(mode: DiagramLayoutMode) {
        if (!canAutoLayoutSelection()) return
        val selectedNodes = selectedNodesForAutoLayout()
        val afterById = autoLayoutNodeTransforms(
            nodes = selectedNodes,
            relations = history.workspace.relations.values,
            mode = mode,
            horizontalGap = 88f * density,
            verticalGap = 96f * density,
        )
        val changes = selectedNodes.mapNotNull { node ->
            val after = afterById[node.id] ?: return@mapNotNull null
            if (after == node.transform) null else {
                TransformChange(node.id, node.version, node.transform, after)
            }
        }
        if (changes.isNotEmpty()) {
            execute(
                TransformObjectsOperation(
                    operationId = "auto-layout-${mode.name.lowercase()}-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun canDistributeSelection(): Boolean = selectedNodesForAutoLayout().size >= 3 &&
        canAutoLayoutSelection()

    fun distributeSelection(axis: DistributionAxis) {
        if (!canDistributeSelection()) return
        val selectedNodes = selectedNodesForAutoLayout()
        val afterById = distributeNodeTransforms(selectedNodes, axis)
        val changes = selectedNodes.mapNotNull { node ->
            val after = afterById[node.id] ?: return@mapNotNull null
            if (after == node.transform) null else TransformChange(node.id, node.version, node.transform, after)
        }
        if (changes.isNotEmpty()) {
            execute(
                TransformObjectsOperation(
                    operationId = "distribute-${axis.name.lowercase()}-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun alignSelection(alignment: SelectionAlignment) {
        if (!canAutoLayoutSelection()) return
        val selectedNodes = selectedNodesForAutoLayout()
        val afterById = alignNodeTransforms(selectedNodes, alignment)
        val changes = selectedNodes.mapNotNull { node ->
            val after = afterById[node.id] ?: return@mapNotNull null
            if (after == node.transform) null else TransformChange(node.id, node.version, node.transform, after)
        }
        if (changes.isNotEmpty()) {
            execute(
                TransformObjectsOperation(
                    operationId = "align-${alignment.name.lowercase()}-${history.workspace.version}",
                    changes = changes,
                ),
            )
        }
    }

    fun openCanvasBackgroundPicker() {
        colorPickerTargets = null
        colorPreviewToken = null
        canvasBackgroundPreview = null
        showCanvasBackgroundPicker = true
    }

    val commandPaletteEntries = buildList {
        if (mediaImportRuntime.selectSource != null) {
            add(
                PaletteEntry(
                    id = if (mediaImportBusy) "cancel-media-import" else "import-media",
                    title = if (mediaImportBusy) Strings.media.cancelImport() else Strings.media.importMedia(),
                    subtitle = if (mediaImportBusy) mediaImportLabel else Strings.media.selecting(),
                    keywords = "image gif video upload import media 圖片 影片 素材 匯入 取消",
                    enabled = mediaImportBusy || (!inputBlocked && workspaceChangeAllowed),
                    disabledReason = Strings.palette.waitForWorkspaceToFinish(),
                ),
            )
        }
        add(
            PaletteEntry(
                id = "new-thought",
                title = Strings.content.newThought(),
                subtitle = Strings.palette.createAndEditTextNode(),
                keywords = paletteKeywords(Strings.palette.keywords.addCreateCaptureNode),
                shortcut = "N",
                enabled = !inputBlocked,
                disabledReason = Strings.palette.waitForWorkspaceToFinish(),
            ),
        )
        addAll(
            diagramShapePaletteEntries(
                enabled = !inputBlocked,
                disabledReason = Strings.palette.waitForWorkspaceToFinish(),
            ),
        )
        addAll(
            diagramTemplatePaletteEntries(
                components = libraryComponents.map(BuiltInComponent::entry),
                enabled = !inputBlocked,
                disabledReason = Strings.palette.waitForWorkspaceToFinish(),
            ),
        )
        add(
            PaletteEntry(
                id = "fit-content",
                title = Strings.palette.fitContent(),
                subtitle = Strings.palette.bringEveryThoughtIntoView(),
                keywords = paletteKeywords(Strings.palette.keywords.zoomResetCanvasView),
                shortcut = "F",
            ),
        )
        add(
            PaletteEntry(
                id = "zoom-in",
                title = Strings.palette.zoomIn(),
                subtitle = Strings.palette.zoomTowardCenterOfCanvas(),
                keywords = paletteKeywords(Strings.palette.keywords.magnifyPlusCanvasView),
            ),
        )
        add(
            PaletteEntry(
                id = "zoom-out",
                title = Strings.palette.zoomOut(),
                subtitle = Strings.palette.showMoreOfCanvasAround(),
                keywords = paletteKeywords(Strings.palette.keywords.minusCanvasView),
            ),
        )
        add(
            PaletteEntry(
                id = "reset-zoom",
                title = Strings.palette.resetZoomTo100Percent(),
                subtitle = Strings.palette.keepCurrentCenterWhileRestoring(),
                keywords = paletteKeywords(Strings.palette.keywords.actualSizeCanvasView),
            ),
        )
        add(
            PaletteEntry(
                id = "toggle-area-selection",
                title = if (areaSelectionMode) Strings.palette.exitAreaSelection() else Strings.palette.selectArea(),
                subtitle = if (areaSelectionMode) {
                    Strings.palette.returnEmptyCanvasDraggingTo()
                } else {
                    Strings.palette.dragEmptyCanvasToReplace()
                },
                keywords = paletteKeywords(Strings.palette.keywords.marqueeBoxMultiSelectDrag),
            ),
        )
        val editableSelectedNode = selectedIds.singleOrNull()
            ?.let(history.workspace::objectById) as? TextNode
        add(
            PaletteEntry(
                id = "edit-selected",
                title = Strings.palette.editSelectedThought(),
                subtitle = Strings.palette.moveKeyboardFocusIntoSelected(),
                keywords = paletteKeywords(Strings.palette.keywords.textTypeRenameWriteKeyboard),
                shortcut = "Enter",
                enabled = editableSelectedNode != null && !editableSelectedNode.locked && !inputBlocked,
                disabledReason = Strings.palette.selectOneUnlockedThought(),
            ),
        )
        add(
            PaletteEntry(
                id = "undo",
                title = Strings.palette.undo(),
                subtitle = Strings.palette.reverseLastWorkspaceOperation(),
                keywords = paletteKeywords(Strings.palette.keywords.historyBackRevert),
                shortcut = "⌘Z",
                enabled = !inputBlocked && history.canUndo,
                disabledReason = Strings.palette.thereIsNoSavedOperation(),
            ),
        )
        add(
            PaletteEntry(
                id = "redo",
                title = Strings.palette.redo(),
                subtitle = Strings.palette.restoreLastUndoneOperation(),
                keywords = paletteKeywords(Strings.palette.keywords.historyForward),
                shortcut = "⇧⌘Z",
                enabled = !inputBlocked && history.canRedo,
                disabledReason = Strings.palette.thereIsNoOperationTo(),
            ),
        )
        add(
            PaletteEntry(
                id = "group",
                title = Strings.palette.groupSelection(),
                subtitle = Strings.palette.createFrameAroundSelectedObjects(),
                keywords = paletteKeywords(Strings.palette.keywords.organizeFrameParent),
                shortcut = "⌘G",
                enabled = selectedIds.size >= 2 && !inputBlocked,
                disabledReason = Strings.palette.selectTwoOrMoreCompatible(),
            ),
        )
        val canConnectSelected = selectedIds.size == 2 &&
            selectedIds.all { history.workspace.objectById(it) is TextNode } &&
            !inputBlocked
        addAll(
            relationPaletteEntries(
                enabled = canConnectSelected,
                disabledReason = Strings.palette.selectExactlyTwoThoughts(),
            ),
        )
        val selectedConnectionSource = selectedIds.singleOrNull()
            ?.let(history.workspace::objectById) as? TextNode
        if (selectedConnectionSource != null) {
            addAll(
                connectionTargetPaletteEntries(
                    sourceId = selectedConnectionSource.id,
                    nodes = history.workspace.objects.values.filterIsInstance<TextNode>(),
                    enabled = !inputBlocked,
                    disabledReason = Strings.palette.waitForWorkspaceToFinish(),
                ),
            )
        }
        add(
            PaletteEntry(
                id = "layout-horizontal",
                title = Strings.layout.layoutSelectionHorizontally(),
                subtitle = Strings.layout.arrangeConnectedNodesAsLeft(),
                keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutAlignFlowHorizontal),
                enabled = canAutoLayoutSelection(),
                disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
            ),
        )
        add(
            PaletteEntry(
                id = "layout-tree",
                title = Strings.layout.layoutSelectionAsTree(),
                subtitle = Strings.layout.arrangeRelationDirectionIntoHierarchy(),
                keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutHierarchyTreeOrganization),
                enabled = canAutoLayoutSelection(),
                disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
            ),
        )
        add(
            PaletteEntry(
                id = "layout-radial",
                title = Strings.layout.layoutSelectionRadially(),
                subtitle = Strings.layout.arrangeCentralNodeAndIts(),
                keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutRadialRelationshipTopology),
                enabled = canAutoLayoutSelection(),
                disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
            ),
        )
        add(
            PaletteEntry(
                id = "layout-grid",
                title = Strings.layout.layoutSelectionAsGrid(),
                subtitle = Strings.layout.arrangeMixedSizeNodesInto(),
                keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutGridArchitectureTopology),
                enabled = canAutoLayoutSelection(),
                disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
            ),
        )
        add(
            PaletteEntry(
                id = "distribute-horizontal",
                title = Strings.layout.distributeSelectionHorizontally(),
                subtitle = Strings.layout.keepOuterNodesAndEqualize(),
                keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutDistributeSpacingHorizontal),
                enabled = canDistributeSelection(),
                disabledReason = Strings.layout.selectThreeOrMoreUnlocked(),
            ),
        )
        add(
            PaletteEntry(
                id = "distribute-vertical",
                title = Strings.layout.distributeSelectionVertically(),
                subtitle = Strings.layout.keepOuterNodesAndEqualizeVertical(),
                keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutDistributeSpacingVertical),
                enabled = canDistributeSelection(),
                disabledReason = Strings.layout.selectThreeOrMoreUnlocked(),
            ),
        )
        listOf(
            Triple("align-left", Strings.layout.alignLeft(), Strings.layout.alignVisualLeftEdges()),
            Triple("align-center-horizontal", Strings.layout.alignHorizontalCenters(), Strings.layout.alignVisualCentersOnHorizontal()),
            Triple("align-right", Strings.layout.alignRight(), Strings.layout.alignVisualRightEdges()),
            Triple("align-top", Strings.layout.alignTop(), Strings.layout.alignVisualTopEdges()),
            Triple("align-center-vertical", Strings.layout.alignVerticalCenters(), Strings.layout.alignVisualCentersOnVertical()),
            Triple("align-bottom", Strings.layout.alignBottom(), Strings.layout.alignVisualBottomEdges()),
        ).forEach { (id, title, subtitle) ->
            add(
                PaletteEntry(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutAlignEdgesCenters),
                    enabled = canAutoLayoutSelection(),
                    disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
                ),
            )
        }
        val selectedGroup = selectedIds.singleOrNull()
            ?.let(history.workspace::objectById) as? GroupFrame
        val selectedGroupHasContents = selectedGroup?.let { group ->
            descendantObjectIds(history.workspace, setOf(group.id)).isNotEmpty()
        } == true
        add(
            PaletteEntry(
                id = "fit-group",
                title = Strings.common.fitGroupToContents(),
                subtitle = Strings.common.resizeSelectedFrameAroundAll(),
                keywords = paletteKeywords(Strings.palette.keywords.groupFrameFitContentsResize),
                enabled = selectedGroup != null && !selectedGroup.locked && selectedGroupHasContents && !inputBlocked,
                disabledReason = Strings.common.selectOneUnlockedGroupWith(),
            ),
        )
        add(
            PaletteEntry(
                id = "ungroup",
                title = Strings.palette.ungroup(),
                subtitle = Strings.palette.keepContentsAndRemoveTheir(),
                keywords = paletteKeywords(Strings.palette.keywords.frameSeparateUnparent),
                shortcut = "⇧⌘G",
                enabled = selectedGroup != null && !selectedGroup.locked && !inputBlocked,
                disabledReason = Strings.palette.selectOneUnlockedGroup(),
            ),
        )
        add(
            PaletteEntry(
                id = "delete",
                title = Strings.palette.deleteSelection(),
                subtitle = Strings.palette.removeSelectedObjectsOrConnection(),
                keywords = paletteKeywords(Strings.palette.keywords.removeTrash),
                shortcut = "⌫",
                enabled = (selectedIds.isNotEmpty() || selectedRelationId != null) && !inputBlocked,
                disabledReason = Strings.palette.nothingIsSelected(),
            ),
        )
        add(
            PaletteEntry(
                id = "layers",
                title = Strings.palette.openLayers(),
                subtitle = Strings.palette.browseObjectsByGroupAnd(),
                keywords = paletteKeywords(Strings.palette.keywords.objectsTreeHierarchyZOrder),
            ),
        )
        add(
            PaletteEntry(
                id = "history",
                title = Strings.palette.openLocalHistory(),
                subtitle = Strings.palette.reviewUndoRedoAndPending(),
                keywords = paletteKeywords(Strings.palette.keywords.activityOperationsUndoRedoSaves),
            ),
        )
        add(
            PaletteEntry(
                id = "workspaces",
                title = Strings.palette.openWorkspaces(),
                subtitle = Strings.palette.createOrSwitchThinkingSpaces(),
                keywords = paletteKeywords(Strings.palette.keywords.spaceProjectSwitchNewOpen),
            ),
        )
        add(
            PaletteEntry(
                id = "members",
                title = Strings.members.openWorkspaceMembers(),
                subtitle = Strings.members.reviewSharedAccessAndManage(),
                keywords = paletteKeywords(Strings.palette.keywords.shareCollaborateMembersPeoplePermissions),
                enabled = session != null && !connectionFailed,
                disabledReason = Strings.members.connectToWorkspaceFirst(),
            ),
        )
        add(
            PaletteEntry(
                id = "library",
                title = Strings.palette.openObjectLibrary(),
                subtitle = Strings.palette.dragBuiltInComponentsOnto(),
                keywords = paletteKeywords(Strings.palette.keywords.componentItemObjectStarter),
            ),
        )
        add(
            PaletteEntry(
                id = "schemes",
                title = Strings.palette.openQuickSchemes(),
                subtitle = Strings.palette.browseAndInsertSavedObject(),
                keywords = paletteKeywords(Strings.palette.keywords.libraryTemplateReusable),
            ),
        )
        add(
            PaletteEntry(
                id = "save-scheme",
                title = Strings.palette.saveSelectionAsScheme(),
                subtitle = Strings.palette.reuseThisArrangementLater(),
                keywords = paletteKeywords(Strings.palette.keywords.libraryTemplateReusable),
                enabled = selectionClipboardPayload() != null && !inputBlocked,
                disabledReason = Strings.palette.selectAtLeastOneText(),
            ),
        )
        add(
            PaletteEntry(
                id = "canvas-background",
                title = Strings.canvasBackground.chooseCanvasBackgroundColor(),
                subtitle = Strings.canvasBackground.current(canvasBackgroundLabel(displaySettings.backgroundToken)),
                keywords = paletteKeywords(Strings.palette.keywords.canvasBackgroundColorPickerHex),
            ),
        )
        add(
            PaletteEntry(
                id = "toggle-grid",
                title = if (displaySettings.showGrid) Strings.palette.hideGrid() else Strings.palette.showGrid(),
                subtitle = Strings.palette.changeGridVisibilityWithoutChanging(),
                keywords = paletteKeywords(Strings.palette.keywords.canvasLines),
            ),
        )
        add(
            PaletteEntry(
                id = "toggle-snap",
                title = if (displaySettings.snapToGrid) Strings.palette.disableGridSnap() else Strings.palette.enableGridSnap(),
                subtitle = Strings.palette.changePlacementPrecision(),
                keywords = paletteKeywords(Strings.palette.keywords.canvasAlignPrecision),
            ),
        )
        add(
            PaletteEntry(
                id = "toggle-transparency",
                title = if (reduceTransparency) Strings.palette.useTranslucentInterface() else Strings.palette.reduceTransparency(),
                subtitle = if (reduceTransparency) {
                    Strings.palette.restoreTranslucentShellSurfaces()
                } else {
                    Strings.palette.useOpaqueHigherContrastShell()
                },
                keywords = paletteKeywords(Strings.palette.keywords.accessibilityContrastGlassBlurAppearance),
            ),
        )
        add(
            PaletteEntry(
                id = "toggle-motion",
                title = if (reduceMotion) Strings.palette.enableInterfaceMotion() else Strings.palette.reduceMotion(),
                subtitle = if (reduceMotion) {
                    Strings.palette.restoreSubtleSelectionFeedback()
                } else {
                    Strings.palette.disableNonEssentialSelectionAnimation()
                },
                keywords = paletteKeywords(Strings.palette.keywords.accessibilityAnimationMotionMovementTransition),
            ),
        )
        val languageKeywords = paletteKeywords(Strings.palette.keywords.languageLocaleTranslateEnglishChinese) +
            Localization.languages.joinToString(separator = " ", prefix = " ") { it.nativeName }
        (listOf(null) + Localization.languages).forEach { language ->
            val preference = language?.let(LanguagePreference::of) ?: LanguagePreference.System
            add(
                PaletteEntry(
                    id = "language:${preference.token}",
                    title = language?.let { Strings.palette.language(it.nativeName) } ?: Strings.palette.languageFollowSystem(),
                    subtitle = language?.let { Strings.palette.showInterfaceIn(it.nativeName) }
                        ?: Strings.palette.matchDeviceLanguage(),
                    keywords = languageKeywords,
                    enabled = preference != languagePreference,
                    disabledReason = Strings.common.alreadyInUse(),
                ),
            )
        }
        history.workspace.objects.values
            .filterIsInstance<TextNode>()
            .sortedByDescending { it.zIndex }
            .forEach { node ->
                add(
                    PaletteEntry(
                        id = "find:${node.id.value}",
                        title = node.text.lineSequence().firstOrNull()?.take(80).orEmpty().ifBlank { Strings.content.untitledThought() },
                        subtitle = Strings.palette.jumpToThought(),
                        keywords = "${paletteKeywords(Strings.palette.keywords.contentNode)} ${node.text}",
                    ),
                )
            }
        addAll(groupNavigationPaletteEntries(history.workspace.objects.values.filterIsInstance<GroupFrame>()))
        addAll(
            relationNavigationPaletteEntries(
                relations = history.workspace.relations.values,
                nodesById = history.workspace.objects.values.filterIsInstance<TextNode>().associateBy { it.id },
            ),
        )
    }

    fun invokePaletteEntry(entry: PaletteEntry) {
        showCommandPalette = false
        commandPaletteQuery = ""
        when (entry.id) {
            "new-thought" -> addTextNodeAtCenter()
            "import-media" -> startMediaImport()
            "cancel-media-import" -> mediaImportJob?.cancel()

            "fit-content" -> fitContent()
            "zoom-in" -> zoomCanvas(1.2f)
            "zoom-out" -> zoomCanvas(1f / 1.2f)
            "reset-zoom" -> resetCanvasZoom()
            "toggle-area-selection" -> areaSelectionMode = !areaSelectionMode
            "edit-selected" -> startEditingSelectedNode()
            "undo" -> undo()
            "redo" -> redo()
            "group" -> groupSelection()
            "connect-relates" -> connectSelected("relates", RelationDirection.Forward)
            "connect-supports" -> connectSelected("supports", RelationDirection.Forward)
            "connect-conflicts" -> connectSelected("conflicts", RelationDirection.Both)
            "layout-horizontal" -> autoLayoutSelection(DiagramLayoutMode.HorizontalFlow)
            "layout-tree" -> autoLayoutSelection(DiagramLayoutMode.VerticalTree)
            "layout-radial" -> autoLayoutSelection(DiagramLayoutMode.RadialRelationship)
            "layout-grid" -> autoLayoutSelection(DiagramLayoutMode.Grid)
            "distribute-horizontal" -> distributeSelection(DistributionAxis.Horizontal)
            "distribute-vertical" -> distributeSelection(DistributionAxis.Vertical)
            "align-left" -> alignSelection(SelectionAlignment.Left)
            "align-center-horizontal" -> alignSelection(SelectionAlignment.HorizontalCenter)
            "align-right" -> alignSelection(SelectionAlignment.Right)
            "align-top" -> alignSelection(SelectionAlignment.Top)
            "align-center-vertical" -> alignSelection(SelectionAlignment.VerticalCenter)
            "align-bottom" -> alignSelection(SelectionAlignment.Bottom)
            "fit-group" -> (selectedIds.singleOrNull()
                ?.let(history.workspace::objectById) as? GroupFrame)
                ?.let(::fitGroupToContents)
            "ungroup" -> (selectedIds.singleOrNull()
                ?.let(history.workspace::objectById) as? GroupFrame)
                ?.let(::ungroup)
            "delete" -> deleteSelection()
            "layers" -> {
                showLayersPanel = true
                showMembersPanel = false
                showHistoryPanel = false
                showWorkspaceSwitcher = false
                showComponentLibrary = false
                showSchemeLibrary = false
            }
            "history" -> {
                showHistoryPanel = true
                showMembersPanel = false
                showLayersPanel = false
                showWorkspaceSwitcher = false
                showComponentLibrary = false
                showSchemeLibrary = false
            }
            "workspaces" -> {
                showWorkspaceSwitcher = true
                showMembersPanel = false
                showComponentLibrary = false
                showSchemeLibrary = false
                showLayersPanel = false
                showHistoryPanel = false
                workspaceRenameDraft = session?.workspace?.title.orEmpty()
                workspaceDeleteArmed = false
            }
            "members" -> {
                showMembersPanel = true
                showWorkspaceSwitcher = false
                showComponentLibrary = false
                showSchemeLibrary = false
                showLayersPanel = false
                showHistoryPanel = false
                memberRemoveArmed = null
            }
            "library" -> {
                showComponentLibrary = true
                showMembersPanel = false
                showSchemeLibrary = false
                showWorkspaceSwitcher = false
                showLayersPanel = false
                showHistoryPanel = false
            }
            "schemes" -> {
                showSchemeLibrary = true
                showMembersPanel = false
                showComponentLibrary = false
                showWorkspaceSwitcher = false
                showLayersPanel = false
                showHistoryPanel = false
            }
            "save-scheme" -> saveQuickScheme()
            "canvas-background" -> openCanvasBackgroundPicker()
            "toggle-grid" -> displaySettings = displaySettings.copy(showGrid = !displaySettings.showGrid)
            "toggle-snap" -> displaySettings = displaySettings.copy(snapToGrid = !displaySettings.snapToGrid)
            "toggle-transparency" -> onReduceTransparencyChange(!reduceTransparency)
            "toggle-motion" -> onReduceMotionChange(!reduceMotion)
            else -> when {
                entry.id.startsWith("language:") ->
                    onLanguagePreferenceChange(LanguagePreference.fromToken(entry.id.removePrefix("language:")))
                entry.id.startsWith("new-shape:") -> {
                    NodeShape.fromToken(entry.id.removePrefix("new-shape:"))
                        ?.let(::addTextNodeAtCenter)
                }

                entry.id.startsWith("insert-component:") -> {
                    val componentId = entry.id.removePrefix("insert-component:")
                    libraryComponents.firstOrNull { it.entry.id == componentId }
                        ?.entry
                        ?.let { component ->
                            insertLibraryComponent(
                                component,
                                Vec2(canvasSize.width / 2f, canvasSize.height / 2f),
                            )
                        }
                }

                entry.id.startsWith("connect-target:") -> {
                    val source = selectedIds.singleOrNull()
                        ?.let(history.workspace::objectById) as? TextNode
                    val targetId = CanvasObjectId(entry.id.removePrefix("connect-target:"))
                    val target = history.workspace.objectById(targetId) as? TextNode
                    if (source != null && target != null) {
                        connectNodes(source, target, "relates", RelationDirection.Forward)?.let { relationId ->
                            selectedIds = emptySet()
                            selectedRelationId = relationId
                        }
                    }
                }

                entry.id.startsWith("find-relation:") -> {
                    val relationId = RelationId(entry.id.removePrefix("find-relation:"))
                    val relation = history.workspace.relationById(relationId)
                    val source = relation?.sourceObjectId?.let(history.workspace::objectById) as? TextNode
                    val target = relation?.targetObjectId?.let(history.workspace::objectById) as? TextNode
                    if (relation != null && source != null && target != null) {
                        if (canvasSize != IntSize.Zero) {
                            val sourceCenter = source.transform.position +
                                Vec2(source.transform.size.width / 2f, source.transform.size.height / 2f)
                            val targetCenter = target.transform.position +
                                Vec2(target.transform.size.width / 2f, target.transform.size.height / 2f)
                            val relationCenter = (sourceCenter + targetCenter) / 2f
                            viewport = viewport.copy(
                                pan = Vec2(canvasSize.width / 2f, canvasSize.height / 2f) -
                                    relationCenter * viewport.zoom,
                            )
                        }
                        selectedIds = emptySet()
                        selectedRelationId = relation.id
                        editingId = null
                        statusMessage = Strings.status.located(
                            relation.label?.takeIf(String::isNotBlank)
                                ?: relation.intent?.let(::relationIntentLabel)
                                ?: Strings.objects.connection(),
                        )
                    }
                }

                entry.id.startsWith("find-group:") -> {
                    val groupId = CanvasObjectId(entry.id.removePrefix("find-group:"))
                    val group = history.workspace.objectById(groupId) as? GroupFrame
                    if (group != null) {
                        if (canvasSize != IntSize.Zero) {
                            val worldCenter = group.transform.position +
                                Vec2(group.transform.size.width / 2f, group.transform.size.height / 2f)
                            viewport = viewport.copy(
                                pan = Vec2(canvasSize.width / 2f, canvasSize.height / 2f) -
                                    worldCenter * viewport.zoom,
                            )
                        }
                        selectedIds = setOf(group.id)
                        selectedRelationId = null
                        editingId = null
                        statusMessage = Strings.status.located(
                            group.title.take(48).ifBlank { Strings.content.untitledGroup() },
                        )
                    }
                }

                entry.id.startsWith("find:") -> {
                    val objectId = CanvasObjectId(entry.id.removePrefix("find:"))
                    val node = history.workspace.objectById(objectId) as? TextNode
                    if (node != null && canvasSize != IntSize.Zero) {
                        val worldCenter = node.transform.position + Vec2(
                            node.transform.size.width / 2f,
                            node.transform.size.height / 2f,
                        )
                        val screenCenter = Vec2(canvasSize.width / 2f, canvasSize.height / 2f)
                        viewport = viewport.copy(pan = screenCenter - worldCenter * viewport.zoom)
                        selectedIds = setOf(node.id)
                        selectedRelationId = null
                        editingId = null
                        statusMessage = Strings.status.located(node.text.take(48))
                    }
                }
            }
        }
        if (
            entry.id != "new-thought" && entry.id != "edit-selected" &&
            !entry.id.startsWith("new-shape:")
        ) {
            focusManager.clearFocus()
            canvasFocusRequester.requestFocus()
        }
    }

    fun toggleCommandPalette() {
        showCommandPalette = !showCommandPalette
        commandPaletteQuery = ""
        if (showCommandPalette) {
            showSchemeLibrary = false
            showComponentLibrary = false
            showWorkspaceSwitcher = false
            showMembersPanel = false
            showLayersPanel = false
            showHistoryPanel = false
            showCompactMenu = false
        }
    }

    fun selectAllObjects() {
        selectedIds = topLevelSelectionIds(history.workspace.objects.values)
        selectedRelationId = null
    }

    if (menuBridge != null) {
        val textEditing = editingId != null || inspectorTextEditing || workspaceFormEditing
        val hasSelection = selectedIds.isNotEmpty()
        val paletteById = commandPaletteEntries.associateBy(PaletteEntry::id)
        val menuCommands = buildMap {
            commandPaletteEntries.forEach { put(it.id, it.enabled && !textEditing) }
            put(WorkspaceMenuCommands.CommandPalette, true)
            put(WorkspaceMenuCommands.Cut, hasSelection && !inputBlocked && !textEditing)
            put(WorkspaceMenuCommands.Copy, hasSelection && !textEditing)
            put(WorkspaceMenuCommands.Paste, !inputBlocked && !textEditing)
            put(WorkspaceMenuCommands.Duplicate, hasSelection && !inputBlocked && !textEditing)
            put(WorkspaceMenuCommands.SelectAll, !textEditing)
        }
        SideEffect {
            menuBridge.commands = menuCommands
            menuBridge.handler = { id ->
                when (id) {
                    WorkspaceMenuCommands.CommandPalette -> toggleCommandPalette()
                    WorkspaceMenuCommands.Cut -> cutSelection()
                    WorkspaceMenuCommands.Copy -> copySelection()
                    WorkspaceMenuCommands.Paste -> pasteFromClipboard()
                    WorkspaceMenuCommands.Duplicate -> duplicateSelection()
                    WorkspaceMenuCommands.SelectAll -> selectAllObjects()
                    else -> paletteById[id]?.let(::invokePaletteEntry)
                }
            }
        }
        DisposableEffect(menuBridge) {
            onDispose {
                menuBridge.commands = emptyMap()
                menuBridge.handler = null
            }
        }
    }

    LaunchedEffect(Unit) {
        canvasFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBackgroundColor(canvasBackgroundPreview ?: displaySettings.backgroundToken, colors))
            .onSizeChanged { canvasSize = it }
            .onPreviewKeyEvent { event ->
                val command = event.isMetaPressed || event.isCtrlPressed
                if (event.type == KeyEventType.KeyDown && command && event.key == Key.K) {
                    toggleCommandPalette()
                    return@onPreviewKeyEvent true
                }
                if (
                    event.type != KeyEventType.KeyDown || editingId != null || inspectorTextEditing ||
                    workspaceFormEditing ||
                    showCommandPalette
                ) {
                    return@onPreviewKeyEvent false
                }
                when {
                    !command && event.key == Key.N -> {
                        addTextNodeAtCenter()
                        true
                    }

                    !command && event.key == Key.F -> {
                        fitContent()
                        true
                    }

                    command && event.key == Key.Z && event.isShiftPressed -> {
                        redo()
                        true
                    }

                    command && event.key == Key.Z -> {
                        undo()
                        true
                    }

                    command && event.key == Key.A -> {
                        selectAllObjects()
                        true
                    }

                    command && event.key == Key.C -> {
                        copySelection()
                        true
                    }

                    command && event.key == Key.X -> {
                        cutSelection()
                        true
                    }

                    command && event.key == Key.V -> {
                        pasteFromClipboard()
                        true
                    }

                    command && event.key == Key.D -> {
                        duplicateSelection()
                        true
                    }

                    command && event.key == Key.G && event.isShiftPressed -> {
                        (selectedIds.singleOrNull()
                            ?.let(history.workspace::objectById) as? GroupFrame)
                            ?.let(::ungroup)
                        true
                    }

                    command && event.key == Key.G -> {
                        groupSelection()
                        true
                    }

                    !command && event.key == Key.Enter -> startEditingSelectedNode()

                    event.key == Key.Delete || event.key == Key.Backspace -> {
                        deleteSelection()
                        true
                    }

                    event.key == Key.DirectionLeft -> {
                        nudgeSelection(Vec2(if (event.isShiftPressed) -10f * density else -density, 0f))
                        true
                    }

                    event.key == Key.DirectionRight -> {
                        nudgeSelection(Vec2(if (event.isShiftPressed) 10f * density else density, 0f))
                        true
                    }

                    event.key == Key.DirectionUp -> {
                        nudgeSelection(Vec2(0f, if (event.isShiftPressed) -10f * density else -density))
                        true
                    }

                    event.key == Key.DirectionDown -> {
                        nudgeSelection(Vec2(0f, if (event.isShiftPressed) 10f * density else density))
                        true
                    }

                    event.key == Key.Escape -> {
                        if (multiSelectionMode) {
                            multiSelectionMode = false
                        } else if (areaSelectionMode) {
                            areaSelectionMode = false
                            marquee = null
                        } else if (showCommandPalette) {
                            showCommandPalette = false
                            commandPaletteQuery = ""
                        } else if (showCompactMenu) {
                            showCompactMenu = false
                        } else if (showWorkspaceSwitcher) {
                            showWorkspaceSwitcher = false
                            workspaceDeleteArmed = false
                        } else if (showMembersPanel) {
                            showMembersPanel = false
                            memberRemoveArmed = null
                        } else if (showComponentLibrary) {
                            showComponentLibrary = false
                            libraryDragPreview = null
                        } else if (showSchemeLibrary) {
                            showSchemeLibrary = false
                        } else if (showLayersPanel) {
                            showLayersPanel = false
                        } else if (showHistoryPanel) {
                            showHistoryPanel = false
                        } else {
                            selectedIds = emptySet()
                            selectedRelationId = null
                            editingId = null
                            dragPreviews = emptyMap()
                            transformPreviews = emptyMap()
                            alignmentGuides = AlignmentGuides()
                        }
                        true
                    }

                    else -> false
                }
            }
            .focusRequester(canvasFocusRequester)
            .focusable(),
    ) {
        GridCanvas(
            viewport = viewport,
            density = density,
            showGrid = displaySettings.showGrid,
            gridColor = canvasGridColor(canvasBackgroundPreview ?: displaySettings.backgroundToken, colors),
            marquee = marquee,
            areaSelectionMode = areaSelectionMode,
            onViewportChange = { viewport = it },
            onTap = { screenPosition ->
                val relationHit = findRelationAt(
                    screenPosition = screenPosition,
                    relations = history.workspace.relations.values,
                    nodes = history.workspace.objects.values.filterIsInstance<TextNode>().associateBy { it.id },
                    viewport = viewport,
                    tolerance = 10f * density,
                )
                if (compactLayout && multiSelectionMode) {
                    selectedRelationId = null
                } else {
                    selectedRelationId = relationHit?.id
                    selectedIds = emptySet()
                }
                editingId = null
                focusManager.clearFocus()
                canvasFocusRequester.requestFocus()
            },
            onDoubleTap = { addTextNode(it) },
            onMarqueeChange = { start, end -> marquee = Marquee(start, end) },
            onMarqueeCancel = { marquee = null },
            onMarqueeCommit = { start, end, additive ->
                marquee = null
                val worldStart = viewport.screenToWorld(start)
                val worldEnd = viewport.screenToWorld(end)
                val hits = history.workspace.objects.values
                    .filterIsInstance<TextNode>()
                    .filter { node ->
                        marqueeIntersectsTransform(node.transform, worldStart, worldEnd)
                    }
                    .mapTo(mutableSetOf()) { it.id }
                selectedIds = applyMarqueeSelection(selectedIds, hits, additive)
                selectedRelationId = null
                editingId = null
                canvasFocusRequester.requestFocus()
            },
        )

        if (session != null && history.workspace.objects.isEmpty() && pendingNewNode == null && !connectionFailed) {
            GlassSurface(modifier = Modifier.align(Alignment.Center)) {
                Column(
                    modifier = Modifier
                        .width(emptyCanvasCardWidthDp(canvasSize.width, density, compactLayout).dp)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        text = Strings.emptyCanvas.startWithThought(),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    BasicText(
                        text = if (compactLayout) {
                            Strings.emptyCanvas.doubleTapCanvasOrChooseComponent()
                        } else {
                            Strings.emptyCanvas.doubleClickCanvasPressN()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            color = colors.contentMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(ButtonGap),
                    ) {
                        ShellButton(
                            label = Strings.emptyCanvas.firstThought(),
                            icon = ShellIcon.Add,
                            accent = true,
                            enabled = !inputBlocked,
                            onClick = ::addTextNodeAtCenter,
                        )
                        ShellButton(
                            label = Strings.emptyCanvas.openLibrary(),
                            icon = ShellIcon.Library,
                            onClick = {
                                showComponentLibrary = true
                                showMembersPanel = false
                                showSchemeLibrary = false
                                showLayersPanel = false
                                showHistoryPanel = false
                            },
                        )
                    }
                }
            }
        }

        val groupDropPreviewId = selectedIds
            .mapNotNull { id -> history.workspace.objectById(id) as? TextNode }
            .takeIf { selectedNodes ->
                selectedNodes.isNotEmpty() &&
                    selectedNodes.size == selectedIds.size &&
                    selectedNodes.all { dragPreviews.containsKey(it.id) }
            }
            ?.let { draggedNodes ->
                groupDropTargetId(
                    groups = history.workspace.objects.values.filterIsInstance<GroupFrame>(),
                    movingTransforms = draggedNodes.map { draggedNode ->
                        draggedNode.transform.copy(
                            position = draggedNode.transform.position + dragPreviews.getValue(draggedNode.id),
                        )
                    },
                )
            }

        history.workspace.objects.values
            .filterIsInstance<GroupFrame>()
            .sortedBy { it.zIndex }
            .forEach { group ->
                GroupFrameCard(
                    group = group,
                    viewport = viewport,
                    previewDelta = dragPreviews[group.id] ?: Vec2.Zero,
                    selected = group.id in selectedIds,
                    dropTarget = group.id == groupDropPreviewId,
                    reduceMotion = reduceMotion,
                    interactionEnabled = !canvasInteractionBlocked,
                    mutationEnabled = !inputBlocked,
                    onAccessibilitySelect = {
                        selectedIds = hierarchyAwareSelectionAfterObjectTap(
                            selectedIds = selectedIds,
                            objectId = group.id,
                            additive = compactLayout && multiSelectionMode,
                            objectsById = history.workspace.objects,
                        )
                        selectedRelationId = null
                        editingId = null
                        canvasFocusRequester.requestFocus()
                    },
                    onKeyboardFocus = {
                        selectedIds = setOf(group.id)
                        selectedRelationId = null
                        editingId = null
                    },
                    onSelect = { localPosition, additive ->
                        val groupScreenPosition = viewport.worldToScreen(group.transform.position)
                        val relationHit = findRelationAt(
                            screenPosition = groupScreenPosition + localPosition,
                            relations = history.workspace.relations.values,
                            nodes = history.workspace.objects.values.filterIsInstance<TextNode>().associateBy { it.id },
                            viewport = viewport,
                            tolerance = 10f * density,
                        )
                        if (relationHit != null && !(compactLayout && multiSelectionMode)) {
                            selectedIds = emptySet()
                            selectedRelationId = relationHit.id
                        } else {
                            selectedIds = hierarchyAwareSelectionAfterObjectTap(
                                selectedIds = selectedIds,
                                objectId = group.id,
                                additive = additive || (compactLayout && multiSelectionMode),
                                objectsById = history.workspace.objects,
                            )
                            selectedRelationId = null
                        }
                        editingId = null
                        canvasFocusRequester.requestFocus()
                    },
                    onDragPreview = { delta ->
                        val movementRoots = if (group.id in selectedIds) selectedIds else setOf(group.id)
                        val movingIds = movableSelectionObjectIds(history.workspace, movementRoots)
                            .takeIf { group.id in it }
                            .orEmpty()
                        dragPreviews = movingIds.associateWith { delta }
                    },
                    onDragCancel = { dragPreviews = emptyMap() },
                    onDragCommit = { delta ->
                        val movementRoots = if (group.id in selectedIds) selectedIds else setOf(group.id)
                        val movingIds = movableSelectionObjectIds(history.workspace, movementRoots)
                            .takeIf { group.id in it }
                            .orEmpty()
                        dragPreviews = emptyMap()
                        if (movingIds.isEmpty()) {
                            statusMessage = Strings.status.unlockGroupAndAllOf()
                        } else if (delta != Vec2.Zero) {
                            val changes = movingIds.mapNotNull { id ->
                                history.workspace.objectById(id)?.let { movingObject ->
                                    TransformChange(
                                        objectId = id,
                                        expectedVersion = movingObject.version,
                                        before = movingObject.transform,
                                        after = movingObject.transform.copy(
                                            position = movingObject.transform.position + delta,
                                        ),
                                    )
                                }
                            }
                            execute(TransformObjectsOperation("move-group-${group.id.value}", changes))
                        }
                    },
                )
            }

        RelationCanvas(
            relations = history.workspace.relations.values,
            nodes = history.workspace.objects.values.filterIsInstance<TextNode>().associateBy { it.id },
            viewport = viewport,
            density = density,
            dragPreviews = dragPreviews,
            transformPreviews = transformPreviews,
            selectedRelationId = selectedRelationId,
        )

        connectionDragPreview?.let { preview ->
            ConnectionDragPreviewCanvas(preview = preview, density = density)
        }

        history.workspace.relations.values.forEach { relation ->
            val label = relation.label?.takeIf(String::isNotBlank) ?: return@forEach
            val segment = relationSegment(
                relation = relation,
                nodes = history.workspace.objects.values.filterIsInstance<TextNode>().associateBy { it.id },
                viewport = viewport,
                dragPreviews = dragPreviews,
                transformPreviews = transformPreviews,
            ) ?: return@forEach
            RelationLabel(
                label = label,
                midpoint = polylineMidpoint(segment.points),
            )
        }

        (history.workspace.objects.values.filterIsInstance<TextNode>() + listOfNotNull(pendingNewNode))
            .sortedBy { it.zIndex }
            .forEach { node ->
                val previewToken = colorPreviewToken?.takeIf { colorPickerTargets?.contains(node.id) == true }
                TextNodeCard(
                    node = if (previewToken != null) node.copy(colorToken = previewToken) else node,
                    viewport = viewport,
                    previewDelta = dragPreviews[node.id] ?: Vec2.Zero,
                    previewTransform = transformPreviews[node.id],
                    selected = node.id in selectedIds,
                    reduceMotion = reduceMotion,
                    editing = editingId == node.id,
                    interactionEnabled = !canvasInteractionBlocked,
                    mutationEnabled = !inputBlocked,
                    tapSelectedToEdit = !compactLayout,
                    onKeyboardFocus = {
                        selectedRelationId = null
                        selectedIds = setOf(node.id)
                        if (editingId != node.id) editingId = null
                    },
                    onSelect = { additive ->
                        selectedRelationId = null
                        selectedIds = hierarchyAwareSelectionAfterObjectTap(
                            selectedIds = selectedIds,
                            objectId = node.id,
                            additive = additive || (compactLayout && multiSelectionMode),
                            objectsById = history.workspace.objects,
                        )
                        if (editingId != node.id) editingId = null
                    },
                    onStartEditing = {
                        selectedRelationId = null
                        selectedIds = setOf(node.id)
                        editingId = node.id
                    },
                    onDragPreview = preview@{ delta, bypassSnapping ->
                        val selectedMovingIds = if (node.id in selectedIds) selectedIds else setOf(node.id).also {
                            selectedIds = it
                            editingId = null
                        }
                        val movingIds = movableSelectionObjectIds(history.workspace, selectedMovingIds)
                        if (movingIds.isEmpty()) {
                            dragPreviews = emptyMap()
                            alignmentGuides = AlignmentGuides()
                            return@preview
                        }
                        val snapResult = when {
                            bypassSnapping -> AlignmentSnapResult(delta)
                            displaySettings.snapToGrid -> AlignmentSnapResult(
                                gridSnappedSelectionDelta(
                                    anchorPosition = node.transform.position,
                                    rawDelta = delta,
                                    gridSize = GridSize * density,
                                ),
                            )
                            else -> calculateAlignmentSnap(
                                movingTransform = node.transform,
                                otherTransforms = history.workspace.objects.values
                                    .filterIsInstance<TextNode>()
                                    .filter { it.id !in movingIds }
                                    .map { it.transform },
                                rawDelta = delta,
                                threshold = 8f * density / viewport.zoom,
                            )
                        }
                        dragPreviews = movingIds.associateWith { snapResult.delta }
                        alignmentGuides = AlignmentGuides(
                            verticalWorldX = snapResult.verticalWorldX,
                            horizontalWorldY = snapResult.horizontalWorldY,
                        )
                    },
                    onDragCancel = {
                        dragPreviews = emptyMap()
                        alignmentGuides = AlignmentGuides()
                    },
                    onDragCommit = commit@{ delta, bypassSnapping ->
                        val movingIds = movableSelectionObjectIds(
                            history.workspace,
                            if (node.id in selectedIds) selectedIds else setOf(node.id),
                        )
                        val committedDelta = dragPreviews[node.id] ?: delta
                        dragPreviews = emptyMap()
                        alignmentGuides = AlignmentGuides()
                        if (movingIds.isEmpty()) {
                            statusMessage = Strings.status.unlockGroupAndAllOf()
                        } else {
                            val appliedDelta = if (displaySettings.snapToGrid && !bypassSnapping) {
                                gridSnappedSelectionDelta(
                                    anchorPosition = node.transform.position,
                                    rawDelta = committedDelta,
                                    gridSize = GridSize * density,
                                )
                            } else {
                                committedDelta
                            }
                            if (appliedDelta == Vec2.Zero) return@commit
                            val changes = movingIds.mapNotNull { id ->
                                history.workspace.objectById(id)?.let { movingObject ->
                                    val before = movingObject.transform
                                    TransformChange(
                                        objectId = id,
                                        expectedVersion = movingObject.version,
                                        before = before,
                                        after = before.copy(position = before.position + appliedDelta),
                                    )
                                }
                            }
                            val transformOperation = TransformObjectsOperation(
                                operationId = "move-${node.id.value}-${history.workspace.version}",
                                changes = changes,
                            )
                            val reparentNodes = selectedIds
                                .mapNotNull { id -> history.workspace.objectById(id) as? TextNode }
                                .takeIf { selectedNodes ->
                                    selectedNodes.isNotEmpty() &&
                                        selectedNodes.size == selectedIds.size &&
                                        movingIds == selectedIds
                                }
                            val nextParentId = reparentNodes?.let { selectedNodes ->
                                groupDropTargetId(
                                    groups = history.workspace.objects.values.filterIsInstance<GroupFrame>(),
                                    movingTransforms = selectedNodes.map { selectedNode ->
                                        selectedNode.transform.copy(
                                            position = selectedNode.transform.position + appliedDelta,
                                        )
                                    },
                                )
                            }
                            val parentChanges = reparentNodes.orEmpty().mapNotNull { selectedNode ->
                                if (selectedNode.parentId == nextParentId) null else {
                                    ParentChange(
                                        objectId = selectedNode.id,
                                        expectedVersion = selectedNode.version + 1,
                                        before = selectedNode.parentId,
                                        after = nextParentId,
                                    )
                                }
                            }
                            if (parentChanges.isNotEmpty()) {
                                execute(
                                    TransactionOperation(
                                        operationId = "move-reparent-${node.id.value}-${history.workspace.version}",
                                        operations = listOf(
                                            transformOperation,
                                            ReparentObjectsOperation(
                                                operationId = "reparent-after-move-${node.id.value}",
                                                changes = parentChanges,
                                            ),
                                        ),
                                    ),
                                )
                            } else {
                                execute(transformOperation)
                            }
                        }
                    },
                    onTextCommit = { text ->
                        editingId = null
                        if (pendingNewNode?.id == node.id) {
                            val committedNode = commitNewNodeDraft(node, text)
                            pendingNewNode = null
                            if (committedNode == null) {
                                selectedIds = emptySet()
                                statusMessage = if (submissionQueue.isEmpty) {
                                    Strings.status.draftDiscardedAllChangesSaved()
                                } else {
                                    Strings.status.draftDiscardedSavingChanges(submissionQueue.size)
                                }
                            } else {
                                execute(CreateObjectsOperation("create-${node.id.value}", listOf(committedNode)))
                                selectedIds = setOf(node.id)
                            }
                        } else if (text != node.text) {
                            execute(
                                EditTextOperation(
                                    operationId = "edit-${node.id.value}-${history.workspace.version}",
                                    changes = listOf(
                                        TextChange(
                                            objectId = node.id,
                                            expectedVersion = node.version,
                                            before = node.text,
                                            after = text.ifBlank { Strings.content.untitledThought() },
                                        ),
                                    ),
                                ),
                            )
                        }
                        canvasFocusRequester.requestFocus()
                    },
                )
            }

        history.workspace.objects.values.filterIsInstance<MediaNode>()
            .sortedBy { it.zIndex }
            .forEach { node ->
                MediaNodeCard(
                    node = node,
                    asset = workspaceAssets[node.assetId],
                    session = session,
                    mediaRuntime = mediaImportRuntime,
                    reduceMotion = reduceMotion,
                    canvasSize = canvasSize,
                    viewport = viewport,
                    previewDelta = dragPreviews[node.id] ?: Vec2.Zero,
                    previewTransform = transformPreviews[node.id],
                    selected = node.id in selectedIds,
                    interactionEnabled = !canvasInteractionBlocked,
                    mutationEnabled = !inputBlocked,
                    onSelect = { additive ->
                        selectedRelationId = null
                        editingId = null
                        selectedIds = hierarchyAwareSelectionAfterObjectTap(
                            selectedIds = selectedIds,
                            objectId = node.id,
                            additive = additive || (compactLayout && multiSelectionMode),
                            objectsById = history.workspace.objects,
                        )
                    },
                    onDragPreview = { delta ->
                        val roots = if (node.id in selectedIds) selectedIds else setOf(node.id).also {
                            selectedIds = it
                            editingId = null
                        }
                        val movingIds = movableSelectionObjectIds(history.workspace, roots)
                        dragPreviews = movingIds.associateWith { delta }
                    },
                    onDragCancel = { dragPreviews = emptyMap() },
                    onDragCommit = commit@{ delta ->
                        val movingIds = movableSelectionObjectIds(
                            history.workspace,
                            if (node.id in selectedIds) selectedIds else setOf(node.id),
                        )
                        val committedDelta = dragPreviews[node.id] ?: delta
                        dragPreviews = emptyMap()
                        if (movingIds.isEmpty() || committedDelta == Vec2.Zero) return@commit
                        val changes = movingIds.mapNotNull { id ->
                            history.workspace.objectById(id)?.let { movingObject ->
                                TransformChange(
                                    objectId = id,
                                    expectedVersion = movingObject.version,
                                    before = movingObject.transform,
                                    after = movingObject.transform.copy(
                                        position = movingObject.transform.position + committedDelta,
                                    ),
                                )
                            }
                        }
                        execute(
                            TransformObjectsOperation(
                                operationId = "move-media-${node.id.value}-${history.workspace.version}",
                                changes = changes,
                            ),
                        )
                    },
                )
            }

        AlignmentGuideCanvas(alignmentGuides, viewport, density)

        if (editingId == null && !inputBlocked) {
            selectedIds.singleOrNull()
                ?.let(history.workspace::objectById)
                ?.let { it as? TextNode }
                ?.takeUnless { it.locked }
                ?.let { node ->
                    NodeTransformHandles(
                        node = node,
                        objectLabel = node.text,
                        previewTransform = transformPreviews[node.id] ?: node.transform,
                        viewport = viewport,
                        largeTouchTargets = compactLayout,
                        onPreview = { transformPreviews = mapOf(node.id to it) },
                        onCancel = { transformPreviews = emptyMap() },
                        onResizeCommit = { after ->
                            transformPreviews = emptyMap()
                            if (after != node.transform) {
                                execute(
                                    TransformObjectsOperation(
                                        operationId = "resize-handle-${node.id.value}-${history.workspace.version}",
                                        changes = listOf(
                                            TransformChange(node.id, node.version, node.transform, after),
                                        ),
                                    ),
                                )
                            }
                        },
                        onRotateCommit = { after ->
                            transformPreviews = emptyMap()
                            if (after != node.transform) {
                                execute(
                                    TransformObjectsOperation(
                                        operationId = "rotate-handle-${node.id.value}-${history.workspace.version}",
                                        changes = listOf(
                                            TransformChange(node.id, node.version, node.transform, after),
                                        ),
                                    ),
                                )
                            }
                        },
                        onConnectionPreview = { start, end ->
                            val sourceId = node.id
                            val targetId = connectorDropTargetId(
                                nodes = history.workspace.objects.values.filterIsInstance<TextNode>(),
                                sourceId = sourceId,
                                worldPoint = viewport.screenToWorld(end),
                            )
                            connectionDragPreview = ConnectionDragPreview(
                                sourceId = sourceId,
                                startScreen = start,
                                endScreen = end,
                                targetId = targetId,
                            )
                        },
                        onConnectionCancel = { connectionDragPreview = null },
                        onConnectionOpenPicker = {
                            showCommandPalette = true
                            commandPaletteQuery = Strings.palette.connectionSearchQuery()
                            showSchemeLibrary = false
                            showComponentLibrary = false
                            showWorkspaceSwitcher = false
                            showMembersPanel = false
                            showLayersPanel = false
                            showHistoryPanel = false
                            showCompactMenu = false
                        },
                        onConnectionCommit = { end ->
                            val targetId = connectorDropTargetId(
                                nodes = history.workspace.objects.values.filterIsInstance<TextNode>(),
                                sourceId = node.id,
                                worldPoint = viewport.screenToWorld(end),
                            )
                            connectionDragPreview = null
                            val target = targetId?.let(history.workspace::objectById) as? TextNode
                            if (target == null) {
                                statusMessage = Strings.status.dropConnectorOnAnotherThought()
                            } else {
                                connectNodes(node, target, "relates", RelationDirection.Forward)?.let { relationId ->
                                    selectedIds = emptySet()
                                    selectedRelationId = relationId
                                }
                            }
                        },
                    )
                }
            selectedIds.singleOrNull()
                ?.let(history.workspace::objectById)
                ?.let { it as? MediaNode }
                ?.takeUnless { it.locked }
                ?.let { node ->
                    NodeTransformHandles(
                        node = node,
                        objectLabel = node.altText.ifBlank { node.mediaKind.name },
                        connectionEnabled = false,
                        previewTransform = transformPreviews[node.id] ?: node.transform,
                        viewport = viewport,
                        largeTouchTargets = compactLayout,
                        onPreview = { transformPreviews = mapOf(node.id to it) },
                        onCancel = { transformPreviews = emptyMap() },
                        onResizeCommit = { after ->
                            transformPreviews = emptyMap()
                            if (after != node.transform) {
                                execute(
                                    TransformObjectsOperation(
                                        operationId = "resize-media-${node.id.value}-${history.workspace.version}",
                                        changes = listOf(
                                            TransformChange(node.id, node.version, node.transform, after),
                                        ),
                                    ),
                                )
                            }
                        },
                        onRotateCommit = { after ->
                            transformPreviews = emptyMap()
                            if (after != node.transform) {
                                execute(
                                    TransformObjectsOperation(
                                        operationId = "rotate-media-${node.id.value}-${history.workspace.version}",
                                        changes = listOf(
                                            TransformChange(node.id, node.version, node.transform, after),
                                        ),
                                    ),
                                )
                            }
                        },
                        onConnectionPreview = { _, _ -> },
                        onConnectionCancel = {},
                        onConnectionOpenPicker = {},
                        onConnectionCommit = {},
                    )
                }
        }

        val compactInspectorAvailable = compactLayout && editingId == null &&
            (selectedRelationId != null || selectedIds.isNotEmpty())
        val compactModalVisible = compactLayout && !showCommandPalette && (
            showCompactMenu || showWorkspaceSwitcher || showComponentLibrary || showSchemeLibrary ||
                showLayersPanel || showHistoryPanel || showMembersPanel
            )
        LaunchedEffect(compactLayout, compactModalVisible, showCommandPalette) {
            if (compactLayout && (compactModalVisible || showCommandPalette)) {
                compactInspectorExpanded = false
            }
        }
        if (compactModalVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.contentText.copy(alpha = 0.14f))
                    .clickable {
                        showCompactMenu = false
                        showWorkspaceSwitcher = false
                        workspaceDeleteArmed = false
                        showMembersPanel = false
                        memberRemoveArmed = null
                        showComponentLibrary = false
                        libraryDragPreview = null
                        showSchemeLibrary = false
                        showLayersPanel = false
                        showHistoryPanel = false
                        focusManager.clearFocus()
                        canvasFocusRequester.requestFocus()
                    },
            )
        }

        if (shouldShowDesktopCanvasToolbar(canvasSize.width, canvasSize.height, density)) GlassSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScrollWithMouseWheel(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (connectionFailed) {
                    ShellButton(
                        label = Strings.toolbar.retryConnection(),
                        icon = ShellIcon.Retry,
                        accent = true,
                        onClick = { connectionAttempt += 1 },
                    )
                } else {
                    ShellButton(
                        label = Strings.objects.thought(),
                        icon = ShellIcon.Add,
                        accent = true,
                        enabled = !inputBlocked,
                        onClick = ::addTextNodeAtCenter,
                    )
                }
                if (mediaImportRuntime.selectSource != null) {
                    ShellButton(
                        label = if (mediaImportBusy) mediaImportLabel else Strings.media.importMedia(),
                        icon = ShellIcon.Library,
                        enabled = !inputBlocked && workspaceChangeAllowed,
                        onClick = ::startMediaImport,
                    )
                    if (mediaImportBusy) ShellButton(
                        label = Strings.media.cancelImport(),
                        icon = ShellIcon.Close,
                        onClick = { mediaImportJob?.cancel() },
                    )
                }
                ShellButton(
                    label = Strings.toolbar.search(),
                    icon = ShellIcon.Search,
                    accent = showCommandPalette,
                    onClick = {
                        showCommandPalette = true
                        showSchemeLibrary = false
                        showComponentLibrary = false
                        showWorkspaceSwitcher = false
                        showMembersPanel = false
                        showLayersPanel = false
                        showHistoryPanel = false
                        commandPaletteQuery = ""
                    },
                )
                ShellButton(
                    label = Strings.toolbar.layers(),
                    icon = ShellIcon.Layers,
                    accent = showLayersPanel,
                    enabled = session != null && !connectionFailed,
                    onClick = {
                        showLayersPanel = !showLayersPanel
                        if (showLayersPanel) {
                            showHistoryPanel = false
                            showWorkspaceSwitcher = false
                            showMembersPanel = false
                            showComponentLibrary = false
                            showSchemeLibrary = false
                        }
                    },
                )
                ShellButton(
                    label = Strings.toolbar.history(),
                    icon = ShellIcon.History,
                    accent = showHistoryPanel,
                    enabled = session != null && !connectionFailed,
                    onClick = {
                        showHistoryPanel = !showHistoryPanel
                        if (showHistoryPanel) {
                            showLayersPanel = false
                            showWorkspaceSwitcher = false
                            showMembersPanel = false
                            showComponentLibrary = false
                            showSchemeLibrary = false
                        }
                    },
                )
                ShellButton(
                    label = if (workspaceSwitchInProgress) Strings.toolbar.opening() else Strings.toolbar.spaces(),
                    icon = ShellIcon.Workspaces,
                    accent = showWorkspaceSwitcher,
                    enabled = session != null && !connectionFailed && !workspaceSwitchInProgress,
                    onClick = {
                        showWorkspaceSwitcher = !showWorkspaceSwitcher
                        if (showWorkspaceSwitcher) {
                            showMembersPanel = false
                            showSchemeLibrary = false
                            showComponentLibrary = false
                            showLayersPanel = false
                            showHistoryPanel = false
                            workspaceRenameDraft = session?.workspace?.title.orEmpty()
                            workspaceDeleteArmed = false
                        }
                    },
                )
                ShellButton(
                    label = Strings.members.members(),
                    icon = ShellIcon.Members,
                    accent = showMembersPanel,
                    enabled = session != null && !connectionFailed,
                    onClick = {
                        showMembersPanel = !showMembersPanel
                        if (showMembersPanel) {
                            showWorkspaceSwitcher = false
                            showSchemeLibrary = false
                            showComponentLibrary = false
                            showLayersPanel = false
                            showHistoryPanel = false
                            memberRemoveArmed = null
                        }
                    },
                )
                ShellButton(
                    label = Strings.toolbar.library(),
                    icon = ShellIcon.Library,
                    accent = showComponentLibrary,
                    enabled = !inputBlocked,
                    onClick = {
                        showComponentLibrary = !showComponentLibrary
                        if (showComponentLibrary) {
                            showMembersPanel = false
                            showSchemeLibrary = false
                            showWorkspaceSwitcher = false
                            showLayersPanel = false
                            showHistoryPanel = false
                        }
                    },
                )
                ShellButton(
                    label = Strings.toolbar.schemes(),
                    icon = ShellIcon.Schemes,
                    accent = showSchemeLibrary,
                    enabled = !inputBlocked,
                    onClick = {
                        showSchemeLibrary = !showSchemeLibrary
                        if (showSchemeLibrary) {
                            showMembersPanel = false
                            showComponentLibrary = false
                            showWorkspaceSwitcher = false
                            showLayersPanel = false
                            showHistoryPanel = false
                        }
                    },
                )
                ShellButton(
                    label = Strings.palette.undo(),
                    icon = ShellIcon.Undo,
                    enabled = !inputBlocked && history.canUndo,
                    onClick = ::undo,
                )
                ShellButton(
                    label = Strings.palette.redo(),
                    icon = ShellIcon.Redo,
                    enabled = !inputBlocked && history.canRedo,
                    onClick = ::redo,
                )
                ShellButton(
                    label = Strings.toolbar.delete(),
                    icon = ShellIcon.Delete,
                    enabled = !inputBlocked && (selectedIds.isNotEmpty() || selectedRelationId != null),
                    onClick = ::deleteSelection,
                )
                ShellButton(
                    label = Strings.toolbar.zoomMinus(),
                    icon = ShellIcon.ZoomOut,
                    enabled = viewport.zoom > 0.2f,
                    onClick = { zoomCanvas(1f / 1.2f) },
                )
                ShellButton(
                    label = "${(viewport.zoom * 100).roundToInt()}%",
                    icon = ShellIcon.ZoomReset,
                    enabled = viewport.zoom != 1f,
                    onClick = ::resetCanvasZoom,
                )
                ShellButton(
                    label = Strings.toolbar.zoomPlus(),
                    icon = ShellIcon.ZoomIn,
                    enabled = viewport.zoom < 4f,
                    onClick = { zoomCanvas(1.2f) },
                )
                ShellButton(
                    label = Strings.toolbar.fit(),
                    icon = ShellIcon.Fit,
                    onClick = ::fitContent,
                )
                ShellButton(
                    label = if (areaSelectionMode) Strings.toolbar.areaSelectOn() else Strings.toolbar.areaSelect(),
                    icon = ShellIcon.AreaSelect,
                    accent = areaSelectionMode,
                    onClick = {
                        areaSelectionMode = !areaSelectionMode
                        if (!areaSelectionMode) marquee = null
                    },
                )
                ShellButton(
                    label = if (displaySettings.showGrid) Strings.toolbar.gridOn() else Strings.toolbar.gridOff(),
                    icon = ShellIcon.Grid,
                    onClick = { displaySettings = displaySettings.copy(showGrid = !displaySettings.showGrid) },
                )
                ShellButton(
                    label = if (displaySettings.snapToGrid) Strings.toolbar.snapOnBypass() else Strings.toolbar.snapOff(),
                    icon = ShellIcon.Snap,
                    onClick = { displaySettings = displaySettings.copy(snapToGrid = !displaySettings.snapToGrid) },
                )
                ShellButton(
                    label = Strings.toolbar.canvas(),
                    icon = ShellIcon.CanvasStyle,
                    accent = showCanvasBackgroundPicker,
                    onClick = { openCanvasBackgroundPicker() },
                )
            }
        }

        if (shouldShowCompactMenuEntry(compactLayout, compactModalVisible, showCommandPalette)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 10.dp, top = 8.dp),
            ) {
                GlassSurface {
                    ShellButton(
                        label = Strings.toolbar.menu(),
                        icon = ShellIcon.Menu,
                        accent = multiSelectionMode,
                        showLabel = false,
                        onClick = { showCompactMenu = true },
                    )
                }
            }
        }

        if (compactLayout && showCompactMenu) {
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(inspectorWidth)
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ButtonGap),
                ) {
                    BasicText(
                        text = Strings.toolbar.canvasMenu(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    ShellButton(
                        label = if (multiSelectionMode) Strings.common.multiSelectOn() else Strings.common.multiSelectMode(),
                        icon = ShellIcon.AreaSelect,
                        accent = multiSelectionMode,
                        enabled = !canvasInteractionBlocked,
                        onClick = {
                            multiSelectionMode = !multiSelectionMode
                            if (multiSelectionMode) {
                                areaSelectionMode = false
                                marquee = null
                                selectedRelationId = null
                                statusMessage = Strings.common.multiSelectModeTapNodes()
                            } else {
                                statusMessage = Strings.common.multiSelectModeOff()
                            }
                            showCompactMenu = false
                            canvasFocusRequester.requestFocus()
                        },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                    val rootSelectionIds = topLevelSelectionIds(history.workspace.objects.values)
                    if (multiSelectionMode && rootSelectionIds.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                            verticalArrangement = Arrangement.spacedBy(ButtonGap),
                        ) {
                            if (selectedIds.size >= 2) {
                                ShellButton(
                                    label = Strings.palette.groupSelection(),
                                    icon = ShellIcon.Group,
                                    enabled = !inputBlocked,
                                    onClick = {
                                        showCompactMenu = false
                                        groupSelection()
                                        canvasFocusRequester.requestFocus()
                                    },
                                )
                            }
                            if (selectedIds != rootSelectionIds) {
                                ShellButton(
                                    label = Strings.common.selectAll(),
                                    icon = ShellIcon.AreaSelect,
                                    onClick = {
                                        selectedIds = rootSelectionIds
                                        selectedRelationId = null
                                        editingId = null
                                        statusMessage = Strings.common.selectedObjects(selectedIds.size)
                                        showCompactMenu = false
                                        canvasFocusRequester.requestFocus()
                                    },
                                )
                            }
                            if (selectedIds.isNotEmpty()) {
                                ShellButton(
                                    label = Strings.common.clearSelection(),
                                    onClick = {
                                        selectedIds = emptySet()
                                        selectedRelationId = null
                                        editingId = null
                                        showCompactMenu = false
                                        canvasFocusRequester.requestFocus()
                                    },
                                )
                            }
                        }
                    }
                    val menuSectionWidth = inspectorWidth - 16.dp
                    fun isMenuSectionCollapsed(section: PanelSection): Boolean =
                        section.token in collapsedPanelSections
                    CollapsibleSectionHeader(
                        label = Strings.common.createAndNavigate(),
                        collapsed = isMenuSectionCollapsed(PanelSection.CreateAndNavigate),
                        width = menuSectionWidth,
                        onToggle = { togglePanelSection(PanelSection.CreateAndNavigate) },
                    )
                    if (!isMenuSectionCollapsed(PanelSection.CreateAndNavigate)) {
                    FlowRow(
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                        verticalArrangement = Arrangement.spacedBy(ButtonGap),
                    ) {
                        ShellButton(
                            label = if (connectionFailed) Strings.toolbar.retryConnection() else Strings.content.newThought(),
                            icon = if (connectionFailed) ShellIcon.Retry else ShellIcon.Add,
                            enabled = connectionFailed || !inputBlocked,
                            onClick = {
                                showCompactMenu = false
                                if (connectionFailed) connectionAttempt += 1 else addTextNodeAtCenter()
                            },
                        )
                        ShellButton(
                            label = Strings.inspector.paste(),
                            icon = ShellIcon.Paste,
                            enabled = !inputBlocked,
                            onClick = {
                                showCompactMenu = false
                                pasteFromClipboard()
                                canvasFocusRequester.requestFocus()
                            },
                        )
                        if (mediaImportRuntime.selectSource != null) {
                            ShellButton(
                                label = if (mediaImportBusy) mediaImportLabel else Strings.media.importMedia(),
                                icon = ShellIcon.Library,
                                enabled = !inputBlocked && workspaceChangeAllowed,
                                onClick = ::startMediaImport,
                            )
                            if (mediaImportBusy) ShellButton(
                                label = Strings.media.cancelImport(),
                                icon = ShellIcon.Close,
                                onClick = { mediaImportJob?.cancel() },
                            )
                        }
                    }
                    listOf(
                        CompactMenuItem(Strings.toolbar.search(), ShellIcon.Search) {
                            showCommandPalette = true
                            commandPaletteQuery = ""
                        },
                        CompactMenuItem(Strings.toolbar.layers(), ShellIcon.Layers) { showLayersPanel = true },
                        CompactMenuItem(Strings.toolbar.history(), ShellIcon.History) { showHistoryPanel = true },
                        CompactMenuItem(Strings.toolbar.spaces(), ShellIcon.Workspaces) {
                            showWorkspaceSwitcher = true
                            workspaceRenameDraft = session?.workspace?.title.orEmpty()
                            workspaceDeleteArmed = false
                        },
                        CompactMenuItem(Strings.members.members(), ShellIcon.Members) {
                            showMembersPanel = true
                            memberRemoveArmed = null
                        },
                        CompactMenuItem(Strings.library.objectLibrary(), ShellIcon.Library) { showComponentLibrary = true },
                        CompactMenuItem(Strings.toolbar.quickSchemes(), ShellIcon.Schemes) { showSchemeLibrary = true },
                    ).forEach { (label, icon, action) ->
                        ShellButton(
                            label = label,
                            icon = icon,
                            enabled = session != null && !connectionFailed,
                            onClick = {
                                showCompactMenu = false
                                showLayersPanel = false
                                showHistoryPanel = false
                                showWorkspaceSwitcher = false
                                showMembersPanel = false
                                showComponentLibrary = false
                                showSchemeLibrary = false
                                action()
                            },
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                    }
                    }
                    CollapsibleSectionHeader(
                        label = Strings.common.historyActions(),
                        collapsed = isMenuSectionCollapsed(PanelSection.History),
                        width = menuSectionWidth,
                        onToggle = { togglePanelSection(PanelSection.History) },
                    )
                    if (!isMenuSectionCollapsed(PanelSection.History)) {
                    FlowRow(
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                        verticalArrangement = Arrangement.spacedBy(ButtonGap),
                    ) {
                        ShellButton(
                            label = Strings.palette.undo(),
                            icon = ShellIcon.Undo,
                            enabled = !inputBlocked && history.canUndo,
                            onClick = ::undo,
                        )
                        ShellButton(
                            label = Strings.palette.redo(),
                            icon = ShellIcon.Redo,
                            enabled = !inputBlocked && history.canRedo,
                            onClick = ::redo,
                        )
                    }
                    }
                    CollapsibleSectionHeader(
                        label = Strings.common.canvasView(),
                        collapsed = isMenuSectionCollapsed(PanelSection.CanvasView),
                        width = menuSectionWidth,
                        onToggle = { togglePanelSection(PanelSection.CanvasView) },
                    )
                    if (!isMenuSectionCollapsed(PanelSection.CanvasView)) {
                    FlowRow(
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                        verticalArrangement = Arrangement.spacedBy(ButtonGap),
                    ) {
                        ShellButton(
                            label = Strings.palette.zoomOut(),
                            icon = ShellIcon.ZoomOut,
                            showLabel = false,
                            enabled = viewport.zoom > 0.2f,
                            onClick = { zoomCanvas(1f / 1.2f) },
                        )
                        ShellButton(
                            label = "${(viewport.zoom * 100).roundToInt()}%",
                            icon = null,
                            enabled = viewport.zoom != 1f,
                            onClick = ::resetCanvasZoom,
                        )
                        ShellButton(
                            label = Strings.palette.zoomIn(),
                            icon = ShellIcon.ZoomIn,
                            showLabel = false,
                            enabled = viewport.zoom < 4f,
                            onClick = { zoomCanvas(1.2f) },
                        )
                        ShellButton(
                            label = Strings.palette.fitContent(),
                            icon = ShellIcon.Fit,
                            showLabel = false,
                            onClick = ::fitContent,
                        )
                    }
                    ShellButton(
                        label = if (areaSelectionMode) Strings.toolbar.areaSelectOn() else Strings.toolbar.areaSelect(),
                        icon = ShellIcon.AreaSelect,
                        accent = areaSelectionMode,
                        onClick = {
                            areaSelectionMode = !areaSelectionMode
                            if (areaSelectionMode) multiSelectionMode = false
                            if (!areaSelectionMode) marquee = null
                        },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                    ShellButton(
                        label = if (displaySettings.showGrid) Strings.toolbar.gridOn() else Strings.toolbar.gridOff(),
                        icon = ShellIcon.Grid,
                        onClick = { displaySettings = displaySettings.copy(showGrid = !displaySettings.showGrid) },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                    ShellButton(
                        label = if (displaySettings.snapToGrid) Strings.toolbar.snapOn() else Strings.toolbar.snapOff(),
                        icon = ShellIcon.Snap,
                        onClick = { displaySettings = displaySettings.copy(snapToGrid = !displaySettings.snapToGrid) },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                    ShellButton(
                        label = Strings.toolbar.changeCanvasBackground(),
                        icon = ShellIcon.CanvasStyle,
                        onClick = {
                            showCompactMenu = false
                            openCanvasBackgroundPicker()
                        },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                    }
                    ShellButton(
                        label = Strings.common.close(),
                        icon = ShellIcon.Close,
                        onClick = {
                            showCompactMenu = false
                            canvasFocusRequester.requestFocus()
                        },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                }
            }
        }

        if (showLayersPanel) {
            val layerEntries = buildLayerTree(history.workspace.objects.values)
            val selectedLayerObject = selectedIds.singleOrNull()?.let(history.workspace::objectById)
            fun canMoveSelectedLayer(move: LayerMove): Boolean = when (selectedLayerObject) {
                is TextNode -> layerZIndexUpdates(
                    history.workspace.objects.values.filterIsInstance<TextNode>(),
                    setOf(selectedLayerObject.id),
                    move,
                ).isNotEmpty()

                is GroupFrame -> layerZIndexUpdates(
                    history.workspace.objects.values.filterIsInstance<GroupFrame>(),
                    setOf(selectedLayerObject.id),
                    move,
                ).isNotEmpty()

                is MediaNode -> layerZIndexUpdates(
                    history.workspace.objects.values.filterIsInstance<MediaNode>(),
                    setOf(selectedLayerObject.id),
                    move,
                ).isNotEmpty()

                else -> false
            }
            fun moveSelectedLayer(move: LayerMove) {
                when (selectedLayerObject) {
                    is TextNode -> reorderSelectedNodeLayers(move)
                    is GroupFrame -> reorderSelectedGroupLayers(move)
                    is MediaNode -> reorderSelectedMediaLayers(move)
                    else -> Unit
                }
            }
            GlassSurface(
                modifier = if (compactLayout) {
                    Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                } else {
                    Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(inspectorWidth)
                        .heightIn(max = if (compactLayout) 520.dp else 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ButtonGap),
                ) {
                    BasicText(
                        text = Strings.toolbar.layers(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    if (layerEntries.isEmpty()) {
                        BasicText(
                            text = Strings.layers.createThoughtOrGroupTo(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                        )
                    }
                    layerEntries.forEach { entry ->
                        val prefix = "  ".repeat(entry.depth)
                        val kind = when (entry.kind) {
                            LayerObjectKind.Group -> Strings.objects.group()
                            LayerObjectKind.Thought -> Strings.objects.thought()
                            LayerObjectKind.Media -> Strings.objects.media()
                        }
                        ShellButton(
                            label = "$prefix${if (entry.depth > 0) "↳ " else ""}$kind • ${entry.title}" +
                                if (entry.locked) " • ${Strings.common.locked()}" else "",
                            accent = entry.objectId in selectedIds,
                            onClick = {
                                selectedIds = setOf(entry.objectId)
                                selectedRelationId = null
                                editingId = null
                            },
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                    }
                    selectedLayerObject?.let { canvasObject ->
                        BasicText(
                            text = Strings.layers.selectedZ(canvasObject.zIndex) +
                                if (canvasObject.locked) " • ${Strings.common.locked()}" else "",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                        ShellButton(
                            label = Strings.layers.centerSelected(),
                            onClick = {
                                val center = canvasObject.transform.position + Vec2(
                                    canvasObject.transform.size.width / 2f,
                                    canvasObject.transform.size.height / 2f,
                                )
                                val screenCenter = Vec2(canvasSize.width / 2f, canvasSize.height / 2f)
                                viewport = viewport.copy(pan = screenCenter - center * viewport.zoom)
                                if (compactLayout) showLayersPanel = false
                            },
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                        listOf(
                            LayerMove.Forward to Strings.layers.bringForward(),
                            LayerMove.Backward to Strings.layers.sendBackward(),
                            LayerMove.Front to Strings.layers.bringToFront(),
                            LayerMove.Back to Strings.layers.sendToBack(),
                        ).forEach { (move, label) ->
                            ShellButton(
                                label = label,
                                icon = when (move) {
                                    LayerMove.Forward -> ShellIcon.Forward
                                    LayerMove.Backward -> ShellIcon.Back
                                    else -> null
                                },
                                enabled = !inputBlocked && !canvasObject.locked && canMoveSelectedLayer(move),
                                onClick = { moveSelectedLayer(move) },
                                modifier = Modifier.width(inspectorWidth - 16.dp),
                            )
                        }
                    }
                    ShellButton(
                        label = Strings.common.close(),
                        icon = ShellIcon.Close,
                        onClick = {
                            showLayersPanel = false
                            canvasFocusRequester.requestFocus()
                        },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                }
            }
        }

        if (showHistoryPanel) {
            GlassSurface(
                modifier = if (compactLayout) {
                    Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                } else {
                    Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(inspectorWidth)
                        .heightIn(max = if (compactLayout) 520.dp else 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ButtonGap),
                ) {
                    BasicText(
                        text = Strings.history.localHistory(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    BasicText(
                        text = Strings.history.workspaceVersion(session?.workspaceVersion ?: 0) + " • " +
                            Strings.history.pendingSaves(submissionQueue.size),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                    )
                    BasicText(
                        text = Strings.history.historyIsLocalToThis(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = TextStyle(color = colors.contentMuted, fontSize = 10.sp),
                    )
                    FlowRow(
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                        verticalArrangement = Arrangement.spacedBy(ButtonGap),
                    ) {
                        ShellButton(
                            label = Strings.palette.undo(),
                            icon = ShellIcon.Undo,
                            enabled = !inputBlocked && history.canUndo,
                            onClick = ::undo,
                        )
                        ShellButton(
                            label = Strings.palette.redo(),
                            icon = ShellIcon.Redo,
                            enabled = !inputBlocked && history.canRedo,
                            onClick = ::redo,
                        )
                    }
                    BasicText(
                        text = Strings.history.availableUndo(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    if (!history.canUndo) {
                        BasicText(
                            text = Strings.history.noLocalOperationToUndo(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    history.undoOperations.asReversed().take(30).forEachIndexed { index, operation ->
                        BasicText(
                            text = "${index + 1}. ${describeHistoryOperation(operation, HistoryDirection.Undo)}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    BasicText(
                        text = Strings.history.availableRedo(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    if (!history.canRedo) {
                        BasicText(
                            text = Strings.history.noLocalOperationToRedo(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    history.redoOperations.asReversed().take(30).forEachIndexed { index, operation ->
                        BasicText(
                            text = "${index + 1}. ${describeHistoryOperation(operation, HistoryDirection.Redo)}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    BasicText(
                        text = Strings.history.recentServerActivity(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    ShellButton(
                        label = if (activityLoading) Strings.history.loadingActivity() else Strings.history.refreshActivity(),
                        icon = if (activityLoading) ShellIcon.History else ShellIcon.Retry,
                        enabled = !activityLoading && session != null && !connectionFailed,
                        onClick = { activityRefreshAttempt += 1 },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                    activityError?.let { error ->
                        BasicText(
                            text = Strings.history.couldNotLoadActivity(error),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = TextStyle(color = colors.danger, fontSize = 10.sp),
                        )
                    }
                    if (!activityLoading && activityError == null && recentWorkspaceActivity.isEmpty()) {
                        BasicText(
                            text = Strings.history.noCommittedOperationYet(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    recentWorkspaceActivity.asReversed().forEach { activity ->
                        val actor = if (activity.actorId == session?.userId) {
                            Strings.common.you()
                        } else {
                            Strings.common.user(activity.actorId.take(8))
                        }
                        BasicText(
                            text = "#${activity.serverSeq} • ${describeServerOperationType(activity.operationType)}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            style = TextStyle(
                                color = colors.contentText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        BasicText(
                            text = "$actor • v${activity.workspaceVersion} • " +
                                activity.committedAt.replace('T', ' ').take(16),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 9.sp),
                        )
                    }
                    ShellButton(
                        label = Strings.common.close(),
                        icon = ShellIcon.Close,
                        onClick = {
                            showHistoryPanel = false
                            canvasFocusRequester.requestFocus()
                        },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                }
            }
        }

        if (showWorkspaceSwitcher) {
            GlassSurface(
                modifier = if (compactLayout) {
                    Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                } else {
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 18.dp)
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(inspectorWidth)
                        .heightIn(max = if (compactLayout) 320.dp else 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ButtonGap),
                ) {
                    BasicText(
                        text = Strings.workspaces.workspaces(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    workspaceSummaries
                        .sortedWith(compareBy<WorkspaceSummary>({ it.title.lowercase() }, { it.id.value }))
                        .forEach { summary ->
                            val current = summary.id == session?.workspace?.id
                            ShellButton(
                                label = buildString {
                                    if (current) append("✓ ")
                                    append(summary.title.take(40))
                                    append(" • ")
                                    append(workspaceRoleLabel(summary.role))
                                },
                                accent = current,
                                enabled = current || workspaceChangeAllowed,
                                onClick = { switchWorkspace(summary) },
                                modifier = Modifier.width(inspectorWidth - 16.dp),
                            )
                        }
                    BasicText(
                        text = Strings.workspaces.currentWorkspace(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                    )
                    BasicTextField(
                        value = workspaceRenameDraft,
                        onValueChange = {
                            workspaceRenameDraft = it.take(200)
                            workspaceDeleteArmed = false
                        },
                        enabled = currentWorkspaceCanRename && workspaceChangeAllowed,
                        modifier = Modifier
                            .width(inspectorWidth - 16.dp)
                            .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                            .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                            .onFocusChanged { workspaceFormEditing = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                    renameCurrentWorkspace()
                                    true
                                } else {
                                    false
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        textStyle = TextStyle(
                            color = if (currentWorkspaceCanRename) colors.contentText else colors.contentMuted,
                            fontSize = 12.sp,
                        ),
                        cursorBrush = SolidColor(colors.selection),
                        singleLine = true,
                    )
                    ShellButton(
                        label = if (currentWorkspaceCanRename) Strings.workspaces.renameCurrent() else Strings.workspaces.readOnlyWorkspace(),
                        icon = if (currentWorkspaceCanRename) ShellIcon.Rename else ShellIcon.Workspaces,
                        enabled = currentWorkspaceCanRename && workspaceChangeAllowed &&
                            workspaceRenameDraft.trim().isNotEmpty() &&
                            workspaceRenameDraft.trim() != session?.workspace?.title,
                        onClick = ::renameCurrentWorkspace,
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                    if (currentWorkspaceCanDelete) {
                        ShellButton(
                            label = if (workspaceDeleteArmed) {
                                Strings.workspaces.confirmDelete(session?.workspace?.title.orEmpty().take(28))
                            } else {
                                Strings.workspaces.deleteCurrentWorkspace()
                            },
                            icon = ShellIcon.Delete,
                            accent = workspaceDeleteArmed,
                            enabled = workspaceChangeAllowed,
                            onClick = ::deleteCurrentWorkspace,
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                    }
                    BasicText(
                        text = if (workspaceChangeAllowed) {
                            Strings.workspaces.createAnotherThinkingSpace()
                        } else {
                            Strings.workspaces.finishEditingAndWaitFor()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                    )
                    BasicTextField(
                        value = workspaceNameDraft,
                        onValueChange = { workspaceNameDraft = it.take(200) },
                        modifier = Modifier
                            .width(inspectorWidth - 16.dp)
                            .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                            .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                            .onFocusChanged { workspaceFormEditing = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                    createWorkspace()
                                    true
                                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                    showWorkspaceSwitcher = false
                                    focusManager.clearFocus()
                                    canvasFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                        cursorBrush = SolidColor(colors.selection),
                        singleLine = true,
                    )
                    ShellButton(
                        label = Strings.workspaces.createAndOpen(),
                        icon = ShellIcon.Add,
                        accent = true,
                        enabled = workspaceChangeAllowed && workspaceNameDraft.trim().isNotEmpty(),
                        onClick = ::createWorkspace,
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                    ShellButton(
                        label = Strings.common.close(),
                        icon = ShellIcon.Close,
                        onClick = {
                            showWorkspaceSwitcher = false
                            workspaceDeleteArmed = false
                            focusManager.clearFocus()
                            canvasFocusRequester.requestFocus()
                        },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                }
            }
        }

        if (showMembersPanel) {
            val activeSession = session
            GlassSurface(
                modifier = if (compactLayout) {
                    Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                } else {
                    Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(inspectorWidth)
                        .heightIn(max = if (compactLayout) 520.dp else 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ButtonGap),
                ) {
                    BasicText(
                        text = Strings.members.workspaceMembers(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    activeSession?.let { opened ->
                        BasicText(
                            text = Strings.members.yourUserId(opened.userId),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 9.sp),
                        )
                        ShellButton(
                            label = Strings.members.copyMyUserId(),
                            icon = ShellIcon.Copy,
                            onClick = {
                                clipboardManager.setText(AnnotatedString(opened.userId))
                                statusMessage = Strings.members.userIdCopied()
                            },
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                    }
                    if (membersLoading) {
                        BasicText(
                            text = Strings.members.loadingMembers(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    membersError?.let { error ->
                        BasicText(
                            text = Strings.members.couldNotLoadMembers(error.take(160)),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.danger, fontSize = 10.sp),
                        )
                        ShellButton(
                            label = Strings.members.retryMembers(),
                            icon = ShellIcon.Retry,
                            enabled = !membersLoading,
                            onClick = { membersRefreshAttempt += 1 },
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                    }
                    workspaceMembers.forEach { member ->
                        BasicText(
                            text = "${member.displayName} • ${workspaceRoleLabel(member.role.token)}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                            style = TextStyle(
                                color = colors.contentText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        BasicText(
                            text = member.userId,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 9.sp),
                        )
                        if (currentWorkspaceCanManageMembers && member.role != WorkspaceMemberRole.Owner) {
                            FlowRow(
                                modifier = Modifier.width(inspectorWidth - 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                                verticalArrangement = Arrangement.spacedBy(ButtonGap),
                            ) {
                                ShellButton(
                                    label = Strings.members.editAccess(),
                                    enabled = !memberMutationInProgress,
                                    onClick = {
                                        memberUserIdDraft = member.userId
                                        memberRoleDraft = member.role
                                        memberRemoveArmed = null
                                    },
                                )
                                ShellButton(
                                    label = if (memberRemoveArmed == member.userId) {
                                        Strings.members.confirmRemove()
                                    } else {
                                        Strings.members.removeAccess()
                                    },
                                    icon = ShellIcon.Delete,
                                    accent = memberRemoveArmed == member.userId,
                                    enabled = !memberMutationInProgress,
                                    onClick = { removeWorkspaceMember(member) },
                                )
                            }
                        }
                    }
                    if (currentWorkspaceCanManageMembers) {
                        BasicText(
                            text = Strings.members.addOrUpdateAccess(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                        BasicTextField(
                            value = memberUserIdDraft,
                            onValueChange = {
                                memberUserIdDraft = it.take(36)
                                memberRemoveArmed = null
                            },
                            enabled = !memberMutationInProgress,
                            modifier = Modifier
                                .width(inspectorWidth - 16.dp)
                                .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                                .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                                .onFocusChanged { workspaceFormEditing = it.isFocused }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            textStyle = TextStyle(color = colors.contentText, fontSize = 11.sp),
                            cursorBrush = SolidColor(colors.selection),
                            singleLine = true,
                        )
                        BasicText(
                            text = Strings.members.pasteAnotherBoarderlessUserId(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 9.sp),
                        )
                        FlowRow(
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                            verticalArrangement = Arrangement.spacedBy(ButtonGap),
                        ) {
                            assignableWorkspaceMemberRoles.forEach { role ->
                                ShellButton(
                                    label = workspaceRoleLabel(role.token),
                                    accent = memberRoleDraft == role,
                                    enabled = !memberMutationInProgress,
                                    onClick = { memberRoleDraft = role },
                                )
                            }
                        }
                        ShellButton(
                            label = if (memberMutationInProgress) Strings.members.updatingMember() else Strings.members.saveMemberAccess(),
                            icon = ShellIcon.Members,
                            accent = true,
                            enabled = !memberMutationInProgress &&
                                normalizedMemberUserId(memberUserIdDraft) != null &&
                                normalizedMemberUserId(memberUserIdDraft) != activeSession?.userId?.lowercase(),
                            onClick = ::saveWorkspaceMember,
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                    } else {
                        BasicText(
                            text = Strings.members.onlyOwnerCanChangeMember(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 10.sp),
                        )
                    }
                    ShellButton(
                        label = Strings.common.close(),
                        icon = ShellIcon.Close,
                        onClick = {
                            showMembersPanel = false
                            memberRemoveArmed = null
                            focusManager.clearFocus()
                            canvasFocusRequester.requestFocus()
                        },
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                    )
                }
            }
        }

        if (showComponentLibrary) {
            ComponentLibrary(
                entries = libraryComponents.map(BuiltInComponent::entry),
                workspaceMediaContent = {
                    MediaAssetLibraryContent(session, workspaceAssets.values, history.workspace.objects.values,
                        mediaImportRuntime, workspaceAssetsLoading, workspaceAssetsUnavailable,
                        !inputBlocked && workspaceChangeAllowed && !connectionFailed,
                        onRefresh = { workspaceAssetsRefreshAttempt++ }, onInsert = ::insertStoredMedia)
                },
                modifier = Modifier
                    .then(
                        if (compactLayout) {
                            Modifier
                                .align(Alignment.Center)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(12.dp)
                        } else {
                            Modifier.align(Alignment.CenterStart).padding(start = 18.dp)
                        },
                    ),
                onInsert = { entry ->
                    insertLibraryComponent(
                        entry,
                        Vec2(canvasSize.width / 2f, canvasSize.height / 2f),
                    )
                    if (compactLayout) showComponentLibrary = false
                },
                onDragStart = { entry, position ->
                    libraryDragPreview = LibraryDragPreview(entry.title, position)
                },
                onDragMove = { entry, position ->
                    libraryDragPreview = LibraryDragPreview(entry.title, position)
                },
                onDragEnd = ::finishLibraryDrag,
                onDragCancel = {
                    libraryDragPreview = null
                    statusMessage = Strings.status.componentDragCancelled()
                },
                onDismiss = {
                    showComponentLibrary = false
                    canvasFocusRequester.requestFocus()
                },
            )
        }

        if (showSchemeLibrary) {
            val selectedScheme = selectedSchemeId?.let { id -> schemes.firstOrNull { it.id == id } }
            GlassSurface(
                modifier = if (compactLayout) {
                    Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                } else {
                    Modifier.align(Alignment.CenterStart).padding(start = 18.dp)
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(276.dp)
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(ButtonGap),
                ) {
                    BasicText(
                        text = Strings.toolbar.quickSchemes(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = TextStyle(
                            color = colors.contentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    if (schemes.isEmpty()) {
                        BasicText(
                            text = Strings.schemes.selectObjectsAndChooseSave(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                        )
                    }
                    schemes.asReversed().forEach { scheme ->
                        val preview = runCatching {
                            ClipboardJson.decodeFromString<ClipboardPayload>(scheme.payload)
                        }.getOrNull()
                        val summary = preview?.let {
                            val objectCount = it.nodes.size + it.groups.size
                            val linkCount = it.relations.size
                            Strings.schemes.objects(objectCount) + " • " +
                                Strings.schemes.links(linkCount) + " • v${scheme.schemaVersion}"
                        }
                        ShellButton(
                            label = scheme.name,
                            accent = scheme.id == selectedSchemeId,
                            onClick = {
                                selectedSchemeId = scheme.id
                                schemeNameDraft = scheme.name
                            },
                        )
                        BasicText(
                            text = if (preview == null) {
                                Strings.schemes.unsupportedSchemeSchema(scheme.schemaVersion)
                            } else {
                                summary.orEmpty()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 10.sp),
                        )
                    }
                    selectedScheme?.let { scheme ->
                        runCatching {
                            ClipboardJson.decodeFromString<ClipboardPayload>(scheme.payload)
                        }.getOrNull()?.let { payload ->
                            QuickSchemePreview(
                                payload = payload,
                                modifier = Modifier
                                    .width(256.dp)
                                    .height(116.dp),
                            )
                        }
                        BasicTextField(
                            value = schemeNameDraft,
                            onValueChange = { schemeNameDraft = it.take(80) },
                            modifier = Modifier
                                .width(256.dp)
                                .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                                .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                                .onFocusChanged { inspectorTextEditing = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        renameSelectedScheme()
                                        focusManager.clearFocus()
                                        canvasFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                            cursorBrush = SolidColor(colors.selection),
                            singleLine = true,
                        )
                        ShellButton(
                            label = Strings.schemes.insert(),
                            enabled = !inputBlocked,
                            onClick = {
                                insertScheme(scheme)
                                if (compactLayout) showSchemeLibrary = false
                            },
                        )
                        ShellButton(
                            label = Strings.schemes.rename(),
                            icon = ShellIcon.Rename,
                            enabled = schemeNameDraft.trim().isNotEmpty() && schemeNameDraft.trim() != scheme.name,
                            onClick = ::renameSelectedScheme,
                        )
                        ShellButton(
                            label = Strings.schemes.deleteScheme(),
                            icon = ShellIcon.Delete,
                            onClick = ::deleteSelectedScheme,
                        )
                    }
                    if (compactLayout) {
                        ShellButton(
                            label = Strings.common.close(),
                            icon = ShellIcon.Close,
                            onClick = {
                                showSchemeLibrary = false
                                canvasFocusRequester.requestFocus()
                            },
                        )
                    }
                }
            }
        }


        (if (
            !compactLayout && (
                showSchemeLibrary || showComponentLibrary || showWorkspaceSwitcher || showMembersPanel ||
                    showLayersPanel || showHistoryPanel || showCompactMenu
                )
        ) null else selectedRelationId)
            ?.let(history.workspace::relationById)
            ?.let { relation ->
                var labelDraft by remember(relation.id, relation.version) {
                    mutableStateOf(relation.label.orEmpty())
                }
                GlassSurface(
                    modifier = if (compactLayout) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 18.dp)
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .width(if (compactLayout) compactBottomSheetWidth else inspectorWidth)
                            .then(
                                if (compactLayout && !compactInspectorExpanded) {
                                    Modifier.height(64.dp).clip(RoundedCornerShape(18.dp))
                                } else {
                                    Modifier
                                        .heightIn(max = if (compactLayout) compactInspectorMaxHeight else 520.dp)
                                        .verticalScroll(rememberScrollState())
                                },
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(ButtonGap),
                    ) {
                        val relationTitle = relation.intent?.let(::relationIntentLabel) ?: Strings.objects.connection()
                        if (compactLayout) {
                            CompactBottomSheetHeader(
                                title = relationTitle,
                                expanded = compactInspectorExpanded,
                                width = compactBottomSheetWidth - 16.dp,
                                density = density,
                                onExpandedChange = { compactInspectorExpanded = it },
                            )
                        } else {
                            BasicText(
                                text = relationTitle,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = TextStyle(
                                    color = colors.contentMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                        val relationSectionWidth = if (compactLayout) {
                            compactBottomSheetWidth - 16.dp
                        } else {
                            inspectorWidth - 16.dp
                        }
                        fun isRelationSectionCollapsed(section: InspectorSection): Boolean =
                            section.token in collapsedInspectorSections
                        CollapsibleSectionHeader(
                            label = Strings.common.connectionProperties(),
                            collapsed = isRelationSectionCollapsed(InspectorSection.Content),
                            width = relationSectionWidth,
                            onToggle = { toggleInspectorSection(InspectorSection.Content) },
                        )
                        if (!isRelationSectionCollapsed(InspectorSection.Content)) {
                        FlowRow(
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                            verticalArrangement = Arrangement.spacedBy(ButtonGap),
                        ) {
                            listOf(
                                "relates" to Strings.objects.relates(),
                                "supports" to Strings.objects.supports(),
                                "conflicts" to Strings.objects.conflicts(),
                            ).forEach { (intent, label) ->
                                ShellButton(
                                    label = label,
                                    enabled = !inputBlocked,
                                    accent = relation.intent == intent,
                                    onClick = { updateSelectedRelation { it.copy(intent = intent) } },
                                )
                            }
                        }
                        FlowRow(
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                            verticalArrangement = Arrangement.spacedBy(ButtonGap),
                        ) {
                            listOf(
                                RelationDirection.Forward to "→",
                                RelationDirection.Backward to "←",
                                RelationDirection.Both to "↔",
                            ).forEach { (direction, label) ->
                                ShellButton(
                                    label = label,
                                    enabled = !inputBlocked,
                                    accent = relation.direction == direction,
                                    onClick = { updateSelectedRelation { it.copy(direction = direction) } },
                                )
                            }
                        }
                        BasicTextField(
                            value = labelDraft,
                            onValueChange = { labelDraft = it.take(500) },
                            modifier = Modifier
                                .width(inspectorWidth - 16.dp)
                                .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                                .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                                .onFocusChanged { inspectorTextEditing = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        val nextLabel = labelDraft.trim().ifBlank { null }
                                        updateSelectedRelation { it.copy(label = nextLabel) }
                                        focusManager.clearFocus()
                                        canvasFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                            cursorBrush = SolidColor(colors.selection),
                            singleLine = true,
                        )
                        ShellButton(
                            label = Strings.inspector.saveLabel(),
                            enabled = !inputBlocked && labelDraft.trim().ifBlank { null } != relation.label,
                            onClick = {
                                val nextLabel = labelDraft.trim().ifBlank { null }
                                updateSelectedRelation { it.copy(label = nextLabel) }
                            },
                        )
                        }
                        CollapsibleSectionHeader(
                            label = Strings.common.actions(),
                            collapsed = isRelationSectionCollapsed(InspectorSection.Reuse),
                            width = relationSectionWidth,
                            onToggle = { toggleInspectorSection(InspectorSection.Reuse) },
                        )
                        if (!isRelationSectionCollapsed(InspectorSection.Reuse)) {
                            ShellButton(
                                label = Strings.inspector.deleteConnection(),
                                icon = ShellIcon.Delete,
                                enabled = !inputBlocked,
                                onClick = ::deleteSelection,
                            )
                        }
                        if (compactLayout) {
                            ShellButton(
                                label = Strings.common.collapseProperties(),
                                onClick = {
                                    compactInspectorExpanded = false
                                    canvasFocusRequester.requestFocus()
                                },
                            )
                        }
                    }
                }
            }

        if (
            (compactLayout || (
                !showSchemeLibrary && !showComponentLibrary && !showWorkspaceSwitcher && !showMembersPanel &&
                    !showLayersPanel && !showHistoryPanel && !showCompactMenu
                )) && selectedIds.isNotEmpty() &&
            selectedRelationId == null && editingId == null
        ) {
            val selectedNodes = selectedIds.mapNotNull { history.workspace.objectById(it) as? TextNode }
            val selectedGroups = selectedIds.mapNotNull { history.workspace.objectById(it) as? GroupFrame }
            val selectedMedia = selectedIds.mapNotNull { history.workspace.objectById(it) as? MediaNode }
            val allLocked = selectedNodes.isNotEmpty() && selectedNodes.all { it.locked }
            val anyLocked = selectedNodes.any { it.locked }
            val allGroupsLocked = selectedGroups.isNotEmpty() && selectedGroups.all { it.locked }
            val anyGroupLocked = selectedGroups.any { it.locked }
            val nodeLockSummary = selectionPropertySummary(selectedNodes.map { it.locked }) {
                if (it) Strings.common.locked() else Strings.common.unlocked()
            }
            val nodeColorSummary = selectionPropertySummary(selectedNodes.map { it.colorToken }, valueLabel = ::nodeColorLabel)
            val nodeShapeSummary = selectionPropertySummary(selectedNodes.map { it.shape }, valueLabel = ::nodeShapeLabel)
            val groupLockSummary = selectionPropertySummary(selectedGroups.map { it.locked }) {
                if (it) Strings.common.locked() else Strings.common.unlocked()
            }
            val groupColorSummary = selectionPropertySummary(selectedGroups.map { it.colorToken }, valueLabel = ::colorTokenLabel)
            val singleGroup = selectedGroups.singleOrNull()
            val singleNode = selectedNodes.singleOrNull()
            val singleMedia = selectedMedia.singleOrNull()
            val allMediaLocked = selectedMedia.isNotEmpty() && selectedMedia.all { it.locked }
            val anyMediaLocked = selectedMedia.any { it.locked }
            val allNodes = history.workspace.objects.values.filterIsInstance<TextNode>()
            val allGroups = history.workspace.objects.values.filterIsInstance<GroupFrame>()
            val selectedNodeIds = selectedNodes.mapTo(mutableSetOf()) { it.id }
            val selectedGroupIds = selectedGroups.mapTo(mutableSetOf()) { it.id }
            fun canMoveNodes(move: LayerMove): Boolean =
                layerZIndexUpdates(allNodes, selectedNodeIds, move).isNotEmpty()
            fun canMoveGroups(move: LayerMove): Boolean =
                layerZIndexUpdates(allGroups, selectedGroupIds, move).isNotEmpty()
            var groupTitleDraft by remember(singleGroup?.id, singleGroup?.version) {
                mutableStateOf(singleGroup?.title.orEmpty())
            }
            var mediaAltTextDraft by remember(singleMedia?.id, singleMedia?.version) {
                mutableStateOf(singleMedia?.altText.orEmpty())
            }
            fun submitMediaAltText() {
                val media = singleMedia ?: return
                val next = mediaAltTextDraft.trim().take(500)
                if (next == media.altText) return
                updateSelectedMedia("media-alt-text") { node, _ ->
                    MediaNodeAttributes(node.zIndex, node.locked, next)
                }
                focusManager.clearFocus()
                canvasFocusRequester.requestFocus()
            }
            fun submitGroupTitle() {
                val title = groupTitleDraft.trim()
                if (singleGroup == null || title.isEmpty() || title == singleGroup.title) return
                updateSelectedGroups("rename-group") { group, _ ->
                    GroupFrameAttributes(group.zIndex, group.locked, group.colorToken, title)
                }
            }
            val initialTransformValues = singleNode?.transform?.toInspectorValues(density)
            var transformX by remember(singleNode?.id, singleNode?.version) {
                mutableStateOf(initialTransformValues?.x?.let(::formatInspectorNumber).orEmpty())
            }
            var transformY by remember(singleNode?.id, singleNode?.version) {
                mutableStateOf(initialTransformValues?.y?.let(::formatInspectorNumber).orEmpty())
            }
            var transformWidth by remember(singleNode?.id, singleNode?.version) {
                mutableStateOf(initialTransformValues?.width?.let(::formatInspectorNumber).orEmpty())
            }
            var transformHeight by remember(singleNode?.id, singleNode?.version) {
                mutableStateOf(initialTransformValues?.height?.let(::formatInspectorNumber).orEmpty())
            }
            var transformRotation by remember(singleNode?.id, singleNode?.version) {
                mutableStateOf(initialTransformValues?.rotationDegrees?.let(::formatInspectorNumber).orEmpty())
            }
            fun submitPreciseTransform() {
                val node = singleNode ?: return
                val values = TransformInspectorValues(
                    x = transformX.toFloatOrNull() ?: Float.NaN,
                    y = transformY.toFloatOrNull() ?: Float.NaN,
                    width = transformWidth.toFloatOrNull() ?: Float.NaN,
                    height = transformHeight.toFloatOrNull() ?: Float.NaN,
                    rotationDegrees = transformRotation.toFloatOrNull() ?: Float.NaN,
                )
                val after = values.toCanvasTransform(
                    unitScale = density,
                    minimumSize = CanvasSize(120f * density, 72f * density),
                )
                if (after == null) {
                    statusMessage = Strings.inspector.enterValidPositionSizeAnd()
                    return
                }
                if (after != node.transform) {
                    execute(
                        TransformObjectsOperation(
                            operationId = "precise-transform-${node.id.value}-${history.workspace.version}",
                            changes = listOf(TransformChange(node.id, node.version, node.transform, after)),
                        ),
                    )
                }
                focusManager.clearFocus()
                canvasFocusRequester.requestFocus()
            }
            GlassSurface(
                modifier = if (compactLayout) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                } else {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(if (compactLayout) compactBottomSheetWidth else inspectorWidth)
                        .then(
                            if (compactLayout && !compactInspectorExpanded) {
                                Modifier.height(64.dp).clip(RoundedCornerShape(18.dp))
                            } else {
                                Modifier
                                    .heightIn(max = if (compactLayout) compactInspectorMaxHeight else 520.dp)
                                    .verticalScroll(rememberScrollState())
                            },
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ButtonGap),
                ) {
                    val inspectorTitle = when {
                        selectedGroups.size == 1 -> selectedGroups.single().title
                        selectedMedia.size == 1 -> selectedMedia.single().altText.ifBlank { Strings.objects.media() }
                        selectedIds.size == 1 -> Strings.objects.node()
                        else -> Strings.objects.nodeCount(selectedIds.size)
                    }
                    if (compactLayout) {
                        CompactBottomSheetHeader(
                            title = inspectorTitle,
                            expanded = compactInspectorExpanded,
                            width = compactBottomSheetWidth - 16.dp,
                            density = density,
                            onExpandedChange = { compactInspectorExpanded = it },
                        )
                    } else {
                        BasicText(
                            text = inspectorTitle,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = TextStyle(
                                color = colors.contentMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                    val inspectorSectionWidth = if (compactLayout) {
                        compactBottomSheetWidth - 16.dp
                    } else {
                        inspectorWidth - 16.dp
                    }
                    fun isSectionCollapsed(section: InspectorSection): Boolean =
                        section.token in collapsedInspectorSections
                    if (selectedGroups.isNotEmpty()) {
                        CollapsibleSectionHeader(
                            label = Strings.common.groupProperties(),
                            collapsed = isSectionCollapsed(InspectorSection.Content),
                            width = inspectorSectionWidth,
                            onToggle = { toggleInspectorSection(InspectorSection.Content) },
                        )
                        if (!isSectionCollapsed(InspectorSection.Content)) {
                        BasicText(
                            text = Strings.inspector.groupStateLockColor(groupLockSummary, groupColorSummary),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                        singleGroup?.let {
                            BasicTextField(
                                value = groupTitleDraft,
                                onValueChange = { groupTitleDraft = it.take(120) },
                                modifier = Modifier
                                    .width(inspectorWidth - 24.dp)
                                    .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                                    .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                                    .onFocusChanged { inspectorTextEditing = it.isFocused }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                            submitGroupTitle()
                                            focusManager.clearFocus()
                                            canvasFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                                cursorBrush = SolidColor(colors.selection),
                                singleLine = true,
                            )
                            ShellButton(
                                label = Strings.inspector.saveGroupName(),
                                enabled = !inputBlocked && groupTitleDraft.trim().isNotEmpty() &&
                                    groupTitleDraft.trim() != it.title,
                                onClick = ::submitGroupTitle,
                            )
                        }
                        ShellButton(
                            label = when {
                                allGroupsLocked -> Strings.inspector.unlockGroup()
                                anyGroupLocked -> Strings.inspector.lockAllGroups()
                                else -> Strings.inspector.lockGroup()
                            },
                            icon = if (allGroupsLocked) ShellIcon.Unlock else ShellIcon.Lock,
                            enabled = !inputBlocked,
                            onClick = {
                                updateSelectedGroups("lock-group") { group, _ ->
                                    GroupFrameAttributes(group.zIndex, !allGroupsLocked, group.colorToken, group.title)
                                }
                            },
                        )
                        ShellButton(
                            label = Strings.inspector.bringGroupForward(),
                            enabled = !inputBlocked && !anyGroupLocked && canMoveGroups(LayerMove.Forward),
                            onClick = { reorderSelectedGroupLayers(LayerMove.Forward) },
                        )
                        ShellButton(
                            label = Strings.inspector.sendGroupBackward(),
                            enabled = !inputBlocked && !anyGroupLocked && canMoveGroups(LayerMove.Backward),
                            onClick = { reorderSelectedGroupLayers(LayerMove.Backward) },
                        )
                        ShellButton(
                            label = Strings.inspector.bringGroupToFront(),
                            enabled = !inputBlocked && !anyGroupLocked && canMoveGroups(LayerMove.Front),
                            onClick = { reorderSelectedGroupLayers(LayerMove.Front) },
                        )
                        ShellButton(
                            label = Strings.inspector.sendGroupToBack(),
                            enabled = !inputBlocked && !anyGroupLocked && canMoveGroups(LayerMove.Back),
                            onClick = { reorderSelectedGroupLayers(LayerMove.Back) },
                        )
                        listOf(
                            "group" to Strings.inspector.groupDefault(),
                            "lilac" to Strings.inspector.groupLilac(),
                            "amber" to Strings.inspector.groupAmber(),
                            "mint" to Strings.inspector.groupMint(),
                        ).forEach { (token, label) ->
                            ShellButton(
                                label = label,
                                enabled = !inputBlocked && !anyGroupLocked,
                                accent = selectedGroups.all { it.colorToken == token },
                                onClick = {
                                    updateSelectedGroups("color-group") { group, _ ->
                                        GroupFrameAttributes(group.zIndex, group.locked, token, group.title)
                                    }
                                },
                            )
                        }
                        }
                    }
                    if (selectedMedia.isNotEmpty()) {
                        CollapsibleSectionHeader(
                            label = Strings.objects.media(),
                            collapsed = isSectionCollapsed(InspectorSection.Content),
                            width = inspectorSectionWidth,
                            onToggle = { toggleInspectorSection(InspectorSection.Content) },
                        )
                        if (!isSectionCollapsed(InspectorSection.Content)) {
                            singleMedia?.let { media ->
                                BasicText(
                                    text = "${when (media.mediaKind) {
                                        MediaKind.Image -> Strings.objects.image()
                                        MediaKind.Gif -> Strings.objects.gif()
                                        MediaKind.Video -> Strings.objects.video()
                                    }} • ${Strings.media.assetReference(media.assetId.take(12))}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                                )
                                BasicTextField(
                                    value = mediaAltTextDraft,
                                    onValueChange = { mediaAltTextDraft = it.take(500) },
                                    modifier = Modifier
                                        .width(inspectorWidth - 24.dp)
                                        .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                                        .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp))
                                        .onFocusChanged { inspectorTextEditing = it.isFocused }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                                    cursorBrush = SolidColor(colors.selection),
                                    singleLine = true,
                                )
                                ShellButton(
                                    label = Strings.media.saveAlternativeText(),
                                    enabled = !inputBlocked && mediaAltTextDraft.trim() != media.altText,
                                    onClick = ::submitMediaAltText,
                                )
                            }
                            ShellButton(
                                label = if (allMediaLocked) Strings.media.unlockMedia() else Strings.media.lockMedia(),
                                icon = if (allMediaLocked) ShellIcon.Unlock else ShellIcon.Lock,
                                enabled = !inputBlocked,
                                onClick = {
                                    updateSelectedMedia("lock-media") { media, _ ->
                                        MediaNodeAttributes(
                                            media.zIndex,
                                            !allMediaLocked,
                                            media.altText,
                                        )
                                    }
                                },
                            )
                            if (anyMediaLocked && !allMediaLocked) {
                                BasicText(
                                    text = Strings.common.mixed(),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    style = TextStyle(color = colors.contentMuted, fontSize = 10.sp),
                                )
                            }
                        }
                    }
                    if (selectedNodes.isNotEmpty()) {
                        CollapsibleSectionHeader(
                            label = Strings.common.contentAndState(),
                            collapsed = isSectionCollapsed(InspectorSection.Content),
                            width = inspectorSectionWidth,
                            onToggle = { toggleInspectorSection(InspectorSection.Content) },
                        )
                        if (!isSectionCollapsed(InspectorSection.Content)) {
                            BasicText(
                                text = Strings.inspector.nodeStateLockColorShape(nodeLockSummary, nodeColorSummary, nodeShapeSummary),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                            )
                            singleNode?.let { node ->
                                ShellButton(
                                    label = Strings.common.editText(),
                                    enabled = !inputBlocked && !node.locked,
                                    onClick = ::startEditingSelectedNode,
                                )
                            }
                            ShellButton(
                                label = when {
                                    allLocked -> Strings.inspector.unlock()
                                    anyLocked -> Strings.inspector.lockAll()
                                    else -> Strings.inspector.lock()
                                },
                                icon = if (allLocked) ShellIcon.Unlock else ShellIcon.Lock,
                                enabled = !inputBlocked,
                                onClick = {
                                    updateSelectedNodes("lock") { node, _ ->
                                        TextNodeAttributes(node.zIndex, !allLocked, node.colorToken, node.shape)
                                    }
                                },
                            )
                        }
                        CollapsibleSectionHeader(
                            label = Strings.common.appearanceTransformAndLayout(),
                            collapsed = isSectionCollapsed(InspectorSection.Appearance),
                            width = inspectorSectionWidth,
                            onToggle = { toggleInspectorSection(InspectorSection.Appearance) },
                        )
                        if (!isSectionCollapsed(InspectorSection.Appearance)) {
                    singleNode?.let { node ->
                        BasicText(
                            text = Strings.inspector.positionSizeAndRotation(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                        Row(
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                        ) {
                            InspectorNumberField(
                                label = "X",
                                value = transformX,
                                onValueChange = { transformX = it },
                                enabled = !inputBlocked && !node.locked,
                                onEditingChange = { inspectorTextEditing = it },
                                onSubmit = ::submitPreciseTransform,
                                modifier = Modifier.weight(1f),
                            )
                            InspectorNumberField(
                                label = "Y",
                                value = transformY,
                                onValueChange = { transformY = it },
                                enabled = !inputBlocked && !node.locked,
                                onEditingChange = { inspectorTextEditing = it },
                                onSubmit = ::submitPreciseTransform,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                        ) {
                            InspectorNumberField(
                                label = Strings.inspector.width(),
                                value = transformWidth,
                                onValueChange = { transformWidth = it },
                                enabled = !inputBlocked && !node.locked,
                                onEditingChange = { inspectorTextEditing = it },
                                onSubmit = ::submitPreciseTransform,
                                modifier = Modifier.weight(1f),
                            )
                            InspectorNumberField(
                                label = Strings.inspector.height(),
                                value = transformHeight,
                                onValueChange = { transformHeight = it },
                                enabled = !inputBlocked && !node.locked,
                                onEditingChange = { inspectorTextEditing = it },
                                onSubmit = ::submitPreciseTransform,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        InspectorNumberField(
                            label = Strings.inspector.rotationDegrees(),
                            value = transformRotation,
                            onValueChange = { transformRotation = it },
                            enabled = !inputBlocked && !node.locked,
                            onEditingChange = { inspectorTextEditing = it },
                            onSubmit = ::submitPreciseTransform,
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                        ShellButton(
                            label = Strings.inspector.applyPreciseTransform(),
                            enabled = !inputBlocked && !node.locked,
                            onClick = ::submitPreciseTransform,
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                        )
                    }
                    ShellButton(
                        label = Strings.layers.bringForward(),
                        icon = ShellIcon.Forward,
                        enabled = !inputBlocked && !anyLocked && canMoveNodes(LayerMove.Forward),
                        onClick = { reorderSelectedNodeLayers(LayerMove.Forward) },
                    )
                    ShellButton(
                        label = Strings.layers.sendBackward(),
                        icon = ShellIcon.Back,
                        enabled = !inputBlocked && !anyLocked && canMoveNodes(LayerMove.Backward),
                        onClick = { reorderSelectedNodeLayers(LayerMove.Backward) },
                    )
                    ShellButton(
                        label = Strings.layers.bringToFront(),
                        enabled = !inputBlocked && !anyLocked && canMoveNodes(LayerMove.Front),
                        onClick = { reorderSelectedNodeLayers(LayerMove.Front) },
                    )
                    ShellButton(
                        label = Strings.layers.sendToBack(),
                        enabled = !inputBlocked && !anyLocked && canMoveNodes(LayerMove.Back),
                        onClick = { reorderSelectedNodeLayers(LayerMove.Back) },
                    )
                    ShellButton(
                        label = Strings.inspector.rotatePlus15Degrees(),
                        icon = ShellIcon.Rotate,
                        enabled = !inputBlocked && !anyLocked,
                        onClick = {
                            transformSelectedNodesAsSelection("rotate-selection") { transforms ->
                                rotateSelectionTransforms(transforms, 15f)
                            }
                        },
                    )
                    ShellButton(
                        label = Strings.inspector.larger(),
                        enabled = !inputBlocked && !anyLocked,
                        onClick = {
                            transformSelectedNodesAsSelection("scale-selection-up") { transforms ->
                                scaleSelectionTransforms(
                                    transforms = transforms,
                                    factor = 1.1f,
                                    minimumSize = CanvasSize(120f * density, 72f * density),
                                )
                            }
                        },
                    )
                    ShellButton(
                        label = Strings.inspector.smaller(),
                        enabled = !inputBlocked && !anyLocked,
                        onClick = {
                            transformSelectedNodesAsSelection("scale-selection-down") { transforms ->
                                scaleSelectionTransforms(
                                    transforms = transforms,
                                    factor = 0.9f,
                                    minimumSize = CanvasSize(120f * density, 72f * density),
                                )
                            }
                        },
                    )
                    BasicText(
                        text = Strings.objects.shape(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                    )
                    FlowRow(
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                        verticalArrangement = Arrangement.spacedBy(ButtonGap),
                    ) {
                        NodeShape.entries.forEach { shape ->
                            ShellButton(
                                label = nodeShapeLabel(shape),
                                enabled = !inputBlocked && !anyLocked,
                                accent = selectedNodes.all { it.shape == shape },
                                onClick = {
                                    updateSelectedNodes("shape") { node, _ ->
                                        TextNodeAttributes(
                                            node.zIndex,
                                            node.locked,
                                            node.colorToken,
                                            shape,
                                        )
                                    }
                                },
                            )
                        }
                    }
                    if (selectedNodes.size >= 2) {
                        BasicText(
                            text = Strings.layout.autoLayout(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                        FlowRow(
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                            verticalArrangement = Arrangement.spacedBy(ButtonGap),
                        ) {
                            ShellButton(
                                label = Strings.layout.horizontalFlow(),
                                icon = ShellIcon.Forward,
                                enabled = canAutoLayoutSelection(),
                                onClick = { autoLayoutSelection(DiagramLayoutMode.HorizontalFlow) },
                            )
                            ShellButton(
                                label = Strings.layout.treeHierarchy(),
                                icon = ShellIcon.Layers,
                                enabled = canAutoLayoutSelection(),
                                onClick = { autoLayoutSelection(DiagramLayoutMode.VerticalTree) },
                            )
                            ShellButton(
                                label = Strings.layout.radialMap(),
                                icon = ShellIcon.Radial,
                                enabled = canAutoLayoutSelection(),
                                onClick = { autoLayoutSelection(DiagramLayoutMode.RadialRelationship) },
                            )
                            ShellButton(
                                label = Strings.layout.gridLayout(),
                                icon = ShellIcon.Grid,
                                enabled = canAutoLayoutSelection(),
                                onClick = { autoLayoutSelection(DiagramLayoutMode.Grid) },
                            )
                            ShellButton(
                                label = Strings.layout.distributeHorizontally(),
                                icon = ShellIcon.DistributeHorizontal,
                                enabled = canDistributeSelection(),
                                onClick = { distributeSelection(DistributionAxis.Horizontal) },
                            )
                            ShellButton(
                                label = Strings.layout.distributeVertically(),
                                icon = ShellIcon.DistributeVertical,
                                enabled = canDistributeSelection(),
                                onClick = { distributeSelection(DistributionAxis.Vertical) },
                            )
                        }
                        BasicText(
                            text = Strings.layout.align(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                        FlowRow(
                            modifier = Modifier.width(inspectorWidth - 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                            verticalArrangement = Arrangement.spacedBy(ButtonGap),
                        ) {
                            listOf(
                                Strings.layout.alignLeft() to SelectionAlignment.Left,
                                Strings.layout.alignHorizontalCenters() to SelectionAlignment.HorizontalCenter,
                                Strings.layout.alignRight() to SelectionAlignment.Right,
                                Strings.layout.alignTop() to SelectionAlignment.Top,
                                Strings.layout.alignVerticalCenters() to SelectionAlignment.VerticalCenter,
                                Strings.layout.alignBottom() to SelectionAlignment.Bottom,
                            ).forEach { (label, alignment) ->
                                ShellButton(
                                    label = label,
                                    icon = when (alignment) {
                                        SelectionAlignment.Left -> ShellIcon.AlignLeft
                                        SelectionAlignment.HorizontalCenter -> ShellIcon.AlignHorizontalCenter
                                        SelectionAlignment.Right -> ShellIcon.AlignRight
                                        SelectionAlignment.Top -> ShellIcon.AlignTop
                                        SelectionAlignment.VerticalCenter -> ShellIcon.AlignVerticalCenter
                                        SelectionAlignment.Bottom -> ShellIcon.AlignBottom
                                    },
                                    enabled = canAutoLayoutSelection(),
                                    onClick = { alignSelection(alignment) },
                                )
                            }
                        }
                    }
                    val sharedCustomColor = selectedNodes.map { it.colorToken }.distinct().singleOrNull()
                        ?.let(::customNodeColor)
                    FlowRow(
                        modifier = Modifier.width(inspectorWidth - 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                        verticalArrangement = Arrangement.spacedBy(ButtonGap),
                    ) {
                        NodeColorPresetTokens.forEach { token ->
                            ColorSwatchButton(
                                label = nodeColorLabel(token),
                                color = presetNodeColor(token, colors),
                                enabled = !inputBlocked && !anyLocked,
                                selected = selectedNodes.all { it.colorToken == token },
                                onClick = {
                                    updateSelectedNodes("color") { node, _ ->
                                        TextNodeAttributes(node.zIndex, node.locked, token, node.shape)
                                    }
                                },
                            )
                        }
                    }
                    ColorSwatchButton(
                        label = sharedCustomColor?.let { Strings.colorPicker.customColorHex(it.toHex()) } ?: Strings.colorPicker.customColor(),
                        color = sharedCustomColor?.let { Color(it.r, it.g, it.b) },
                        enabled = !inputBlocked && !anyLocked,
                        selected = colorPickerTargets != null || sharedCustomColor != null,
                        onClick = {
                            colorPickerInitialToken = selectedNodes.map { it.colorToken }.distinct().singleOrNull()
                                ?: selectedNodes.first().colorToken
                            colorPreviewToken = null
                            showCanvasBackgroundPicker = false
                            canvasBackgroundPreview = null
                            colorPickerTargets = selectedNodes.mapTo(mutableSetOf()) { it.id }
                        },
                    )
                    if (selectedNodes.size == 2) {
                        ShellButton(
                            label = Strings.inspector.relate(),
                            enabled = !inputBlocked,
                            onClick = { connectSelected("relates", RelationDirection.Forward) },
                        )
                        ShellButton(
                            label = Strings.inspector.supports(),
                            enabled = !inputBlocked,
                            onClick = { connectSelected("supports", RelationDirection.Forward) },
                        )
                        ShellButton(
                            label = Strings.inspector.conflicts(),
                            enabled = !inputBlocked,
                            onClick = { connectSelected("conflicts", RelationDirection.Both) },
                        )
                    }
                        }
                    }
                    CollapsibleSectionHeader(
                        label = Strings.common.reuseAndActions(),
                        collapsed = isSectionCollapsed(InspectorSection.Reuse),
                        width = inspectorSectionWidth,
                        onToggle = { toggleInspectorSection(InspectorSection.Reuse) },
                    )
                    if (!isSectionCollapsed(InspectorSection.Reuse)) {
                        singleGroup?.let { group ->
                            ShellButton(
                                label = Strings.common.fitGroupToContents(),
                                enabled = !inputBlocked && !group.locked &&
                                    descendantObjectIds(history.workspace, setOf(group.id)).isNotEmpty(),
                                onClick = { fitGroupToContents(group) },
                            )
                            ShellButton(
                                label = Strings.palette.ungroup(),
                                enabled = !inputBlocked && !group.locked,
                                onClick = { ungroup(group) },
                            )
                        }
                        if (selectedNodes.size + selectedGroups.size + selectedMedia.size >= 2) {
                            ShellButton(
                                label = Strings.palette.groupSelection(),
                                icon = ShellIcon.Group,
                                enabled = !inputBlocked,
                                onClick = ::groupSelection,
                            )
                        }
                        ShellButton(
                            label = Strings.inspector.copy(),
                            icon = ShellIcon.Copy,
                            enabled = !inputBlocked,
                            onClick = { copySelection() },
                        )
                        ShellButton(
                            label = Strings.inspector.cut(),
                            icon = ShellIcon.Cut,
                            enabled = !inputBlocked,
                            onClick = ::cutSelection,
                        )
                        ShellButton(
                            label = Strings.inspector.duplicate(),
                            icon = ShellIcon.Duplicate,
                            enabled = !inputBlocked,
                            onClick = ::duplicateSelection,
                        )
                        ShellButton(
                            label = Strings.inspector.saveAsScheme(),
                            icon = ShellIcon.Schemes,
                            enabled = !inputBlocked,
                            onClick = ::saveQuickScheme,
                        )
                        ShellButton(
                            label = Strings.palette.deleteSelection(),
                            icon = ShellIcon.Delete,
                            enabled = !inputBlocked,
                            onClick = ::deleteSelection,
                        )
                    }
                    if (compactLayout) {
                        ShellButton(
                            label = Strings.common.collapseProperties(),
                            onClick = {
                                compactInspectorExpanded = false
                                canvasFocusRequester.requestFocus()
                            },
                        )
                    }
                }
            }
        }

        if (
            !compactLayout && !showLayersPanel && !showHistoryPanel && !showMembersPanel &&
                history.workspace.objects.isNotEmpty() &&
                canvasSize != IntSize.Zero
        ) {
            CanvasMiniMap(
                objects = history.workspace.objects.values,
                viewport = viewport,
                canvasSize = canvasSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp),
                onNavigate = { worldPoint ->
                    val screenCenter = Vec2(canvasSize.width / 2f, canvasSize.height / 2f)
                    viewport = viewport.copy(pan = screenCenter - worldPoint * viewport.zoom)
                },
            )
        }

        if (shouldShowStatusOverlay(compactModalVisible || compactInspectorAvailable, showCommandPalette)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (compactLayout) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                    .padding(horizontal = if (compactLayout) 16.dp else 0.dp)
                    .padding(bottom = 18.dp),
            ) {
                GlassSurface {
                    BasicText(
                        text = statusMessage,
                        modifier = Modifier
                            .then(
                                if (compactLayout) {
                                    Modifier.widthIn(max = compactStatusMaxWidthDp(canvasSize.width, density).dp)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        style = TextStyle(
                            color = colors.contentMuted,
                            fontSize = 12.sp,
                        ),
                        maxLines = if (compactLayout) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        libraryDragPreview?.let { preview ->
            GlassSurface(
                modifier = Modifier.offset {
                    IntOffset(
                        x = preview.screenPosition.x.roundToInt() + 14,
                        y = preview.screenPosition.y.roundToInt() + 14,
                    )
                },
            ) {
                BasicText(
                    text = preview.title,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = TextStyle(
                        color = colors.contentText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        colorPickerTargets?.let { targets ->
            val targetsEditable = targets == selectedIds && !inputBlocked &&
                targets.all { (history.workspace.objectById(it) as? TextNode)?.locked == false }
            if (!targetsEditable) {
                LaunchedEffect(targets) {
                    colorPickerTargets = null
                    colorPreviewToken = null
                }
            } else {
                val closePicker = {
                    colorPickerTargets = null
                    colorPreviewToken = null
                    inspectorTextEditing = false
                }
                ColorPickerWindow(
                    initialToken = colorPickerInitialToken,
                    initialColor = nodeFillColor(colorPickerInitialToken, colors)
                        .let { RgbColor(it.red, it.green, it.blue) },
                    presets = NodeColorPresetTokens.map { token ->
                        ColorSwatchOption(token, nodeColorLabel(token), presetNodeColor(token, colors))
                    },
                    recentColors = history.workspace.objects.values
                        .filterIsInstance<TextNode>()
                        .mapNotNull { customNodeColor(it.colorToken) }
                        .distinctBy { it.toHex() }
                        .take(12),
                    model = colorPickerModel,
                    onModelChange = { model ->
                        colorPickerModel = model
                        uiPreferences.colorPickerModel = model
                    },
                    onPreview = { token -> colorPreviewToken = token },
                    onApply = { token ->
                        updateSelectedNodes("color") { node, _ ->
                            TextNodeAttributes(node.zIndex, node.locked, token, node.shape)
                        }
                        closePicker()
                    },
                    onCancel = closePicker,
                    onEditingChange = { inspectorTextEditing = it },
                    modifier = if (compactLayout) {
                        Modifier
                            .align(Alignment.Center)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(12.dp)
                    } else {
                        // Top-anchored so the window grows downward instead of shifting as content changes.
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 84.dp, end = inspectorWidth + 30.dp)
                    },
                    fieldSize = if (compactLayout) (inspectorWidth - 148.dp).coerceIn(140.dp, 188.dp) else 188.dp,
                )
            }
        }

        if (showCanvasBackgroundPicker) {
            val closeBackgroundPicker = {
                showCanvasBackgroundPicker = false
                canvasBackgroundPreview = null
                inspectorTextEditing = false
            }
            ColorPickerWindow(
                initialToken = displaySettings.backgroundToken,
                initialColor = canvasBackgroundColor(displaySettings.backgroundToken, colors)
                    .let { RgbColor(it.red, it.green, it.blue) },
                presets = CanvasBackgroundPresetTokens.map { token ->
                    ColorSwatchOption(token, canvasBackgroundLabel(token), presetCanvasBackground(token, colors))
                },
                recentColors = history.workspace.objects.values
                    .filterIsInstance<TextNode>()
                    .mapNotNull { customNodeColor(it.colorToken) }
                    .distinctBy { it.toHex() }
                    .take(12),
                model = colorPickerModel,
                onModelChange = { model ->
                    colorPickerModel = model
                    uiPreferences.colorPickerModel = model
                },
                title = Strings.canvasBackground.title(),
                onPreview = { token -> canvasBackgroundPreview = token },
                onApply = { token ->
                    displaySettings = displaySettings.copy(backgroundToken = token)
                    closeBackgroundPicker()
                },
                onCancel = closeBackgroundPicker,
                onEditingChange = { inspectorTextEditing = it },
                modifier = if (compactLayout) {
                    Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                } else {
                    // Left side, clear of the inspector, so the whole canvas stays visible while previewing.
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 84.dp, start = 18.dp)
                },
                fieldSize = if (compactLayout) (inspectorWidth - 148.dp).coerceIn(140.dp, 188.dp) else 188.dp,
            )
        }

        if (showCommandPalette) {
            CommandPalette(
                query = commandPaletteQuery,
                entries = commandPaletteEntries,
                compact = compactLayout,
                onQueryChange = { commandPaletteQuery = it },
                onInvoke = ::invokePaletteEntry,
                onDismiss = {
                    showCommandPalette = false
                    commandPaletteQuery = ""
                    focusManager.clearFocus()
                    canvasFocusRequester.requestFocus()
                },
            )
        }
    }
}

@Composable
private fun CompactBottomSheetHeader(
    title: String,
    expanded: Boolean,
    width: Dp,
    density: Float,
    onExpandedChange: (Boolean) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Column(
        modifier = Modifier
            .width(width)
            .pointerInput(expanded, density) {
                var dragY = 0f
                detectDragGestures(
                    onDragStart = { dragY = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragY += amount.y
                    },
                    onDragEnd = {
                        onExpandedChange(compactSheetExpandedAfterDrag(expanded, dragY, density))
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp, bottom = 4.dp)
                .width(38.dp)
                .height(4.dp)
                .background(colors.contentMuted.copy(alpha = 0.45f), RoundedCornerShape(99.dp)),
        )
        Row(
            modifier = Modifier
                .width(width)
                .padding(start = 12.dp, end = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = title,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = colors.contentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            ShellButton(
                label = if (expanded) Strings.common.collapseProperties() else Strings.common.showProperties(),
                icon = if (expanded) ShellIcon.Back else ShellIcon.Forward,
                showLabel = false,
                onClick = { onExpandedChange(!expanded) },
            )
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    label: String,
    collapsed: Boolean,
    width: Dp,
    onToggle: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Row(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.canvas.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                stateDescription = if (collapsed) Strings.common.collapsed() else Strings.common.expanded()
            }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = colors.contentText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        BasicText(
            text = if (collapsed) "+" else "−",
            style = TextStyle(color = colors.contentMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun CanvasMiniMap(
    objects: Collection<CanvasObject>,
    viewport: Viewport,
    canvasSize: IntSize,
    modifier: Modifier = Modifier,
    onNavigate: (Vec2) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val density = LocalDensity.current.density
    val contentBounds = canvasObjectBounds(objects) ?: return
    val miniMapSize = CanvasSize(width = 180f * density, height = 112f * density)
    val projection = createMiniMapProjection(
        contentBounds = contentBounds,
        viewport = viewport,
        canvasSize = CanvasSize(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
        miniMapSize = miniMapSize,
        padding = 8f * density,
    )

    GlassSurface(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .width(180.dp)
                .height(112.dp)
                .semantics {
                    contentDescription = Strings.a11y.canvasMinimapClickToCenter()
                    role = Role.Button
                    onClick(label = Strings.a11y.centerWorkspaceOnContent()) {
                        onNavigate(contentBounds.center)
                        true
                    }
                }
                .pointerInput(projection) {
                    detectTapGestures { position ->
                        onNavigate(projection.miniMapToWorld(Vec2(position.x, position.y)))
                    }
                },
        ) {
            drawRect(colors.canvas.copy(alpha = 0.56f))
            objects.sortedBy(CanvasObject::zIndex).forEach { canvasObject ->
                val bounds = canvasObjectBounds(listOf(canvasObject)) ?: return@forEach
                val topLeft = projection.worldToMiniMap(bounds.topLeft)
                val bottomRight = projection.worldToMiniMap(bounds.bottomRight)
                drawRect(
                    color = miniMapObjectColor(canvasObject, colors),
                    topLeft = Offset(topLeft.x, topLeft.y),
                    size = Size(
                        width = (bottomRight.x - topLeft.x).coerceAtLeast(2f),
                        height = (bottomRight.y - topLeft.y).coerceAtLeast(2f),
                    ),
                )
            }
            val viewportTopLeft = projection.viewportRect.topLeft
            val viewportBottomRight = projection.viewportRect.bottomRight
            drawRect(
                color = colors.selection,
                topLeft = Offset(viewportTopLeft.x, viewportTopLeft.y),
                size = Size(
                    width = (viewportBottomRight.x - viewportTopLeft.x).coerceAtLeast(2f),
                    height = (viewportBottomRight.y - viewportTopLeft.y).coerceAtLeast(2f),
                ),
                style = Stroke(width = 2f * density),
            )
        }
    }
}

private fun miniMapObjectColor(canvasObject: CanvasObject, colors: BoarderLessColors) =
    if (canvasObject is GroupFrame) colors.accent.copy(alpha = 0.24f) else colors.contentText.copy(alpha = 0.58f)

@Composable
private fun RelationLabel(label: String, midpoint: Vec2) {
    val colors = BoarderLessTheme.colors
    BasicText(
        text = label,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = midpoint.x.roundToInt() - 24,
                    y = midpoint.y.roundToInt() - 10,
                )
            }
            .background(colors.canvas.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .border(1.dp, colors.contentBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = TextStyle(
            color = colors.contentText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun GroupFrameCard(
    group: GroupFrame,
    viewport: Viewport,
    previewDelta: Vec2,
    selected: Boolean,
    dropTarget: Boolean,
    reduceMotion: Boolean,
    interactionEnabled: Boolean,
    mutationEnabled: Boolean,
    onAccessibilitySelect: () -> Unit,
    onKeyboardFocus: () -> Unit,
    onSelect: (Vec2, additive: Boolean) -> Unit,
    onDragPreview: (Vec2) -> Unit,
    onDragCancel: () -> Unit,
    onDragCommit: (Vec2) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val density = LocalDensity.current
    val screenPosition = viewport.worldToScreen(group.transform.position + previewDelta)
    val screenWidth = with(density) { (group.transform.size.width * viewport.zoom).toDp() }
    val screenHeight = with(density) { (group.transform.size.height * viewport.zoom).toDp() }
    // The frame lives in world space, so its corners and title scale with it instead of staying fixed
    // on screen (a fixed 20dp radius turns a zoomed-out frame into a pill).
    val shape = RoundedCornerShape((20f * viewport.zoom).coerceAtLeast(4f).dp)
    val titleScale = viewport.zoom.coerceIn(0.55f, 1.5f)
    val targetAlpha = when {
        dropTarget -> 0.26f
        selected -> 0.18f
        else -> 0.09f
    }
    val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, label = "group-selection")
    val groupAlpha = if (reduceMotion) targetAlpha else animatedAlpha
    val groupColor = when (group.colorToken) {
        "lilac" -> colors.nodeLilac
        "amber" -> colors.nodeAmber
        "mint" -> colors.nodeMint
        else -> colors.selection
    }
    Box(
        modifier = Modifier
            .canvasObjectBounds(screenPosition, screenWidth, screenHeight)
            .graphicsLayer(rotationZ = group.transform.rotationDegrees)
            .clip(shape)
            .background(groupColor.copy(alpha = groupAlpha))
            .border(
                width = when {
                    dropTarget -> 3.dp
                    selected -> 2.dp
                    else -> 1.dp
                },
                color = groupColor.copy(alpha = if (selected || dropTarget) 0.95f else 0.55f),
                shape = shape,
            )
            .semantics {
                contentDescription = Strings.a11y.group(group.title)
                this.selected = selected
                stateDescription = when {
                    group.locked -> Strings.common.locked()
                    dropTarget -> Strings.common.dropTarget()
                    !mutationEnabled -> Strings.common.readOnly()
                    else -> Strings.common.unlocked()
                }
                role = Role.Button
                onClick(label = Strings.a11y.selectGroup()) {
                    onAccessibilitySelect()
                    true
                }
            }
            .onFocusChanged { state ->
                if (state.isFocused) onKeyboardFocus()
            }
            .focusable(enabled = interactionEnabled)
            .pointerInput(group.id, viewport.zoom, interactionEnabled, mutationEnabled, group.version) {
                if (interactionEnabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val additiveSelection = currentEvent.keyboardModifiers.isShiftPressed
                        down.consume()
                        var accumulated = Vec2.Zero
                        var moved = false
                        val completed = drag(down.id) { change ->
                            val dragAmount = change.positionChange()
                            if (dragAmount != Offset.Zero && !group.locked && mutationEnabled) {
                                moved = true
                                change.consume()
                                accumulated += Vec2(dragAmount.x, dragAmount.y) / viewport.zoom
                                onDragPreview(accumulated)
                            }
                        }
                        when {
                            !completed -> onDragCancel()
                            moved -> onDragCommit(accumulated)
                            else -> onSelect(Vec2(down.position.x, down.position.y), additiveSelection)
                        }
                    }
                }
            },
    ) {
        BasicText(
            text = group.title,
            modifier = Modifier.padding(
                horizontal = (16f * viewport.zoom).coerceIn(4f, 24f).dp,
                vertical = (10f * viewport.zoom).coerceIn(2f, 15f).dp,
            ),
            style = TextStyle(
                color = groupColor,
                fontSize = (12f * titleScale).sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RelationCanvas(
    relations: Collection<Relation>,
    nodes: Map<CanvasObjectId, TextNode>,
    viewport: Viewport,
    density: Float,
    dragPreviews: Map<CanvasObjectId, Vec2>,
    transformPreviews: Map<CanvasObjectId, CanvasTransform>,
    selectedRelationId: RelationId?,
) {
    val colors = BoarderLessTheme.colors
    Canvas(modifier = Modifier.fillMaxSize()) {
        relations.forEach { relation ->
            val segment = relationSegment(
                relation,
                nodes,
                viewport,
                dragPreviews,
                transformPreviews,
            ) ?: return@forEach
            val points = segment.points
            val start = points.first()
            val end = points.last()
            val color = when (relation.intent) {
                "conflicts" -> colors.danger
                "supports" -> colors.selection
                else -> colors.contentMuted
            }
            if (relation.id == selectedRelationId) {
                points.zipWithNext().forEach { (segmentStart, segmentEnd) ->
                    drawLine(
                        color = colors.contentText.copy(alpha = 0.28f),
                        start = Offset(segmentStart.x, segmentStart.y),
                        end = Offset(segmentEnd.x, segmentEnd.y),
                        strokeWidth = 8f * density,
                        cap = StrokeCap.Round,
                    )
                }
            }
            points.zipWithNext().forEach { (segmentStart, segmentEnd) ->
                drawLine(
                    color = color,
                    start = Offset(segmentStart.x, segmentStart.y),
                    end = Offset(segmentEnd.x, segmentEnd.y),
                    strokeWidth = 2f * density,
                    cap = StrokeCap.Round,
                )
            }
            if (relation.direction == RelationDirection.Forward || relation.direction == RelationDirection.Both) {
                drawArrowHead(start = points[points.lastIndex - 1], end = end, color = color, density = density)
            }
            if (relation.direction == RelationDirection.Backward || relation.direction == RelationDirection.Both) {
                drawArrowHead(start = points[1], end = start, color = color, density = density)
            }
        }
    }
}

@Composable
private fun ConnectionDragPreviewCanvas(
    preview: ConnectionDragPreview,
    density: Float,
) {
    val colors = BoarderLessTheme.colors
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = if (preview.targetId == null) colors.contentMuted else colors.selection
        drawLine(
            color = color,
            start = Offset(preview.startScreen.x, preview.startScreen.y),
            end = Offset(preview.endScreen.x, preview.endScreen.y),
            strokeWidth = 2f * density,
        )
        drawArrowHead(preview.startScreen, preview.endScreen, color, density)
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = 12f * density,
            center = Offset(preview.endScreen.x, preview.endScreen.y),
        )
    }
}

private data class RelationPath(val points: List<Vec2>)

private fun relationSegment(
    relation: Relation,
    nodes: Map<CanvasObjectId, TextNode>,
    viewport: Viewport,
    dragPreviews: Map<CanvasObjectId, Vec2> = emptyMap(),
    transformPreviews: Map<CanvasObjectId, CanvasTransform> = emptyMap(),
): RelationPath? {
    val source = nodes[relation.sourceObjectId] ?: return null
    val target = nodes[relation.targetObjectId] ?: return null
    val renderedTransforms = nodes.mapValues { (id, node) ->
        val transform = transformPreviews[id] ?: node.transform
        transform.copy(position = transform.position + (dragPreviews[id] ?: Vec2.Zero))
    }
    val sourceRenderedTransform = renderedTransforms.getValue(source.id)
    val targetRenderedTransform = renderedTransforms.getValue(target.id)
    return RelationPath(
        points = orthogonalRelationRoute(
            source = source,
            sourceTransform = sourceRenderedTransform,
            target = target,
            targetTransform = targetRenderedTransform,
            obstacles = renderedTransforms
                .filterKeys { it != source.id && it != target.id }
                .values,
            clearance = (min(
                sourceRenderedTransform.size.height,
                targetRenderedTransform.size.height,
            ) * 0.18f).coerceAtLeast(18f),
        ).map(viewport::worldToScreen),
    )
}

private fun findRelationAt(
    screenPosition: Vec2,
    relations: Collection<Relation>,
    nodes: Map<CanvasObjectId, TextNode>,
    viewport: Viewport,
    tolerance: Float,
): Relation? = relations
    .mapNotNull { relation ->
        val segment = relationSegment(relation, nodes, viewport) ?: return@mapNotNull null
        relation to distanceToPolyline(screenPosition, segment.points)
    }
    .filter { (_, distance) -> distance <= tolerance }
    .minByOrNull { (_, distance) -> distance }
    ?.first

internal fun calculateAlignmentSnap(
    movingTransform: CanvasTransform,
    otherTransforms: List<CanvasTransform>,
    rawDelta: Vec2,
    threshold: Float,
): AlignmentSnapResult {
    if (otherTransforms.isEmpty()) return AlignmentSnapResult(rawDelta)

    val movingLeft = movingTransform.position.x + rawDelta.x
    val movingTop = movingTransform.position.y + rawDelta.y
    val movingXAnchors = listOf(
        movingLeft,
        movingLeft + movingTransform.size.width / 2f,
        movingLeft + movingTransform.size.width,
    )
    val movingYAnchors = listOf(
        movingTop,
        movingTop + movingTransform.size.height / 2f,
        movingTop + movingTransform.size.height,
    )
    val targetXAnchors = otherTransforms.flatMap { transform ->
        listOf(
            transform.position.x,
            transform.position.x + transform.size.width / 2f,
            transform.position.x + transform.size.width,
        )
    }
    val targetYAnchors = otherTransforms.flatMap { transform ->
        listOf(
            transform.position.y,
            transform.position.y + transform.size.height / 2f,
            transform.position.y + transform.size.height,
        )
    }

    val xMatch = closestAlignment(movingXAnchors, targetXAnchors, threshold)
    val yMatch = closestAlignment(movingYAnchors, targetYAnchors, threshold)
    return AlignmentSnapResult(
        delta = Vec2(
            x = rawDelta.x + (xMatch?.first ?: 0f),
            y = rawDelta.y + (yMatch?.first ?: 0f),
        ),
        verticalWorldX = xMatch?.second,
        horizontalWorldY = yMatch?.second,
    )
}

internal fun descendantObjectIds(
    workspace: Workspace,
    rootIds: Set<CanvasObjectId>,
): Set<CanvasObjectId> {
    if (rootIds.isEmpty()) return emptySet()
    val descendants = mutableSetOf<CanvasObjectId>()
    var frontier = rootIds
    while (frontier.isNotEmpty()) {
        val children = workspace.objects.values
            .filter { it.parentId in frontier && it.id !in descendants && it.id !in rootIds }
            .mapTo(mutableSetOf()) { it.id }
        if (children.isEmpty()) break
        descendants += children
        frontier = children
    }
    return descendants
}

internal fun movableSelectionObjectIds(
    workspace: Workspace,
    selectedIds: Set<CanvasObjectId>,
): Set<CanvasObjectId> {
    val selectedGroups = selectedIds.mapNotNull { id ->
        (workspace.objectById(id) as? GroupFrame)?.let { group ->
            setOf(group.id) + descendantObjectIds(workspace, setOf(group.id))
        }
    }
    val blockedGroupTrees = selectedGroups
        .filter { tree -> tree.any { workspace.objectById(it)?.locked != false } }
        .flatten()
        .toSet()
    val movableGroupTrees = selectedGroups
        .filterNot { tree -> tree.any { workspace.objectById(it)?.locked != false } }
        .flatten()
        .toSet()
    val movableDirectObjects = selectedIds.filterTo(mutableSetOf()) { id ->
        val canvasObject = workspace.objectById(id)
        canvasObject != null && canvasObject !is GroupFrame && !canvasObject.locked
    }
    return (movableGroupTrees + movableDirectObjects) - blockedGroupTrees
}

internal fun isLibraryDropOnCanvas(
    screenPosition: Vec2,
    canvasSize: CanvasSize,
    density: Float,
): Boolean {
    val libraryEdge = 312f * density
    val toolbarEdge = 106f * density
    val statusEdge = 58f * density
    return screenPosition.x >= libraryEdge &&
        screenPosition.x <= canvasSize.width &&
        screenPosition.y >= toolbarEdge &&
        screenPosition.y <= canvasSize.height - statusEdge
}

private fun closestAlignment(
    movingAnchors: List<Float>,
    targetAnchors: List<Float>,
    threshold: Float,
): Pair<Float, Float>? {
    var best: Pair<Float, Float>? = null
    movingAnchors.forEach { moving ->
        targetAnchors.forEach { target ->
            val correction = target - moving
            if (abs(correction) <= threshold && (best == null || abs(correction) < abs(best!!.first))) {
                best = correction to target
            }
        }
    }
    return best
}

@Composable
private fun AlignmentGuideCanvas(
    guides: AlignmentGuides,
    viewport: Viewport,
    density: Float,
) {
    if (guides.verticalWorldX == null && guides.horizontalWorldY == null) return
    val colors = BoarderLessTheme.colors
    Canvas(modifier = Modifier.fillMaxSize()) {
        guides.verticalWorldX?.let { worldX ->
            val screenX = viewport.worldToScreen(Vec2(worldX, 0f)).x
            drawLine(
                color = colors.selection.copy(alpha = 0.78f),
                start = Offset(screenX, 0f),
                end = Offset(screenX, size.height),
                strokeWidth = 1.25f * density,
            )
        }
        guides.horizontalWorldY?.let { worldY ->
            val screenY = viewport.worldToScreen(Vec2(0f, worldY)).y
            drawLine(
                color = colors.selection.copy(alpha = 0.78f),
                start = Offset(0f, screenY),
                end = Offset(size.width, screenY),
                strokeWidth = 1.25f * density,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    start: Vec2,
    end: Vec2,
    color: androidx.compose.ui.graphics.Color,
    density: Float,
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val unitX = dx / length
    val unitY = dy / length
    val arrowLength = 12f * density
    val halfWidth = 5f * density
    val baseX = end.x - unitX * arrowLength
    val baseY = end.y - unitY * arrowLength
    val perpendicularX = -unitY
    val perpendicularY = unitX
    drawLine(
        color = color,
        start = Offset(end.x, end.y),
        end = Offset(baseX + perpendicularX * halfWidth, baseY + perpendicularY * halfWidth),
        strokeWidth = 2f * density,
    )
    drawLine(
        color = color,
        start = Offset(end.x, end.y),
        end = Offset(baseX - perpendicularX * halfWidth, baseY - perpendicularY * halfWidth),
        strokeWidth = 2f * density,
    )
}

@Composable
private fun GridCanvas(
    viewport: Viewport,
    density: Float,
    showGrid: Boolean,
    gridColor: Color,
    marquee: Marquee?,
    areaSelectionMode: Boolean,
    onViewportChange: (Viewport) -> Unit,
    onTap: (Vec2) -> Unit,
    onDoubleTap: (Vec2) -> Unit,
    onMarqueeChange: (start: Vec2, end: Vec2) -> Unit,
    onMarqueeCancel: () -> Unit,
    onMarqueeCommit: (start: Vec2, end: Vec2, additive: Boolean) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val currentViewport by rememberUpdatedState(viewport)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.firstOrNull() ?: continue
                            val factor = if (change.scrollDelta.y > 0f) 0.9f else 1.1f
                            onViewportChange(
                                currentViewport.zoomAt(
                                    screenPoint = Vec2(change.position.x, change.position.y),
                                    factor = factor,
                                ),
                            )
                            change.consume()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap(Vec2(it.x, it.y)) },
                    onDoubleTap = { onDoubleTap(Vec2(it.x, it.y)) },
                )
            }
            .pointerInput(areaSelectionMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val additive = currentEvent.keyboardModifiers.isShiftPressed
                    if (!areaSelectionMode && !additive) return@awaitEachGesture
                    val start = Vec2(down.position.x, down.position.y)
                    var end = start
                    down.consume()
                    onMarqueeChange(start, end)
                    val completed = drag(down.id) { change ->
                        end = Vec2(change.position.x, change.position.y)
                        change.consume()
                        onMarqueeChange(start, end)
                    }
                    if (completed) onMarqueeCommit(start, end, additive) else onMarqueeCancel()
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    onViewportChange(
                        currentViewport
                            .zoomAt(
                                screenPoint = Vec2(centroid.x, centroid.y),
                                factor = gestureZoom,
                            )
                            .panBy(Vec2(pan.x, pan.y)),
                    )
                }
            },
    ) {
        val spacing = GridSize * density * viewport.zoom
        if (showGrid && spacing >= 10f) {
            var x = viewport.pan.x % spacing
            while (x < size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += spacing
            }

            var y = viewport.pan.y % spacing
            while (y < size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += spacing
            }
        }

        marquee?.let { selection ->
            val left = min(selection.start.x, selection.end.x)
            val top = min(selection.start.y, selection.end.y)
            val width = max(selection.start.x, selection.end.x) - left
            val height = max(selection.start.y, selection.end.y) - top
            drawRect(
                color = colors.selection.copy(alpha = 0.14f),
                topLeft = Offset(left, top),
                size = Size(width, height),
            )
            drawRect(
                color = colors.selection,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 1.5f * density),
            )
        }
    }
}

@Composable
private fun InspectorNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BoarderLessTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(
            text = label,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = TextStyle(color = colors.contentMuted, fontSize = 9.sp),
        )
        BasicTextField(
            value = value,
            onValueChange = { next ->
                if (next.length <= 16) onValueChange(next)
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                .border(1.dp, colors.contentBorder, RoundedCornerShape(8.dp))
                .onFocusChanged { onEditingChange(it.isFocused) }
                .padding(horizontal = 8.dp, vertical = 7.dp),
            textStyle = TextStyle(
                color = if (enabled) colors.contentText else colors.contentMuted,
                fontSize = 11.sp,
            ),
            cursorBrush = SolidColor(colors.selection),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            singleLine = true,
        )
    }
}

@Composable
private fun NodeTransformHandles(
    node: CanvasObject,
    objectLabel: String,
    connectionEnabled: Boolean = true,
    previewTransform: CanvasTransform,
    viewport: Viewport,
    largeTouchTargets: Boolean,
    onPreview: (CanvasTransform) -> Unit,
    onCancel: () -> Unit,
    onResizeCommit: (CanvasTransform) -> Unit,
    onRotateCommit: (CanvasTransform) -> Unit,
    onConnectionPreview: (start: Vec2, end: Vec2) -> Unit,
    onConnectionCancel: () -> Unit,
    onConnectionOpenPicker: () -> Unit,
    onConnectionCommit: (end: Vec2) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val density = LocalDensity.current.density
    val touchTargetSize = transformHandleTouchTargetDp(largeTouchTargets).dp
    val visualSize = transformHandleVisualSizeDp(largeTouchTargets).dp
    val handleRadius = transformHandleTouchTargetDp(largeTouchTargets) * density / 2f
    val screenTopLeft = viewport.worldToScreen(previewTransform.position)
    val screenWidth = previewTransform.size.width * viewport.zoom
    val screenHeight = previewTransform.size.height * viewport.zoom
    val center = Vec2(screenTopLeft.x + screenWidth / 2f, screenTopLeft.y + screenHeight / 2f)
    val topCenter = rotatePoint(
        point = Vec2(center.x, screenTopLeft.y),
        center = center,
        degrees = previewTransform.rotationDegrees,
    )
    val rotateCenter = rotatePoint(
        point = Vec2(center.x, screenTopLeft.y - (if (largeTouchTargets) 46f else 34f) * density),
        center = center,
        degrees = previewTransform.rotationDegrees,
    )
    val resizeCenter = rotatePoint(
        point = Vec2(screenTopLeft.x + screenWidth, screenTopLeft.y + screenHeight),
        center = center,
        degrees = previewTransform.rotationDegrees,
    )
    val connectionCenter = rotatePoint(
        point = Vec2(screenTopLeft.x + screenWidth, center.y),
        center = center,
        degrees = previewTransform.rotationDegrees,
    )
    val rotateHandleTopLeft = rotateCenter - Vec2(handleRadius, handleRadius)
    val connectionHandleTopLeft = connectionCenter - Vec2(handleRadius, handleRadius)
    val currentRotateHandleTopLeft by rememberUpdatedState(rotateHandleTopLeft)
    val currentConnectionHandleTopLeft by rememberUpdatedState(connectionHandleTopLeft)
    val currentConnectionCenter by rememberUpdatedState(connectionCenter)
    val currentCenter by rememberUpdatedState(center)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawLine(
            color = colors.selection,
            start = Offset(topCenter.x, topCenter.y),
            end = Offset(rotateCenter.x, rotateCenter.y),
            strokeWidth = 1.5f * density,
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (resizeCenter.x - handleRadius).roundToInt(),
                    y = (resizeCenter.y - handleRadius).roundToInt(),
                )
            }
            .width(touchTargetSize)
            .height(touchTargetSize)
            .semantics {
                contentDescription = Strings.a11y.resizeHandleFor(objectLabel.take(80))
                stateDescription = Strings.a11y.dragToResize()
                role = Role.Button
                onClick(label = Strings.a11y.increaseSize()) {
                    onResizeCommit(
                        accessibilityResizedTransform(
                            transform = node.transform,
                            step = 32f * density,
                        ),
                    )
                    true
                }
            }
            .pointerInput(node.id, node.version, viewport.zoom, "resize-handle") {
                var accumulated = Vec2.Zero
                var latest = node.transform
                detectDragGestures(
                    onDragStart = {
                        accumulated = Vec2.Zero
                        latest = node.transform
                    },
                    onDragCancel = onCancel,
                    onDragEnd = { onResizeCommit(latest) },
                ) { change, dragAmount ->
                    change.consume()
                    accumulated += Vec2(dragAmount.x, dragAmount.y)
                    latest = resizedTransform(
                        transform = node.transform,
                        screenDelta = accumulated,
                        zoom = viewport.zoom,
                        minimumSize = CanvasSize(120f * density, 72f * density),
                    )
                    onPreview(latest)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .width(visualSize)
                .height(visualSize)
                .shadow(5.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(colors.selection)
                .border(2.dp, colors.contentText, RoundedCornerShape(6.dp)),
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = rotateHandleTopLeft.x.roundToInt(),
                    y = rotateHandleTopLeft.y.roundToInt(),
                )
            }
            .width(touchTargetSize)
            .height(touchTargetSize)
            .semantics {
                contentDescription = Strings.a11y.rotateHandleFor(objectLabel.take(80))
                stateDescription = Strings.a11y.dragToRotate()
                role = Role.Button
                onClick(label = Strings.a11y.rotateClockwise15Degrees()) {
                    onRotateCommit(accessibilityRotatedTransform(node.transform))
                    true
                }
            }
            .pointerInput(node.id, node.version, viewport.zoom, "rotate-handle") {
                var latest = node.transform
                detectDragGestures(
                    onDragStart = { latest = node.transform },
                    onDragCancel = onCancel,
                    onDragEnd = { onRotateCommit(latest) },
                ) { change, _ ->
                    change.consume()
                    val handleTopLeft = currentRotateHandleTopLeft
                    val screenPoint = handleTopLeft + Vec2(change.position.x, change.position.y)
                    val nodeCenter = currentCenter
                    latest = node.transform.copy(
                        rotationDegrees = rotationDegreesForPointer(nodeCenter, screenPoint),
                    )
                    onPreview(latest)
                }
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(visualSize)
                .height(visualSize)
                .shadow(5.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(colors.selection)
                .border(2.dp, colors.contentText, RoundedCornerShape(50)),
        ) {
            BasicText(
                text = "↻",
                style = TextStyle(
                    color = colors.contentText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    if (connectionEnabled) Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = connectionHandleTopLeft.x.roundToInt(),
                    y = connectionHandleTopLeft.y.roundToInt(),
                )
            }
            .width(touchTargetSize)
            .height(touchTargetSize)
            .semantics {
                contentDescription = Strings.a11y.connectorHandleFor(objectLabel.take(80))
                stateDescription = Strings.a11y.dragOntoAnotherThoughtTo()
                role = Role.Button
                onClick(label = Strings.a11y.chooseConnectionTarget()) {
                    onConnectionOpenPicker()
                    true
                }
            }
            .pointerInput(node.id, node.version, viewport.zoom, "connector-handle") {
                var latestEnd = currentConnectionCenter
                detectDragGestures(
                    onDragStart = {
                        latestEnd = currentConnectionCenter
                        onConnectionPreview(currentConnectionCenter, latestEnd)
                    },
                    onDragCancel = onConnectionCancel,
                    onDragEnd = { onConnectionCommit(latestEnd) },
                ) { change, _ ->
                    change.consume()
                    latestEnd = currentConnectionHandleTopLeft + Vec2(change.position.x, change.position.y)
                    onConnectionPreview(currentConnectionCenter, latestEnd)
                }
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(visualSize)
                .height(visualSize)
                .shadow(5.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(colors.accent)
                .border(2.dp, colors.contentText, RoundedCornerShape(50)),
        ) {
            BasicText(
                text = "→",
                style = TextStyle(
                    color = colors.contentText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

private fun rotatePoint(point: Vec2, center: Vec2, degrees: Float): Vec2 {
    val rotated = rotateVector(point - center, degrees)
    return center + rotated
}

internal fun rotateVector(vector: Vec2, degrees: Float): Vec2 {
    val radians = degrees / 180f * PI.toFloat()
    val cosine = cos(radians)
    val sine = sin(radians)
    return Vec2(
        x = vector.x * cosine - vector.y * sine,
        y = vector.x * sine + vector.y * cosine,
    )
}

internal fun normalizeDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

internal fun resizedTransform(
    transform: CanvasTransform,
    screenDelta: Vec2,
    zoom: Float,
    minimumSize: CanvasSize,
): CanvasTransform {
    val localDelta = rotateVector(
        vector = screenDelta / zoom,
        degrees = -transform.rotationDegrees,
    )
    return transform.copy(
        size = CanvasSize(
            width = max(transform.size.width + localDelta.x, minimumSize.width),
            height = max(transform.size.height + localDelta.y, minimumSize.height),
        ),
    )
}

internal fun rotationDegreesForPointer(center: Vec2, pointer: Vec2): Float {
    val angle = atan2(
        y = pointer.y - center.y,
        x = pointer.x - center.x,
    ) * 180f / PI.toFloat() + 90f
    return normalizeDegrees(angle)
}

@Composable
private fun TextNodeCard(
    node: TextNode,
    viewport: Viewport,
    previewDelta: Vec2,
    previewTransform: CanvasTransform?,
    selected: Boolean,
    reduceMotion: Boolean,
    editing: Boolean,
    interactionEnabled: Boolean,
    mutationEnabled: Boolean,
    tapSelectedToEdit: Boolean,
    onKeyboardFocus: () -> Unit,
    onSelect: (additive: Boolean) -> Unit,
    onStartEditing: () -> Unit,
    onDragPreview: (Vec2, bypassSnapping: Boolean) -> Unit,
    onDragCancel: () -> Unit,
    onDragCommit: (Vec2, bypassSnapping: Boolean) -> Unit,
    onTextCommit: (String) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val density = LocalDensity.current
    val renderedTransform = previewTransform ?: node.transform
    val screenPosition = viewport.worldToScreen(renderedTransform.position + previewDelta)
    val screenWidth = with(density) { (renderedTransform.size.width * viewport.zoom).toDp() }
    val screenHeight = with(density) { (renderedTransform.size.height * viewport.zoom).toDp() }
    val shape = node.shape.composeShape()
    val horizontalContentPadding = node.shape.horizontalContentPadding(screenWidth)
    val verticalContentPadding = node.shape.verticalContentPadding(screenHeight)
    val centeredContent = node.shape != NodeShape.RoundedRectangle
    val textColors = nodeTextColors(node.colorToken, colors)
    var draft by remember(node.id, node.text) { mutableStateOf(node.text) }
    val targetScale = if (selected) 1.012f else 1f
    val animatedScale by animateFloatAsState(targetValue = targetScale, label = "node-selection-scale")
    val targetElevation = if (selected) 12f else 5f
    val animatedElevation by animateFloatAsState(targetValue = targetElevation, label = "node-selection-shadow")
    val selectionScale = if (reduceMotion) targetScale else animatedScale
    val selectionElevation = if (reduceMotion) targetElevation else animatedElevation

    Box(
        modifier = Modifier
            .canvasObjectBounds(screenPosition, screenWidth, screenHeight)
            .graphicsLayer(
                rotationZ = renderedTransform.rotationDegrees,
                scaleX = selectionScale,
                scaleY = selectionScale,
            )
            .shadow(elevation = selectionElevation.dp, shape = shape)
            .clip(shape)
            .background(nodeFillColor(node.colorToken, colors))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.selection else colors.contentBorder,
                shape = shape,
            )
            .semantics {
                contentDescription = Strings.a11y.thought(node.text.take(120))
                this.selected = selected
                stateDescription = when {
                    editing -> Strings.common.editing()
                    node.locked -> Strings.common.locked()
                    !mutationEnabled -> Strings.common.readOnly()
                    else -> Strings.common.editable()
                }
                role = Role.Button
                onClick(
                    label = if (tapSelectedToEdit && selected && !node.locked && mutationEnabled) {
                        Strings.a11y.editThought()
                    } else {
                        Strings.a11y.selectThought()
                    },
                ) {
                    if (tapSelectedToEdit && selected && !node.locked && mutationEnabled) {
                        onStartEditing()
                    } else {
                        onSelect(false)
                    }
                    true
                }
            }
            .onFocusChanged { state ->
                if (state.isFocused) onKeyboardFocus()
            }
            .focusable(enabled = interactionEnabled)
            .pointerInput(node.id, viewport.zoom, editing, mutationEnabled, node.version) {
                if (interactionEnabled && !editing) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val halfWidth = renderedTransform.size.width * viewport.zoom / 2f
                        val halfHeight = renderedTransform.size.height * viewport.zoom / 2f
                        if (!node.shape.containsLocalPoint(
                                point = Vec2(down.position.x - halfWidth, down.position.y - halfHeight),
                                halfWidth = halfWidth,
                                halfHeight = halfHeight,
                            )
                        ) {
                            return@awaitEachGesture
                        }
                        val additiveSelection = currentEvent.keyboardModifiers.isShiftPressed
                        down.consume()
                        var accumulated = Vec2.Zero
                        var moved = false
                        var bypassSnapping = false
                        val completed = drag(down.id) { change ->
                            val dragAmount = change.positionChange()
                            if (dragAmount != Offset.Zero) {
                                if (!node.locked && mutationEnabled) {
                                    moved = true
                                    bypassSnapping = currentEvent.keyboardModifiers.isAltPressed
                                    change.consume()
                                    accumulated += Vec2(dragAmount.x, dragAmount.y) / viewport.zoom
                                    onDragPreview(accumulated, bypassSnapping)
                                }
                            }
                        }

                        when {
                            !completed -> onDragCancel()
                            moved -> {
                                onDragCommit(accumulated, bypassSnapping)
                            }
                            tapSelectedToEdit && selected && !additiveSelection && !node.locked && mutationEnabled ->
                                onStartEditing()
                            else -> {
                                onSelect(additiveSelection)
                            }
                        }
                    }
                }
            },
    ) {
        if (node.shape == NodeShape.Database) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = if (selected) colors.selection else colors.contentBorder,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height * 0.3f),
                    style = Stroke(if (selected) 2.dp.toPx() else 1.dp.toPx()),
                )
            }
        }
        if (editing) {
            NodeTextEditor(
                value = draft,
                textColor = textColors.text,
                onValueChange = { draft = it },
                onCommit = { onTextCommit(draft) },
                horizontalPadding = horizontalContentPadding,
                verticalPadding = verticalContentPadding,
                centered = centeredContent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalContentPadding, vertical = verticalContentPadding),
                verticalArrangement = if (centeredContent) Arrangement.Center else Arrangement.Top,
            ) {
                BasicText(
                    text = node.text,
                    modifier = if (centeredContent) Modifier.fillMaxWidth() else Modifier,
                    style = TextStyle(
                        color = textColors.text,
                        fontSize = (16f * viewport.zoom.coerceIn(0.8f, 1.25f)).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = if (centeredContent) TextAlign.Center else TextAlign.Start,
                    ),
                )
                if (node.locked) {
                    BasicText(
                        text = Strings.common.locked(),
                        modifier = Modifier.padding(top = 8.dp),
                        style = TextStyle(
                            color = textColors.muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaNodeCard(
    node: MediaNode,
    asset: WorkspaceAsset?,
    session: WorkspaceSession?,
    mediaRuntime: MediaImportRuntime,
    reduceMotion: Boolean,
    canvasSize: IntSize,
    viewport: Viewport,
    previewDelta: Vec2,
    previewTransform: CanvasTransform?,
    selected: Boolean,
    interactionEnabled: Boolean,
    mutationEnabled: Boolean,
    onSelect: (additive: Boolean) -> Unit,
    onDragPreview: (Vec2) -> Unit,
    onDragCancel: () -> Unit,
    onDragCommit: (Vec2) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val density = LocalDensity.current
    val renderedTransform = previewTransform ?: node.transform
    val screenPosition = viewport.worldToScreen(renderedTransform.position + previewDelta)
    val screenWidth = with(density) { (renderedTransform.size.width * viewport.zoom).toDp() }
    val screenHeight = with(density) { (renderedTransform.size.height * viewport.zoom).toDp() }
    val previewAssetId = node.thumbnailAssetId ?: node.assetId.takeIf { node.mediaKind != MediaKind.Video }
    val radius = kotlin.math.hypot(renderedTransform.size.width, renderedTransform.size.height) * viewport.zoom
    val visible = screenPosition.x + radius >= 0 && screenPosition.y + radius >= 0 &&
        screenPosition.x - radius <= canvasSize.width && screenPosition.y - radius <= canvasSize.height
    // Session-scoped state is discarded on account/workspace/reference changes; never persisted.
    var preview by remember(session?.userId, session?.workspace?.id, previewAssetId, asset) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var previewLoading by remember(session?.userId, session?.workspace?.id, previewAssetId, asset) { mutableStateOf(false) }
    var previewFailed by remember(session?.userId, session?.workspace?.id, previewAssetId, asset) { mutableStateOf(false) }
    var previewAttempt by remember(session?.userId, session?.workspace?.id, previewAssetId, asset) { mutableStateOf(0) }
    val activityFlow = remember(mediaRuntime) { mediaRuntime.playbackActivity ?: MutableStateFlow(MediaPlaybackActivity()) }
    val playbackActivity by activityFlow.collectAsState()
    var gifActivated by remember(session?.userId, session?.workspace?.id, node.assetId, playbackActivity.epoch) { mutableStateOf(false) }
    var gifPlaying by remember(session?.userId, session?.workspace?.id, node.assetId, playbackActivity.epoch) { mutableStateOf(false) }
    var gifFinished by remember(session?.userId, session?.workspace?.id, node.assetId, playbackActivity.epoch) { mutableStateOf(false) }
    val currentGifPlaying by rememberUpdatedState(gifPlaying)
    LaunchedEffect(session?.userId, session?.workspace?.id, node.assetId, node.mediaKind, previewAssetId, asset, visible, mediaRuntime, previewAttempt, gifActivated, reduceMotion, playbackActivity) {
        preview = null
        previewFailed = false
        previewLoading = false
        val loader = mediaRuntime.loadPreview
        if (visible && playbackActivity.available && asset?.status == AssetStatus.Ready && session != null && previewAssetId != null && loader != null) {
            previewLoading = true
            var animation: GifAnimation? = null
            try {
                val gifLoader = mediaRuntime.loadGif
                if (node.mediaKind == MediaKind.Gif && gifActivated && !reduceMotion && gifLoader != null) {
                    val opened = gifLoader(session, node.assetId).also { animation = it }
                    gifFinished = false
                    playGifFrames(
                        opened.frameCount, opened.repetitionCount, opened::durationMs,
                        awaitPlaying = { snapshotFlow { currentGifPlaying }.first { it } },
                        showFrame = { index ->
                            val frame = opened.frame(index)
                            if (index != 0) snapshotFlow { currentGifPlaying }.first { it }
                            preview = frame
                            previewLoading = false
                        },
                    )
                    gifPlaying = false
                    gifFinished = true
                } else preview = loader(session, previewAssetId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                previewFailed = true
                gifFinished = true
                gifPlaying = false
            } finally {
                previewLoading = false
                withContext(NonCancellable) { animation?.release() }
            }
        }
    }
    val kindLabel = when (node.mediaKind) {
        MediaKind.Image -> Strings.objects.image()
        MediaKind.Gif -> Strings.objects.gif()
        MediaKind.Video -> Strings.objects.video()
    }
    val statusLabel = when (asset?.status) {
        AssetStatus.Pending -> Strings.media.pending()
        AssetStatus.Ready -> when {
            previewFailed -> Strings.media.retryPreview()
            preview != null -> Strings.media.previewReady()
            previewLoading -> Strings.media.loadingPreview()
            else -> if (mediaRuntime.loadPreview == null) Strings.media.readyDownloadUnavailable()
                else Strings.media.previewUnavailable()
        }
        AssetStatus.Rejected -> Strings.media.rejected()
        AssetStatus.Missing -> Strings.media.missing()
        null -> Strings.media.metadataUnavailable()
    }
    val accessibleName = node.altText.ifBlank { kindLabel }

    Box(
        modifier = Modifier
            .canvasObjectBounds(screenPosition, screenWidth, screenHeight)
            .graphicsLayer(rotationZ = renderedTransform.rotationDegrees)
            .shadow(if (selected) 12.dp else 5.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(colors.canvas.copy(alpha = 0.94f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.selection else colors.contentBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .semantics {
                contentDescription = "$kindLabel • $accessibleName"
                this.selected = selected
                stateDescription = when {
                    node.locked -> "${Strings.common.locked()} • $statusLabel"
                    !mutationEnabled -> "${Strings.common.readOnly()} • $statusLabel"
                    else -> statusLabel
                }
                role = Role.Button
                onClick(label = Strings.a11y.selectThought()) {
                    if (previewFailed) previewAttempt++
                    onSelect(false)
                    true
                }
            }
            .focusable(enabled = interactionEnabled)
            .pointerInput(node.id, viewport.zoom, mutationEnabled, node.version) {
                if (interactionEnabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val additiveSelection = currentEvent.keyboardModifiers.isShiftPressed
                        down.consume()
                        var accumulated = Vec2.Zero
                        var moved = false
                        val completed = drag(down.id) { change ->
                            val dragAmount = change.positionChange()
                            if (dragAmount != Offset.Zero && !node.locked && mutationEnabled) {
                                moved = true
                                change.consume()
                                accumulated += Vec2(dragAmount.x, dragAmount.y) / viewport.zoom
                                onDragPreview(accumulated)
                            }
                        }
                        when {
                            !completed -> onDragCancel()
                            moved -> onDragCommit(accumulated)
                            else -> {
                                if (previewFailed) previewAttempt++
                                onSelect(additiveSelection)
                            }
                        }
                    }
                }
            },
    ) {
        val displayedPreview = preview
        if (displayedPreview != null) {
            Image(
                bitmap = displayedPreview,
                contentDescription = null, // The outer object owns its accessible name and selection.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = when (node.mediaKind) {
                    MediaKind.Image -> "▧"
                    MediaKind.Gif -> "GIF"
                    MediaKind.Video -> "▶"
                },
                style = TextStyle(
                    color = if (selected) colors.selection else colors.accent,
                    fontSize = (if (node.mediaKind == MediaKind.Gif) 18f else 28f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            BasicText(
                text = accessibleName,
                modifier = Modifier.padding(top = 8.dp),
                style = TextStyle(
                    color = colors.contentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = statusLabel,
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(
                    color = colors.contentMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (asset == null) {
                BasicText(
                    text = Strings.media.assetReference(node.assetId.take(12)),
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(color = colors.contentMuted, fontSize = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        }
        if (node.mediaKind == MediaKind.Video && mediaRuntime.loadVideo != null && mediaRuntime.videoSurface != null) {
            MediaVideoContent(node, asset, session, mediaRuntime, visible, selected, interactionEnabled, reduceMotion)
        }
        if (selected && node.mediaKind == MediaKind.Gif && mediaRuntime.loadGif != null) {
            ShellButton(
                label = if (gifPlaying && !reduceMotion) Strings.media.pauseAnimation() else Strings.media.playAnimation(),
                enabled = interactionEnabled && playbackActivity.available && asset?.status == AssetStatus.Ready && !reduceMotion,
                accent = true,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                onClick = {
                    if (gifFinished) previewAttempt++
                    gifActivated = true
                    gifPlaying = !gifPlaying
                },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.MediaVideoContent(
    node: MediaNode, asset: WorkspaceAsset?, session: WorkspaceSession?, runtime: MediaImportRuntime,
    visible: Boolean, selected: Boolean, interactionEnabled: Boolean, reduceMotion: Boolean,
) {
    // Only explicit playback creates a player. All state/handles remain local to this composition.
    val activityFlow = remember(runtime) { runtime.playbackActivity ?: MutableStateFlow(MediaPlaybackActivity()) }
    val playbackActivity by activityFlow.collectAsState()
    val key = listOf(session?.userId, session?.workspace?.id, node.assetId, asset, runtime, playbackActivity.epoch)
    var activated by remember(key) { mutableStateOf(false) }
    var attempt by remember(key) { mutableStateOf(0) }
    var loading by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    var lease by remember(key) { mutableStateOf<VideoPlaybackLease?>(null) }
    val playback = lease?.player
    val scope = rememberCoroutineScope()
    val emptyState = remember { MutableStateFlow(VideoPlaybackState()) }
    val state by (playback?.state ?: emptyState).collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(key, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) activated = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(key, activated, attempt, visible, reduceMotion, playbackActivity.available) {
        failed = false
        if (activated && visible && playbackActivity.available && !reduceMotion && session != null && asset?.status == AssetStatus.Ready) {
            loading = true
            var opened: VideoPlaybackLease? = null
            try {
                val owned = VideoPlaybackLease(checkNotNull(runtime.loadVideo)(session, node.assetId)).also { opened = it }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                lease = owned
                loading = false
                owned.execute { setPlaying(true) }
                kotlinx.coroutines.flow.combine(owned.player.state, owned.failed) { native, commandFailed ->
                    native.failed || native.released || commandFailed
                }.first { it }
                failed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally {
                loading = false
                lease = null
                try { withContext(NonCancellable) { opened?.release() } }
                catch (_: Exception) { failed = true }
            }
        }
    }
    fun command(block: suspend VideoPlayback.() -> Unit) {
        val owned = lease ?: return
        scope.launch {
            try { owned.execute(block) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }
    playback?.takeIf { !state.released && !state.failed }?.let { player ->
        runtime.videoSurface?.invoke(player, Modifier.fillMaxSize())
    }
    if (selected) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(BoarderLessTheme.colors.contentSurface.copy(alpha = .9f)).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (playback != null) BasicText(
                text = "${state.positionMs / 1000} / ${state.durationMs / 1000} s",
                style = TextStyle(color = BoarderLessTheme.colors.contentText, fontSize = 10.sp),
            )
            ShellButton(
                label = when {
                    loading -> Strings.media.videoLoading()
                    failed || state.failed || state.released -> Strings.media.videoFailed()
                    state.audioFocusBlocked -> Strings.media.audioFocusBlocked()
                    state.playing -> Strings.media.pauseVideo()
                    else -> Strings.media.playVideo()
                },
                enabled = interactionEnabled && playbackActivity.available && asset?.status == AssetStatus.Ready && !loading && !reduceMotion,
                accent = true,
                onClick = {
                    val player = playback
                    if (player == null || state.failed || state.released) { attempt++; activated = true }
                    else command { setPlaying(!state.playing) }
                },
            )
            if (playback != null && !state.failed && !state.released) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShellButton(label = Strings.media.videoBack(), enabled = interactionEnabled,
                        onClick = { command { seekTo(state.positionMs - 10_000) } })
                    ShellButton(label = Strings.media.videoForward(), enabled = interactionEnabled,
                        onClick = { command { seekTo(state.positionMs + 10_000) } })
                }
                ShellButton(label = if (state.muted) Strings.media.unmuteVideo() else Strings.media.muteVideo(), enabled = interactionEnabled,
                    onClick = { command { setMuted(!state.muted) } })
            }
        }
    }
}

@Composable
private fun NodeTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp = 18.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 18.dp,
    centered: Boolean = false,
    textColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val colors = BoarderLessTheme.colors
    val focusRequester = remember { FocusRequester() }
    var receivedFocus by remember { mutableStateOf(false) }
    var commitRequested by remember { mutableStateOf(false) }
    fun commitOnce() {
        if (!commitRequested) {
            commitRequested = true
            onCommit()
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commitOnce() }),
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Tab || event.key == Key.Escape)
                ) {
                    commitOnce()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    receivedFocus = true
                } else if (receivedFocus) {
                    commitOnce()
                }
            },
        textStyle = TextStyle(
            color = textColor ?: colors.contentText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        ),
        cursorBrush = SolidColor(colors.accent),
    )

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
}
