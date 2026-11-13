package io.apimeter.wear.crypto

import io.apimeter.wear.core.FocusMetric
import io.apimeter.wear.core.RangePreset
import io.apimeter.wear.net.ProxyMode
import io.apimeter.wear.storage.CredentialMeta
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 加密层测试。
 *
 * 重点不是“能加密”，而是：
 * 1. scrypt 与 RFC 7914 测试向量一致（证明 KDF 参数/接线正确）；
 * 2. 密文能往返；
 * 3. 恢复码错、头部被改、密文被改、KDF 参数被恶意放大 —— 都必须明确失败。
 */
class BackupCryptoTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }

    // ---------- scrypt 与 RFC 7914 对齐 ----------

    @Test
    fun scrypt_matchesRfc7914_vectorEmptyPassword() {
        // scrypt("", "", N=16, r=1, p=1, dkLen=64)
        val expected = hex(
            "77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442" +
                "fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906",
        )
        val actual = org.bouncycastle.crypto.generators.SCrypt.generate(
            ByteArray(0),
            ByteArray(0),
            16,
            1,
            1,
            64,
        )
        assertArrayEquals(expected, actual)
    }

    @Test
    fun scrypt_matchesRfc7914_vectorPasswordNaCl() {
        // scrypt("password", "NaCl", N=1024, r=8, p=16, dkLen=64)
        val expected = hex(
            "fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b373162" +
                "2eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640",
        )
        val actual = org.bouncycastle.crypto.generators.SCrypt.generate(
            "password".toByteArray(),
            "NaCl".toByteArray(),
            1024,
            8,
            16,
            64,
        )
        assertArrayEquals(expected, actual)
    }

    @Test
    fun deriveKeyBytes_isDeterministicAnd32Bytes() {
        val salt = ByteArray(16) { it.toByte() }
        val first = BackupCrypto.deriveKeyBytes("secret".toByteArray(), salt, 1 shl 10, 8, 1)
        val second = BackupCrypto.deriveKeyBytes("secret".toByteArray(), salt, 1 shl 10, 8, 1)
        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }

    // ---------- 恢复码 ----------

    @Test
    fun recoveryCode_roundTripsThroughText() {
        val secret = BackupCrypto.newRecoverySecret()
        val text = BackupCrypto.formatRecoveryCode(secret)
        assertArrayEquals(secret, BackupCrypto.parseRecoveryCode(text))
    }

    @Test
    fun recoveryCode_toleratesCaseAndMissingDashes() {
        val secret = BackupCrypto.newRecoverySecret()
        val text = BackupCrypto.formatRecoveryCode(secret)
        val sloppy = text.lowercase().replace("-", "")
        assertArrayEquals(secret, BackupCrypto.parseRecoveryCode(sloppy))
    }

    @Test
    fun recoveryCode_rejectsIllegalCharacters() {
        assertNull(BackupCrypto.parseRecoveryCode("AAAA-BBBB-!!!!"))
        assertNull(BackupCrypto.parseRecoveryCode(""))
    }

    @Test
    fun deriveKey_ignoresFormatting() {
        val salt = ByteArray(16) { 7 }
        val grouped = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-23"
        val plain = "abcdefghjkmnpqrstvwxyz0123"
        assertArrayEquals(
            BackupCrypto.deriveKey(grouped, salt, 1 shl 10, 8, 1),
            BackupCrypto.deriveKey(plain, salt, 1 shl 10, 8, 1),
        )
    }

    // ---------- 加解密往返 ----------

    @Test
    fun sealAndOpen_roundTrip() {
        val secret = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val payload = samplePayload()
        val plain = BackupCrypto.encodePayload(payload)

        val envelope = BackupCrypto.seal(plain, secret)
        assertTrue(envelope.contains("apimeter-backup"))
        // 明文不能出现在容器里
        assertFalse(envelope.contains("sk-test-123456"))

        val opened = BackupCrypto.open(envelope, secret)
        val restored = BackupCrypto.decodePayload(opened)
        assertEquals(payload.revision, restored.revision)
        assertEquals(1, restored.credentials.size)
        assertEquals("sk-test-123456", restored.credentials.first().secrets["api_key"])
        assertEquals("主力账号", restored.credentials.first().meta.label)
    }

    @Test
    fun open_failsWithWrongSecret() {
        val secret = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val other = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val envelope = BackupCrypto.seal(BackupCrypto.encodePayload(samplePayload()), secret)
        expectCryptoFailure { BackupCrypto.open(envelope, other) }
    }

    @Test
    fun open_failsWhenSaltTampered() {
        val secret = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val envelope = BackupCrypto.seal(BackupCrypto.encodePayload(samplePayload()), secret)
        val parsed = json.decodeFromString(BackupEnvelope.serializer(), envelope)

        val tampered = parsed.copy(
            kdf = parsed.kdf.copy(
                salt = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { 9 }),
            ),
        )
        expectCryptoFailure {
            BackupCrypto.open(json.encodeToString(BackupEnvelope.serializer(), tampered), secret)
        }
    }

    @Test
    fun open_failsWhenIterationsTampered() {
        val secret = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val envelope = BackupCrypto.seal(BackupCrypto.encodePayload(samplePayload()), secret)
        val parsed = json.decodeFromString(BackupEnvelope.serializer(), envelope)

        val tampered = parsed.copy(kdf = parsed.kdf.copy(n = parsed.kdf.n * 2))
        expectCryptoFailure {
            BackupCrypto.open(json.encodeToString(BackupEnvelope.serializer(), tampered), secret)
        }
    }

    @Test
    fun open_failsWhenCiphertextTampered() {
        val secret = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val envelope = BackupCrypto.seal(BackupCrypto.encodePayload(samplePayload()), secret)
        val parsed = json.decodeFromString(BackupEnvelope.serializer(), envelope)

        val raw = java.util.Base64.getDecoder().decode(parsed.ciphertext)
        raw[0] = (raw[0].toInt() xor 0x01).toByte()
        val tampered = parsed.copy(
            ciphertext = java.util.Base64.getEncoder().encodeToString(raw),
        )
        expectCryptoFailure {
            BackupCrypto.open(json.encodeToString(BackupEnvelope.serializer(), tampered), secret)
        }
    }

    @Test
    fun open_rejectsAbsurdKdfCost() {
        val secret = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val envelope = BackupCrypto.seal(BackupCrypto.encodePayload(samplePayload()), secret)
        val parsed = json.decodeFromString(BackupEnvelope.serializer(), envelope)

        // 恶意文件把 N 拉到 2^21，必须被安全上限挡住，而不是把设备拖死。
        val tampered = parsed.copy(kdf = parsed.kdf.copy(n = 1 shl 21))
        expectCryptoFailure {
            BackupCrypto.open(json.encodeToString(BackupEnvelope.serializer(), tampered), secret)
        }
    }

    @Test
    fun open_rejectsUnknownFormat() {
        expectCryptoFailure { BackupCrypto.open("""{"format":"something-else"}""", "ABCD") }
    }

    // ---------- helpers ----------

    private fun samplePayload(): BackupPayload = BackupPayload(
        revision = 7L,
        writtenAt = 1_700_000_000_000L,
        writtenBy = "test",
        settings = BackupSettingsSnapshot(
            hapticsEnabled = false,
            refreshIntervalMinutes = 60,
            rangePreset = RangePreset.LAST_7_DAYS,
            proxyMode = ProxyMode.AUTO,
            proxyHost = "192.168.1.8",
            proxyPort = 7890,
        ),
        credentials = listOf(
            BackupCredential(
                meta = CredentialMeta(
                    id = "cred-1",
                    providerKey = "deepseek",
                    label = "主力账号",
                    quotaAmount = 0.0,
                ),
                secrets = mapOf("api_key" to "sk-test-123456"),
            ),
        ),
    )

    private fun expectCryptoFailure(block: () -> Unit) {
        try {
            block()
            fail("预期抛出 BackupCryptoException，但解密成功了")
        } catch (expected: BackupCryptoException) {
            assertNotNull(expected.message)
        }
    }

    private fun hex(text: String): ByteArray =
        text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
