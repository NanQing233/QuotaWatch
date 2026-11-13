package io.apimeter.wear.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.apimeter.wear.core.ProviderCapability
import io.apimeter.wear.core.ProviderId
import io.apimeter.wear.providers.ProviderCatalog
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.components.Caption
import io.apimeter.wear.ui.components.InfoCard
import io.apimeter.wear.ui.components.WearScreen

@Composable
fun ProviderPickerScreen(
    haptics: WearHaptics,
    onPick: (ProviderId) -> Unit,
) {
    WearScreen(haptics = haptics) {
        item {
            ListHeader {
                Text(text = "选择厂商", style = MaterialTheme.typography.titleSmall)
            }
        }
        item { Caption(text = "同厂商可添加多个凭据") }

        ProviderCatalog.all.forEach { descriptor ->
            item(key = descriptor.id.key) {
                InfoCard(
                    title = stringResource(descriptor.labelRes),
                    subtitle = stringResource(descriptor.noteRes),
                    footnote = capabilityText(descriptor.capabilities),
                    onClick = { haptics.tick(); onPick(descriptor.id) },
                )
            }
        }
    }
}

private fun capabilityText(capabilities: Set<ProviderCapability>): String {
    val parts = buildList {
        if (ProviderCapability.USAGE_DETAIL in capabilities) add("Token 用量")
        if (ProviderCapability.COST_DETAIL in capabilities) add("成本")
        if (ProviderCapability.BALANCE in capabilities) add("余额")
        if (ProviderCapability.RATE_LIMIT in capabilities) add("速率窗口")
    }
    return if (parts.isEmpty()) "仅能校验 Key" else parts.joinToString(" · ")
}
