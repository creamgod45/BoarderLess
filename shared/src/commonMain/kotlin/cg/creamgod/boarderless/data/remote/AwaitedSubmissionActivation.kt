package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ActivatedAwaitedSubmission internal constructor(val runtime: AwaitedSubmissionRuntime,
    private val awaitCalls: suspend () -> Unit, private val release: () -> Unit) {
    fun close() = release()
    /** Logical runtime calls only. Host must also drain platform physical I/O before
     * releasing its real writer lease; cancelled browser calls may still own an IDB ticket. */
    suspend fun closeAndAwaitCalls() { release(); awaitCalls() }
}

/** Initial cutover and explicit reopening of previously activated registered scopes.
 * Real writer exclusion and original actor/client identity checks have NO defaults and
 * remain required throughout the returned lease, not just while the wizard is visible.
 * Does not change Settings, client identity, selected repository or legacy records. */
internal class AwaitedSubmissionActivationManager(private val controls: AwaitedDurableLocalRecordStore,
    private val publisher: AwaitedLegacyMigrationPublisher, private val drafts: AwaitedAtomicWorkspaceDraftPersistence,
    private val sequences: AwaitedAtomicClientSequenceAllocator, private val safety: AwaitedAtomicRecoverySafetyPersistence,
    private val requireAllOtherWritersExcluded: () -> Unit, private val requireOriginalIdentity: (PendingSubmissionScope) -> Unit) {
    private val mutex = Mutex()
    private class LeaseIdentity
    private data class LeaseState(val closed: Boolean = false, val calls: Int = 0, val releasing: Boolean = false)
    private val issued = MutableStateFlow<LeaseIdentity?>(null)
    private fun guard(target: PendingSubmissionScope) { requireAllOtherWritersExcluded(); requireOriginalIdentity(target) }
    private val activationStore = AwaitedSubmissionActivationStore(controls, ::guard)
    suspend fun confirm(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan): ActivatedAwaitedSubmission =
        confirmOrReopen(target, review, recapture, reopening = false)

    suspend fun reopen(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan): ActivatedAwaitedSubmission =
        confirmOrReopen(target, review, recapture, reopening = true)

    /** Explicit local enrollment while the original runtime is closed and drained. Does not
     * create a server workspace, initialize/reset its shared sequence ledger or switch APP.
     * A new invocation resumes only the same Preparing entry after a fresh complete capture. */
    suspend fun registerWorkspace(target: PendingSubmissionScope, scope: PendingSubmissionScope,
        review: LegacySubmissionMigrationReview, recapture: suspend () -> LegacySubmissionMigrationPlan) = mutex.withLock {
        check(issued.value == null) { "Close and drain the runtime before workspace enrollment" }
        currentCoroutineContext().ensureActive(); guard(target)
        WorkspaceDraftScopeBundleCodec.scopeHash(scope)
        require(scope.apiBase == target.apiBase && scope.userId == target.userId && scope.clientId == target.clientId)
        var control = requireNotNull(activationStore.read(target)) { "Activation receipt missing; do not initialize" }
        val published = publisher.confirmAdvanced(target, review, recapture)
        currentCoroutineContext().ensureActive(); guard(target)
        require(control.record.samePublication(AwaitedSubmissionActivationRecord(target = target,
            publicationGeneration = published.generation, sourceDigest = review.sourceDigest, planDigest = review.planDigest)))
        val plan = review.requireUnchanged(recapture())
        val originalScopes = plan.scopes.map { it.scope }.toSet()
        require(control.record.enrollments.none { it.scope in originalScopes })
        require(control.record.enrollments.none { it.phase == AwaitedWorkspaceEnrollmentPhase.Preparing && it.scope != scope }) {
            "Resume the original workspace enrollment first"
        }
        require(originalScopes.size + control.record.enrollments.size <= 256)
        confirmEnrollments(target, control, allowPreparing = true)
        if (scope in originalScopes || control.record.enrollments.any {
                it.scope == scope && it.phase == AwaitedWorkspaceEnrollmentPhase.Ready }) return@withLock
        if (control.record.enrollments.none { it.scope == scope }) {
            require(originalScopes.size + control.record.enrollments.size < 256)
            require(drafts.read(scope) == null && safety.read(scope) == null) {
                "Unregistered records require recovery; cannot enroll as empty"
            }
            sequences.readDurable(scope) // Must already exist for this exact origin/user/client.
            currentCoroutineContext().ensureActive(); guard(target)
            review.requireUnchanged(recapture())
            control = activationStore.save(target, control, control.record.copy(version = 2,
                enrollments = control.record.enrollments + AwaitedWorkspaceEnrollment(scope, AwaitedWorkspaceEnrollmentPhase.Preparing)))
        }
        // Preparing binds the identity BEFORE either artifact. An advanced or foreign record
        // cannot be adopted after a lost acknowledgement, even if it currently looks empty.
        confirmEnrollments(target, control, allowPreparing = true)
        currentCoroutineContext().ensureActive(); guard(target)
        drafts.adoptReviewedBundle(scope, null)
        currentCoroutineContext().ensureActive(); guard(target)
        safety.adoptLegacy(scope, emptyList(), emptyList())
        require(drafts.readDurable(scope) == AwaitedDraftBundle(1, null))
        require(safety.readDurable(scope) == AwaitedRecoverySafetyRecord(1,
            RecoverySafetyBundleCodec.prepareLegacy(scope, emptyList(), emptyList())))
        sequences.readDurable(scope)
        review.requireUnchanged(recapture())
        currentCoroutineContext().ensureActive(); guard(target)
        control = activationStore.save(target, control, control.record.copy(version = 2,
            enrollments = control.record.enrollments.map {
                if (it.scope == scope) it.copy(phase = AwaitedWorkspaceEnrollmentPhase.Ready) else it
            }))
        confirmEnrollments(target, control, allowPreparing = false)
    }

    private suspend fun confirmEnrollments(target: PendingSubmissionScope, control: AwaitedSubmissionActivationReceipt,
        allowPreparing: Boolean) {
        activationStore.confirm(target, control)
        for (entry in control.record.enrollments) {
            currentCoroutineContext().ensureActive(); guard(target)
            val scope = entry.scope
            val emptySafety = RecoverySafetyBundleCodec.prepareLegacy(scope, emptyList(), emptyList())
            if (entry.phase == AwaitedWorkspaceEnrollmentPhase.Preparing) {
                require(allowPreparing) { "Workspace enrollment needs explicit recovery before activation" }
                val draft = drafts.read(scope)
                val safe = safety.read(scope)
                require(draft == null || draft == AwaitedDraftBundle(1, null))
                require(safe == null || safe == AwaitedRecoverySafetyRecord(1, emptySafety))
            } else {
                val draft = drafts.readDurable(scope)
                val safe = safety.readDurable(scope)
                if (draft.generation == 1L) require(draft.bundle == null)
                if (safe.generation == 1L) require(safe.bundle == emptySafety)
                require(safe.bundle.legacyImportDigest == emptySafety.legacyImportDigest)
                val ledger = sequences.readDurable(scope)
                val wires = listOfNotNull(draft.bundle?.pending, draft.bundle?.stoppedPending) +
                    draft.bundle?.retainedStopped.orEmpty().map { it.submitted }
                require(wires.all { wire -> wire.request.operations.all { it.clientSeq <= ledger.highWater } })
            }
            sequences.readDurable(scope)
        }
        activationStore.confirm(target, control)
    }

    private suspend fun confirmOrReopen(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan, reopening: Boolean): ActivatedAwaitedSubmission = mutex.withLock {
        require(issued.value == null) { "Close the existing runtime lease before confirming another" }
        currentCoroutineContext().ensureActive(); guard(target)
        publisher.requirePublished(target) // Preparing/missing must not trigger publication here.
        guard(target)
        val plan = review.requireUnchanged(recapture()); guard(target)
        // Reopen cannot adopt coincidentally matching files without an activation receipt.
        if (reopening) requireNotNull(activationStore.read(target)) { "Activation receipt missing; do not initialize" }
        currentCoroutineContext().ensureActive(); guard(target)
        val published = if (reopening) publisher.confirmAdvanced(target, review, recapture)
            else publisher.confirmPublished(target, review, recapture)
        require(published.publication.phase == AwaitedMigrationPhase.Published)
        currentCoroutineContext().ensureActive(); guard(target)
        val next = AwaitedSubmissionActivationRecord(target = target, publicationGeneration = published.generation,
            sourceDigest = review.sourceDigest, planDigest = review.planDigest)
        val before = activationStore.read(target)
        currentCoroutineContext().ensureActive(); guard(target)
        if (before != null) {
            require(before.record.samePublication(next)) {
                "Activation receipt changed; explicit reconciliation required"
            }
            activationStore.confirm(target, before)
        } else {
            require(!reopening) { "Activation receipt disappeared during reopen" }
            activationStore.save(target, null, next)
        }
        currentCoroutineContext().ensureActive(); guard(target)
        val saved = requireNotNull(activationStore.read(target))
        require(saved == (before ?: AwaitedSubmissionActivationReceipt(1, next)))
        val originalScopes = plan.scopes.map { it.scope }.toSet()
        require(saved.record.enrollments.none { it.scope in originalScopes })
        require(originalScopes.size + saved.record.enrollments.size <= 256)
        confirmEnrollments(target, saved, allowPreparing = false)
        review.requireUnchanged(recapture())
        currentCoroutineContext().ensureActive(); guard(target)
        val scopes = originalScopes + saved.record.enrollments.map { it.scope }
        val state = MutableStateFlow(LeaseState())
        val drained = MutableStateFlow(false)
        val leaseIdentity = LeaseIdentity()
        fun drain() {
            while (true) {
                val before = state.value
                if (!before.closed || before.calls != 0 || before.releasing) return
                if (state.compareAndSet(before, before.copy(releasing = true))) {
                    issued.compareAndSet(leaseIdentity, null)
                    drained.value = true
                    return
                }
            }
        }
        fun closeLease() {
            while (true) {
                val before = state.value
                if (before.closed || state.compareAndSet(before, before.copy(closed = true))) break
            }
            drain()
        }
        fun active(scope: PendingSubmissionScope) {
            check(!state.value.closed && issued.value === leaseIdentity); guard(target)
            require(scope in scopes && scope.apiBase == target.apiBase && scope.userId == target.userId && scope.clientId == target.clientId) {
                "Scope needs explicit registration or historical recovery"
            }
        }
        suspend fun <T> call(scope: PendingSubmissionScope, action: suspend () -> T): T {
            currentCoroutineContext().ensureActive()
            while (true) {
                active(scope)
                val before = state.value
                check(!before.closed && before.calls < Int.MAX_VALUE)
                if (state.compareAndSet(before, before.copy(calls = before.calls + 1))) break
            }
            try {
                active(scope)
                val value = action(); currentCoroutineContext().ensureActive(); active(scope); return value
            } finally {
                while (true) {
                    val before = state.value
                    check(before.calls > 0)
                    if (state.compareAndSet(before, before.copy(calls = before.calls - 1))) break
                }
                drain()
            }
        }
        val guardedDrafts = object : AwaitedWorkspaceDraftPersistence {
            override suspend fun read(scope: PendingSubmissionScope) = call(scope) { drafts.read(scope) }
            override suspend fun append(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) = call(scope) { drafts.append(scope, session, before, operation) }
            override suspend fun appendWithIntent(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace,
                operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
                beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) =
                call(scope) { drafts.appendWithIntent(scope, session, before, operation, intent, beforeHistory) }
            override suspend fun stage(entry: PendingWorkspaceSubmission) = call(entry.scope) { drafts.stage(entry) }
            override suspend fun acknowledge(entry: PendingWorkspaceSubmission, version: Long, seq: Long) = call(entry.scope) { drafts.acknowledge(entry, version, seq) }
            override suspend fun acknowledgeWithHistory(entry: PendingWorkspaceSubmission, version: Long, seq: Long,
                history: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) =
                call(entry.scope) { drafts.acknowledgeWithHistory(entry, version, seq, history) }
            override suspend fun restore(scope: PendingSubmissionScope, id: String, current: WorkspaceSession) = call(scope) { drafts.restore(scope, id, current) }
            override suspend fun remove(scope: PendingSubmissionScope, id: String) = call(scope) { drafts.remove(scope, id) }
            override suspend fun stop(scope: PendingSubmissionScope, transactionId: String) = call(scope) { drafts.stop(scope, transactionId) }
            override suspend fun recordStoppedEvidence(scope: PendingSubmissionScope, expectedGeneration: Long, submitted: PendingWorkspaceSubmission, evidence: StoppedSubmissionEvidence) = call(scope) { drafts.recordStoppedEvidence(scope, expectedGeneration, submitted, evidence) }
        }
        val guardedSequences = object : AwaitedClientSequenceAllocator {
            override suspend fun reserve(scope: PendingSubmissionScope, count: Int) = call(scope) { sequences.reserve(scope, count) }
            override suspend fun advance(scope: PendingSubmissionScope, sequence: Long) = call(scope) { sequences.advance(scope, sequence) }
        }
        val guardedSafety = object : AwaitedRecoverySafetyPersistence {
            override suspend fun fenceState(pending: PendingWorkspaceSubmission) = call(pending.scope) { safety.fenceState(pending) }
            override suspend fun beginFence(pending: PendingWorkspaceSubmission) = call(pending.scope) { safety.beginFence(pending) }
            override suspend fun observeFence(pending: PendingWorkspaceSubmission, status: PendingReceiptStatus) = call(pending.scope) { safety.observeFence(pending, status) }
            override suspend fun deletion(scope: PendingSubmissionScope, entity: String) = call(scope) { safety.deletion(scope, entity) }
            override suspend fun recordDeletions(scope: PendingSubmissionScope, records: List<CommittedDeletionEvidence>) = call(scope) { safety.recordDeletions(scope, records) }
        }
        guard(target)
        currentCoroutineContext().ensureActive()
        check(issued.compareAndSet(null, leaseIdentity)) { "Runtime lease changed during activation" }
        try { active(target) } catch (failure: Throwable) {
            closeLease()
            throw failure
        }
        ActivatedAwaitedSubmission(AwaitedSubmissionRuntime(guardedDrafts, guardedSequences, guardedSafety, ::active, target),
            awaitCalls = {
                check(state.value.closed)
                drained.first { it }
                currentCoroutineContext().ensureActive()
                check(state.value.calls == 0 && issued.value !== leaseIdentity)
            }, release = ::closeLease)
    }
}
