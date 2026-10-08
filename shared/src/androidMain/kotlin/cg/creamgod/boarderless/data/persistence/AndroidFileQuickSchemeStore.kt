package cg.creamgod.boarderless.data.persistence

import android.content.Context
import java.io.File

/** Android app-private library. The framework parent is canonicalized, never the owned leaf. */
fun androidFileQuickSchemeStore(context: Context): QuickSchemeRepository =
    AndroidFileQuickSchemeStore(File(context.applicationContext.filesDir.canonicalFile, "quick-schemes"))

internal class AndroidFileQuickSchemeStore(root: File) : QuickSchemeRepository {
    private val delegate = PersistentQuickSchemeStore(AndroidAtomicLocalRecordStore(root, 4 * 1024 * 1024))
    override fun list(): List<QuickScheme> = synchronized(this) { delegate.list() }
    override fun save(payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme =
        synchronized(this) { delegate.save(payload, name, schemaVersion, sourceWorkspaceId) }
    override fun rename(id: Int, name: String): QuickScheme? = synchronized(this) { delegate.rename(id, name) }
    override fun delete(id: Int): Boolean = synchronized(this) { delegate.delete(id) }
}
