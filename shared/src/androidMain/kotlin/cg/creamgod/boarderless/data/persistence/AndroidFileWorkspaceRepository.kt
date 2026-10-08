package cg.creamgod.boarderless.data.persistence

import android.content.Context
import cg.creamgod.boarderless.data.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** App-private local documents. APP mode selection must also supply local media gateways. */
fun androidFileWorkspaceRepository(context: Context): CanvasWorkspaceRepository =
    AndroidFileWorkspaceRepository(File(context.applicationContext.filesDir.canonicalFile, "local-documents"))

private class AndroidLocalDocuments(val root: File) {
    val repository = PersistentLocalWorkspaceRepository(
        { AndroidAtomicLocalRecordStore(root, 4 * 1024 * 1024) },
        object : LocalDocumentLock {
            override fun <T> withLock(block: () -> T): T = synchronized(this) { block() }
        },
        ::androidLocalDocumentTimestamp,
    )
}

internal fun androidLocalDocumentTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

internal class AndroidFileWorkspaceRepository private constructor(private val documents: AndroidLocalDocuments) :
    CanvasWorkspaceRepository by documents.repository {
    constructor(root: File) : this(AndroidLocalDocuments(root))
    override val localAssetGateway by lazy {
        AndroidFileAssetGateway(File(documents.root, "assets")) { session -> refresh(session); Unit }
    }
    override suspend fun listAssets(session: WorkspaceSession): List<WorkspaceAsset> = localAssetGateway.list(session)
    override suspend fun getAsset(session: WorkspaceSession, assetId: String): WorkspaceAsset = localAssetGateway.recover(session, assetId)
}
