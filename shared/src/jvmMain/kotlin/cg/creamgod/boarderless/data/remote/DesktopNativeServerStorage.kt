package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** Fresh prerelease namespace. No legacy import, sequence reset, Settings fallback or retry.
 * The Desktop lifetime storage lease excludes other application writers.
 */
internal class DesktopNativeServerStorage private constructor(
    loadFiles: () -> DesktopAtomicDraftStore,
) {
    constructor(root: Path) : this({ DesktopAtomicDraftStore(root) })
    internal constructor(files: DesktopAtomicDraftStore) : this({ files })

    private val files by lazy(loadFiles)
    private val json = Json { encodeDefaults = true }

    @Serializable private enum class Phase { Preparing, Ready }

    @Serializable private data class Entry(
        val scope: PendingSubmissionScope,
        val phase: Phase,
    )

    @Serializable private data class Control(
        val version: Int = 1,
        val clientId: String,
        val metadata: Map<String, String> = emptyMap(),
        val entries: List<Entry> = emptyList(),
    )

    private data class Read(
        val generation: Long,
        val control: Control,
    )

    private val rawDrafts by lazy { DesktopDraftScopeBundleStore(files) }
    private val rawSafety by lazy { DesktopRecoverySafetyStore(files) }
    private val rawLedger by lazy { DesktopClientSequenceLedger(files) }
    private var confirmedControl: Long? = null

    private fun read(): Read {
        val record = files.read(Key)
        if (record == null) {
            check(files.recordKeys().isEmpty()) { "Server storage identity is missing" }
            val initial = Control(clientId = UUID.randomUUID().toString())
            val saved = files.compareAndSet(Key, null, json.encodeToString(initial).encodeToByteArray())
            confirmedControl = saved.generation
            return Read(saved.generation, initial)
        }
        val bytes = requireNotNull(record.payload)
        require(bytes.size <= 1024 * 1024)
        val root = parseStrictJsonObject(bytes.decodeToString(throwOnInvalidSequence = true))
        require(root.keys == setOf("version", "clientId", "metadata", "entries"))
        val control = json.decodeFromJsonElement(Control.serializer(), root)
        require(control.version == 1 && UUID.fromString(control.clientId).toString() == control.clientId)
        require(control.metadata.keys.all { it in MetadataKeys })
        require(control.metadata.values.all { it.isNotBlank() && it.encodeToByteArray(throwOnInvalidSequence = true).size <= 4096 })
        require(
            control.entries.size <= 256 && control.entries
                .map { it.scope }
                .distinct()
                .size == control.entries.size,
        )
        require(control.entries.count { it.phase == Phase.Preparing } <= 1)
        control.entries.forEach {
            require(it.scope.clientId == control.clientId)
            WorkspaceDraftScopeBundleCodec.scopeHash(it.scope)
        }
        return Read(record.generation, control)
    }

    private fun write(
        before: Read,
        control: Control,
    ): Read {
        val bytes = json.encodeToString(control).encodeToByteArray()
        require(bytes.size <= 1024 * 1024)
        val next = files.compareAndSet(Key, before.generation, bytes)
        confirmedControl = next.generation
        return Read(next.generation, control)
    }

    private fun confirmControl(current: Read) {
        if (confirmedControl != current.generation) {
            files.confirmDurable(Key, current.generation)
            confirmedControl = current.generation
        }
    }

    private fun ensureScope(scope: PendingSubmissionScope) =
        synchronized(this) {
            var current = read()
            require(scope.clientId == current.control.clientId)
            val entry = current.control.entries.singleOrNull { it.scope == scope }
            if (entry == null) {
                require(current.control.entries.size < 256 && current.control.entries.none { it.phase == Phase.Preparing })
                val draftKey = WorkspaceDraftScopeBundleCodec.scopeHash(scope)
                require(files.read(draftKey) == null && files.read(rawSafety.key(scope)) == null)
                val shared = current.control.entries.any { rawLedger.key(it.scope) == rawLedger.key(scope) }
                require(if (shared) rawLedger.read(scope) != null else rawLedger.read(scope) == null)
                current = write(current, current.control.copy(entries = current.control.entries + Entry(scope, Phase.Preparing)))
            }
            val preparing =
                current.control.entries
                    .single { it.scope == scope }
                    .phase == Phase.Preparing
            if (preparing) {
                val draftKey = WorkspaceDraftScopeBundleCodec.scopeHash(scope)
                val draft = files.read(draftKey)
                if (draft == null) {
                    files.compareAndSet(draftKey, null, null)
                } else {
                    require(draft.generation == 1L && draft.payload == null)
                }
                val safety = rawSafety.read(scope)
                if (safety ==
                    null
                ) {
                    files.compareAndSet(rawSafety.key(scope), null, RecoverySafetyBundleCodec.encode(RecoverySafetyBundle(scope = scope)))
                } else {
                    require(safety.generation == 1L && safety.bundle == RecoverySafetyBundle(scope = scope))
                }
                val shared = current.control.entries.any { it.scope != scope && rawLedger.key(it.scope) == rawLedger.key(scope) }
                if (rawLedger.read(scope) == null) {
                    require(!shared)
                    rawLedger.initialize(scope, 0)
                }
                for (key in listOf(draftKey, rawSafety.key(scope), rawLedger.key(scope))) {
                    files.confirmDurable(key, requireNotNull(files.read(key)).generation)
                }
                current =
                    write(
                        current,
                        current.control.copy(
                            entries =
                                current.control.entries.map {
                                    if (it.scope ==
                                        scope
                                    ) {
                                        it.copy(phase = Phase.Ready)
                                    } else {
                                        it
                                    }
                                },
                        ),
                    )
            }
            require(rawDrafts.read(scope) != null && rawSafety.read(scope) != null && rawLedger.read(scope) != null) {
                "Registered server storage is missing"
            }
            confirmControl(current)
        }

    private val drafts by lazy { DesktopWorkspaceDraftPersistence(DesktopDraftScopeBundleStore(files, ::ensureScope)) }
    private val safety by lazy { DesktopRecoverySafetyStore(files, ::ensureScope) }
    private val ledger by lazy { DesktopClientSequenceLedger(files, ::ensureScope) }
    private val draftAdapter =
        object : WorkspaceDraftPersistence {
            override val supportsAtomicStoppedEvidence = true

            override fun recoveryGeneration(scope: PendingSubmissionScope) = drafts.recoveryGeneration(scope)

            override fun stoppedEvidence(scope: PendingSubmissionScope) = drafts.stoppedEvidence(scope)

            override fun stoppedEvidence(
                scope: PendingSubmissionScope,
                id: String,
            ) = drafts.stoppedEvidence(scope, id)

            override fun recordStoppedEvidence(
                scope: PendingSubmissionScope,
                generation: Long,
                submitted: PendingWorkspaceSubmission,
                evidence: StoppedSubmissionEvidence,
            ) = drafts.recordStoppedEvidence(scope, generation, submitted, evidence)

            override fun journal(scope: PendingSubmissionScope) = drafts.journal(scope)

            override fun pending(scope: PendingSubmissionScope) = drafts.pending(scope)

            override fun stopped(scope: PendingSubmissionScope) = drafts.stopped(scope)

            override fun append(
                scope: PendingSubmissionScope,
                session: WorkspaceSession,
                before: Workspace,
                operation: WorkspaceOperation,
            ) = drafts.append(scope, session, before, operation)

            override fun stage(entry: PendingWorkspaceSubmission) = drafts.stage(entry)

            override fun acknowledge(
                entry: PendingWorkspaceSubmission,
                version: Long,
                seq: Long,
            ) = drafts.acknowledge(entry, version, seq)

            override fun restore(
                scope: PendingSubmissionScope,
                id: String,
                current: WorkspaceSession,
            ) = drafts.restore(scope, id, current)

            override fun remove(
                scope: PendingSubmissionScope,
                id: String,
            ) = drafts.remove(scope, id)

            override fun stop(
                scope: PendingSubmissionScope,
                id: String,
            ) = drafts.stop(scope, id)
        }
    private val safetyAdapter =
        object : RecoverySafetyPersistence {
            override fun fenceState(pending: PendingWorkspaceSubmission) = safety.fenceState(pending)

            override fun beginFence(pending: PendingWorkspaceSubmission) = safety.beginFence(pending)

            override fun observeFence(
                pending: PendingWorkspaceSubmission,
                status: PendingReceiptStatus,
            ) = safety.observeFence(pending, status)

            override fun deletion(
                scope: PendingSubmissionScope,
                entity: String,
            ) = safety.deletion(scope, entity)

            override fun recordDeletions(
                scope: PendingSubmissionScope,
                records: List<CommittedDeletionEvidence>,
            ) = safety.recordDeletions(scope, records)
        }
    private val sequences =
        object : ClientSequenceAllocator {
            override fun reserve(
                scope: PendingSubmissionScope,
                count: Int,
            ) = ledger.reserve(scope, count)

            override fun advance(
                scope: PendingSubmissionScope,
                sequence: Long,
            ) = ledger.advance(scope, sequence)
        }
    val preferences =
        SessionPreferences(MetadataSettings(), safetyAdapter, draftAdapter, sequences) {
            synchronized(this) { read().also(::confirmControl).control.clientId }
        }

    private inner class MetadataSettings : Settings {
        override val keys: Set<String> get() = synchronized(this@DesktopNativeServerStorage) { read().control.metadata.keys }
        override val size: Int get() = keys.size

        override fun hasKey(key: String) = key in keys

        override fun clear() {
            synchronized(this@DesktopNativeServerStorage) {
                val current = read()
                write(current, current.control.copy(metadata = emptyMap()))
            }
        }

        override fun remove(key: String) {
            require(key in MetadataKeys)
            synchronized(this@DesktopNativeServerStorage) {
                val current = read()
                if (key in
                    current.control.metadata
                ) {
                    write(current, current.control.copy(metadata = current.control.metadata - key))
                }
            }
        }

        override fun putString(
            key: String,
            value: String,
        ) {
            require(key in MetadataKeys && value.isNotBlank() && value.encodeToByteArray(throwOnInvalidSequence = true).size <= 4096)
            synchronized(this@DesktopNativeServerStorage) {
                val current = read()
                if (current.control.metadata[key] !=
                    value
                ) {
                    write(current, current.control.copy(metadata = current.control.metadata + (key to value)))
                }
            }
        }

        override fun getStringOrNull(key: String): String? {
            require(key in MetadataKeys)
            return synchronized(this@DesktopNativeServerStorage) { read().control.metadata[key] }
        }

        override fun getString(
            key: String,
            defaultValue: String,
        ) = getStringOrNull(key) ?: defaultValue

        override fun putInt(
            key: String,
            value: Int,
        ): Unit = error("Native server metadata is string-only")

        override fun getIntOrNull(key: String): Int? = error("Native server metadata is string-only")

        override fun getInt(
            key: String,
            defaultValue: Int,
        ): Int = error("Native server metadata is string-only")

        override fun putLong(
            key: String,
            value: Long,
        ): Unit = error("Use scoped native sequences")

        override fun getLongOrNull(key: String): Long? = error("Use scoped native sequences")

        override fun getLong(
            key: String,
            defaultValue: Long,
        ): Long = error("Use scoped native sequences")

        override fun putFloat(
            key: String,
            value: Float,
        ): Unit = error("Native server metadata is string-only")

        override fun getFloatOrNull(key: String): Float? = error("Native server metadata is string-only")

        override fun getFloat(
            key: String,
            defaultValue: Float,
        ): Float = error("Native server metadata is string-only")

        override fun putDouble(
            key: String,
            value: Double,
        ): Unit = error("Native server metadata is string-only")

        override fun getDoubleOrNull(key: String): Double? = error("Native server metadata is string-only")

        override fun getDouble(
            key: String,
            defaultValue: Double,
        ): Double = error("Native server metadata is string-only")

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): Unit = error("Native server metadata is string-only")

        override fun getBooleanOrNull(key: String): Boolean? = error("Native server metadata is string-only")

        override fun getBoolean(
            key: String,
            defaultValue: Boolean,
        ): Boolean = error("Native server metadata is string-only")
    }

    private companion object {
        val MetadataKeys = setOf("backend.userId", "backend.workspaceId")
        val Key =
            MessageDigest.getInstance("SHA-256").digest("BoarderLess.native-server-control.v1".toByteArray()).joinToString("") {
                (
                    it.toInt() and
                        255
                ).toString(16).padStart(2, '0')
            }
    }
}

fun desktopNativeBackendWorkspaceRepository(root: Path): BackendWorkspaceRepository =
    BackendWorkspaceRepository(preferences = DesktopNativeServerStorage(root).preferences)
