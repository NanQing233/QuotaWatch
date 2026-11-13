package io.apimeter.wear.storage

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** 密钥保险箱：Keystore AES/GCM 加密后存私有 SharedPreferences；明文只在内存中短暂存在。 */
class SecretStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun put(credentialId: String, values: Map<String, String>) {
        val payload = AppJson.encodeToString(MAP_SERIALIZER, values)
        prefs.edit().putString(credentialId, CryptoBox.encrypt(payload)).apply()
    }

    fun get(credentialId: String): Map<String, String>? {
        val encrypted = prefs.getString(credentialId, null) ?: return null
        return runCatching {
            AppJson.decodeFromString(MAP_SERIALIZER, CryptoBox.decrypt(encrypted))
        }.getOrNull()
    }

    fun remove(credentialId: String) {
        prefs.edit().remove(credentialId).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun has(credentialId: String): Boolean = prefs.contains(credentialId)

    private companion object {
        const val PREFS_NAME = "apimeter_secrets"
        val MAP_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}
