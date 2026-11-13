package io.apimeter.wear.complication

/** 单家厂商信息位基类：按 [providerKey] 取最可用快照，无可用时退回第一份并显示占位。 */
abstract class ProviderComplicationService(private val providerKey: String) : BaseComplicationService() {

    final override fun serviceLabel(): String = providerLabel(this, providerKey, short = true)

    final override suspend fun currentValue(): ComplicationValue {
        val state = awaitUsageState(this)
        val snapshot = state?.snapshotForProvider(providerKey)
        val label = serviceLabel()
        val display = compactDisplay(snapshot)
        val detail = syncLine(this, snapshot?.fetchedAt ?: 0L)
        val usable = snapshot != null && snapshot.isUsable
        val metricLabel = snapshot?.primaryLabel?.takeIf { it.isNotBlank() } ?: "用量"
        val description = if (usable) {
            "$label $metricLabel $display，$detail"
        } else {
            "$label 暂无数据，$detail"
        }
        val numeric = snapshot?.primaryValue ?: 0.0
        // 额度上限存在凭据元数据上（套餐/额度包场景），有上限才画进度。
        val quota = state?.credentials?.firstOrNull { it.id == snapshot?.credentialId }?.quotaAmount ?: 0.0
        val ceiling = ceilingFor(snapshot, quota)
        val suffix = quotaDisplay(snapshot, quota)
        return ComplicationValue(
            title = label,
            display = display + suffix,
            detail = detail,
            description = if (suffix.isEmpty()) description else "$description，额度上限$suffix",
            numeric = if (ceiling != null) numeric.coerceIn(0.0, ceiling) else 0.0,
            ceiling = ceiling,
        )
    }
}
