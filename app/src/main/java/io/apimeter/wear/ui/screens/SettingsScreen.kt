package io.apimeter.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import io.apimeter.wear.data.UsageUiState
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.components.AccentSwatches
import io.apimeter.wear.ui.components.Caption
import io.apimeter.wear.ui.components.InfoCard
import io.apimeter.wear.ui.components.OutlinedAction
import io.apimeter.wear.ui.components.SectionHeader
import io.apimeter.wear.ui.components.WearScreen
import io.apimeter.wear.ui.rangeLabel
import io.apimeter.wear.ui.theme.AccentPalettes

/** 设置：凭据、代理、统计窗口、触觉、后台同步、表盘入口，均为标准 Material 列表项；手动取色未选色时先落默认淡蓝。 */
@Composable
fun SettingsScreen(
    ui: UsageUiState,
    haptics: WearHaptics,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleBackgroundSync: (Boolean) -> Unit,
    onCycleInterval: () -> Unit,
    onCycleRange: () -> Unit,
    onProxy: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onToggleManualAccent: (Boolean) -> Unit,
    onPickAccent: (Int) -> Unit,
) {
    val settings = ui.settings
    WearScreen(haptics = haptics) {
        item {
            ListHeader {
                Text(text = "设置", style = MaterialTheme.typography.titleSmall)
            }
        }

        item {
            InfoCard(
                title = "网络代理",
                subtitle = settings.proxy.describe(),
                onClick = { haptics.tick(); onProxy() },
            )
        }

        item {
            InfoCard(
                title = "WebDAV 备份",
                subtitle = "通过端到端加密存储",
                onClick = { haptics.tick(); onBackup() },
            )
        }

        item { SectionHeader(text = "显示") }

        item {
            InfoCard(
                title = "统计窗口",
                subtitle = rangeLabel(settings.rangePreset),
                onClick = { haptics.tick(); onCycleRange() },
            )
        }

        item {
            SwitchButton(
                checked = settings.hapticsEnabled,
                onCheckedChange = { enabled ->
                    haptics.tick()
                    onToggleHaptics(enabled)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "触觉反馈") },
            )
        }

        item {
            SwitchButton(
                checked = settings.manualAccent,
                onCheckedChange = { enabled ->
                    haptics.tick()
                    if (enabled) {
                        onPickAccent(
                            settings.accentArgb.takeIf { it != 0 }
                                ?: AccentPalettes.WEAR_DEFAULT_BLUE,
                        )
                    }
                    onToggleManualAccent(enabled)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "手动取色") },
            )
        }

        if (settings.manualAccent) {
            item {
                AccentSwatches(
                    selectedArgb = settings.accentArgb
                        .takeIf { it != 0 } ?: AccentPalettes.WEAR_DEFAULT_BLUE,
                    onPick = onPickAccent,
                    haptics = haptics,
                )
            }
            item { Caption(text = "关闭开关则跟随系统自动取色") }
        }

        item { SectionHeader(text = "同步") }

        item {
            SwitchButton(
                checked = settings.backgroundSync,
                onCheckedChange = { enabled ->
                    haptics.tick()
                    onToggleBackgroundSync(enabled)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "后台定时拉取") },
            )
        }

        item {
            InfoCard(
                title = "同步间隔",
                subtitle = intervalLabel(settings.refreshIntervalMinutes),
                footnote = "点击切换",
                onClick = { haptics.tick(); onCycleInterval() },
            )
        }

        item {
            OutlinedAction(
                text = "关于应用",
                onClick = { haptics.tick(); onAbout() },
            )
        }
    }
}

fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> minutes.toString() + " 分钟"
    minutes % 60 == 0 -> (minutes / 60).toString() + " 小时"
    else -> minutes.toString() + " 分钟"
}

fun nextInterval(minutes: Int): Int {
    val steps = listOf(15, 30, 60, 120, 240, 480, 1440)
    val index = steps.indexOfFirst { it > minutes }
    return if (index == -1) steps.first() else steps[index]
}
