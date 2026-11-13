package io.apimeter.wear.crypto

import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.SCrypt
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 解密/加密过程中的可预期失败（恢复码不对、文件被改过、版本不支持等）。 */
class BackupCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 端到端加密：scrypt 派生 256 位密钥 → AES-256-GCM。服务器只存密文。
 * 参数按手表可承受的开销选定（N=2^14 约 16MB、单次 1 秒内）；nonce 每次写入重新随机，绝不复用。
 */
object BackupCrypto {

    const val KDF_ALGO = "scrypt"
    const val AEAD_ALGO = "AES-256-GCM"

    /** scrypt 参数：N 必须是 2 的幂。 */
    const val KDF_N = 1 shl 14
    const val KDF_R = 8
    const val KDF_P = 1

    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private const val RECOVERY_SECRET_BYTES = 16

    private val random = SecureRandom()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun newRandom(bytes: Int): ByteArray = ByteArray(bytes).also { random.nextBytes(it) }

    /** 16 字节 = 128 位熵的恢复码种子。 */
    fun newRecoverySecret(): ByteArray = newRandom(RECOVERY_SECRET_BYTES)

    /** 格式化成每 4 位一组的可抄写文本。 */
    fun formatRecoveryCode(secret: ByteArray): String = Base32Crockford.group(Base32Crockford.encode(secret))

    /** 手输文本 → 种子；格式不对返回 null。 */
    fun parseRecoveryCode(text: String): ByteArray? = Base32Crockford.decode(text)

    /** 参与 KDF 的是规整后的文本（大写、去分隔符），抄错大小写或漏分隔符仍可解开。 */
    fun deriveKey(secretText: String, salt: ByteArray, n: Int, r: Int, p: Int): ByteArray {
        val material = Base32Crockford.normalize(secretText).toByteArray(Charsets.UTF_8)
        if (material.isEmpty()) throw BackupCryptoException("恢复码不能为空")
        return deriveKeyBytes(material, salt, n, r, p)
    }

    /** 直接对字节做 scrypt，供 RFC 7914 测试向量使用。 */
    internal fun deriveKeyBytes(material: ByteArray, salt: ByteArray, n: Int, r: Int, p: Int): ByteArray =
        SCrypt.generate(material, salt, n, r, p, KEY_BYTES)

    /** 加密并输出可直接写入 WebDAV 的 JSON。 */
    fun seal(plaintext: ByteArray, secretText: String): String {
        val salt = newRandom(SALT_BYTES)
        val nonce = newRandom(NONCE_BYTES)
        val kdf = BackupKdfSpec(KDF_ALGO, KDF_N, KDF_R, KDF_P, base64(salt))
        val aead = BackupAeadSpec(AEAD_ALGO, base64(nonce))
        val key = deriveKey(secretText, salt, kdf.n, kdf.r, kdf.p)
        val aad = associatedData(BackupEnvelope.VERSION, kdf, aead).toByteArray(Charsets.UTF_8)
        val cipherText = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, nonce),
            )
            cipher.updateAAD(aad)
            cipher.doFinal(plaintext)
        } finally {
            key.fill(0)
        }
        val envelope = BackupEnvelope(kdf = kdf, aead = aead, ciphertext = base64(cipherText))
        return json.encodeToString(BackupEnvelope.serializer(), envelope)
    }

    /** 解密；任何校验失败都抛 [BackupCryptoException]。 */
    fun open(envelopeText: String, secretText: String): ByteArray {
        val envelope = try {
            json.decodeFromString(BackupEnvelope.serializer(), envelopeText)
        } catch (other: Exception) {
            throw BackupCryptoException("文件格式无法识别（不是本应用的备份？）", other)
        }
        if (envelope.format != BackupEnvelope.FORMAT) {
            throw BackupCryptoException("文件格式无法识别（不是本应用的备份？）")
        }
        if (envelope.version != BackupEnvelope.VERSION) {
            throw BackupCryptoException("备份文件版本 ${envelope.version} 暂不支持，请升级应用")
        }
        val kdf = envelope.kdf
        if (kdf.algo != KDF_ALGO) throw BackupCryptoException("不支持的 KDF：" + kdf.algo)
        if (envelope.aead.algo != AEAD_ALGO) throw BackupCryptoException("不支持的加密算法：" + envelope.aead.algo)
        if (kdf.n <= 0 || kdf.n and (kdf.n - 1) != 0 || kdf.r <= 0 || kdf.p <= 0) {
            throw BackupCryptoException("备份文件的 KDF 参数不合法")
        }
        // 防止恶意文件用超大 N 把设备拖死。
        if (kdf.n > MAX_KDF_N) throw BackupCryptoException("备份文件的 KDF 参数超出安全上限")

        val salt = decodeBase64(kdf.salt)
        val nonce = decodeBase64(envelope.aead.nonce)
        if (salt.size != SALT_BYTES) throw BackupCryptoException("备份文件的盐长度不对")
        if (nonce.size != NONCE_BYTES) throw BackupCryptoException("备份文件的随机数长度不对")

        val key = deriveKey(secretText, salt, kdf.n, kdf.r, kdf.p)
        val cipherText = decodeBase64(envelope.ciphertext)
        val aad = associatedData(envelope.version, kdf, envelope.aead).toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, nonce),
            )
            cipher.updateAAD(aad)
            cipher.doFinal(cipherText)
        } catch (other: Exception) {
            throw BackupCryptoException("恢复码不对，或文件已被篡改", other)
        } finally {
            key.fill(0)
        }
    }

    fun encodePayload(payload: BackupPayload): ByteArray =
        json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

    fun decodePayload(bytes: ByteArray): BackupPayload = try {
        json.decodeFromString(BackupPayload.serializer(), bytes.toString(Charsets.UTF_8))
    } catch (other: Exception) {
        throw BackupCryptoException("备份内容解析失败", other)
    }

    /** 整个明文头参与 AAD：服务器改写 version/salt/nonce/KDF 参数都会导致 GCM 校验失败。 */
    private fun associatedData(version: Int, kdf: BackupKdfSpec, aead: BackupAeadSpec): String =
        listOf(
            BackupEnvelope.FORMAT,
            version.toString(),
            kdf.algo,
            kdf.n.toString(),
            kdf.r.toString(),
            kdf.p.toString(),
            kdf.salt,
            aead.algo,
            aead.nonce,
        ).joinToString("|")

    private const val MAX_KDF_N = 1 shl 20

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decodeBase64(text: String): ByteArray = try {
        Base64.getDecoder().decode(text)
    } catch (other: IllegalArgumentException) {
        throw BackupCryptoException("备份文件里的 base64 字段损坏", other)
    }
}
