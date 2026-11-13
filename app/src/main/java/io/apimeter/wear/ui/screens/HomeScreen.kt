package io.apimeter.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.apimeter.wear.core.Fmt
import io.apimeter.wear.core.ProviderCapability
import io.apimeter.wear.data.UsageUiState
import io.apimeter.wear.providers.ProviderCatalog
import io.apimeter.wear.storage.CredentialMeta
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.components.Caption
import io.apimeter.wear.ui.components.InfoCard
import io.apimeter.wear.ui.components.OutlinedAction
import io.apimeter.wear.ui.components.PrimaryAction
import io.apimeter.wear.ui.components.SecondaryAction
import io.apimeter.wear.ui.components.StatusDot
import io.apimeter.wear.ui.components.WearScreen
import io.apimeter.wear.ui.providerShortLabel
import io.apimeter.wear.ui.rangeLabel
import io.apimeter.wear.ui.snapshotHeadline
import io.apimeter.wear.ui.snapshotShortCaption
import io.apimeter.wear.ui.statusColor

/** 首页：先给数据再给操作——一行上下文 → 凭据卡片 → 标准 Material 按钮三档。 */
@Composable
fun HomeScreen(
    ui: UsageUiState,
    haptics: WearHaptics,
    onRefresh: () -> Unit,
    onRefreshSurfaces: () -> Unit,
    onCredential: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    WearScreen(haptics = haptics) {
        item {
            // 只有真取到「用量类」数据才显示时间窗口，否则写「余额」；查询失败不算，别把余额冒充成区间用量。
            val hasUsageData = ui.credentials.any { meta ->
                ui.snapshots[meta.id]?.isUsable == true &&
                    ProviderCatalog.byKey(meta.providerKey)
                        ?.capabilities?.contains(ProviderCapability.USAGE_DETAIL) == true
            }
            val prefix = when {
                hasUsageData -> rangeLabel(ui.settings.rangePreset)
                ui.credentials.isEmpty() -> null
                else -> "余额"
            }
            val syncText = if (ui.lastRefreshAt > 0L) {
                Fmt.relativeTime(System.currentTimeMillis(), ui.lastRefreshAt)
            } else {
                "尚未同步"
            }
            Caption(text = listOfNotNull(prefix, syncText).joinToString(" · "))
        }

        if (ui.credentials.isEmpty()) {
            item {
                InfoCard(
                    title = "还没有 API Key",
                    onClick = { haptics.tick(); onAdd() },
                )
            }
            item { Caption(text = "点击添加 API Key") }
        } else {
            ui.credentials.forEach { meta ->
                item(key = meta.id) {
                    CredentialCard(
                        meta = meta,
                        ui = ui,
                        onClick = { haptics.tick(); onCredential(meta.id) },
                    )
                }
            }
            // 新增做成和「刷新全部」同尺寸的标准按钮，不与上面 64dp 的凭据卡片挤在同一视觉层级。
            item {
                PrimaryAction(
                    text = "添加新凭据",
                    onClick = { haptics.tick(); onAdd() },
                )
            }
        }

        ui.notice?.let { notice ->
            item { Caption(text = notice) }
        }

        item {
            SecondaryAction(
                text = if (ui.isRefreshing) "正在查询…" else "刷新全部",
                onClick = { haptics.tick(); onRefresh() },
                enabled = !ui.isRefreshing,
            )
        }

        item {
            // 与「刷新全部」同一档风格、紧挨着放：两个都是刷新类动作，不该一个实心一个描边。
            SecondaryAction(
                text = "刷新表盘与小组件",
                onClick = { haptics.tick(); onRefreshSurfaces() },
            )
        }

        item {
            OutlinedAction(
                text = "设置",
                onClick = { haptics.tick(); onSettings() },
            )
        }
    }
}

@Composable
private fun CredentialCard(
    meta: CredentialMeta,
    ui: UsageUiState,
    onClick: () -> Unit,
) {
    val snapshot = ui.snapshots[meta.id]
    val refreshing = meta.id in ui.refreshing
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (meta.enabled) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = providerShortLabel(meta.providerKey),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusDot(
                color = when {
                    refreshing -> MaterialTheme.colorScheme.secondary
                    snapshot == null -> MaterialTheme.colorScheme.outline
                    else -> statusColor(snapshot.status)
                },
                modifier = Modifier.size(8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snapshotHeadline(snapshot),
                style = MaterialTheme.typography.titleMedium,
                color = if (snapshot?.isUsable == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = snapshotShortCaption(snapshot),
                style = MaterialTheme.typography.labelSmall,
                color = if (snapshot != null && !snapshot.isUsable) {
                    statusColor(snapshot.status)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}
