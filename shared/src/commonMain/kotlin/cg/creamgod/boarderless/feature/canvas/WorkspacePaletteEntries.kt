package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.*

/** Read-only palette snapshot; invoking a command still rechecks the live mutation guards. */
internal data class WorkspacePaletteContext(
    val workspace: Workspace,
    val selectedIds: Set<CanvasObjectId>,
    val selectedRelationId: RelationId?,
    val supportsSequence: Boolean,
    val supportsVectors: Boolean,
    val canvasInteractionBlocked: Boolean,
    val queueEmpty: Boolean,
    val syncInProgress: Boolean,
    val inputBlocked: Boolean,
    val canEdit: Boolean,
    val hasSession: Boolean,
    val connectionFailed: Boolean,
    val workspaceSwitchInProgress: Boolean,
    val pendingRecoveryBusy: Boolean,
    val canImportMedia: Boolean,
    val mediaImportBusy: Boolean,
    val mediaImportLabel: String,
    val workspaceChangeAllowed: Boolean,
    val components: List<ComponentLibraryEntry>,
    val areaSelectionMode: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val canAutoLayout: Boolean,
    val canDistribute: Boolean,
    val canSaveScheme: Boolean,
    val backgroundLabel: String,
    val showGrid: Boolean,
    val snapToGrid: Boolean,
    val reduceTransparency: Boolean,
    val reduceMotion: Boolean,
    val languagePreference: LanguagePreference,
)

internal fun workspacePaletteEntries(context: WorkspacePaletteContext): List<PaletteEntry> =
    with(context) {
        buildList {
            add(
                PaletteEntry(
                    id = "ai-understanding",
                    title = Strings.aiProbe.title(),
                    subtitle = Strings.aiProbe.warning(),
                    keywords = "ai json understanding diagram 理解 驗證 人工智慧",
                    enabled = !canvasInteractionBlocked && queueEmpty && !syncInProgress,
                ),
            )
            if (supportsSequence) {
                add(
                    PaletteEntry(
                        id = "create-sequence",
                        title = Strings.sequence.createTitle(),
                        subtitle = Strings.sequence.authoringHint(),
                        keywords = "sequence mermaid participant message 時序圖 生命線 參與者 訊息",
                        enabled =
                            !inputBlocked && canEdit == true && queueEmpty && !syncInProgress,
                    ),
                )
            }
            add(
                PaletteEntry(
                    id = "pen-path-draft",
                    title = Strings.penDraft.title(),
                    subtitle = if (supportsVectors) Strings.penDraft.insertHint() else Strings.penDraft.warning(),
                    keywords = "pen anchor bezier vector custom path 鋼筆 自訂 圖形 曲線 控制點",
                    enabled = !inputBlocked && canEdit == true,
                ),
            )
            add(
                PaletteEntry(
                    id = "inspect-draft-backup",
                    title = Strings.draftImport.title(),
                    subtitle = Strings.draftImport.previewWarning(),
                    keywords = "draft backup json import inspect 草稿 備份 匯入 檢視",
                    enabled = hasSession && !connectionFailed && !workspaceSwitchInProgress && !pendingRecoveryBusy && queueEmpty,
                ),
            )
            if (canImportMedia) {
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
                    components = components,
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
                    subtitle =
                        if (areaSelectionMode) {
                            Strings.palette.returnEmptyCanvasDraggingTo()
                        } else {
                            Strings.palette.dragEmptyCanvasToReplace()
                        },
                    keywords = paletteKeywords(Strings.palette.keywords.marqueeBoxMultiSelectDrag),
                ),
            )
            val editableSelectedNode =
                selectedIds
                    .singleOrNull()
                    ?.let(workspace::objectById) as? TextNode
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
                    enabled = !inputBlocked && canUndo,
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
                    enabled = !inputBlocked && canRedo,
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
            val canConnectSelected =
                selectedIds.size == 2 &&
                    selectedIds.all { workspace.objectById(it).let { node -> node is TextNode || node is MediaNode } } &&
                    !inputBlocked
            addAll(
                relationPaletteEntries(
                    enabled = canConnectSelected,
                    disabledReason = Strings.palette.selectExactlyTwoThoughts(),
                ),
            )
            val selectedConnectionSource =
                selectedIds
                    .singleOrNull()
                    ?.let(workspace::objectById)
                    ?.takeIf { it is TextNode || it is MediaNode }
            if (selectedConnectionSource != null) {
                addAll(
                    connectionTargetPaletteEntries(
                        sourceId = selectedConnectionSource.id,
                        nodes = workspace.objects.values.connectableNodes(),
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
                    enabled = canAutoLayout,
                    disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
                ),
            )
            add(
                PaletteEntry(
                    id = "layout-tree",
                    title = Strings.layout.layoutSelectionAsTree(),
                    subtitle = Strings.layout.arrangeRelationDirectionIntoHierarchy(),
                    keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutHierarchyTreeOrganization),
                    enabled = canAutoLayout,
                    disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
                ),
            )
            add(
                PaletteEntry(
                    id = "layout-radial",
                    title = Strings.layout.layoutSelectionRadially(),
                    subtitle = Strings.layout.arrangeCentralNodeAndIts(),
                    keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutRadialRelationshipTopology),
                    enabled = canAutoLayout,
                    disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
                ),
            )
            add(
                PaletteEntry(
                    id = "layout-grid",
                    title = Strings.layout.layoutSelectionAsGrid(),
                    subtitle = Strings.layout.arrangeMixedSizeNodesInto(),
                    keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutGridArchitectureTopology),
                    enabled = canAutoLayout,
                    disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
                ),
            )
            add(
                PaletteEntry(
                    id = "distribute-horizontal",
                    title = Strings.layout.distributeSelectionHorizontally(),
                    subtitle = Strings.layout.keepOuterNodesAndEqualize(),
                    keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutDistributeSpacingHorizontal),
                    enabled = canDistribute,
                    disabledReason = Strings.layout.selectThreeOrMoreUnlocked(),
                ),
            )
            add(
                PaletteEntry(
                    id = "distribute-vertical",
                    title = Strings.layout.distributeSelectionVertically(),
                    subtitle = Strings.layout.keepOuterNodesAndEqualizeVertical(),
                    keywords = paletteKeywords(Strings.palette.keywords.diagramLayoutDistributeSpacingVertical),
                    enabled = canDistribute,
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
                        enabled = canAutoLayout,
                        disabledReason = Strings.layout.selectTwoOrMoreUnlocked(),
                    ),
                )
            }
            val selectedGroup =
                selectedIds
                    .singleOrNull()
                    ?.let(workspace::objectById) as? GroupFrame
            val selectedGroupHasContents =
                selectedGroup?.let { group ->
                    descendantObjectIds(workspace, setOf(group.id)).isNotEmpty()
                } == true
            add(
                PaletteEntry(
                    id = "fit-group",
                    title = Strings.common.fitGroupToContents(),
                    subtitle = Strings.common.resizeSelectedFrameAroundAll(),
                    keywords = paletteKeywords(Strings.palette.keywords.groupFrameFitContentsResize),
                    enabled =
                        selectedGroup != null && selectedGroup.id !in workspace.sequenceDiagrams && !selectedGroup.locked &&
                            selectedGroupHasContents &&
                            !inputBlocked,
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
                    enabled =
                        selectedGroup != null && selectedGroup.id !in workspace.sequenceDiagrams && !selectedGroup.locked && !inputBlocked,
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
                    enabled = hasSession && !connectionFailed,
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
                    enabled = canSaveScheme && !inputBlocked,
                    disabledReason = Strings.palette.selectAtLeastOneText(),
                ),
            )
            add(
                PaletteEntry(
                    id = "canvas-background",
                    title = Strings.canvasBackground.chooseCanvasBackgroundColor(),
                    subtitle = Strings.canvasBackground.current(backgroundLabel),
                    keywords = paletteKeywords(Strings.palette.keywords.canvasBackgroundColorPickerHex),
                ),
            )
            add(
                PaletteEntry(
                    id = "toggle-grid",
                    title = if (showGrid) Strings.palette.hideGrid() else Strings.palette.showGrid(),
                    subtitle = Strings.palette.changeGridVisibilityWithoutChanging(),
                    keywords = paletteKeywords(Strings.palette.keywords.canvasLines),
                ),
            )
            add(
                PaletteEntry(
                    id = "toggle-snap",
                    title = if (snapToGrid) Strings.palette.disableGridSnap() else Strings.palette.enableGridSnap(),
                    subtitle = Strings.palette.changePlacementPrecision(),
                    keywords = paletteKeywords(Strings.palette.keywords.canvasAlignPrecision),
                ),
            )
            add(
                PaletteEntry(
                    id = "toggle-transparency",
                    title = if (reduceTransparency) Strings.palette.useTranslucentInterface() else Strings.palette.reduceTransparency(),
                    subtitle =
                        if (reduceTransparency) {
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
                    subtitle =
                        if (reduceMotion) {
                            Strings.palette.restoreSubtleSelectionFeedback()
                        } else {
                            Strings.palette.disableNonEssentialSelectionAnimation()
                        },
                    keywords = paletteKeywords(Strings.palette.keywords.accessibilityAnimationMotionMovementTransition),
                ),
            )
            val languageKeywords =
                paletteKeywords(Strings.palette.keywords.languageLocaleTranslateEnglishChinese) +
                    Localization.languages.joinToString(separator = " ", prefix = " ") { it.nativeName }
            (listOf(null) + Localization.languages).forEach { language ->
                val preference = language?.let(LanguagePreference::of) ?: LanguagePreference.System
                add(
                    PaletteEntry(
                        id = "language:${preference.token}",
                        title = language?.let { Strings.palette.language(it.nativeName) } ?: Strings.palette.languageFollowSystem(),
                        subtitle =
                            language?.let { Strings.palette.showInterfaceIn(it.nativeName) }
                                ?: Strings.palette.matchDeviceLanguage(),
                        keywords = languageKeywords,
                        enabled = preference != languagePreference,
                        disabledReason = Strings.common.alreadyInUse(),
                    ),
                )
            }
            workspace.objects.values
                .filterIsInstance<TextNode>()
                .sortedByDescending { it.zIndex }
                .forEach { node ->
                    add(
                        PaletteEntry(
                            id = "find:${node.id.value}",
                            title =
                                node.text
                                    .lineSequence()
                                    .firstOrNull()
                                    ?.take(80)
                                    .orEmpty()
                                    .ifBlank { Strings.content.untitledThought() },
                            subtitle = Strings.palette.jumpToThought(),
                            keywords = "${paletteKeywords(Strings.palette.keywords.contentNode)} ${node.text}",
                        ),
                    )
                }
            addAll(groupNavigationPaletteEntries(workspace.objects.values.filterIsInstance<GroupFrame>()))
            addAll(
                relationNavigationPaletteEntries(
                    relations = workspace.relations.values,
                    nodesById =
                        workspace.objects.values
                            .connectableNodes()
                            .associateBy { it.id },
                ),
            )
        }
    }
