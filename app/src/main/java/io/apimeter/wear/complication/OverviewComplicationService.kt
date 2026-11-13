package io.apimeter.wear.complication

import io.apimeter.wear.R
import io.apimeter.wear.core.MetricUnit

/**
 * 「总览」信息位：汇总所有可用快照。单位与币种一致时主数值取各厂商之和；
 * 单位或币种不一致（¥ 与 $ 不能相加）时退化为「有数据的厂商数」；无快照展示 "--"。
 */
class OverviewComplicationService : BaseComplicationService() {

    override fun serviceLabel(): String = getString(R.string.complication_overview_label)

    override suspend fun currentValue(): ComplicationValue {
        val state = awaitUsageState(this)
        val label = serviceLabel()
        val detail = syncLine(this, lastSyncMillis(state))
        val credentials = state?.credentials.orEmpty()
        val snapshots = state?.snapshots.orEmpty()
        val usable = credentials.mapNotNull { meta -> snapshots[meta.id]?.takeIf { it.isUsable } }

        if (usable.isEmpty()) {
            return ComplicationValue(
                title = label,
                display = PLACEHOLDER_TEXT,
                detail = detail,
                description = "$label 暂无数据，$detail",
                numeric = 0.0,
                ceiling = null,
            )
        }

        val focusDisplay = state?.focusCredential
            ?.let { snapshots[it.id] }
            ?.takeIf { it.isUsable }
            ?.let { compactDisplay(it) }

        val units = usable.map { it.primaryUnit }.distinct()
        val currencies = usable.mapNotNull { it.currency }.distinct()
        val summable = units.size == 1 &&
            (units.first() != MetricUnit.MONEY || currencies.size <= 1)

        if (summable) {
            val unit = units.first()
            val sum = usable.sumOf { it.primaryValue }
            val currency = currencies.firstOrNull()
            val display = formatMetric(unit, sum, "${usable.size} 家", currency)
            // 只有速率窗口利用率这种天然有上限的指标才画进度。
            val ceiling = if (unit == MetricUnit.PERCENT) 1.0 else null
            return ComplicationValue(
                title = label,
                display = display,
                detail = detail,
                description = "$label $display，$detail",
                numeric = if (ceiling != null) sum.coerceIn(0.0, ceiling) else 0.0,
                ceiling = ceiling,
            )
        }

        val count = usable.size
        val display = "$count 家"
        val mixedDetail = if (focusDisplay != null) "$focusDisplay · $detail" else detail
        val description = if (focusDisplay != null) {
            "$label ${display}有数据，焦点账户 $focusDisplay，$detail"
        } else {
            "$label ${display}有数据，$detail"
        }
        return ComplicationValue(
            title = label,
            display = display,
            detail = mixedDetail,
            description = description,
            numeric = 0.0,
            ceiling = null,
        )
    }
}
