package dev.bidu.app.security

/**
 * Small secrets (the optional cloud API key) encrypted at rest with an AES-GCM key held in
 * AndroidKeyStore. Values never appear in logs, settings exports or the DataStore file.
 */
interface SecretStore {
    suspend fun get(name: String): String?
    suspend fun put(name: String, value: String)
    suspend fun remove(name: String)
    suspend fun has(name: String): Boolean = get(name) != null

    companion object {
        const val CLOUD_API_KEY = "cloud_api_key"
    }
}
