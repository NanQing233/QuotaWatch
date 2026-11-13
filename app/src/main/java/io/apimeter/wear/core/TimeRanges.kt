package io.apimeter.wear.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object TimeRanges {

    fun preset(preset: RangePreset, zone: ZoneId, nowMillis: Long): UsageRange {
        val now = Instant.ofEpochMilli(nowMillis)
        val today: LocalDate = now.atZone(zone).toLocalDate()
        val startDate: LocalDate = when (preset) {
            RangePreset.TODAY -> today
            RangePreset.LAST_7_DAYS -> today.minusDays(6)
            RangePreset.LAST_30_DAYS -> today.minusDays(29)
            RangePreset.THIS_MONTH -> today.with(TemporalAdjusters.firstDayOfMonth())
        }
        val startSec = startDate.atStartOfDay(zone).toEpochSecond()
        val endSec = now.epochSecond.coerceAtLeast(startSec + 1)
        return UsageRange(startSec, endSec)
    }

    fun floorToHour(epochSec: Long): Long = epochSec - Math.floorMod(epochSec, 3_600L)

    fun floorToDay(epochSec: Long, zone: ZoneId): Long =
        Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()

    fun epochDay(epochSec: Long, zone: ZoneId): Long =
        Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate().toEpochDay()
}
