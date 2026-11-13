package io.apimeter.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.Text
import io.apimeter.wear.data.UsageUiState
import io.apimeter.wear.net.ProxyConfig
import io.apimeter.wear.net.ProxyMode
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.components.Caption
import io.apimeter.wear.ui.components.PrimaryAction
import io.apimeter.wear.ui.components.WearScreen
import io.apimeter.wear.ui.components.WearTextField

/** 代理设置：三种模式用 RadioButton（整行可点），保存用实心主按钮；返回交给系统手势/返回键。 */
@Composable
fun ProxyScreen(
    ui: UsageUiState,
    haptics: WearHaptics,
    onApply: (ProxyConfig) -> Unit,
    onProbe: () -> Unit,
) {
    val current = ui.settings.proxy
    var mode by remember { mutableStateOf(current.mode) }
    var host by remember { mutableStateOf(current.host) }
    var port by remember { mutableStateOf(current.port.toString()) }
    val probe = ui.proxyProbe

    WearScreen(haptics = haptics) {
        item {
            ListHeader {
                Text(text = "网络代理", style = MaterialTheme.typography.titleSmall)
            }
        }

        item {
            RadioButton(
                selected = mode == ProxyMode.OFF,
                onSelect = { haptics.tick(); mode = ProxyMode.OFF },
                label = { Text(text = "直连") },
                secondaryLabel = { Text(text = "不使用代理") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            RadioButton(
                selected = mode == ProxyMode.AUTO,
                onSelect = { haptics.tick(); mode = ProxyMode.AUTO },
                label = { Text(text = "自动（推荐）") },
                secondaryLabel = { Text(text = "境外走代理，国内直连") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            RadioButton(
                selected = mode == ProxyMode.ALWAYS,
                onSelect = { haptics.tick(); mode = ProxyMode.ALWAYS },
                label = { Text(text = "始终走代理") },
                secondaryLabel = { Text(text = "全部请求都经过代理") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { Caption(text = "自动模式下失败会自动切换另一条链路") }

        item {
            WearTextField(
                value = host,
                onValueChange = { host = it },
                label = "代理服务器地址",
                placeholder = "192.168.1.8",
            )
        }

        item {
            WearTextField(
                value = port,
                onValueChange = { input -> port = input.filter { it.isDigit() }.take(5) },
                label = "端口",
                placeholder = "7890",
                number = true,
            )
        }

        item {
            Card(onClick = { haptics.tick(); onProbe() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "测试当前代理",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = probe?.let { result ->
                        if (result.ok) "可用 · " + result.tookMs + " ms" else result.message
                    } ?: "点一下测试连通性",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            PrimaryAction(
                text = "保存并生效",
                onClick = {
                    haptics.confirm()
                    onApply(
                        ProxyConfig(
                            mode = mode,
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 7890,
                        ),
                    )
                },
            )
        }
    }
}
