package cg.creamgod.boarderless.data.persistence

/** A separate strict IndexedDB library; no Settings or volatile fallback. */
fun browserQuickSchemeRepository(): AwaitedQuickSchemeRepository = AwaitedPersistentQuickSchemeStore(BrowserDurableLocalRecordStore(scope = BrowserRecordScope.Library))
