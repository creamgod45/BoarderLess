package cg.creamgod.boarderless.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM with a non-exportable Android Keystore key; only the ciphertext reaches app-private
 * preferences. Excluded from backup so a restored ciphertext is never paired with a missing key.
 */
class AndroidGiphyKeyStore(
    context: Context,
) : GiphyKeyStore {
    private val preferences = context.applicationContext.getSharedPreferences("boarderless_giphy", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override suspend fun load(): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = Base64.decode(preferences.getString(VALUE, null) ?: return@runCatching null, Base64.NO_WRAP)
                require(raw.size > IV_BYTES)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, IV_BYTES)))
                cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES).decodeToString().takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    override suspend fun save(key: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val sealed = cipher.iv + cipher.doFinal(key.encodeToByteArray())
                preferences.edit().putString(VALUE, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
            }.getOrDefault(false)
        }

    override suspend fun clear(): Boolean = withContext(Dispatchers.IO) { runCatching { preferences.edit().remove(VALUE).commit() }.getOrDefault(false) }

    private companion object {
        const val ALIAS = "boarderless.giphy.key"
        const val VALUE = "sealed"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
