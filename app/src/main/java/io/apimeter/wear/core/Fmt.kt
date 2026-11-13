package io.apimeter.wear.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Fmt {

    private val groupFormat = java.text.DecimalFormat("#,##0")
    private val decimal1 = java.text.DecimalFormat("0.0")
    private val decimal2 = java.text.DecimalFormat("0.00")

    fun tokens(value: Long): String {
        val v = abs(value)
        return when {
            v < 1_000L -> value.toString()
            v < 1_000_000L -> compact(value, 1_000.0, "K")
            v < 1_000_000_000L -> compact(value, 1_000_000.0, "M")
            else -> compact(value, 1_000_000_000.0, "B")
        }
    }

    fun exactCount(value: Long): String = groupFormat.format(value.toDouble())

    private fun compact(value: Long, unit: Double, suffix: String): String {
        val scaled = value / unit
        val text = if (abs(scaled) >= 100) decimal1.format(scaled) else decimal2.format(scaled)
        val trimmed = text.trimEnd('0').trimEnd('.')
        return trimmed + suffix
    }

    fun money(amount: Double, currency: String = "USD"): String {
        val symbol = when (currency.uppercase(Locale.US)) {
            "USD" -> "$"
            "CNY", "RMB" -> "¥"
            else -> ""
        }
        val abs = abs(amount)
        val text = when {
            abs >= 1000 -> groupFormat.format(amount)
            abs >= 1 -> decimal2.format(amount)
            abs >= 0.01 -> decimal2.format(amount)
            abs > 0 -> "<0.01"
            else -> "0.00"
        }
        return symbol + text
    }

    fun microsAsMoney(micros: Long, currency: String = "USD"): String =
        money(micros / 1_000_000.0, currency)

    fun percent(value: Double): String {
        val pct = if (value <= 1.0) value * 100.0 else value
        return decimal1.format(pct).trimEnd('0').trimEnd('.') + "%"
    }

    fun relativeTime(nowMillis: Long, thenMillis: Long): String {
        if (thenMillis <= 0L) return "从未"
        val delta = (nowMillis - thenMillis).coerceAtLeast(0L) / 1000L
        return when {
            delta < 60 -> "刚刚"
            delta < 3_600 -> (delta / 60).toString() + " 分钟前"
            delta < 86_400 -> (delta / 3_600).toString() + " 小时前"
            else -> (delta / 86_400).toString() + " 天前"
        }
    }

    fun clock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun date(epochSecond: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("M/d").format(Instant.ofEpochSecond(epochSecond).atZone(zone))

    fun shortCount(value: Double): String = groupFormat.format(value.roundToLong().toDouble())
}
