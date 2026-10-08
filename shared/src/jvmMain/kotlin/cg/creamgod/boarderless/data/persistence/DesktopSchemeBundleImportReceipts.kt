package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.remote.DesktopAtomicDraftStore
import cg.creamgod.boarderless.feature.canvas.SchemeBundleAsset
import cg.creamgod.boarderless.feature.canvas.SchemeBundleReceipts
import java.nio.file.Path

internal class DesktopSchemeBundleImportReceipts(
    root: Path,
    owner: WorkspaceSession,
    bundleDigest: String,
    assets: List<SchemeBundleAsset>,
) : SchemeBundleReceipts by PersistentSchemeBundleImportReceipts(object : DurableLocalRecordStore {
    private val files = DesktopAtomicDraftStore(root)
    override fun read(key: String): DurableLocalRecord? = files.read(key)?.let {
        DurableLocalRecord(it.generation, requireNotNull(it.payload))
    }
    override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
        files.compareAndSet(key, generation, payload)
    }
    override fun confirmDurable(key: String, generation: Long) { files.confirmDurable(key, generation) }
}, owner, bundleDigest, assets)
