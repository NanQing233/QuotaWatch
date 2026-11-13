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
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/** Anthropic Admin Usage Report（GET /v1/organizations/usage_report/messages）：需 Admin API Key（sk-ant-admin…），普通 Key 无权限。 */
class AnthropicAdapter : ProviderAdapter {

    override val id: ProviderId = ProviderId.ANTHROPIC

    override fun fields(): List<CredentialField> = listOf(
        CredentialField(
            key = KEY_ADMIN,
            labelRes = R.string.field_admin_key,
            hintRes = R.string.provider_anthropic_note,
            kind = FieldKind.SECRET,
            secret = true,
        ),
    )

    override suspend fun fetch(ctx: FetchContext): UsageSnapshot {
        val adminKey = ctx.credential.required(KEY_ADMIN, "Admin Key")
        val base = ctx.baseUrl(ProviderCatalog.descriptor(id).defaultBaseUrl)
        val startingAt = Instant.ofEpochSecond(ctx.range.startSec).toString()
        val endingAt = Instant.ofEpochSecond(ctx.range.endSec).toString()

        var input = 0L
        var output = 0L
        var cacheRead = 0L
        var cacheCreate = 0L
        val models = LinkedHashMap<String, Long>()
        val daily = ArrayList<DayPoint>()

        var page: String? = null
        var pages = 0
        var hasMore = false
        do {
            val url = buildString {
                append(base).append("/v1/organizations/usage_report/messages")
                append("?starting_at=").append(startingAt)
                append("&ending_at=").append(endingAt)
                append("&bucket_width=1d")
                append("&group_by%5B%5D=model")
                append("&limit=31")
                if (page != null) append("&page=").append(page)
            }
            val result = ctx.http.get(
                url,
                id.key,
                mapOf(
                    "x-api-key" to adminKey,
                    "anthropic-version" to ANTHROPIC_VERSION,
                    "accept" to "application/json",
                ),
            )
            HttpSupport.requireSuccess(result)
            val root = runCatching { LENIENT.parseToJsonElement(result.body).jsonObject }
                .getOrElse { throw ProviderException("Anthropic 返回了无法解析的内容：" + HttpSupport.snippet(result.body)) }

            val buckets = root["data"].asArrayOrNull().orEmpty()
            for (element in buckets) {
                val bucket = element.asObjectOrNull() ?: continue
                var bucketTokens = 0L
                for (row in bucket["results"].asArrayOrNull().orEmpty()) {
                    val item = row.asObjectOrNull() ?: continue
                    val uncached = item.longOrZero("uncached_input_tokens")
                    val read = item.longOrZero("cache_read_input_tokens")
                    val created = cacheCreation(item)
                    val out = item.longOrZero("output_tokens")
                    input += uncached
                    cacheRead += read
                    cacheCreate += created
                    output += out
                    bucketTokens += uncached + read + created + out
                    val model = item.text("model") ?: "unknown"
                    models[model] = (models[model] ?: 0L) + uncached + out
                }
                val start = bucket.text("starting_at")
                if (start != null) {
                    runCatching { Instant.parse(start).epochSecond }.getOrNull()?.let { epoch ->
                        daily += DayPoint(epochSecond = epoch, tokens = bucketTokens)
                    }
                } else if (bucketTokens > 0) {
                    daily += DayPoint(epochSecond = ctx.range.startSec, tokens = bucketTokens)
                }
            }

            hasMore = root.boolOrFalse("has_more")
            val next = root.text("next_page")
            page = if (hasMore && next != null && pages + 1 < MAX_PAGES) next else null
            pages++
        } while (page != null)

        val total = input + output + cacheRead + cacheCreate
        val lines = buildList {
            add(MetricLine("输入 Token", Fmt.exactCount(input)))
            add(MetricLine("输出 Token", Fmt.exactCount(output)))
            if (cacheRead > 0) add(MetricLine("缓存读取", Fmt.exactCount(cacheRead)))
            if (cacheCreate > 0) add(MetricLine("缓存写入", Fmt.exactCount(cacheCreate)))
            add(MetricLine("模型数", models.size.toString()))
            models.entries.sortedByDescending { it.value }.take(4).forEach { entry ->
                add(MetricLine(entry.key, Fmt.tokens(entry.value)))
            }
        }

        val empty = total == 0L
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
            secondary = if (models.isEmpty()) null else models.keys.take(2).joinToString(" · "),
            lines = lines,
            daily = daily,
            status = if (empty) SnapshotStatus.PARTIAL else SnapshotStatus.OK,
            message = if (empty) "该时间窗口内没有 Messages 用量数据" else null,
        )
    }

    /** cache_creation 在不同版本里既可能是数字，也可能是 ephemeral_1h/5m 对象。 */
    private fun cacheCreation(item: JsonObject): Long {
        val direct = item.longOrZero("cache_creation_input_tokens")
        if (direct > 0) return direct
        val obj = item["cache_creation"].asObjectOrNull() ?: return 0L
        return obj.longOrZero("ephemeral_1h_input_tokens") + obj.longOrZero("ephemeral_5m_input_tokens")
    }

    private companion object {
        const val KEY_ADMIN = "admin_key"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MAX_PAGES = 5
        val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
