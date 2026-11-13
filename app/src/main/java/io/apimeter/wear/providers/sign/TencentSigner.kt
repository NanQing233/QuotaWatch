package io.apimeter.wear.providers.sign

import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 腾讯云 API 3.0 签名（TC3-HMAC-SHA256），纯 JVM 实现。参考：https://cloud.tencent.com/document/api/213/30654 */
object TencentSigner {

    private const val ALGORITHM = "TC3-HMAC-SHA256"
    private const val SERVICE_REQUEST = "tc3_request"
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    data class Input(
        val method: String,
        val host: String,
        val path: String = "/",
        val query: Map<String, String> = emptyMap(),
        val contentType: String,
        val body: ByteArray,
        val secretId: String,
        val secretKey: String,
        val service: String,
        val timestampSec: Long = System.currentTimeMillis() / 1000L,
    ) {
        companion object
    }

    fun authorization(input: Input): String {
        val timestamp = input.timestampSec
        val date = DATE.format(Instant.ofEpochSecond(timestamp))

        val canonicalQuery = canonicalQuery(input.query)
        val canonicalHeaders = "content-type:" + input.contentType.trim() + "\n" + "host:" + input.host + "\n"
        val signedHeaders = "content-type;host"
        val hashedPayload = sha256Hex(input.body)
        val canonicalRequest = buildString {
            append(input.method.uppercase()).append('\n')
            append(input.path.ifBlank { "/" }).append('\n')
            append(canonicalQuery).append('\n')
            append(canonicalHeaders).append('\n')
            append(signedHeaders).append('\n')
            append(hashedPayload)
        }

        val credentialScope = date + "/" + input.service + "/" + SERVICE_REQUEST
        val stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n" +
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))

        val secretDate = hmac(("TC3" + input.secretKey).toByteArray(Charsets.UTF_8), date.toByteArray(Charsets.UTF_8))
        val secretService = hmac(secretDate, input.service.toByteArray(Charsets.UTF_8))
        val secretSigning = hmac(secretService, SERVICE_REQUEST.toByteArray(Charsets.UTF_8))
        val signature = hex(hmac(secretSigning, stringToSign.toByteArray(Charsets.UTF_8)))

        return ALGORITHM + " Credential=" + input.secretId + "/" + credentialScope +
            ", SignedHeaders=" + signedHeaders + ", Signature=" + signature
    }

    internal fun canonicalQuery(query: Map<String, String>): String =
        query.entries
            .map { (key, value) -> encode(key) + "=" + encode(value) }
            .sorted()
            .joinToString("&")

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    internal fun sha256Hex(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    internal fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    internal fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val v = byte.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
