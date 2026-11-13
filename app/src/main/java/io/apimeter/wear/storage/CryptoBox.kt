package io.apimeter.wear.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore 支撑的对称加密，用于 API Key 落盘。 */
internal object CryptoBox {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "apimeter_secret_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val data64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return VERSION_PREFIX + iv64 + ":" + data64
    }

    fun decrypt(payload: String): String {
        require(payload.startsWith(VERSION_PREFIX)) { "unknown ciphertext version" }
        val body = payload.substring(VERSION_PREFIX.length)
        val parts = body.split(":")
        require(parts.size == 2) { "malformed ciphertext" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private const val VERSION_PREFIX = "v1:"
}
