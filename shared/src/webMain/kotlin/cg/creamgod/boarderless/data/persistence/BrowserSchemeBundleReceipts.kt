package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.feature.canvas.SchemeBundleAsset
import cg.creamgod.boarderless.feature.canvas.SchemeBundleReceipts

/** Scope/digest-bound browser receipts, awaiting strict IndexedDB transactions. */
fun browserSchemeBundleReceipts(owner: WorkspaceSession, digest: String, assets: List<SchemeBundleAsset>): SchemeBundleReceipts =
    PersistentSchemeBundleImportReceipts(BrowserDurableLocalRecordStore(), owner, digest, assets)
