package io.apimeter.wear.providers

import io.apimeter.wear.R
import io.apimeter.wear.core.CredentialField
import io.apimeter.wear.core.FieldKind
import io.apimeter.wear.core.Fmt
import io.apimeter.wear.core.MetricLine
import io.apimeter.wear.core.MetricUnit
import io.apimeter.wear.core.ProviderId
import io.apimeter.wear.core.SnapshotStatus
import io.apimeter.wear.core.UsageSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Moonshot(Kimi) 官方只提供余额查询（GET /v1/users/me/balance），消费明细只能控制台导出。 */
class MoonshotAdapter : ProviderAdapter {

    override val id: ProviderId = ProviderId.MOONSHOT

    override fun fields(): List<CredentialField> = listOf(
        CredentialField(
            key = KEY,
            labelRes = R.string.field_api_key,
            hintRes = R.string.provider_moonshot_note,
            kind = FieldKind.SECRET,
            secret = true,
        ),
    )

    override suspend fun fetch(ctx: FetchContext): UsageSnapshot {
        val apiKey = ctx.credential.required(KEY, "API Key")
        val base = ctx.baseUrl(ProviderCatalog.descriptor(id).defaultBaseUrl)
        val result = ctx.http.get(
            base + "/v1/users/me/balance",
            id.key,
            mapOf("Authorization" to "Bearer " + apiKey, "Accept" to "application/json"),
        )
        HttpSupport.requireSuccess(result)
        val root = runCatching { LENIENT.parseToJsonElement(result.body).jsonObject }
            .getOrElse { throw ProviderException("Moonshot 返回了无法解析的内容：" + HttpSupport.snippet(result.body)) }

        val data = root["data"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: root
        val available = data.num("available_balance")
        val voucher = data.num("voucher_balance")
        val cash = data.num("cash_balance")
        val total = if (available != 0.0) available else voucher + cash
        // Moonshot 余额响应无币种字段：国内站(moonshot.cn)人民币、国际站(moonshot.ai)美元，按域名判断，判不出不加符号。
        val currency = when {
            base.contains("moonshot.cn") -> "CNY"
            base.contains("moonshot.ai") -> "USD"
            else -> null
        }

        val lines = buildList {
            add(MetricLine("代金券余额", formatMoney(voucher, currency)))
            add(MetricLine("现金余额", formatMoney(cash, currency)))
            add(MetricLine("可用余额", formatMoney(available, currency), emphasis = true))
        }

        return UsageSnapshot(
            providerKey = id.key,
            credentialId = ctx.credential.credentialId,
            credentialLabel = ctx.credential.label,
            fetchedAt = ctx.nowMillis,
            windowStartSec = ctx.range.startSec,
            windowEndSec = ctx.range.endSec,
            primaryLabel = "账户余额",
            primaryValue = total,
            primaryDisplay = formatMoney(total, currency),
            primaryUnit = MetricUnit.MONEY,
            currency = currency,
            secondary = "官方未提供用量明细 API",
            lines = lines,
            status = SnapshotStatus.OK,
        )
    }

    private fun JsonObject.num(name: String): Double =
        this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0

    private fun formatMoney(value: Double, currency: String?): String =
        if (currency == null) Fmt.shortCount(value) else Fmt.money(value, currency)

    private companion object {
        const val KEY = "api_key"
        val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
