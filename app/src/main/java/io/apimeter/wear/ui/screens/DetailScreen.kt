package io.apimeter.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.apimeter.wear.core.Fmt
import io.apimeter.wear.core.ProviderCapability
import io.apimeter.wear.core.SnapshotStatus
import io.apimeter.wear.data.UsageUiState
import io.apimeter.wear.providers.ProviderCatalog
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.components.Caption
import io.apimeter.wear.ui.components.DailyBars
import io.apimeter.wear.ui.components.MetricRow
import io.apimeter.wear.ui.components.PrimaryAction
import io.apimeter.wear.ui.components.SecondaryAction
import io.apimeter.wear.ui.components.SectionHeader
import io.apimeter.wear.ui.components.WearScreen
import io.apimeter.wear.ui.providerFullLabel
import io.apimeter.wear.ui.rangeLabel
import io.apimeter.wear.ui.statusColor

/** 单个凭据详情：数值、明细行、近 N 天走势；余额类厂商没有时间窗口，不能套用「今天 / 近 7 天」。 */
@Composable
fun DetailScreen(
    ui: UsageUiState,
    credentialId: String,
    haptics: WearHaptics,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
) {
    val meta = ui.settings.credential(credentialId)
    val snapshot = ui.snapshots[credentialId]
    val refreshing = credentialId in ui.refreshing

    WearScreen(haptics = haptics) {
        item {
            ListHeader {
                Text(
                    text = meta?.let { providerFullLabel(it.providerKey) } ?: "未知凭据",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        item {
            val supportsUsage = meta?.let {
                ProviderCatalog.byKey(it.providerKey)
                    ?.capabilities?.contains(ProviderCapability.USAGE_DETAIL) == true
            } == true
            Caption(
                text = (meta?.label?.takeIf { it.isNotBlank() } ?: "未命名") + " · " +
                    if (supportsUsage) rangeLabel(ui.settings.rangePreset) else "余额快照",
            )
        }

        item {
            Card(onClick = { haptics.tick(); onRefresh() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = snapshot?.primaryDisplay ?: "--",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (snapshot?.isUsable == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = snapshot?.primaryLabel ?: "尚未同步",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (refreshing) "查询中…" else "点一下重新查询 · " + Fmt.relativeTime(
                        System.currentTimeMillis(),
                        snapshot?.fetchedAt ?: 0L,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        if (snapshot != null && snapshot.isUsable && snapshot.daily.size > 1) {
            item {
                DailyBars(
                    daily = snapshot.daily,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        if (snapshot != null && snapshot.lines.isNotEmpty()) {
            item { SectionHeader(text = "明细") }
            snapshot.lines.forEach { line ->
                item(key = line.label) {
                    MetricRow(label = line.label, value = line.value, emphasis = line.emphasis)
                }
            }
        }

        if (snapshot != null && snapshot.status != SnapshotStatus.OK && snapshot.message != null) {
            item {
                Text(
                    text = snapshot.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(snapshot.status),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        item {
            SecondaryAction(
                text = "编辑凭据",
                onClick = { haptics.tick(); onEdit() },
            )
        }
    }
}
