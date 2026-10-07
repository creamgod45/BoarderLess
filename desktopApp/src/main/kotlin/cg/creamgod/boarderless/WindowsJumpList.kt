package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.RecentWorkspace
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.Executors

/**
 * The taskbar jump list's recent workspaces category, built with ICustomDestinationList. Each entry
 * starts the app's launcher with [OpenWorkspaceArgument]; [DesktopInstanceChannel] hands that to the
 * running app. Only the installed app has a launcher ([launcherPath]), so development runs skip it.
 */
internal class WindowsJumpList(private val launcherPath: String) {
    // COM objects live on one single-threaded apartment thread.
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "BoarderLess jump list").apply { isDaemon = true }
    }.also { it.execute { Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED) } }

    fun publish(category: String, workspaces: List<RecentWorkspace>) {
        executor.execute { runCatching { commit(category, workspaces) } }
    }

    private fun commit(category: String, workspaces: List<RecentWorkspace>) {
        val list = create(CLSID_DestinationList, IID_ICustomDestinationList)
        try {
            val removed = PointerByReference()
            list.call(4, IntByReference(), IID_IObjectArray, removed) // BeginList
            // Re-adding an entry the user removed from the jump list makes AppendCategory fail.
            val removedIds = removed.value?.let { ComObject(it).use(::workspaceIds) }.orEmpty()
            val entries = workspaces.filterNot { it.id in removedIds }
            try {
                if (entries.isNotEmpty()) {
                    create(CLSID_EnumerableObjectCollection, IID_IObjectCollection).use { collection ->
                        entries.forEach { workspace -> shellLink(workspace).use { collection.call(5, it.pointer) } } // AddObject
                        collection.query(IID_IObjectArray).use { array ->
                            list.call(5, WString(category), array.pointer) // AppendCategory
                        }
                    }
                }
                list.call(8) // CommitList
            } catch (error: Throwable) {
                runCatching { list.call(11) } // AbortList
                throw error
            }
        } finally {
            list.release()
        }
    }

    private fun shellLink(workspace: RecentWorkspace): ComObject {
        val link = create(CLSID_ShellLink, IID_IShellLinkW)
        try {
            describe(link, workspace)
        } catch (error: Throwable) {
            link.release()
            throw error
        }
        return link
    }

    private fun describe(link: ComObject, workspace: RecentWorkspace) {
        link.call(20, WString(launcherPath)) // SetPath
        link.call(11, WString(OpenWorkspaceArgument + workspace.id)) // SetArguments
        link.call(17, WString(launcherPath), 0) // SetIconLocation
        link.call(7, WString(workspace.title)) // SetDescription
        link.query(IID_IPropertyStore).use { properties ->
            val key = Memory(20).apply {
                write(0, PKEY_Title.toByteArray(), 0, 16)
                setInt(16, 2)
            }
            val title = Memory((workspace.title.length + 1L) * 2).apply { setWideString(0, workspace.title) }
            val value = Memory(24).apply {
                clear()
                setShort(0, VT_LPWSTR)
                setPointer(8, title)
            }
            properties.call(6, key, value) // SetValue copies the value
            properties.call(7) // Commit
        }
    }

    private fun workspaceIds(array: ComObject): Set<String> {
        val count = IntByReference().also { array.call(3, it) }.value // GetCount
        return (0 until count).mapNotNullTo(mutableSetOf()) { index ->
            val link = PointerByReference()
            runCatching { array.call(4, index, IID_IShellLinkW, link) }.getOrNull() ?: return@mapNotNullTo null // GetAt
            ComObject(link.value).use { shellLink ->
                val arguments = Memory(2048)
                shellLink.call(10, arguments, 1024) // GetArguments
                arguments.getWideString(0).takeIf { it.startsWith(OpenWorkspaceArgument) }?.removePrefix(OpenWorkspaceArgument)
            }
        }
    }

    private fun create(clsid: GUID, iid: GUID): ComObject {
        val result = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(clsid, null, WTypes.CLSCTX_INPROC_SERVER, iid, result)
        check(hr.toInt() >= 0) { "CoCreateInstance failed: 0x%08x".format(hr.toInt()) }
        return ComObject(result.value)
    }

    /** A COM interface pointer; methods are called by their vtable index. */
    private class ComObject(val pointer: Pointer) : AutoCloseable {
        fun call(index: Int, vararg args: Any?) {
            val hr = method(index).invokeInt(arrayOf(pointer, *args))
            check(hr >= 0) { "COM call $index failed: 0x%08x".format(hr) }
        }

        fun query(iid: GUID): ComObject = PointerByReference().let { call(0, iid, it); ComObject(it.value) }

        fun release() {
            runCatching { method(2).invokeInt(arrayOf(pointer)) } // Release returns a count, not an HRESULT
        }

        override fun close() = release()

        private fun method(index: Int): Function {
            val vtable = pointer.getPointer(0)
            return Function.getFunction(vtable.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        }
    }

    private companion object {
        const val VT_LPWSTR: Short = 31
        val CLSID_DestinationList = GUID("{77f10cf0-3db5-4966-b520-b7c54fd35ed6}")
        val IID_ICustomDestinationList = GUID("{6332debf-87b5-4670-90c0-5e57b408a49e}")
        val CLSID_EnumerableObjectCollection = GUID("{2d3468c1-36a7-43b6-ac24-d3f02fd9607a}")
        val IID_IObjectCollection = GUID("{5632b1a4-e38a-400a-928a-d4cd63230295}")
        val IID_IObjectArray = GUID("{92ca9dcd-5622-4bba-a805-5e9f541bd8c9}")
        val CLSID_ShellLink = GUID("{00021401-0000-0000-c000-000000000046}")
        val IID_IShellLinkW = GUID("{000214f9-0000-0000-c000-000000000046}")
        val IID_IPropertyStore = GUID("{886d8eeb-8cf2-4446-8d02-cdba1dbdcf99}")
        val PKEY_Title = GUID("{f29f85e0-4ff9-1068-ab91-08002b27b3d9}")
    }
}
