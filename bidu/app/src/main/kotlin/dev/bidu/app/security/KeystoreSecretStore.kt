package dev.bidu.app.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM key in AndroidKeyStore (no user-auth binding, so the background service can read the
 * cloud key). Each value is encrypted with a fresh keystore-generated IV; "v1:<iv>:<ciphertext>"
 * (Base64) lives in a private SharedPreferences file. The entry name is bound as AAD so ciphertexts
 * cannot be swapped between entries. Values and plaintext lengths are never logged.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val lock = Mutex()

    override suspend fun get(name: String): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            val stored = prefs.getString(entryKey(name), null) ?: return@withLock null
            try {
                decrypt(name, stored)
            } catch (e: GeneralSecurityException) {
                discard(name, e)
            } catch (e: IllegalArgumentException) {
                discard(name, e)
            }
        }
    }

    override suspend fun put(name: String, value: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Secret name must not be blank" }
        lock.withLock {
            if (value.isEmpty()) {
                commit(prefs.edit().remove(entryKey(name)))
            } else {
                commit(prefs.edit().putString(entryKey(name), encrypt(name, value)))
            }
        }
    }

    override suspend fun remove(name: String) = withContext(Dispatchers.IO) {
        lock.withLock { commit(prefs.edit().remove(entryKey(name))) }
    }

    override suspend fun has(name: String): Boolean = get(name) != null

    private fun encrypt(name: String, value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(aad(name))
        val plain = value.toByteArray(Charsets.UTF_8)
        try {
            val sealed = cipher.doFinal(plain)
            val iv = cipher.iv
            val b64 = Base64.getEncoder()
            return "$FORMAT_VERSION:${b64.encodeToString(iv)}:${b64.encodeToString(sealed)}"
        } finally {
            plain.fill(0)
        }
    }

    private fun decrypt(name: String, stored: String): String {
        val parts = stored.split(':')
        require(parts.size == 3 && parts[0] == FORMAT_VERSION) { "Unknown secret format" }
        val b64 = Base64.getDecoder()
        val iv = b64.decode(parts[1])
        val sealed = b64.decode(parts[2])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(name))
        val plain = cipher.doFinal(sealed)
        try {
            return String(plain, Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    /** An entry that no longer decrypts (key lost, tampered file) is removed rather than retried forever. */
    private fun discard(name: String, e: Exception): String? {
        Log.w(TAG, "Secret entry could not be decrypted and was removed (${e.javaClass.simpleName})")
        prefs.edit().remove(entryKey(name)).commit()
        return null
    }

    private fun commit(editor: SharedPreferences.Editor) {
        if (!editor.commit()) throw IOException("Could not persist the secret store")
    }

    private fun entryKey(name: String) = "secret.$name"

    private fun aad(name: String) = "bidu-secret:$name".toByteArray(Charsets.UTF_8)

    private companion object {
        const val TAG = "BiduSecrets"
        const val PREFS_FILE = "bidu_secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bidu.secret.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val GCM_TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
