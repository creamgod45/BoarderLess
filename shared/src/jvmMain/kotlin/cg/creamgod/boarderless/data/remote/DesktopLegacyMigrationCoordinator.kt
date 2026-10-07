package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

internal enum class DesktopMigrationPhase { Preparing, Published }

internal enum class DesktopMigrationArtifactKind { Draft, Safety, Sequence }

@Serializable
internal data class DesktopMigrationArtifact(
    val kind: DesktopMigrationArtifactKind,
    val scope: PendingSubmissionScope,
    val key: String,
    val payloadDigest: String?,
    val generation: Long?,
)

@Serializable
internal data class DesktopMigrationReceipt(
    val schema: Int = 1,
    val target: PendingSubmissionScope,
    val sourceDigest: String,
    val phase: DesktopMigrationPhase,
    val artifacts: List<DesktopMigrationArtifact>,
)

internal data class StoredDesktopMigrationReceipt(
    val generation: Long,
    val receipt: DesktopMigrationReceipt,
)

private data class PreparedMigrationArtifact(
    val descriptor: DesktopMigrationArtifact,
    val payload: ByteArray?,
)

/** Full publication coordinator, not runtime cutover or server receipt authority.
 * ALL old AND native writers must remain excluded, including noncooperating legacy builds.
 * Mandatory injected guard has no default; Desktop's cooperative lease alone does not satisfy it.
 * Capture callback must inventory all legacy sources without minting identity/resetting counter.
 * Retains legacy bytes. No HTTP, userprefs activation, GC, per-call retry or advanced-state rewind.
 * After any unknown publication, a new invocation rechecks source, receipt and exact file state.
 */
internal class DesktopLegacyMigrationCoordinator(
    private val files: DesktopAtomicDraftStore,
    private val requireWriterExclusion: () -> Unit,
) {
    private val json = Json { encodeDefaults = true }
    private val safety = DesktopRecoverySafetyStore(files)
    private val ledger = DesktopClientSequenceLedger(files)

    private fun digest(bytes: ByteArray) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    fun receiptKey(target: PendingSubmissionScope) =
        digest(("BoarderLess.legacy-publication.v1:" + WorkspaceDraftScopeBundleCodec.scopeHash(target)).encodeToByteArray())

    private fun validate(
        receipt: DesktopMigrationReceipt,
        target: PendingSubmissionScope,
    ) {
        require(receipt.schema == 1 && receipt.target == target)
        WorkspaceDraftScopeBundleCodec.scopeHash(target)
        require(receipt.sourceDigest.matches(Regex("[0-9a-f]{64}")))
        require(receipt.artifacts.size in 3..768)
        require(
            receipt.artifacts
                .map { it.key }
                .distinct()
                .size == receipt.artifacts.size,
        )
        receipt.artifacts.forEach { artifact ->
            WorkspaceDraftScopeBundleCodec.scopeHash(artifact.scope)
            require(
                artifact.key ==
                    when (artifact.kind) {
                        DesktopMigrationArtifactKind.Draft -> WorkspaceDraftScopeBundleCodec.scopeHash(artifact.scope)
                        DesktopMigrationArtifactKind.Safety -> safety.key(artifact.scope)
                        DesktopMigrationArtifactKind.Sequence -> ledger.key(artifact.scope)
                    },
            )
            require(artifact.generation == if (artifact.payloadDigest == null) null else 1L)
            require(artifact.payloadDigest == null || artifact.payloadDigest.matches(Regex("[0-9a-f]{64}")))
            require(artifact.payloadDigest != null || artifact.kind == DesktopMigrationArtifactKind.Draft)
        }
    }

    private fun encode(receipt: DesktopMigrationReceipt): ByteArray {
        validate(receipt, receipt.target)
        val raw = json.encodeToString(receipt)
        requireBoundedDraftJsonDepth(raw)
        return raw.encodeToByteArray().also { require(it.size in 1..1024 * 1024) }
    }

    fun read(target: PendingSubmissionScope): StoredDesktopMigrationReceipt? {
        requireWriterExclusion()
        val record = files.read(receiptKey(target)) ?: return null
        val bytes = requireNotNull(record.payload) { "Migration receipt tombstone requires explicit recovery" }
        require(bytes.size in 1..1024 * 1024)
        val raw = bytes.decodeToString(throwOnInvalidSequence = true)
        requireBoundedDraftJsonDepth(raw)
        val receipt = json.decodeFromString<DesktopMigrationReceipt>(raw)
        validate(receipt, target)
        require(record.generation == if (receipt.phase == DesktopMigrationPhase.Preparing) 1L else 2L)
        return StoredDesktopMigrationReceipt(record.generation, receipt)
    }

    private fun artifacts(plan: DesktopLegacyMigrationPlan): List<PreparedMigrationArtifact> {
        val result = mutableListOf<PreparedMigrationArtifact>()

        fun add(
            kind: DesktopMigrationArtifactKind,
            scope: PendingSubmissionScope,
            key: String,
            bytes: ByteArray?,
        ) {
            result.add(
                PreparedMigrationArtifact(
                    DesktopMigrationArtifact(
                        kind,
                        scope,
                        key,
                        bytes?.let(::digest),
                        bytes?.let { 1L },
                    ),
                    bytes,
                ),
            )
        }
        plan.scopes.forEach { scope ->
            add(
                DesktopMigrationArtifactKind.Draft,
                scope.scope,
                WorkspaceDraftScopeBundleCodec.scopeHash(scope.scope),
                scope.draft?.let(WorkspaceDraftScopeBundleCodec::encode),
            )
            add(DesktopMigrationArtifactKind.Safety, scope.scope, safety.key(scope.scope), RecoverySafetyBundleCodec.encode(scope.safety))
        }
        // One ledger per origin/user/client, across all captured workspaces. Never one per scope.
        plan.scopes.groupBy { ledger.key(it.scope) }.toSortedMap().forEach { (key, scopes) ->
            require(scopes.map { it.minimumClientSequence }.distinct().size == 1)
            add(DesktopMigrationArtifactKind.Sequence, scopes.first().scope, key, ledger.encode(scopes.first().minimumClientSequence))
        }
        return result.sortedBy { it.descriptor.key }
    }

    /** An exact Published receipt means all initial files were reread together under exclusion.
     * It is NOT permission to switch runtime writers; activation/routing requires a separate gate.
     */
    fun publish(
        target: PendingSubmissionScope,
        captureLegacy: () -> LegacySequenceCapture,
    ): StoredDesktopMigrationReceipt {
        requireWriterExclusion()
        val plan = DesktopLegacyMigrationPlanner.prepare(target, captureLegacy(), emptyList())
        val prepared = artifacts(plan)
        val intent =
            DesktopMigrationReceipt(
                target = target,
                sourceDigest = plan.sourceDigest,
                phase = DesktopMigrationPhase.Preparing,
                artifacts = prepared.map { it.descriptor },
            )
        encode(intent) // Validate and bound every artifact/receipt before publishing anything.
        val key = receiptKey(target)
        require(prepared.none { it.descriptor.key == key })

        fun recheckSource() {
            requireWriterExclusion()
            val fresh = DesktopLegacyMigrationPlanner.prepare(target, captureLegacy(), emptyList())
            require(fresh.sourceDigest == plan.sourceDigest && artifacts(fresh).map { it.descriptor } == intent.artifacts) {
                "Legacy migration source changed; explicit recovery required"
            }
        }

        fun recheckFiles(
            requireComplete: Boolean,
            allowMatching: Boolean,
        ) {
            requireWriterExclusion()
            val allowed = prepared.filter { it.payload != null }.map { it.descriptor.key }.toSet() + key
            require(files.recordKeys().all { it in allowed }) { "Uninventoried native state requires explicit recovery" }
            prepared.forEach { artifact ->
                val current = files.read(artifact.descriptor.key)
                if (artifact.payload == null) {
                    require(current == null) { "Unexpected native draft or tombstone" }
                } else if (current == null) {
                    require(!requireComplete) { "Published migration record is missing" }
                } else {
                    require(allowMatching) { "Existing native state has no migration intent receipt" }
                    require(
                        current.generation == artifact.descriptor.generation && current.payload != null &&
                            current.payload.contentEquals(artifact.payload),
                    ) { "Native state advanced or differs; never rewind" }
                }
            }
        }
        var stored = read(target)
        if (stored == null) {
            recheckSource()
            recheckFiles(requireComplete = false, allowMatching = false)
            val published = files.compareAndSet(key, null, encode(intent))
            stored = StoredDesktopMigrationReceipt(published.generation, intent)
        } else {
            require(stored.receipt.copy(phase = DesktopMigrationPhase.Preparing) == intent) {
                "Migration receipt belongs to a different complete source"
            }
        }
        // From here on, missing records may be resumed only while this exact intent is Preparing.
        recheckSource()
        recheckFiles(requireComplete = stored.receipt.phase == DesktopMigrationPhase.Published, allowMatching = true)
        if (stored.receipt.phase == DesktopMigrationPhase.Published) return stored
        for (artifact in prepared) {
            requireWriterExclusion()
            require(read(target) == stored) { "Migration receipt changed during publication" }
            val current = files.read(artifact.descriptor.key)
            if (artifact.payload == null) {
                require(current == null)
            } else if (current == null) {
                files.compareAndSet(artifact.descriptor.key, null, artifact.payload)
            } else {
                require(current.generation == 1L && current.payload?.contentEquals(artifact.payload) == true)
            }
        }
        recheckSource()
        recheckFiles(requireComplete = true, allowMatching = true)
        require(read(target) == stored)
        val complete = intent.copy(phase = DesktopMigrationPhase.Published)
        files.compareAndSet(key, stored.generation, encode(complete))
        recheckSource()
        recheckFiles(requireComplete = true, allowMatching = true)
        return requireNotNull(read(target)).also { require(it.receipt == complete) }
    }
}
