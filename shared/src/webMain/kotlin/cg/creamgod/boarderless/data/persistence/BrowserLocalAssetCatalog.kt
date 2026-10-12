package cg.creamgod.boarderless.data.persistence

/** Metadata only. Original bytes require their own verified storage and publication barriers. */
internal fun browserLocalAssetCatalog() = AwaitedLocalAssetCatalog(
    BrowserDurableLocalRecordStore(BrowserRecordScope.AssetCatalog),
)
