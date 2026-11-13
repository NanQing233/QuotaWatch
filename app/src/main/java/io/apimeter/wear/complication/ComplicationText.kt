package io.apimeter.wear.complication

import io.apimeter.wear.core.Fmt
import kotlin.math.roundToLong

/** 短文本槽位官方上限：SHORT_TEXT 的 text 与 title 各限 7 字符，超出可能被截断。 */
internal const val SHORT_TEXT_MAX_CHARS = 7

/**
 * 圆形槽位（SHORT_TEXT）标题缩写：仅 TokenHub→腾讯云、DeepSeek→DS 两家超 7 字符；
 * 其余 ≤7 原样返回。横向槽 LONG_TEXT 无长度限制，用完整名、不套缩写。
 */
internal fun circularTitle(title: String): String = when (title) {
    "TokenHub" -> "腾讯云"
    "DeepSeek" -> "DS"
    else -> title
}

/** 把主数值压进 7 字符：去掉「 / 额度」后缀，仍超长则紧凑化并保留币种前缀（¥1,234.56→¥1.23K）。 */
internal fun shortComplicationText(display: String): String {
    val base = display.substringBefore(" / ").trim()
    if (base.isEmpty()) return PLACEHOLDER_TEXT
    if (base.length <= SHORT_TEXT_MAX_CHARS) return base

    val numberStart = base.indexOfFirst { it.isDigit() || it == '-' }
        .let { if (it < 0) 0 else it }
    val prefix = base.substring(0, numberStart)
    val numeric = base.substring(numberStart).replace(",", "").toDoubleOrNull()
    val compact = if (numeric != null) prefix + Fmt.tokens(numeric.roundToLong()) else base
    return compact.take(SHORT_TEXT_MAX_CHARS)
}
