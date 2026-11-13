package io.apimeter.wear.complication

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import io.apimeter.wear.ApiMeterApplication
import io.apimeter.wear.AppContainer
import io.apimeter.wear.R
import io.apimeter.wear.core.Fmt
import io.apimeter.wear.core.MetricUnit
import io.apimeter.wear.core.SnapshotStatus
import io.apimeter.wear.core.UsageSnapshot
import io.apimeter.wear.data.UsageUiState
import io.apimeter.wear.providers.ProviderCatalog
import io.apimeter.wear.ui.theme.AccentPalettes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong

/** 强调色规则与 App 一致（手动选色 → 动态色 → Wear 默认淡蓝），否则 Tile 与 App 撞色。 */
internal fun accentColorOf(context: Context, state: UsageUiState?): Int {
    val settings = state?.settings
    if (settings != null && settings.manualAccent && settings.accentArgb != 0) {
        return settings.accentArgb
    }
    val dynamic = runCatching {
        androidx.wear.compose.material3.dynamicColorScheme(context)?.primary?.toArgb()
    }.getOrNull()
    return dynamic ?: AccentPalettes.WEAR_DEFAULT_BLUE
}


/** 无凭据 / 无数据 / 拉取失败时统一显示的占位符。 */
internal const val PLACEHOLDER_TEXT: String = "--"

/** 等待设置与快照缓存从磁盘恢复的最长时间，超时后退回内存里的当前值。 */
private const val STATE_WAIT_MILLIS = 800L

/** 取进程级容器；拿不到容器（理论上不会发生）时返回 null，由调用方降级展示。 */
internal fun containerOf(context: Context): AppContainer? =
    (context.applicationContext as? ApiMeterApplication)?.container

/** 读 [UsageUiState]：先等缓存恢复，超时或无容器退回 `state.value`。 */
internal suspend fun awaitUsageState(context: Context): UsageUiState? {
    val container = containerOf(context) ?: return null
    val loaded = withTimeoutOrNull(STATE_WAIT_MILLIS) {
        container.usageStore.state.first { state ->
            state.lastRefreshAt > 0L || state.settings.credentials.isNotEmpty()
        }
    }
    return loaded ?: container.usageStore.state.value
}

/** 最近一次成功同步的毫秒时间戳（缓存恢复时间与快照抓取时间取较大者）。 */
internal fun lastSyncMillis(state: UsageUiState?): Long {
    if (state == null) return 0L
    val fetchedAt = state.snapshots.values
        .filter { it.isUsable }
        .maxOfOrNull { it.fetchedAt } ?: 0L
    return maxOf(state.lastRefreshAt, fetchedAt)
}

/** 按单位格式化紧凑文本；金额须带真实币种，拿不到币种时只显示数字、不加符号。 */
internal fun formatMetric(
    unit: MetricUnit,
    value: Double,
    fallback: String = PLACEHOLDER_TEXT,
    currency: String? = null,
): String = when (unit) {
    MetricUnit.TOKENS -> Fmt.tokens(value.roundToLong())
    MetricUnit.MONEY -> if (currency.isNullOrBlank()) Fmt.shortCount(value) else Fmt.money(value, currency)
    MetricUnit.PERCENT -> Fmt.percent(value)
    MetricUnit.COUNT -> fallback
}

/**
 * 单个快照的主数值展示：优先用快照自带 `primaryDisplay`（已按正确币种格式化），
 * 仅缺失时才按 [UsageSnapshot.currency] 重格式化，否则 ¥ 会被显示成 $。
 */
internal fun compactDisplay(snapshot: UsageSnapshot?): String {
    if (snapshot == null || !snapshot.isUsable) return PLACEHOLDER_TEXT
    val original = snapshot.primaryDisplay.takeIf { it.isNotBlank() }
    return when (snapshot.primaryUnit) {
        MetricUnit.MONEY -> original
            ?: formatMetric(MetricUnit.MONEY, snapshot.primaryValue, PLACEHOLDER_TEXT, snapshot.currency)
        MetricUnit.COUNT -> original ?: PLACEHOLDER_TEXT
        else -> formatMetric(
            snapshot.primaryUnit,
            snapshot.primaryValue,
            original ?: PLACEHOLDER_TEXT,
            snapshot.currency,
        )
    }
}

internal fun syncLine(
    context: Context,
    fetchedAt: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String = if (fetchedAt <= 0L) {
    context.getString(R.string.common_never)
} else {
    Fmt.relativeTime(nowMillis, fetchedAt) + "同步"
}

/**
 * Tile / 表盘上只用一句状态代替原始错误文本，避免技术细节铺满小组件并被截断；
 * 详细信息留给详情页。
 */
internal fun shortStatusText(snapshot: UsageSnapshot?): String? = when (snapshot?.status) {
    SnapshotStatus.ERROR -> "查询失败"
    SnapshotStatus.UNSUPPORTED -> "暂不支持"
    else -> null
}

/** 厂商简称 / 全称；厂商表里查不到时兜底成包名调用方给的 key 或应用名。 */
internal fun providerLabel(context: Context, providerKey: String, short: Boolean = true): String {
    val descriptor = ProviderCatalog.byKey(providerKey)
    val resId = descriptor?.let { if (short) it.shortLabelRes else it.labelRes } ?: R.string.app_name
    return runCatching { context.getString(resId) }.getOrDefault(providerKey)
}

/**
 * RANGED_VALUE 的取值上限；null 表示无真实上限。仅「额度上限」与百分比指标（0..1）
 * 算真实上限，token 用量与账户余额无上限，不编造满格。
 */
internal fun ceilingFor(snapshot: UsageSnapshot?, quotaAmount: Double = 0.0): Double? {
    if (quotaAmount > 0.0) return quotaAmount
    if (snapshot?.primaryUnit == MetricUnit.PERCENT) return 1.0
    return null
}

/** 数值后的「 / 额度」后缀，未设额度时返回空串。 */
internal fun quotaDisplay(snapshot: UsageSnapshot?, quotaAmount: Double): String {
    if (quotaAmount <= 0.0) return ""
    val formatted = when (snapshot?.primaryUnit) {
        MetricUnit.MONEY -> {
            val currency = snapshot.currency
            if (currency.isNullOrBlank()) Fmt.shortCount(quotaAmount) else Fmt.money(quotaAmount, currency)
        }
        else -> Fmt.tokens(quotaAmount.roundToLong())
    }
    return " / " + formatted
}
