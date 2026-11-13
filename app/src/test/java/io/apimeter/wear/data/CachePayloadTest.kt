package io.apimeter.wear.data

import io.apimeter.wear.core.DayPoint
import io.apimeter.wear.core.MetricLine
import io.apimeter.wear.core.MetricUnit
import io.apimeter.wear.core.SnapshotStatus
import io.apimeter.wear.core.UsageSnapshot
import io.apimeter.wear.storage.AppJson
import org.junit.Assert.assertEquals
import org.junit.Test

class CachePayloadTest {

    @Test
    fun snapshot_roundTripsThroughJson() {
        val snapshot = UsageSnapshot(
            providerKey = "openai",
            credentialId = "cred-1",
            credentialLabel = "工作号",
            fetchedAt = 1_700_000_000_000,
            windowStartSec = 1_699_996_800,
            windowEndSec = 1_700_000_000,
            primaryLabel = "Token 用量",
            primaryValue = 1_234_567.0,
            primaryDisplay = "1.23M",
            primaryUnit = MetricUnit.TOKENS,
            secondary = "$3.21",
            lines = listOf(MetricLine("输入 Token", "1,000,000")),
            daily = listOf(DayPoint(1_699_996_800, 1_234_567)),
            status = SnapshotStatus.OK,
        )
        val payload = CachePayload(listOf(snapshot), 1_700_000_001_000)
        val encoded = AppJson.encodeToString(CachePayload.serializer(), payload)
        val decoded = AppJson.decodeFromString(CachePayload.serializer(), encoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun unknownFields_areIgnored() {
        val legacy = """{"snapshots":[],"lastRefreshAt":5,"extra":"ignored"}"""
        val decoded = AppJson.decodeFromString(CachePayload.serializer(), legacy)
        assertEquals(5L, decoded.lastRefreshAt)
        assertEquals(0, decoded.snapshots.size)
    }
}
