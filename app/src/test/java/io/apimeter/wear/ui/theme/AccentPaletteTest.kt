package io.apimeter.wear.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 取色推导测试：保证「用户随便点一个色」生成的整套配色依然是深色可读的，
 * 而不是把界面变成一片糊在一起的颜色。
 */
class AccentPaletteTest {

    @Test
    fun hsvRoundTrip_preservesColor() {
        val samples = listOf(
            0xFF4FD8C8.toInt(),
            0xFFA8C7FA.toInt(),
            0xFFFF6B6B.toInt(),
            0xFFE2E86A.toInt(),
        )
        samples.forEach { argb ->
            val hsv = AccentPalettes.rgbToHsv(argb)
            val back = AccentPalettes.hsvToArgb(hsv[0], hsv[1], hsv[2])
            // 允许 1/255 的量化误差
            assertTrue("色相丢失：${argb.toUInt().toString(16)} -> ${back.toUInt().toString(16)}", channelsClose(argb, back, 2))
        }
    }

    @Test
    fun primaryKeepsTheChosenHue() {
        val palette = AccentPalettes.fromAccent(0xFFFF6B6B.toInt())
        val hue = AccentPalettes.rgbToHsv(palette.primary)[0]
        assertTrue("主色应保持用户选择的色相，实际 $hue", abs(hue - 0f) < 12f || abs(hue - 360f) < 12f)
    }

    @Test
    fun primaryIsBrightAndContainerIsDark() {
        AccentPalettes.presets.forEach { (name, argb) ->
            val p = AccentPalettes.fromAccent(argb)
            val primaryLum = AccentPalettes.luminance(p.primary)
            val containerLum = AccentPalettes.luminance(p.primaryContainer)
            assertTrue("$name 的主色太暗：$primaryLum", primaryLum > 0.35f)
            assertTrue("$name 的容器色不够深：$containerLum", containerLum < primaryLum)
            // 文字压在容器上要看得清
            assertTrue(
                "$name 的 onPrimaryContainer 对比不足",
                AccentPalettes.luminance(p.onPrimaryContainer) > containerLum,
            )
            // onPrimary 压在主色上要足够暗
            assertTrue(
                "$name 的 onPrimary 不够暗",
                AccentPalettes.luminance(p.onPrimary) < primaryLum,
            )
        }
    }

    @Test
    fun presetsHaveDistinctHues() {
        val hues = AccentPalettes.presets.map { AccentPalettes.rgbToHsv(it.second)[0].toInt() }
        assertEquals(hues.size, hues.toSet().size)
    }

    @Test
    fun generatedColorsAreOpaque() {
        val p = AccentPalettes.fromAccent(AccentPalettes.WEAR_DEFAULT_BLUE)
        listOf(
            p.primary, p.onPrimary, p.primaryContainer, p.onPrimaryContainer,
            p.secondary, p.tertiary, p.tertiaryContainer,
        ).forEach { argb ->
            assertEquals(0xFF, (argb ushr 24) and 0xFF)
        }
    }

    private fun channelsClose(a: Int, b: Int, tolerance: Int): Boolean {
        fun c(value: Int, shift: Int) = (value ushr shift) and 0xFF
        return abs(c(a, 16) - c(b, 16)) <= tolerance &&
            abs(c(a, 8) - c(b, 8)) <= tolerance &&
            abs(c(a, 0) - c(b, 0)) <= tolerance
    }
}