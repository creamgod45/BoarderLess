package cg.creamgod.boarderless.data.remote

internal data class StoredDraftScopeBundle(
    val generation: Long,
    val bundle: WorkspaceDraftScopeBundle?,
)

/** Typed boundary for private local POSIX storage. Not wired into production preferences yet. */
internal class DesktopDraftScopeBundleStore(
    private val files: DesktopAtomicDraftStore,
    private val requireScope: (PendingSubmissionScope) -> Unit = {},
) {
    /** Explicit caller confirmation/authorization required; exact ID plus generation prevents races. */
    fun removeDraft(
        scope: PendingSubmissionScope,
        expectedGeneration: Long,
        draftId: String,
    ): StoredDraftScopeBundle {
        val current = read(scope)
        if (current?.generation != expectedGeneration) throw AtomicDraftConflictException()
        WorkspaceDraftBundleTransitions.requireRemovable(requireNotNull(current.bundle), draftId)
        val bundle = requireNotNull(current.bundle)
        return compareAndSet(
            scope,
            expectedGeneration,
            if (bundle.retainedStopped.isEmpty()) {
                null
            } else {
                bundle.copy(journal = null, acknowledged = null)
            },
        )
    }

    /** Fixture-ready adoption, NOT a production Settings migration coordinator.
     * Caller must exclude all legacy writers before capturing source and keep them excluded.
     * Keeps legacy bytes; publishes bundle+reader marker in one CAS, no automatic retry/GC.
     */
    fun adoptLegacy(source: WorkspaceDraftLegacySnapshot): StoredDraftScopeBundle {
        val prepared = WorkspaceDraftLegacyMigration.prepare(source)
        val current = read(source.scope)
        if (current != null) {
            val bundle = requireNotNull(current.bundle) // Tombstones must not resurrect legacy state.
            if (bundle.legacyImportDigest == null && bundle.retainedStopped.isNotEmpty() &&
                bundle.journal == null && bundle.pending == null && bundle.stoppedPending == null && bundle.acknowledged == null
            ) {
                return compareAndSet(
                    source.scope,
                    current.generation,
                    prepared.copy(version = 3, retainedStopped = bundle.retainedStopped),
                )
            }
            // An already adopted bundle may have advanced. Never rewind to the legacy snapshot.
            require(bundle.legacyImportDigest == prepared.legacyImportDigest)
            return current
        }
        return compareAndSet(source.scope, null, prepared)
    }

    /** Keeps all exact historical wires under this scope's atomic generation. Caller must
     * exclude legacy writers and retain legacy sources until full migration is verified.
     */
    fun adoptStoppedArchives(
        scope: PendingSubmissionScope,
        expectedGeneration: Long?,
        entries: List<PendingWorkspaceSubmission>,
    ): StoredDraftScopeBundle =
        update(scope, expectedGeneration) {
            WorkspaceDraftBundleTransitions.retainStopped(it, scope, entries)
        }

    /** One-shot transition; conflict/unknown outcome must be freshly reviewed, never retried here. */
    fun update(
        scope: PendingSubmissionScope,
        expectedGeneration: Long?,
        transition: (WorkspaceDraftScopeBundle?) -> WorkspaceDraftScopeBundle,
    ): StoredDraftScopeBundle {
        val current = read(scope)
        if (current?.generation != expectedGeneration) throw AtomicDraftConflictException()
        return compareAndSet(scope, expectedGeneration, transition(current?.bundle))
    }

    fun read(scope: PendingSubmissionScope): StoredDraftScopeBundle? {
        requireScope(scope)
        val record = files.read(WorkspaceDraftScopeBundleCodec.scopeHash(scope)) ?: return null
        return StoredDraftScopeBundle(
            record.generation,
            record.payload?.let { WorkspaceDraftScopeBundleCodec.decode(it, scope) },
        )
    }

    /** Verify the typed current generation and complete its durability barrier without a new
     * publication. Generation drift is rejected by the locked file store, never retried.
     */
    fun confirmDurable(
        scope: PendingSubmissionScope,
        expectedGeneration: Long,
    ): StoredDraftScopeBundle {
        val current = read(scope)
        if (current?.generation != expectedGeneration) throw AtomicDraftConflictException()
        val record = files.confirmDurable(WorkspaceDraftScopeBundleCodec.scopeHash(scope), expectedGeneration)
        return StoredDraftScopeBundle(record.generation, record.payload?.let { WorkspaceDraftScopeBundleCodec.decode(it, scope) })
    }

    fun compareAndSet(
        scope: PendingSubmissionScope,
        expectedGeneration: Long?,
        bundle: WorkspaceDraftScopeBundle?,
    ): StoredDraftScopeBundle {
        require(bundle == null || bundle.scope == scope)
        val encoded = bundle?.let(WorkspaceDraftScopeBundleCodec::encode)
        // Never replace an unreadable/unknown typed record, including deletion. Concurrent changes
        // after this read still fail the underlying atomic generation CAS.
        val current = read(scope)
        if (current?.generation != expectedGeneration) throw AtomicDraftConflictException()
        val record = files.compareAndSet(WorkspaceDraftScopeBundleCodec.scopeHash(scope), expectedGeneration, encoded)
        return StoredDraftScopeBundle(
            record.generation,
            record.payload?.let { WorkspaceDraftScopeBundleCodec.decode(it, scope) },
        )
    }
}
