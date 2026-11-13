package io.apimeter.wear.providers

import io.apimeter.wear.R
import io.apimeter.wear.core.CredentialField
import io.apimeter.wear.core.FieldKind
import io.apimeter.wear.core.Fmt
import io.apimeter.wear.core.MetricLine
import io.apimeter.wear.core.MetricUnit
import io.apimeter.wear.core.ProviderId
import io.apimeter.wear.core.UsageSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** DeepSeek 官方只提供余额查询（GET /user/balance），无用量明细 API，故只展示余额。 */
class DeepSeekAdapter : ProviderAdapter {

    override val id: ProviderId = ProviderId.DEEPSEEK

    override fun fields(): List<CredentialField> = listOf(
        CredentialField(
            key = KEY,
            labelRes = R.string.field_api_key,
            hintRes = R.string.provider_deepseek_note,
            kind = FieldKind.SECRET,
            secret = true,
        ),
    )

    override suspend fun fetch(ctx: FetchContext): UsageSnapshot {
        val apiKey = ctx.credential.required(KEY, "API Key")
        val base = ctx.baseUrl(ProviderCatalog.descriptor(id).defaultBaseUrl)
        val result = ctx.http.get(
            base + "/user/balance",
            id.key,
            mapOf("Authorization" to "Bearer " + apiKey, "Accept" to "application/json"),
        )
        HttpSupport.requireSuccess(result)
        val root = runCatching { LENIENT.parseToJsonElement(result.body).jsonObject }
            .getOrElse { throw ProviderException("DeepSeek 返回了无法解析的内容：" + HttpSupport.snippet(result.body)) }

        val available = root["is_available"]?.jsonPrimitive?.booleanOrNull ?: true
        val infos = root["balance_infos"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
        val primary = infos.firstOrNull()
        val total = primary?.get("total_balance")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val currency = primary?.get("currency")?.jsonPrimitive?.content?.uppercase() ?: "CNY"
        val granted = primary?.get("granted_balance")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val topped = primary?.get("topped_up_balance")?.jsonPrimitive?.doubleOrNull ?: 0.0

        val lines = buildList {
            add(MetricLine("赠送余额", Fmt.money(granted, currency)))
            add(MetricLine("充值余额", Fmt.money(topped, currency)))
            infos.drop(1).forEach { info ->
                val c = info["currency"]?.jsonPrimitive?.content?.uppercase() ?: ""
                val v = info["total_balance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                add(MetricLine("余额（" + c + "）", Fmt.money(v, c)))
            }
            add(MetricLine("账户状态", if (available) "正常" else "不可用"))
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
            primaryDisplay = Fmt.money(total, currency),
            primaryUnit = MetricUnit.MONEY,
            currency = currency,
            secondary = "官方未提供用量明细 API",
            lines = lines,
            status = if (available) io.apimeter.wear.core.SnapshotStatus.OK else io.apimeter.wear.core.SnapshotStatus.PARTIAL,
            message = if (available) null else "账户当前不可用（is_available=false）",
        )
    }

    private fun JsonObject.stringField(name: String): String? =
        this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private companion object {
        const val KEY = "api_key"
        val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
