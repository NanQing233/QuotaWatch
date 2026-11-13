package io.apimeter.wear.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {

    @Test
    fun tokens_compactsByMagnitude() {
        assertEquals("999", Fmt.tokens(999))
        assertEquals("1.23K", Fmt.tokens(1_234))
        assertEquals("1K", Fmt.tokens(1_000))
        assertEquals("1.5M", Fmt.tokens(1_500_000))
        assertEquals("2.25B", Fmt.tokens(2_250_000_000))
    }

    @Test
    fun exactCount_groupsThousands() {
        assertEquals("1,234", Fmt.exactCount(1_234))
    }

    @Test
    fun money_keepsTwoDecimalsAndSymbol() {
        assertEquals("$12.50", Fmt.money(12.5, "USD"))
        assertEquals("¥8.00", Fmt.money(8.0, "CNY"))
        assertEquals("$<0.01", Fmt.money(0.004, "USD"))
    }

    @Test
    fun microsAsMoney_convertsMicros() {
        assertEquals("$3.40", Fmt.microsAsMoney(3_400_000, "USD"))
    }

    @Test
    fun percent_normalizesBothForms() {
        assertEquals("42%", Fmt.percent(0.42))
        assertEquals("42%", Fmt.percent(42.0))
    }

    @Test
    fun relativeTime_buckets() {
        val now = 1_000_000_000_000L
        assertEquals("从未", Fmt.relativeTime(now, 0L))
        assertEquals("刚刚", Fmt.relativeTime(now, now - 30_000))
        assertEquals("5 分钟前", Fmt.relativeTime(now, now - 5 * 60_000))
        assertEquals("2 小时前", Fmt.relativeTime(now, now - 2 * 3_600_000))
        assertEquals("3 天前", Fmt.relativeTime(now, now - 3 * 86_400_000L))
    }
}
