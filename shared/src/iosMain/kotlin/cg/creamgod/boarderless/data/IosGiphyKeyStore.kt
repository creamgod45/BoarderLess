package cg.creamgod.boarderless.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** iOS Keychain generic password, this device only (never synced or migrated to another device). */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosGiphyKeyStore : GiphyKeyStore {
    /** Builds the CFDictionary and always releases it, plus every bridged value, after [block]. */
    private fun <T> withQuery(
        extra: List<Pair<COpaquePointer?, COpaquePointer?>> = emptyList(),
        block: (CFDictionaryRef?) -> T,
    ): T {
        val owned = mutableListOf<COpaquePointer?>()
        val dictionary = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            val service = CFBridgingRetain(SERVICE as NSString).also(owned::add)
            val account = CFBridgingRetain(ACCOUNT as NSString).also(owned::add)
            CFDictionarySetValue(dictionary, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dictionary, kSecAttrService, service)
            CFDictionarySetValue(dictionary, kSecAttrAccount, account)
            extra.forEach { (key, value) -> CFDictionarySetValue(dictionary, key, value) }
            return block(dictionary)
        } finally {
            owned.forEach { CFRelease(it) }
            CFRelease(dictionary)
        }
    }

    private fun remove(): Boolean {
        val status = withQuery { SecItemDelete(it) }
        return status == errSecSuccess || status == errSecItemNotFound
    }

    override suspend fun load(): String? =
        withContext(Dispatchers.Default) {
            withQuery(listOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)) { request ->
                memScoped {
                    val result = alloc<CFTypeRefVar>()
                    if (SecItemCopyMatching(request, result.ptr) != errSecSuccess) return@memScoped null
                    val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
                    NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()?.takeIf { it.isNotBlank() }
                }
            }
        }

    override suspend fun save(key: String): Boolean =
        withContext(Dispatchers.Default) {
            val data = (key as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return@withContext false
            if (!remove()) return@withContext false
            val retained = CFBridgingRetain(data)
            try {
                withQuery(
                    listOf(kSecValueData to retained, kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly),
                ) { SecItemAdd(it, null) } == errSecSuccess
            } finally {
                CFRelease(retained)
            }
        }

    override suspend fun clear(): Boolean = withContext(Dispatchers.Default) { remove() }

    private companion object {
        const val SERVICE = "BoarderLess GIPHY key"
        const val ACCOUNT = "boarderless"
    }
}
