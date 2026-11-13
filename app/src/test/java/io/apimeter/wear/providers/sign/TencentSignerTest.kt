package io.apimeter.wear.providers.sign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TencentSignerTest {

    private val body = """{"Dimension":"apikey","StartTime":"2026-10-25T00:00:00+08:00","EndTime":"2026-10-25T12:00:00+08:00","MetricType":"tokens","Period":86400,"ShowAll":true}"""
        .toByteArray(Charsets.UTF_8)

    private fun auth(timestamp: Long = 1792886400L): String = TencentSigner.authorization(
        TencentSigner.Input(
            method = "POST",
            host = "tokenhub.tencentcloudapi.com",
            path = "/",
            contentType = "application/json; charset=utf-8",
            body = body,
            secretId = "AKIDTEST",
            secretKey = "SECRETTEST",
            service = "tokenhub",
            timestampSec = timestamp,
        ),
    )

    @Test
    fun authorization_matchesIndependentReferenceImplementation() {
        assertEquals(
            "TC3-HMAC-SHA256 Credential=AKIDTEST/2026-10-25/tokenhub/tc3_request, " +
                "SignedHeaders=content-type;host, " +
                "Signature=d1d131f017cab16ff453f37284d4b013dd1259f068f0cd11f2346c68356a454c",
            auth(),
        )
    }

    @Test
    fun credentialScope_usesUtcDate() {
        // 1792929600 = 2026-10-25T12:00:00Z，仍是同一天；1792972800 = 2026-10-26T00:00:00Z
        assertTrue(auth(1792929600L).contains("/2026-10-25/tokenhub/tc3_request"))
        assertTrue(auth(1792972800L).contains("/2026-10-26/tokenhub/tc3_request"))
    }

    @Test
    fun signatureIsStableForSameInput() {
        assertEquals(auth(), auth())
    }

    @Test
    fun canonicalQuery_sortsPairs() {
        val canonical = TencentSigner.canonicalQuery(linkedMapOf("b" to "2", "a" to "1 2"))
        assertEquals("a=1%202&b=2", canonical)
    }
}
