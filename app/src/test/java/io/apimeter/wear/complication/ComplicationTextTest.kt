package io.apimeter.wear.complication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationTextTest {

    @Test
    fun shortText_keepsShortValuesIntact() {
        assertEquals("--", shortComplicationText("--"))
        assertEquals("¥6.45", shortComplicationText("¥6.45"))
        assertEquals("¥88.00", shortComplicationText("¥88.00"))
        assertEquals("12.3K", shortComplicationText("12.3K"))
        assertEquals("42%", shortComplicationText("42%"))
        assertEquals("3 家", shortComplicationText("3 家"))
    }

    @Test
    fun shortText_stripsQuotaSuffix() {
        assertEquals("¥15.59", shortComplicationText("¥15.59 / ¥1,000.00"))
        assertEquals("1.2M", shortComplicationText("1.2M / 50M"))
    }

    @Test
    fun shortText_compactsLongNumbers() {
        // Fmt.tokens 会先 roundToLong 再紧凑化，故 1234.56 → 1235 → 1.24K。
        assertEquals("¥1.24K", shortComplicationText("¥1,234.56"))
        assertEquals("$1.24K", shortComplicationText("$1,234.56"))
        assertEquals("12.35M", shortComplicationText("12345678"))
    }

    @Test
    fun shortText_neverExceedsSevenChars() {
        val samples = listOf(
            "--", "¥6.45", "¥88.00", "¥15.59 / ¥1,000.00",
            "¥1,234.56", "$1,234,567.89", "1.2M / 50M", "123456789",
            "<0.01", "¥0.00 / ¥10,000.00",
        )
        samples.forEach { sample ->
            val result = shortComplicationText(sample)
            assertTrue("'$sample' -> '$result' exceeds 7 chars", result.length <= SHORT_TEXT_MAX_CHARS)
            assertTrue("'$sample' -> '$result' is blank", result.isNotBlank())
        }
    }

    @Test
    fun shortText_handlesEmptyOrSuffixOnly() {
        assertEquals("--", shortComplicationText(""))
        assertEquals("--", shortComplicationText(" / ¥1,000.00"))
    }

    @Test
    fun circularTitle_abbreviatesOnlyTheTwoOversizedNames() {
        // 只有这两家超过官方 7 字符上限，按选定做缩写。
        assertEquals("DS", circularTitle("DeepSeek"))
        assertEquals("腾讯云", circularTitle("TokenHub"))
        // 其余保持原样，不截断（它们本身 ≤7）。
        assertEquals("总览", circularTitle("总览"))
        assertEquals("OpenAI", circularTitle("OpenAI"))
        assertEquals("Claude", circularTitle("Claude"))
        assertEquals("方舟", circularTitle("方舟"))
        assertEquals("Kimi", circularTitle("Kimi"))
    }

    @Test
    fun circularTitle_outputFitsSevenCharsForEveryCurrentLabel() {
        // 圆形槽标题必须完整显示：本应用当前全部简称经缩写后都 ≤7。
        listOf("总览", "OpenAI", "Claude", "方舟", "Kimi", "DeepSeek", "TokenHub")
            .forEach { title ->
                val result = circularTitle(title)
                assertTrue("'$title' -> '$result' exceeds 7", result.length <= SHORT_TEXT_MAX_CHARS)
            }
    }
}
