package io.apimeter.wear.providers

import io.apimeter.wear.R
import io.apimeter.wear.core.CredentialField
import io.apimeter.wear.core.DayPoint
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** OpenAI Organization Usage / Costs API，需组织 Admin Key（sk-admin…），普通 API Key 无权限。 */
class OpenAiAdapter : ProviderAdapter {

    override val id: ProviderId = ProviderId.OPENAI

    override fun fields(): List<CredentialField> = listOf(
        CredentialField(
            key = KEY,
            labelRes = R.string.field_admin_key,
            hintRes = R.string.provider_openai_note,
            kind = FieldKind.SECRET,
            secret = true,
        ),
    )

    override suspend fun fetch(ctx: FetchContext): UsageSnapshot {
        val adminKey = ctx.credential.required(KEY, "Admin Key")
        val base = ctx.baseUrl(ProviderCatalog.descriptor(id).defaultBaseUrl)
        val headers = mapOf(
            "Authorization" to "Bearer " + adminKey,
            "Accept" to "application/json",
        )

        val usageUrl = base + "/v1/organization/usage/completions" +
            "?start_time=" + ctx.range.startSec +
            "&end_time=" + ctx.range.endSec +
            "&bucket_width=1d&limit=31&group_by=model"
        val usageResult = ctx.http.get(usageUrl, id.key, headers)
        HttpSupport.requireSuccess(usageResult)
        val usageBody = parse(usageResult.body)
        val buckets = collectBuckets(usageBody)

        var input = 0L
        var output = 0L
        var cached = 0L
        var requests = 0L
        val perModel = LinkedHashMap<String, Long>()
        val daily = ArrayList<DayPoint>()
        for (bucket in buckets) {
            var bucketTokens = 0L
            for (item in bucket.results) {
                val i = item.longField("input_tokens")
                val o = item.longField("output_tokens")
                val c = item.longField("input_cached_tokens")
                input += i
                output += o
                cached += c
                requests += item.longField("num_model_requests")
                val model = item.stringField("model") ?: "unknown"
                perModel[model] = (perModel[model] ?: 0L) + i + o
                bucketTokens += i + o
            }
            if (bucket.startTime > 0) {
                daily += DayPoint(epochSecond = bucket.startTime, tokens = bucketTokens)
            }
        }
        val total = input + output

        var costMicros = 0L
        var currency = "USD"
        var costNote: String? = null
        val costUrl = base + "/v1/organization/costs" +
            "?start_time=" + ctx.range.startSec +
            "&end_time=" + ctx.range.endSec +
            "&bucket_width=1d&limit=31"
        val costResult = ctx.http.get(costUrl, id.key, headers)
        if (costResult.isSuccess) {
            val parsed = runCatching { parseCost(parse(costResult.body)) }.getOrNull()
            if (parsed != null) {
                costMicros = parsed.first
                currency = parsed.second
            }
        } else {
            costNote = "成本接口不可用（" + HttpSupport.friendlyError(costResult.code, costResult.body) + "）"
        }

        val lines = buildList {
            add(MetricLine("输入 Token", Fmt.exactCount(input)))
            add(MetricLine("输出 Token", Fmt.exactCount(output)))
            if (cached > 0) add(MetricLine("缓存输入", Fmt.exactCount(cached)))
            add(MetricLine("请求数", Fmt.exactCount(requests)))
            if (costMicros > 0) add(MetricLine("成本", Fmt.microsAsMoney(costMicros, currency), emphasis = true))
            perModel.entries.sortedByDescending { it.value }.take(4).forEach { entry ->
                add(MetricLine(entry.key, Fmt.tokens(entry.value)))
            }
        }

        val status = if (costNote != null) SnapshotStatus.PARTIAL else SnapshotStatus.OK
        return UsageSnapshot(
            providerKey = id.key,
            credentialId = ctx.credential.credentialId,
            credentialLabel = ctx.credential.label,
            fetchedAt = ctx.nowMillis,
            windowStartSec = ctx.range.startSec,
            windowEndSec = ctx.range.endSec,
            primaryLabel = "Token 用量",
            primaryValue = total.toDouble(),
            primaryDisplay = Fmt.tokens(total),
            primaryUnit = MetricUnit.TOKENS,
            // 只有真的取到成本时才带币种，否则金额字段为空，避免出现一个没来由的符号。
            currency = if (costMicros > 0) currency else null,
            secondary = if (costMicros > 0) Fmt.microsAsMoney(costMicros, currency) else null,
            lines = lines,
            daily = daily,
            status = status,
            message = costNote,
        )
    }

    private data class Bucket(val startTime: Long, val results: List<JsonObject>)

    private fun collectBuckets(root: JsonObject): List<Bucket> {
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val obj = element.jsonObject
            val start = obj.longField("start_time")
            val results = obj["results"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
            Bucket(start, results)
        }
    }

    private fun parseCost(root: JsonObject): Pair<Long, String> {
        var micros = 0L
        var currency = "USD"
        val data = root["data"]?.jsonArray ?: return 0L to currency
        for (element in data) {
            val results = element.jsonObject["results"]?.jsonArray ?: continue
            for (item in results) {
                val amount = item.jsonObject["amount"]?.jsonObject ?: continue
                val value = amount["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                currency = amount["currency"]?.jsonPrimitive?.content?.uppercase() ?: currency
                micros += Math.round(value * 1_000_000.0)
            }
        }
        return micros to currency
    }

    private fun parse(body: String): JsonObject =
        runCatching { LENIENT.parseToJsonElement(body).jsonObject }
            .getOrElse { throw ProviderException("OpenAI 返回了无法解析的内容：" + HttpSupport.snippet(body)) }

    private fun JsonObject.longField(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull ?: 0L

    private fun JsonObject.stringField(name: String): String? =
        this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private companion object {
        const val KEY = "admin_key"
        val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
