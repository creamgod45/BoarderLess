package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

internal enum class DesktopActivationPhase { Preparing, Active }

internal enum class DesktopWorkspaceEnrollmentPhase { Preparing, Ready }

@Serializable
internal data class DesktopWorkspaceEnrollment(
    val scope: PendingSubmissionScope,
    val phase: DesktopWorkspaceEnrollmentPhase,
)

@Serializable
internal data class DesktopStorageActivationReceipt(
    val schema: Int = 2,
    val target: PendingSubmissionScope,
    val migrationReceiptDigest: String,
    val sourceDigest: String,
    val phase: DesktopActivationPhase,
    val enrollments: List<DesktopWorkspaceEnrollment> = emptyList(),
)

/** Explicit native runtime routing. No production default bootstrap or Settings fallback.
 * The mandatory guard must keep ALL legacy/native writers excluded, including old builds.
 * Preparing intent enrolls empty draft tombstones; Active is the last durable file switch.
 * Unknown activation outcome requires fresh open/read, never a blind second activation.
 * Reopen permits typed native generations to advance; never re-imports frozen legacy state.
 */
internal class DesktopStorageActivation(
    private val files: DesktopAtomicDraftStore,
    private val requireWriterExclusion: () -> Unit,
) {
    private val json = Json { encodeDefaults = true }
    private val migration = DesktopLegacyMigrationCoordinator(files, requireWriterExclusion)
    private val rawDrafts = DesktopDraftScopeBundleStore(files)
    private val rawSafety = DesktopRecoverySafetyStore(files)
    private val rawLedger = DesktopClientSequenceLedger(files)

    private fun digest(bytes: ByteArray) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    val key: String get() = digest("BoarderLess.desktop-storage-activation.v1".encodeToByteArray())

    private fun controlGeneration(receipt: DesktopStorageActivationReceipt): Long =
        if (receipt.phase == DesktopActivationPhase.Preparing) {
            1L
        } else {
            2L + 2L * receipt.enrollments.size -
                receipt.enrollments.count { it.phase == DesktopWorkspaceEnrollmentPhase.Preparing }
        }

    private fun validateEnrollments(receipt: DesktopStorageActivationReceipt) {
        require(
            receipt.enrollments.size <= 256 && receipt.enrollments
                .map { it.scope }
                .distinct()
                .size == receipt.enrollments.size,
        )
        require(receipt.phase == DesktopActivationPhase.Active || receipt.enrollments.isEmpty())
        require(receipt.enrollments.count { it.phase == DesktopWorkspaceEnrollmentPhase.Preparing } <= 1)
        require(receipt.enrollments.dropLast(1).none { it.phase == DesktopWorkspaceEnrollmentPhase.Preparing })
        receipt.enrollments.forEach {
            WorkspaceDraftScopeBundleCodec.scopeHash(it.scope)
            require(
                it.scope.apiBase == receipt.target.apiBase && it.scope.userId == receipt.target.userId &&
                    it.scope.clientId == receipt.target.clientId && it.scope.workspaceId != receipt.target.workspaceId,
            )
        }
    }

    private fun encode(receipt: DesktopStorageActivationReceipt): ByteArray {
        WorkspaceDraftScopeBundleCodec.scopeHash(receipt.target)
        validateEnrollments(receipt)
        require(
            receipt.schema == 2 && receipt.sourceDigest.matches(Regex("[0-9a-f]{64}")) &&
                receipt.migrationReceiptDigest.matches(Regex("[0-9a-f]{64}")),
        )
        val raw = json.encodeToString(receipt)
        requireBoundedDraftJsonDepth(raw)
        return raw.encodeToByteArray().also { require(it.size in 1..1024 * 1024) }
    }

    fun read(): DesktopStorageActivationReceipt? {
        requireWriterExclusion()
        val record = files.read(key) ?: return null
        val bytes = requireNotNull(record.payload) { "Activation tombstone requires explicit recovery" }
        require(bytes.size in 1..1024 * 1024)
        val raw = bytes.decodeToString(throwOnInvalidSequence = true)
        requireBoundedDraftJsonDepth(raw)
        return json.decodeFromString<DesktopStorageActivationReceipt>(raw).also {
            validateEnrollments(it)
            require(it.schema == 2 && record.generation == controlGeneration(it))
            WorkspaceDraftScopeBundleCodec.scopeHash(it.target)
            require(it.sourceDigest.matches(Regex("[0-9a-f]{64}")) && it.migrationReceiptDigest.matches(Regex("[0-9a-f]{64}")))
        }
    }

    /** Explicit activation only, after exclusion and the full source/catalog are verified.
     * Preparing intent can resume only exact initial files; Active is rejected, use openPreferences.
     */
    fun activate(
        target: PendingSubmissionScope,
        captureLegacy: () -> LegacySequenceCapture,
    ): DesktopStorageActivationReceipt {
        requireWriterExclusion()
        var preparing = read()
        require(preparing?.phase != DesktopActivationPhase.Active) { "Storage is active; reopen instead" }
        val published = if (preparing == null) migration.publish(target, captureLegacy) else requireNotNull(migration.read(target))
        require(published.generation == 2L && published.receipt.phase == DesktopMigrationPhase.Published)
        val migrationKey = migration.receiptKey(target)
        val payload = requireNotNull(files.read(migrationKey)?.payload)
        val intent =
            DesktopStorageActivationReceipt(
                target = target,
                migrationReceiptDigest = digest(payload),
                sourceDigest = published.receipt.sourceDigest,
                phase = DesktopActivationPhase.Preparing,
            )
        if (preparing == null) {
            requireWriterExclusion()
            require(read() == null && migration.publish(target, captureLegacy) == published)
            files.compareAndSet(key, null, encode(intent))
            preparing = intent
        }
        require(preparing == intent) { "Activation intent differs; explicit recovery required" }
        val artifacts = published.receipt.artifacts
        val allowed = artifacts.map { it.key }.toSet() + key + migrationKey

        fun verify(complete: Boolean) {
            requireWriterExclusion()
            require(read() == intent && migration.read(target) == published)
            require(files.read(migrationKey)?.payload?.let(::digest) == intent.migrationReceiptDigest)
            require(DesktopLegacyMigrationPlanner.prepare(target, captureLegacy(), emptyList()).sourceDigest == intent.sourceDigest)
            require(files.recordKeys().all { it in allowed })
            artifacts.forEach { artifact ->
                val record = files.read(artifact.key)
                if (artifact.generation == null) {
                    require(artifact.kind == DesktopMigrationArtifactKind.Draft)
                    if (record == null) {
                        require(!complete)
                    } else {
                        require(record.generation == 1L && record.payload == null) { "Empty draft enrollment advanced before activation" }
                    }
                } else {
                    require(
                        record != null && record.generation == artifact.generation &&
                            record.payload?.let(::digest) == artifact.payloadDigest,
                    ) { "Native state advanced before activation" }
                }
            }
        }
        verify(complete = false)
        // Persist an empty generation, so a future missing file can NEVER masquerade as initial absence.
        for (artifact in artifacts.filter { it.generation == null }) {
            requireWriterExclusion()
            require(read() == intent && migration.read(target) == published)
            if (files.read(artifact.key) == null) files.compareAndSet(artifact.key, null, null)
        }
        verify(complete = true)
        val active = intent.copy(phase = DesktopActivationPhase.Active)
        requireWriterExclusion()
        files.compareAndSet(key, 1L, encode(active))
        return requireNotNull(read()).also { require(it == active) }
    }

    /** Local storage enrollment only: no workspace creation, server permission grant or HTTP.
     * Scope must share the already initialized origin/user/client ledger with the migration anchor.
     * An explicit fresh invocation may resume Preparing after rechecking the entire source/control.
     */
    fun enrollWorkspace(
        settings: Settings,
        target: PendingSubmissionScope,
        scope: PendingSubmissionScope,
        captureLegacy: () -> LegacySequenceCapture,
    ) {
        requireWriterExclusion()
        require(scope.apiBase == target.apiBase && scope.userId == target.userId && scope.clientId == target.clientId)
        WorkspaceDraftScopeBundleCodec.scopeHash(scope)
        openStoragePreferences(settings, target, captureLegacy, allowPreparingEnrollment = true) // Full read-only bootstrap gate.
        var control = requireNotNull(read())
        val migrationReceipt = requireNotNull(migration.read(target))
        val originalScopes =
            migrationReceipt.receipt.artifacts
                .filter { it.kind == DesktopMigrationArtifactKind.Draft }
                .map { it.scope }
                .toSet()
        if (scope in originalScopes) return
        val previous = control.enrollments.singleOrNull { it.scope == scope }
        if (previous?.phase == DesktopWorkspaceEnrollmentPhase.Ready) return
        require(control.enrollments.none { it.phase == DesktopWorkspaceEnrollmentPhase.Preparing && it.scope != scope })
        val draftKey = WorkspaceDraftScopeBundleCodec.scopeHash(scope)
        val safetyKey = rawSafety.key(scope)
        val expectedSafety = RecoverySafetyBundleCodec.encode(RecoverySafetyBundle(scope = scope))
        if (previous == null) {
            require(
                files.read(draftKey) == null && files.read(safetyKey) == null,
            ) { "Orphan native data cannot be adopted as an empty workspace" }
            require(
                (files.recordKeys() + draftKey + safetyKey).size <= 1024,
            ) { "Native catalog has no room for a complete workspace enrollment" }
            val intent =
                control.copy(
                    enrollments =
                        control.enrollments + DesktopWorkspaceEnrollment(scope, DesktopWorkspaceEnrollmentPhase.Preparing),
                )
            val bytes = encode(intent) // Bound the catalog before any publication.
            requireWriterExclusion()
            require(read() == control)
            files.compareAndSet(key, controlGeneration(control), bytes)
            control = intent
        }
        openStoragePreferences(settings, target, captureLegacy, allowPreparingEnrollment = true)
        requireWriterExclusion()
        require(read() == control)
        if (files.read(draftKey) == null) files.compareAndSet(draftKey, null, null)
        requireWriterExclusion()
        require(read() == control)
        if (files.read(safetyKey) == null) files.compareAndSet(safetyKey, null, expectedSafety)
        require(files.read(draftKey)?.let { it.generation == 1L && it.payload == null } == true)
        require(files.read(safetyKey)?.let { it.generation == 1L && it.payload?.contentEquals(expectedSafety) == true } == true)
        openStoragePreferences(settings, target, captureLegacy, allowPreparingEnrollment = true)
        val ready =
            control.copy(
                enrollments =
                    control.enrollments.map {
                        if (it.scope == scope) it.copy(phase = DesktopWorkspaceEnrollmentPhase.Ready) else it
                    },
            )
        requireWriterExclusion()
        require(read() == control)
        files.compareAndSet(key, controlGeneration(control), encode(ready))
        openPreferences(settings, target, captureLegacy) // Verify Ready publication; no mutation/retry.
    }

    /** Retains old identity metadata, but every draft/safety/sequence operation uses native files.
     * New scopes are opened only after explicit Ready enrollment in the same initialized namespace.
     * No Settings fallback or unverified zero sequence initialization.
     */
    fun openPreferences(
        settings: Settings,
        target: PendingSubmissionScope,
        captureLegacy: () -> LegacySequenceCapture,
    ): SessionPreferences = openStoragePreferences(settings, target, captureLegacy, allowPreparingEnrollment = false)

    private fun openStoragePreferences(
        settings: Settings,
        target: PendingSubmissionScope,
        captureLegacy: () -> LegacySequenceCapture,
        allowPreparingEnrollment: Boolean,
    ): SessionPreferences {
        requireWriterExclusion()
        val activated = requireNotNull(read()) { "Storage activation is missing; no legacy fallback" }
        require(activated.target == target && activated.phase == DesktopActivationPhase.Active)
        val published = requireNotNull(migration.read(target))
        require(published.receipt.phase == DesktopMigrationPhase.Published && published.generation == 2L)
        val receiptKey = migration.receiptKey(target)
        val receiptBytes = requireNotNull(files.read(receiptKey)?.payload)
        require(digest(receiptBytes) == activated.migrationReceiptDigest && published.receipt.sourceDigest == activated.sourceDigest)
        val source = DesktopLegacyMigrationPlanner.prepare(target, captureLegacy(), emptyList())
        require(source.sourceDigest == activated.sourceDigest) { "Legacy source changed after activation; explicit recovery required" }
        val floors = source.scopes.associate { rawLedger.key(it.scope) to it.minimumClientSequence }
        val scopes = source.scopes.map { it.scope }.toSet()
        require(
            published.receipt.artifacts
                .filter { it.kind == DesktopMigrationArtifactKind.Draft }
                .map { it.scope }
                .toSet() == scopes,
        )
        val artifacts = published.receipt.artifacts.associateBy { it.key }
        val allowedKeys = artifacts.keys + key + receiptKey
        require(activated.enrollments.none { it.scope in scopes })
        val activationGeneration = controlGeneration(activated)

        fun requireControl(): DesktopStorageActivationReceipt {
            requireWriterExclusion()
            check(settings.getStringOrNull("backend.clientId") == target.clientId) { "Activated client identity is missing or changed" }
            val current = requireNotNull(read())
            require(current.copy(enrollments = emptyList()) == activated.copy(enrollments = emptyList()))
            require(controlGeneration(current) >= activationGeneration && current.enrollments.size >= activated.enrollments.size)
            activated.enrollments.forEachIndexed { index, previous ->
                val next = current.enrollments[index]
                require(
                    next.scope == previous.scope &&
                        (previous.phase == DesktopWorkspaceEnrollmentPhase.Preparing || next.phase == previous.phase),
                )
            }
            require(current.enrollments.none { it.scope in scopes })
            require(allowPreparingEnrollment || current.enrollments.none { it.phase == DesktopWorkspaceEnrollmentPhase.Preparing }) {
                "Workspace enrollment requires recovery before runtime access"
            }
            require(migration.read(target) == published)
            require(files.read(receiptKey)?.payload?.let(::digest) == activated.migrationReceiptDigest)
            val registeredKeys =
                current.enrollments
                    .flatMap {
                        listOf(WorkspaceDraftScopeBundleCodec.scopeHash(it.scope), rawSafety.key(it.scope))
                    }.toSet()
            require(
                files.recordKeys().all { it in allowedKeys || it in registeredKeys },
            ) { "Unenrolled native records require explicit recovery" }
            return current
        }

        fun requireArtifact(
            artifact: DesktopMigrationArtifact,
            enrolledEmptyDraft: Boolean,
        ) {
            val record = requireNotNull(files.read(artifact.key)) { "Activated native record is missing" }
            require(record.generation >= (artifact.generation ?: 1L))
            if (artifact.generation == null) {
                require(enrolledEmptyDraft && artifact.kind == DesktopMigrationArtifactKind.Draft)
                if (record.generation == 1L) require(record.payload == null) // Enrolled empty slot, not absence.
            } else if (record.generation == artifact.generation) {
                require(record.payload?.let(::digest) == artifact.payloadDigest) { "Initial native record differs from migration evidence" }
            }
        }

        fun requireEnrolledWorkspace(entry: DesktopWorkspaceEnrollment) {
            val scope = entry.scope
            val draft = files.read(WorkspaceDraftScopeBundleCodec.scopeHash(scope))
            val safety = files.read(rawSafety.key(scope))
            val expectedSafety = RecoverySafetyBundleCodec.encode(RecoverySafetyBundle(scope = scope))
            if (entry.phase == DesktopWorkspaceEnrollmentPhase.Preparing) {
                require(allowPreparingEnrollment)
                require(draft == null || (draft.generation == 1L && draft.payload == null))
                require(safety == null || (safety.generation == 1L && safety.payload?.contentEquals(expectedSafety) == true))
            } else {
                require(draft != null && safety != null)
                if (draft.generation == 1L) require(draft.payload == null)
                if (safety.generation == 1L) require(safety.payload?.contentEquals(expectedSafety) == true)
                rawDrafts.read(scope)
                requireNotNull(rawSafety.read(scope))
            }
            val sequence = requireNotNull(artifacts[rawLedger.key(scope)])
            requireArtifact(sequence, enrolledEmptyDraft = false)
            require(requireNotNull(rawLedger.read(scope)).highWater >= requireNotNull(floors[sequence.key]))
        }

        fun requireScope(scope: PendingSubmissionScope) {
            val control = requireControl()
            require(scope.clientId == target.clientId) { "Scope belongs to another client" }
            if (scope !in scopes) {
                val registered =
                    requireNotNull(
                        control.enrollments.singleOrNull {
                            it.scope == scope &&
                                it.phase == DesktopWorkspaceEnrollmentPhase.Ready
                        },
                    ) {
                        "Scope is not enrolled for this activated client"
                    }
                requireEnrolledWorkspace(registered)
                return
            }
            val draft = requireNotNull(artifacts[WorkspaceDraftScopeBundleCodec.scopeHash(scope)])
            val safety = requireNotNull(artifacts[rawSafety.key(scope)])
            val sequence = requireNotNull(artifacts[rawLedger.key(scope)])
            require(
                draft.kind == DesktopMigrationArtifactKind.Draft && safety.kind == DesktopMigrationArtifactKind.Safety &&
                    sequence.kind == DesktopMigrationArtifactKind.Sequence,
            )
            requireArtifact(draft, enrolledEmptyDraft = true)
            rawDrafts.read(scope) // Typed schema/depth/scope, including legitimate generation-preserving tombstones.
            requireArtifact(safety, enrolledEmptyDraft = false)
            requireNotNull(rawSafety.read(scope))
            requireArtifact(sequence, enrolledEmptyDraft = false)
            require(requireNotNull(rawLedger.read(scope)).highWater >= requireNotNull(floors[sequence.key]))
        }
        requireControl()
        // Foreign archived client scopes remain inventoried, but cannot be submitted by this identity.
        source.scopes.forEach { planned ->
            val scope = planned.scope
            for (kind in DesktopMigrationArtifactKind.entries) {
                val artifact =
                    requireNotNull(
                        artifacts[
                            when (kind) {
                                DesktopMigrationArtifactKind.Draft -> WorkspaceDraftScopeBundleCodec.scopeHash(scope)
                                DesktopMigrationArtifactKind.Safety -> rawSafety.key(scope)
                                DesktopMigrationArtifactKind.Sequence -> rawLedger.key(scope)
                            },
                        ],
                    )
                require(artifact.kind == kind)
                requireArtifact(artifact, kind == DesktopMigrationArtifactKind.Draft)
            }
            rawDrafts.read(scope)
            requireNotNull(rawSafety.read(scope))
            require(requireNotNull(rawLedger.read(scope)).highWater >= planned.minimumClientSequence)
        }
        requireControl().enrollments.forEach(::requireEnrolledWorkspace)
        // Recheck the entire frozen legacy capture around bootstrap; never mirror sequence writes.
        require(DesktopLegacyMigrationPlanner.prepare(target, captureLegacy(), emptyList()).sourceDigest == activated.sourceDigest)
        requireControl()
        val drafts = DesktopWorkspaceDraftPersistence(DesktopDraftScopeBundleStore(files, ::requireScope))
        val safety = DesktopRecoverySafetyStore(files, ::requireScope)
        val ledger = DesktopClientSequenceLedger(files, ::requireScope)
        return SessionPreferences(settings, safety, drafts, ledger) {
            requireControl()
            target.clientId
        }
    }
}
