package cg.creamgod.boarderless.data.remote

/** Native callers keep their source API; planning is shared, not duplicated per platform. */
internal typealias DesktopLegacyMigrationPlan = LegacySubmissionMigrationPlan
internal object DesktopLegacyMigrationPlanner {
    fun prepare(target: PendingSubmissionScope, capture: LegacySequenceCapture,
        existingNativeBundles: List<WorkspaceDraftScopeBundle>): DesktopLegacyMigrationPlan =
        LegacySubmissionMigrationPlanner.prepare(target, capture, existingNativeBundles)
}
