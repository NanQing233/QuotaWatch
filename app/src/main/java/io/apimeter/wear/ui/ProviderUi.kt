package io.apimeter.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.MaterialTheme
import io.apimeter.wear.core.RangePreset
import io.apimeter.wear.core.SnapshotStatus
import io.apimeter.wear.core.UsageSnapshot
import io.apimeter.wear.providers.ProviderCatalog

/** 厂商短名（表盘、卡片上空间有限，统一用短名）。 */
@Composable
fun providerShortLabel(providerKey: String): String {
    val res = ProviderCatalog.byKey(providerKey)?.shortLabelRes
    return if (res != null) stringResource(res) else providerKey
}

@Composable
fun providerFullLabel(providerKey: String): String {
    val res = ProviderCatalog.byKey(providerKey)?.labelRes
    return if (res != null) stringResource(res) else providerKey
}

fun rangeLabel(preset: RangePreset): String = when (preset) {
    RangePreset.TODAY -> "今天"
    RangePreset.LAST_7_DAYS -> "近 7 天"
    RangePreset.LAST_30_DAYS -> "近 30 天"
    RangePreset.THIS_MONTH -> "本月"
}

fun nextRange(preset: RangePreset): RangePreset = when (preset) {
    RangePreset.TODAY -> RangePreset.LAST_7_DAYS
    RangePreset.LAST_7_DAYS -> RangePreset.LAST_30_DAYS
    RangePreset.LAST_30_DAYS -> RangePreset.THIS_MONTH
    RangePreset.THIS_MONTH -> RangePreset.TODAY
}

@Composable
fun statusColor(status: SnapshotStatus): Color = when (status) {
    SnapshotStatus.OK -> MaterialTheme.colorScheme.primary
    SnapshotStatus.PARTIAL -> MaterialTheme.colorScheme.secondary
    SnapshotStatus.ERROR -> MaterialTheme.colorScheme.error
    SnapshotStatus.UNSUPPORTED -> MaterialTheme.colorScheme.outline
}

@Composable
fun snapshotHeadline(snapshot: UsageSnapshot?): String = when {
    snapshot == null -> "--"
    snapshot.isUsable -> snapshot.primaryDisplay
    snapshot.status == SnapshotStatus.UNSUPPORTED -> "不支持"
    else -> "--"
}

@Composable
fun snapshotCaption(snapshot: UsageSnapshot?): String = when {
    snapshot == null -> "尚未同步"
    snapshot.isUsable -> snapshot.primaryLabel
    snapshot.status == SnapshotStatus.UNSUPPORTED -> snapshot.message ?: "该厂商未提供用量接口"
    else -> snapshot.message ?: "同步失败"
}

/** 首页卡片上的短状态文案：圆屏卡片只有一行放状态，错误详情留给详情页，避免被裁成半句话。 */
@Composable
fun snapshotShortCaption(snapshot: UsageSnapshot?): String = when {
    snapshot == null -> "尚未同步"
    snapshot.isUsable -> snapshot.primaryLabel
    snapshot.status == SnapshotStatus.UNSUPPORTED -> "仅支持余额"
    else -> "查询失败 · 点开查看"
}
