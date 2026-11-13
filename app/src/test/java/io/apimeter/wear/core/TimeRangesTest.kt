package io.apimeter.wear.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeRangesTest {

    private val zone = ZoneId.of("UTC")
    private val nowMillis = Instant.parse("2026-10-25T10:30:00Z").toEpochMilli()

    @Test
    fun today_startsAtLocalMidnight() {
        val range = TimeRanges.preset(RangePreset.TODAY, zone, nowMillis)
        assertEquals(Instant.parse("2026-10-25T00:00:00Z").epochSecond, range.startSec)
        assertEquals(Instant.parse("2026-10-25T10:30:00Z").epochSecond, range.endSec)
    }

    @Test
    fun last7Days_includesToday() {
        val range = TimeRanges.preset(RangePreset.LAST_7_DAYS, zone, nowMillis)
        assertEquals(Instant.parse("2026-10-19T00:00:00Z").epochSecond, range.startSec)
        assertEquals(7L, range.days)
    }

    @Test
    fun thisMonth_startsOnFirstDay() {
        val range = TimeRanges.preset(RangePreset.THIS_MONTH, zone, nowMillis)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z").epochSecond, range.startSec)
    }

    @Test
    fun floorToHour_matchesModulo() {
        assertEquals(0L, TimeRanges.floorToHour(59))
        assertEquals(3_600L, TimeRanges.floorToHour(3_600))
        assertEquals(3_600L, TimeRanges.floorToHour(7_199))
    }
}
