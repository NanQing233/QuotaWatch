package io.apimeter.wear.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/** 配色遵循 Wear 官方指南：纯黑背景(OLED 省电)、surfaceContainer 三档做层次；强调色优先级：手动主色 > 系统动态取色(Android 12+) > Wear 默认淡蓝。 */
@Composable
fun ApiMeterTheme(
    manualAccent: Boolean = false,
    accentArgb: Int = 0,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = remember(manualAccent, accentArgb) {
        if (manualAccent && accentArgb != 0) {
            fromAccent(argb = accentArgb)
        } else {
            automaticScheme(context)
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 自动取色：系统动态色 → 取不到就用 Wear 默认淡蓝。 */
private fun automaticScheme(context: Context): ColorScheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching { dynamicColorScheme(context) }.getOrNull()?.let { return it }
    }
    return fromAccent(AccentPalettes.WEAR_DEFAULT_BLUE)
}

/** 由一个主色生成整套深色配色，表层中性色保持不变。 */
private fun fromAccent(argb: Int): ColorScheme {
    val palette = AccentPalettes.fromAccent(argb)
    return ColorScheme(
        primary = Color(palette.primary),
        primaryDim = Color(dim(palette.primary)),
        primaryContainer = Color(palette.primaryContainer),
        onPrimary = Color(palette.onPrimary),
        onPrimaryContainer = Color(palette.onPrimaryContainer),
        secondary = Color(palette.secondary),
        secondaryDim = Color(dim(palette.secondary)),
        secondaryContainer = Color(palette.secondaryContainer),
        onSecondary = Color(palette.onSecondary),
        onSecondaryContainer = Color(palette.onSecondaryContainer),
        tertiary = Color(palette.tertiary),
        tertiaryDim = Color(dim(palette.tertiary)),
        tertiaryContainer = Color(palette.tertiaryContainer),
        onTertiary = Color(palette.onTertiary),
        onTertiaryContainer = Color(palette.onTertiaryContainer),
        surfaceContainerLow = Color(ContainerLow),
        surfaceContainer = Color(Container),
        surfaceContainerHigh = Color(ContainerHigh),
        onSurface = Color(OnSurface),
        onSurfaceVariant = Color(OnSurfaceVariant),
        outline = Color(Outline),
        outlineVariant = Color(OutlineVariant),
        background = Color(Ink),
        onBackground = Color(OnSurface),
        error = Color(ErrorRed),
        errorDim = Color(ErrorRedDim),
        errorContainer = Color(ErrorContainer),
        onError = Color(OnError),
        onErrorContainer = Color(OnErrorContainer),
    )
}

/** “dim” 变体：同色相压暗一档，用于按下态等次要场合。 */
private fun dim(argb: Int): Int {
    val hsv = AccentPalettes.rgbToHsv(argb)
    return AccentPalettes.hsvToArgb(hsv[0], hsv[1], (hsv[2] * 0.72f).coerceIn(0f, 1f))
}

private val Ink = 0xFF000000.toInt()
private val ContainerLow = 0xFF0F1216.toInt()
private val Container = 0xFF171A1F.toInt()
private val ContainerHigh = 0xFF22262C.toInt()
private val OnSurface = 0xFFE3E2E6.toInt()
private val OnSurfaceVariant = 0xFFC4C6D0.toInt()
private val Outline = 0xFF8E9099.toInt()
private val OutlineVariant = 0xFF44474F.toInt()

private val ErrorRed = 0xFFFFB4AB.toInt()
private val ErrorRedDim = 0xFFC97B73.toInt()
private val ErrorContainer = 0xFF93000A.toInt()
private val OnError = 0xFF690005.toInt()
private val OnErrorContainer = 0xFFFFDAD6.toInt()