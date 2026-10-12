package cg.creamgod.boarderless.feature.canvas

import androidx.compose.runtime.*
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.i18n.Strings

/** Two explicit reviews, one final durable save. Neither phase edits either canvas. */
@Composable internal fun LocalSyncRebaseDialog(snapshot: LocalWorkspaceSyncSnapshot, context: LocalSyncRebaseContext,
    canContinue: () -> Boolean, onStage: suspend (LocalSyncRebaseProposal) -> Unit, onDismiss: () -> Unit) {
    val plan = remember(snapshot.local.workspace, context) { LocalSyncRebasePlan(snapshot.local, context) }
    val saved = snapshot.rebaseProposal?.takeIf { snapshot.rebaseIsCurrent && it.context == context }
    var intentChoices by remember(plan) { mutableStateOf<Map<String, LocalSyncMergeChoice>?>(null) }
    val intent = intentChoices
    if (intent == null) LocalSyncMergeDialog(plan.intentSnapshot, context.committedSession(), canContinue,
        onStage = { check(canContinue()); intentChoices = it.choices.toMap() }, onDismiss = onDismiss,
        heading = Strings.localSync.rebaseIntentTitle(), hint = Strings.localSync.rebaseIntentHint(), initialChoices = saved?.intentChoices)
    else LocalSyncMergeDialog(plan.latestSnapshot(intent), context.remoteSession(), canContinue,
        onStage = { reviewed ->
            check(canContinue())
            onStage(plan.proposal(reviewed.id, syncCheckpointDigest(requireNotNull(snapshot.checkpoint)), intent, reviewed.choices))
        }, onDismiss = onDismiss, heading = Strings.localSync.rebaseLatestTitle(), hint = Strings.localSync.rebaseLatestHint(),
        initialChoices = saved?.latestChoices?.takeIf { saved.intentChoices == intent })
}
