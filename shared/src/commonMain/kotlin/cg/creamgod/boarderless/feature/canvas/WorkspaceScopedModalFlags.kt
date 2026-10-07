package cg.creamgod.boarderless.feature.canvas

import androidx.compose.runtime.mutableStateOf

/** Independent flags recreated together at the existing workspace review scope boundary. */
internal class WorkspaceScopedModalFlags {
    val stoppedRecoveryUnavailable = mutableStateOf(false)
    val showStoppedRecovery = mutableStateOf(false)
    val schemeTransferBusy = mutableStateOf(false)
    val incomingSchemeConfirmed = mutableStateOf(false)
    val showSchemeBundleImport = mutableStateOf(false)
    val exportSchemeBundle = mutableStateOf<cg.creamgod.boarderless.data.persistence.QuickScheme?>(null)
    val showDraftImport = mutableStateOf(false)
    val pendingClipboardPaste = mutableStateOf<ClipboardPasteDraft?>(null)
    val showSequenceCreation = mutableStateOf(false)
    val showPenDraft = mutableStateOf(false)
    val showAiUnderstanding = mutableStateOf(false)
    val showAiDiagram = mutableStateOf(false)
}
