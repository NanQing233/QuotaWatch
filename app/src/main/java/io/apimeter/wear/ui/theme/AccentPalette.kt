package io.apimeter.wear.ui.theme

/** 由一个主色推出整套强调色：纯计算无 Android 依赖；primary 提亮到 V≈0.92 保证纯黑背景可读，container 用同色相深色（V≈0.32），次要/第三色只小幅偏移、不做撞色。 */
data class AccentPalette(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
)

object AccentPalettes {

    /** Wear OS 系统应用默认的淡蓝（取不到动态色时的兜底）。 */
    const val WEAR_DEFAULT_BLUE: Int = 0xFFA8C7FA.toInt()

    fun fromAccent(argb: Int): AccentPalette {
        val hsv = rgbToHsv(argb)
        var hue = hsv[0]
        var saturation = hsv[1]
        if (saturation < 0.25f) saturation = 0.55f
        hue = ((hue % 360f) + 360f) % 360f
        val tertiaryHue = (hue + 40f) % 360f

        return AccentPalette(
            primary = hsvToArgb(hue, saturation.coerceIn(0.2f, 1f), 0.92f),
            onPrimary = hsvToArgb(hue, 0.75f, 0.18f),
            primaryContainer = hsvToArgb(hue, 0.85f, 0.32f),
            onPrimaryContainer = hsvToArgb(hue, 0.45f, 0.94f),
            secondary = hsvToArgb(hue, (saturation * 0.35f).coerceAtLeast(0.1f), 0.88f),
            onSecondary = hsvToArgb(hue, 0.40f, 0.16f),
            secondaryContainer = hsvToArgb(hue, 0.45f, 0.26f),
            onSecondaryContainer = hsvToArgb(hue, 0.30f, 0.92f),
            tertiary = hsvToArgb(tertiaryHue, saturation * 0.8f, 0.90f),
            onTertiary = hsvToArgb(tertiaryHue, 0.70f, 0.18f),
            tertiaryContainer = hsvToArgb(tertiaryHue, 0.80f, 0.30f),
            onTertiaryContainer = hsvToArgb(tertiaryHue, 0.40f, 0.93f),
        )
    }

    /** 预设的几种主色，供手表上直接点选（比拖色环更好按）。 */
    val presets: List<Pair<String, Int>> = listOf(
        "淡蓝" to WEAR_DEFAULT_BLUE,
        "青绿" to 0xFF4FD8C8.toInt(),
        "紫" to 0xFFCFBCFF.toInt(),
        "粉" to 0xFFFFB3C1.toInt(),
        "橙" to 0xFFFFB68A.toInt(),
        "柠檬" to 0xFFE2E86A.toInt(),
    )

    /** 粗算相对亮度（0..1），用于对比度断言。 */
    fun luminance(argb: Int): Float {
        fun channel(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.03928f) {
                v / 12.92f
            } else {
                Math.pow(((v + 0.055) / 1.055).toDouble(), 2.4).toFloat()
            }
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun rgbToHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val saturation = if (max == 0f) 0f else delta / max
        return floatArrayOf(((hue % 360f) + 360f) % 360f, saturation, max)
    }

    fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(input: Float): Int = ((input + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }
}