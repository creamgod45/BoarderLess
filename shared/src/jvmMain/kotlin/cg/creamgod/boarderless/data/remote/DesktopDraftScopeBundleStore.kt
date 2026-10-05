package cg.creamgod.boarderless.data.remote

internal data class StoredDraftScopeBundle(val generation: Long, val bundle: WorkspaceDraftScopeBundle?)

/** Typed boundary for private local POSIX storage. Not wired into production preferences yet. */
internal class DesktopDraftScopeBundleStore(private val files: DesktopAtomicDraftStore) {
    /** Explicit caller confirmation/authorization required; exact ID plus generation prevents races. */
    fun removeDraft(scope: PendingSubmissionScope, expectedGeneration: Long, draftId: String): StoredDraftScopeBundle {
        val current = read(scope)
        if (current?.generation != expectedGeneration) throw AtomicDraftConflictException()
        WorkspaceDraftBundleTransitions.requireRemovable(requireNotNull(current.bundle), draftId)
        return compareAndSet(scope, expectedGeneration, null)
    }

    /** Fixture-ready adoption, NOT a production Settings migration coordinator.
     * Caller must exclude all legacy writers before capturing source and keep them excluded.
     * Keeps legacy bytes; publishes bundle+reader marker in one CAS, no automatic retry/GC.
     */
    fun adoptLegacy(source: WorkspaceDraftLegacySnapshot): StoredDraftScopeBundle {
        val prepared = WorkspaceDraftLegacyMigration.prepare(source)
        val current = read(source.scope)
        if (current != null) {
            // An already adopted bundle may have advanced. Never rewind to the legacy snapshot.
            require(current.bundle?.legacyImportDigest == prepared.legacyImportDigest)
            return current
        }
        return compareAndSet(source.scope, null, prepared)
    }

    /** One-shot transition; conflict/unknown outcome must be freshly reviewed, never retried here. */
    fun update(scope: PendingSubmissionScope, expectedGeneration: Long?,
               transition: (WorkspaceDraftScopeBundle?) -> WorkspaceDraftScopeBundle): StoredDraftScopeBundle {
        val current = read(scope)
        if (current?.generation != expectedGeneration) throw AtomicDraftConflictException()
        return compareAndSet(scope, expectedGeneration, transition(current?.bundle))
    }

    fun read(scope: PendingSubmissionScope): StoredDraftScopeBundle? {
        val record = files.read(WorkspaceDraftScopeBundleCodec.scopeHash(scope)) ?: return null
        return StoredDraftScopeBundle(record.generation,
            record.payload?.let { WorkspaceDraftScopeBundleCodec.decode(it, scope) })
    }

    fun compareAndSet(scope: PendingSubmissionScope, expectedGeneration: Long?, bundle: WorkspaceDraftScopeBundle?): StoredDraftScopeBundle {
        require(bundle == null || bundle.scope == scope)
        val encoded = bundle?.let(WorkspaceDraftScopeBundleCodec::encode)
        // Never replace an unreadable/unknown typed record, including deletion. Concurrent changes
        // after this read still fail the underlying atomic generation CAS.
        val current = read(scope)
        if (current?.generation != expectedGeneration) throw AtomicDraftConflictException()
        val record = files.compareAndSet(WorkspaceDraftScopeBundleCodec.scopeHash(scope), expectedGeneration, encoded)
        return StoredDraftScopeBundle(record.generation,
            record.payload?.let { WorkspaceDraftScopeBundleCodec.decode(it, scope) })
    }
}
