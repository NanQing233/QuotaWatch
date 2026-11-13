package io.apimeter.wear.core

import kotlinx.serialization.Serializable

@Serializable
enum class MetricUnit { TOKENS, MONEY, PERCENT, COUNT }

@Serializable
enum class SnapshotStatus { OK, PARTIAL, ERROR, UNSUPPORTED }

@Serializable
data class MetricLine(
    val label: String,
    val value: String,
    val emphasis: Boolean = false,
)

@Serializable
data class DayPoint(
    val epochSecond: Long,
    val tokens: Long,
    val costMicros: Long = 0L,
)

/** 某厂商某凭据在时间窗口内的用量快照；primaryValue 为归一化数值，供 RANGED_VALUE 信息位使用。 */
@Serializable
data class UsageSnapshot(
    val providerKey: String,
    val credentialId: String,
    val credentialLabel: String,
    val fetchedAt: Long,
    val windowStartSec: Long,
    val windowEndSec: Long,
    val primaryLabel: String,
    val primaryValue: Double,
    val primaryDisplay: String,
    val primaryUnit: MetricUnit,
    val secondary: String? = null,
    /** 金额型指标真实币种（ISO，如 "CNY"/"USD"），非金额为 null；必须由厂商从响应读出、不能展示层猜（曾默认 USD 把 ¥ 显示成 $）。 */
    val currency: String? = null,
    val lines: List<MetricLine> = emptyList(),
    val daily: List<DayPoint> = emptyList(),
    val status: SnapshotStatus = SnapshotStatus.OK,
    val message: String? = null,
) {
    val isUsable: Boolean
        get() = status == SnapshotStatus.OK || status == SnapshotStatus.PARTIAL

    fun providerId(): ProviderId? = ProviderId.fromKey(providerKey)

    companion object {
        fun failure(
            providerKey: String,
            credentialId: String,
            credentialLabel: String,
            message: String,
            fetchedAt: Long,
            windowStartSec: Long,
            windowEndSec: Long,
            status: SnapshotStatus = SnapshotStatus.ERROR,
            primaryLabel: String = "--",
        ): UsageSnapshot = UsageSnapshot(
            providerKey = providerKey,
            credentialId = credentialId,
            credentialLabel = credentialLabel,
            fetchedAt = fetchedAt,
            windowStartSec = windowStartSec,
            windowEndSec = windowEndSec,
            primaryLabel = primaryLabel,
            primaryValue = 0.0,
            primaryDisplay = "--",
            primaryUnit = MetricUnit.COUNT,
            status = status,
            message = message,
        )

        fun unsupported(
            providerKey: String,
            credentialId: String,
            credentialLabel: String,
            message: String,
            fetchedAt: Long,
            windowStartSec: Long,
            windowEndSec: Long,
        ): UsageSnapshot = failure(
            providerKey, credentialId, credentialLabel, message, fetchedAt,
            windowStartSec, windowEndSec, SnapshotStatus.UNSUPPORTED,
        )
    }
}

@Serializable
enum class ProviderId(val key: String) {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    VOLC_ARK("volc_ark"),
    TENCENT_TOKENHUB("tencent_tokenhub"),
    DEEPSEEK("deepseek"),
    MOONSHOT("moonshot");

    /** 国内可直连的厂商端点，代理决策时优先直连。 */
    val isDomestic: Boolean
        get() = when (this) {
            VOLC_ARK, TENCENT_TOKENHUB, DEEPSEEK -> true
            OPENAI, ANTHROPIC, MOONSHOT -> false
        }

    companion object {
        fun fromKey(key: String): ProviderId? = entries.firstOrNull { it.key == key }
    }
}

/** 厂商官方能力边界：明确标注哪些只有余额、没有用量明细。 */
@Serializable
enum class ProviderCapability { USAGE_DETAIL, COST_DETAIL, BALANCE, RATE_LIMIT }

@Serializable
data class UsageRange(val startSec: Long, val endSec: Long) {
    /** 覆盖的自然天数，向上取整：今天 10:30 到此刻仍算 1 天，近 7 天算 7 天。 */
    val days: Long get() = ((endSec - startSec + 86_399L) / 86_400L).coerceAtLeast(1L)
}

@Serializable
enum class RangePreset { TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH }

@Serializable
enum class FocusMetric { TODAY_TOKENS, TODAY_COST, BALANCE }
