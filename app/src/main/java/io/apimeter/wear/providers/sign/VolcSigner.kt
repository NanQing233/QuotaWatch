package io.apimeter.wear.providers.sign

import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 火山引擎 V4 签名（HMAC-SHA256），纯 JVM 实现，方便单测。参考：https://www.volcengine.com/docs/6369/67269 */
object VolcSigner {

    private const val ALGORITHM = "HMAC-SHA256"
    private const val SERVICE_REQUEST = "request"
    private val DATE_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val SHORT_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    data class Input(
        val method: String,
        val host: String,
        val path: String = "/",
        val query: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray = ByteArray(0),
        val accessKey: String,
        val secretKey: String,
        val region: String,
        val service: String,
        val nowMillis: Long = System.currentTimeMillis(),
    )

    fun sign(input: Input): Map<String, String> {
        val now = Instant.ofEpochMilli(input.nowMillis)
        val xDate = DATE_TIME.format(now)
        val shortDate = SHORT_DATE.format(now)
        val payloadHash = sha256Hex(input.body)

        val signedMap = sortedMapOf<String, String>()
        signedMap["host"] = input.host
        signedMap["x-date"] = xDate
        signedMap["x-content-sha256"] = payloadHash
        input.headers.forEach { (key, value) -> signedMap[key.lowercase()] = value.trim() }

        val signedHeaders = signedMap.keys.joinToString(";")
        val canonicalHeaders = signedMap.entries.joinToString(separator = "") { (key, value) -> key + ":" + value + "\n" }
        val canonicalRequest = buildString {
            append(input.method.uppercase()).append('\n')
            append(input.path.ifBlank { "/" }).append('\n')
            append(canonicalQuery(input.query)).append('\n')
            append(canonicalHeaders).append('\n')
            append(signedHeaders).append('\n')
            append(payloadHash)
        }

        val scope = shortDate + "/" + input.region + "/" + input.service + "/" + SERVICE_REQUEST
        val stringToSign = ALGORITHM + "\n" + xDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))

        val kDate = hmac(input.secretKey.toByteArray(Charsets.UTF_8), shortDate.toByteArray(Charsets.UTF_8))
        val kRegion = hmac(kDate, input.region.toByteArray(Charsets.UTF_8))
        val kService = hmac(kRegion, input.service.toByteArray(Charsets.UTF_8))
        val kSigning = hmac(kService, SERVICE_REQUEST.toByteArray(Charsets.UTF_8))
        val signature = hex(hmac(kSigning, stringToSign.toByteArray(Charsets.UTF_8)))

        val authorization = ALGORITHM + " Credential=" + input.accessKey + "/" + scope +
            ", SignedHeaders=" + signedHeaders + ", Signature=" + signature

        val out = LinkedHashMap<String, String>()
        out["Authorization"] = authorization
        out["X-Date"] = xDate
        out["X-Content-Sha256"] = payloadHash
        input.headers.forEach { (key, value) -> out[key] = value }
        return out
    }

    internal fun canonicalQuery(query: Map<String, String>): String =
        query.entries
            .map { (key, value) -> rfc3986(key) + "=" + rfc3986(value) }
            .sorted()
            .joinToString("&")

    internal fun rfc3986(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    internal fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return hex(digest)
    }

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
