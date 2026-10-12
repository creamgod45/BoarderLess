package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kotlincrypto.hash.sha2.SHA256

@Serializable internal enum class AwaitedMigrationPhase { Preparing, Published }
@Serializable internal enum class AwaitedMigrationArtifactKind { Draft, Safety, Sequence }
@Serializable internal data class AwaitedMigrationArtifact(val kind: AwaitedMigrationArtifactKind,
    val scope: PendingSubmissionScope, val generation: Long, val contentDigest: String)
@Serializable internal data class AwaitedMigrationPublication(val version: Int = 1, val target: PendingSubmissionScope,
    val sourceDigest: String, val planDigest: String, val phase: AwaitedMigrationPhase,
    val artifacts: List<AwaitedMigrationArtifact> = emptyList())
internal data class AwaitedMigrationPublicationRecord(val generation: Long, val publication: AwaitedMigrationPublication)

/** Explicit local publication ONLY, not repository cutover or server receipt authority.
 * The caller must hold real exclusion of ALL legacy/new writers (including other builds),
 * recapture a complete catalog and obtain human confirmation of the frozen review.
 * No default guard, network, source deletion, retries, Settings fallback or runtime activation.
 * After uncertainty a new invocation rechecks the original receipt and exact artifacts. */
internal class AwaitedLegacyMigrationPublisher(private val receipts: AwaitedDurableLocalRecordStore,
    private val drafts: AwaitedAtomicWorkspaceDraftPersistence, private val safety: AwaitedAtomicRecoverySafetyPersistence,
    private val sequences: AwaitedAtomicClientSequenceAllocator, private val reviews: LegacyMigrationReviewStore,
    private val requireAllWritersExcluded: () -> Unit) {
    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()
    private fun digest(bytes: ByteArray) = SHA256().digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun key(target: PendingSubmissionScope) = digest("boarderless.awaited-legacy-publication.v1\n${WorkspaceDraftScopeBundleCodec.scopeHash(target)}".encodeToByteArray())
    private suspend fun active() { currentCoroutineContext().ensureActive(); requireAllWritersExcluded() }
    private suspend fun read(target: PendingSubmissionScope): AwaitedMigrationPublicationRecord? {
        active(); val record = receipts.read(key(target)) ?: return null; active()
        require(record.generation in 1 until RealtimeMaxSequence && record.payload.size in 1..512 * 1024)
        val raw = parseStrictJsonObject(record.payload.decodeToString(throwOnInvalidSequence = true))
        require(raw.keys == setOf("version", "target", "sourceDigest", "planDigest", "phase", "artifacts"))
        require(raw["version"] == JsonPrimitive(1))
        (raw["artifacts"] as? JsonArray)?.forEach { element ->
            val artifact = element as? JsonObject ?: error("Invalid migration artifact")
            require((artifact["generation"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != null)
        }
        val publication = json.decodeFromJsonElement(AwaitedMigrationPublication.serializer(), raw)
        require(publication.version == 1 && publication.target == target)
        require(publication.sourceDigest.matches(Regex("[0-9a-f]{64}")) && publication.planDigest.matches(Regex("[0-9a-f]{64}")))
        require(publication.artifacts.size <= 768 && publication.artifacts.map { it.kind to it.scope }.distinct().size == publication.artifacts.size)
        publication.artifacts.forEach { require(it.generation in 1 until RealtimeMaxSequence && it.contentDigest.matches(Regex("[0-9a-f]{64}"))) }
        require((publication.phase == AwaitedMigrationPhase.Preparing && publication.artifacts.isEmpty()) ||
            (publication.phase == AwaitedMigrationPhase.Published && publication.artifacts.isNotEmpty()))
        return AwaitedMigrationPublicationRecord(record.generation, publication)
    }
    /** Receipt inspection is not artifact verification or cutover permission. */
    suspend fun requirePublished(target: PendingSubmissionScope): AwaitedMigrationPublicationRecord = mutex.withLock {
        val found = requireNotNull(read(target)) { "Migration publication receipt missing" }
        require(found.publication.phase == AwaitedMigrationPhase.Published)
        receipts.confirmDurable(key(target), found.generation); active()
        require(read(target) == found)
        found
    }
    /** Loads the receipt-bound original review only; not permission to open a writer. */
    suspend fun loadPublishedReview(target: PendingSubmissionScope): LegacySubmissionMigrationReview = mutex.withLock {
        val receipt = requireNotNull(read(target))
        require(receipt.publication.phase == AwaitedMigrationPhase.Published)
        val review = requireNotNull(reviews.read(target, receipt.publication.planDigest)) { "Original review packet missing" }
        active(); require(review.sourceDigest == receipt.publication.sourceDigest)
        receipts.confirmDurable(key(target), receipt.generation); active()
        require(read(target) == receipt)
        review
    }
    private suspend fun save(target: PendingSubmissionScope, before: AwaitedMigrationPublicationRecord?, next: AwaitedMigrationPublication): AwaitedMigrationPublicationRecord {
        active(); require(read(target) == before)
        val bytes = json.encodeToString(AwaitedMigrationPublication.serializer(), next).encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size <= 512 * 1024)
        if (before?.publication == next) receipts.confirmDurable(key(target), before.generation)
        else {
            receipts.compareAndSet(key(target), before?.generation, bytes)
            receipts.confirmDurable(key(target), (before?.generation ?: 0L) + 1)
        }
        active()
        return requireNotNull(read(target)).also {
            require(it.publication == next && it.generation == if (before?.publication == next) requireNotNull(before).generation else (before?.generation ?: 0L) + 1)
        }
    }
    private fun draftDigest(scope: PendingSubmissionScope, bundle: WorkspaceDraftScopeBundle?) =
        digest(bundle?.let(WorkspaceDraftScopeBundleCodec::encode) ?: "verified-empty-draft\n${WorkspaceDraftScopeBundleCodec.scopeHash(scope)}".encodeToByteArray())
    private fun sequenceDigest(scope: PendingSubmissionScope, floor: Long) = digest(
        json.encodeToString(listOf("awaited-migrated-ledger.v1", scope.apiBase, scope.userId, scope.clientId, floor.toString())).encodeToByteArray())

    /** Read/confirm advanced runtime records, never replay initial publication or lower floors.
     * This is valid only with the original activation receipt checked by the caller. */
    suspend fun confirmAdvanced(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan): AwaitedMigrationPublicationRecord = mutex.withLock {
        active(); val plan = review.requireUnchanged(recapture()); active()
        val receipt = requireNotNull(read(target))
        require(receipt.publication.phase == AwaitedMigrationPhase.Published &&
            receipt.publication.sourceDigest == review.sourceDigest && receipt.publication.planDigest == review.planDigest)
        val originalReview = requireNotNull(reviews.read(target, receipt.publication.planDigest))
        active(); require(originalReview.encode().contentEquals(review.encode()))
        val expectedKeys = plan.scopes.flatMap { listOf(AwaitedMigrationArtifactKind.Draft to it.scope,
            AwaitedMigrationArtifactKind.Safety to it.scope) }.toMutableSet()
        val groups = plan.scopes.groupBy { Triple(it.scope.apiBase, it.scope.userId, it.scope.clientId) }.values
        groups.forEach { expectedKeys += AwaitedMigrationArtifactKind.Sequence to it.first().scope }
        require(receipt.publication.artifacts.map { it.kind to it.scope }.toSet() == expectedKeys)
        fun checkArtifact(kind: AwaitedMigrationArtifactKind, scope: PendingSubmissionScope, generation: Long, hash: String) {
            val original = receipt.publication.artifacts.single { it.kind == kind && it.scope == scope }
            require(generation >= original.generation)
            if (generation == original.generation) require(hash == original.contentDigest)
        }
        for (scope in plan.scopes) {
            active(); val draft = drafts.readDurable(scope.scope); active()
            checkArtifact(AwaitedMigrationArtifactKind.Draft, scope.scope, draft.generation, draftDigest(scope.scope, draft.bundle))
            val safe = safety.readDurable(scope.scope); active()
            require(safe.bundle.legacyImportDigest == scope.safety.legacyImportDigest)
            scope.safety.fences.forEach { original ->
                val current = safe.bundle.fences.single { it.transactionId == original.transactionId }
                require(current.copy(state = original.state) == original)
                if (original.state != PendingFenceAttemptStore.State.Unconfirmed) require(current.state == original.state)
            }
            scope.safety.deletions.forEach { original ->
                val current = safe.bundle.deletions.single { it.entity == original.entity }
                require(current == original || (current.tombstoneVersion > original.tombstoneVersion &&
                    current.throughServerSeq > original.throughServerSeq))
            }
            checkArtifact(AwaitedMigrationArtifactKind.Safety, scope.scope, safe.generation, digest(RecoverySafetyBundleCodec.encode(safe.bundle)))
            val ledger = sequences.readDurable(scope.scope); active()
            require(ledger.highWater >= scope.minimumClientSequence)
            val wires = listOfNotNull(draft.bundle?.pending, draft.bundle?.stoppedPending) +
                draft.bundle?.retainedStopped.orEmpty().map { it.submitted }
            wires.forEach { wire -> require(wire.request.operations.all { it.clientSeq <= ledger.highWater }) }
        }
        for (group in groups) {
            val scope = group.first().scope; val ledger = sequences.readDurable(scope); active()
            checkArtifact(AwaitedMigrationArtifactKind.Sequence, scope, ledger.generation, sequenceDigest(scope, ledger.highWater))
        }
        review.requireUnchanged(recapture()); active()
        receipts.confirmDurable(key(target), receipt.generation); active()
        require(read(target) == receipt)
        receipt
    }

    suspend fun publish(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan): AwaitedMigrationPublicationRecord =
        publishOrConfirm(target, review, recapture, publishedOnly = false)

    /** Activation cannot create/resume Preparing, even if the receipt changes between calls. */
    suspend fun confirmPublished(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan): AwaitedMigrationPublicationRecord =
        publishOrConfirm(target, review, recapture, publishedOnly = true)

    private suspend fun publishOrConfirm(target: PendingSubmissionScope, review: LegacySubmissionMigrationReview,
        recapture: suspend () -> LegacySubmissionMigrationPlan, publishedOnly: Boolean): AwaitedMigrationPublicationRecord = mutex.withLock {
        active(); val plan = review.requireUnchanged(recapture()); active()
        require(plan.scopes.any { it.scope == target })
        val groups = plan.scopes.groupBy { Triple(it.scope.apiBase, it.scope.userId, it.scope.clientId) }.values.toList()
        groups.forEach { require(it.map { scope -> scope.minimumClientSequence }.distinct().size == 1) }
        val initial = read(target)
        if (publishedOnly) require(initial?.publication?.phase == AwaitedMigrationPhase.Published) {
            "Activation requires an already Published migration receipt"
        }
        if (initial == null) {
            // No receipt means we cannot claim ownership of coincidentally matching files.
            for (scope in plan.scopes) {
                require(drafts.read(scope.scope) == null && safety.read(scope.scope) == null); active()
            }
            for (group in groups) { require(sequences.read(group.first().scope) == null); active() }
        } else require(initial.publication.sourceDigest == review.sourceDigest && initial.publication.planDigest == review.planDigest)
        if (initial?.publication?.phase == AwaitedMigrationPhase.Published) {
            val originalReview = requireNotNull(reviews.read(target, review.planDigest)) { "Published review packet missing" }
            active(); require(originalReview.encode().contentEquals(review.encode()))
        } else {
            // Original packet is durable before Preparing or any data artifacts are written.
            reviews.save(target, review); active()
            review.requireUnchanged(recapture()); active()
        }
        val preparing = initial ?: save(target, null, AwaitedMigrationPublication(target = target, sourceDigest = review.sourceDigest,
            planDigest = review.planDigest, phase = AwaitedMigrationPhase.Preparing))
        suspend fun unchanged() { active(); review.requireUnchanged(recapture()); active() }
        val artifacts = mutableListOf<AwaitedMigrationArtifact>()
        if (preparing.publication.phase == AwaitedMigrationPhase.Preparing) {
            for (scope in plan.scopes) {
                unchanged(); drafts.adoptReviewedBundle(scope.scope, scope.draft); active()
                safety.adoptLegacy(scope.scope, scope.safety.fences, scope.safety.deletions); active()
            }
            for (group in groups) {
                unchanged(); val scope = group.first(); val existing = sequences.read(scope.scope); active()
                if (existing == null) sequences.initialize(scope.scope, scope.minimumClientSequence)
                else { require(existing.highWater == scope.minimumClientSequence); sequences.advance(scope.scope, existing.highWater) }
                active()
            }
        }
        // Complete typed readback, not a count-only or "write enqueued" success.
        for (scope in plan.scopes) {
            unchanged()
            val draft = requireNotNull(drafts.read(scope.scope)); require(draft.bundle == scope.draft)
            val safe = requireNotNull(safety.read(scope.scope)); require(safe.bundle == scope.safety)
            artifacts += AwaitedMigrationArtifact(AwaitedMigrationArtifactKind.Draft, scope.scope, draft.generation, draftDigest(scope.scope, draft.bundle))
            artifacts += AwaitedMigrationArtifact(AwaitedMigrationArtifactKind.Safety, scope.scope, safe.generation, digest(RecoverySafetyBundleCodec.encode(safe.bundle)))
        }
        for (group in groups) {
            unchanged(); val scope = group.first(); val ledger = requireNotNull(sequences.read(scope.scope))
            require(ledger.highWater == scope.minimumClientSequence)
            artifacts += AwaitedMigrationArtifact(AwaitedMigrationArtifactKind.Sequence, scope.scope, ledger.generation, sequenceDigest(scope.scope, ledger.highWater))
        }
        unchanged()
        val published = preparing.publication.copy(phase = AwaitedMigrationPhase.Published, artifacts = artifacts)
        if (preparing.publication.phase == AwaitedMigrationPhase.Published) require(preparing.publication == published) {
            "Published artifacts changed; activation-receipt reconciliation required"
        }
        save(target, preparing, published) // Only this final durable receipt proves local publication.
    }
}
