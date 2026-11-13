package io.apimeter.wear.providers.sign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VolcSignerTest {

    private val body = """{"StartTime":"2026-10-01","EndTime":"2026-10-25","QueryInterval":"Day","ProjectName":"default"}"""
        .toByteArray(Charsets.UTF_8)

    private fun sign(nowMillis: Long = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli()): Map<String, String> =
        VolcSigner.sign(
            VolcSigner.Input(
                method = "POST",
                host = "ark.cn-beijing.volcengineapi.com",
                path = "/",
                query = linkedMapOf("Action" to "GetInferenceUsage", "Version" to "2024-01-01"),
                headers = mapOf("content-type" to "application/json; charset=utf-8"),
                body = body,
                accessKey = "AKTESTACCESSKEY",
                secretKey = "SKTESTSECRETKEY",
                region = "cn-beijing",
                service = "ark",
                nowMillis = nowMillis,
            ),
        )

    @Test
    fun authorization_matchesIndependentReferenceImplementation() {
        val headers = sign()
        assertEquals(
            "HMAC-SHA256 Credential=AKTESTACCESSKEY/20261025/cn-beijing/ark/request, " +
                "SignedHeaders=content-type;host;x-content-sha256;x-date, " +
                "Signature=b8ace2b67caa84341ebdae6447adf6489fc354d0cfcff0259c24654e17e838b4",
            headers["Authorization"],
        )
    }

    @Test
    fun requiredHeaders_areProduced() {
        val headers = sign()
        assertEquals("20261025T000000Z", headers["X-Date"])
        assertEquals("7c9ccca45f03ec43adc585241993305d48245dbf615a2c7fb3b4a6dfac10ce4f", headers["X-Content-Sha256"])
        assertEquals("application/json; charset=utf-8", headers["content-type"])
    }

    @Test
    fun signatureChangesWithBodyAndSecret() {
        val first = sign()["Authorization"]
        val second = sign(Instant.parse("2026-10-26T00:00:00Z").toEpochMilli())["Authorization"]
        assertTrue(first != second)
    }

    @Test
    fun canonicalQuery_isSortedAndEncoded() {
        val canonical = VolcSigner.canonicalQuery(linkedMapOf("b" to "2", "a" to "1 2", "c" to "x/y"))
        assertEquals("a=1%202&b=2&c=x%2Fy", canonical)
    }
}
