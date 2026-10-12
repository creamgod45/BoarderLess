package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.remote.BackendRealtimeStatus
import cg.creamgod.boarderless.data.remote.BackendRealtimeDiagnostic
import cg.creamgod.boarderless.data.remote.BackendRealtimeHttpException
import cg.creamgod.boarderless.i18n.Strings

internal fun realtimeStatusText(status: BackendRealtimeStatus): String = when (status) {
    BackendRealtimeStatus.Idle -> Strings.presence.realtimeIdle()
    BackendRealtimeStatus.Preparing -> Strings.presence.realtimePreparing()
    BackendRealtimeStatus.Joining -> Strings.presence.realtimeJoining()
    BackendRealtimeStatus.CatchingUp -> Strings.presence.realtimeCatchingUp()
    BackendRealtimeStatus.Live -> Strings.presence.realtimeLive()
    BackendRealtimeStatus.Disconnected -> Strings.presence.realtimeDisconnected()
    BackendRealtimeStatus.ResyncRequired -> Strings.presence.realtimeResync()
    BackendRealtimeStatus.Revoked -> Strings.presence.realtimeRevoked()
    BackendRealtimeStatus.Failed -> Strings.presence.realtimeFailedHint()
}

internal fun realtimeDiagnosticText(diagnostic: BackendRealtimeDiagnostic): String =
    Strings.presence.realtimeDiagnostic(realtimeStatusText(diagnostic.stage), diagnostic.code,
        diagnostic.httpStatus?.toString() ?: "—", diagnostic.retryAfterMs?.toString() ?: "—")

internal fun realtimeFailureText(error: Exception): String = if (error is BackendRealtimeHttpException)
    realtimeDiagnosticText(BackendRealtimeDiagnostic(BackendRealtimeStatus.Preparing, "http_rejected", error.status))
    else Strings.presence.realtimeFailedHint()

internal fun realtimeWritePrerequisiteText(missing: Set<cg.creamgod.boarderless.data.remote.RealtimeWritePrerequisite>): String =
    missing.joinToString(" · ") {
        when (it) {
            cg.creamgod.boarderless.data.remote.RealtimeWritePrerequisite.DurableCheckpoint -> Strings.presence.writeMissingCheckpoint()
            cg.creamgod.boarderless.data.remote.RealtimeWritePrerequisite.SequenceLedger -> Strings.presence.writeMissingSequence()
            cg.creamgod.boarderless.data.remote.RealtimeWritePrerequisite.AtomicRecovery -> Strings.presence.writeMissingRecovery()
        }
    }
