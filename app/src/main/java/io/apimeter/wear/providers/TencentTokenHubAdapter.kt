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
import io.apimeter.wear.providers.sign.TencentSigner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 腾讯云 TokenHub 查询用量排行：POST Action=DescribeUsageRankList / Version 2026-03-22，TC3-HMAC-SHA256 签名，Region 为公共必填参数。 */
class TencentTokenHubAdapter : ProviderAdapter {

    override val id: ProviderId = ProviderId.TENCENT_TOKENHUB

    override fun fields(): List<CredentialField> = listOf(
        CredentialField(
            key = KEY_SECRET_ID,
            labelRes = R.string.field_secret_id,
            hintRes = R.string.provider_tencent_tokenhub_note,
            kind = FieldKind.SECRET,
            secret = true,
        ),
        CredentialField(
            key = KEY_SECRET_KEY,
            labelRes = R.string.field_secret_key_tencent,
            hintRes = R.string.provider_tencent_tokenhub_note,
            kind = FieldKind.SECRET,
            secret = true,
        ),
        CredentialField(
            key = KEY_REGION,
            labelRes = R.string.field_region,
            hintRes = R.string.provider_tencent_tokenhub_note,
            kind = FieldKind.TEXT,
            required = false,
            defaultValue = "ap-guangzhou",
        ),
        CredentialField(
            key = KEY_TARGET,
            labelRes = R.string.field_api_key_id,
            hintRes = R.string.provider_tencent_tokenhub_note,
            kind = FieldKind.TEXT,
            required = false,
        ),
    )

    override suspend fun fetch(ctx: FetchContext): UsageSnapshot {
        val secretId = ctx.credential.required(KEY_SECRET_ID, "SecretId")
        val secretKey = ctx.credential.required(KEY_SECRET_KEY, "SecretKey")
        val region = ctx.credential.raw(KEY_REGION).ifBlank { "ap-guangzhou" }
        val target = ctx.credential.raw(KEY_TARGET)
        val base = ctx.baseUrl(ProviderCatalog.descriptor(id).defaultBaseUrl)

        val offset = ZoneOffset.ofHours(8)
        val startIso = RFC3339.format(Instant.ofEpochSecond(ctx.range.startSec).atOffset(offset))
        val endIso = RFC3339.format(Instant.ofEpochSecond(ctx.range.endSec).atOffset(offset))
        val spanDays = ((ctx.range.endSec - ctx.range.startSec + 86_399L) / 86_400L).coerceAtLeast(1L)
        val period = when {
            spanDays <= 1 -> 60
            spanDays <= 5 -> 300
            spanDays <= 10 -> 3600
            else -> 86400
        }

        val bodyJson = buildJsonObject {
            put("Dimension", "apikey")
            put("StartTime", startIso)
            put("EndTime", endIso)
            put("MetricType", "tokens")
            put("Period", period)
            put("ShowAll", true)
            if (target.isNotBlank()) put("Target", target)
        }
        val body = bodyJson.toString().toByteArray(Charsets.UTF_8)

        val uri = URI(base)
        val host = uri.host ?: "tokenhub.tencentcloudapi.com"
        val scheme = uri.scheme ?: "https"
        val path = uri.path.ifBlank { "/" }
        val timestamp = ctx.nowSec
        val authorization = TencentSigner.authorization(
            TencentSigner.Input(
                method = "POST",
                host = host,
                path = path,
                contentType = CONTENT_TYPE,
                body = body,
                secretId = secretId,
                secretKey = secretKey,
                service = "tokenhub",
                timestampSec = timestamp,
            ),
        )

        val result = ctx.http.post(
            url = scheme + "://" + host + path,
            providerKey = id.key,
            headers = mapOf(
                "Authorization" to authorization,
                "Content-Type" to CONTENT_TYPE,
                "Host" to host,
                "X-TC-Action" to ACTION,
                "X-TC-Version" to VERSION,
                "X-TC-Timestamp" to timestamp.toString(),
                "X-TC-Region" to region,
            ),
            body = body,
            contentType = CONTENT_TYPE,
        )
        HttpSupport.requireSuccess(result)
        val root = runCatching { LENIENT.parseToJsonElement(result.body).jsonObject }
            .getOrElse { throw ProviderException("腾讯云返回了无法解析的内容：" + HttpSupport.snippet(result.body)) }

        val response = root["Response"].asObjectOrNull() ?: root
        response["Error"].asObjectOrNull()?.let { error ->
            val code = error.text("Code") ?: ""
            val message = error.text("Message") ?: ""
            throw ProviderException("TokenHub 返回错误：" + code + " " + message)
        }

        val totalStats = response["TotalStats"].asObjectOrNull()
        var total = totalStats.metric("TotalToken")
        var input = totalStats.metric("InputTotalToken")
        var output = totalStats.metric("OutputTotalToken")
        var cacheRead = totalStats.metric("CacheTotalToken")
        val objects = response["Total"].asObjectOrNull()?.text("Value")?.toLongOrNull()
            ?: response["Total"].toString().trim('"').toLongOrNull()
            ?: 0L

        val topList = response["TopList"].asArrayOrNull().orEmpty()
        if (total == 0L) {
            for (element in topList) {
                val item = element.asObjectOrNull() ?: continue
                val stats = item["Stats"].asObjectOrNull() ?: item
                total += stats.metric("TotalToken")
                input += stats.metric("InputTotalToken")
                output += stats.metric("OutputTotalToken")
                cacheRead += stats.metric("CacheTotalToken")
            }
        }

        val daily = buildDailySeries(response, topList)

        val lines = buildList {
            add(MetricLine("输入 Token", Fmt.exactCount(input)))
            add(MetricLine("输出 Token", Fmt.exactCount(output)))
            if (cacheRead > 0) add(MetricLine("读缓存 Token", Fmt.exactCount(cacheRead)))
            if (objects > 0) add(MetricLine("API Key 数", objects.toString()))
            if (target.isNotBlank()) add(MetricLine("过滤对象", target))
            add(MetricLine("统计粒度", periodLabel(period)))
        }

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
            secondary = if (objects > 0) objects.toString() + " 个对象" else null,
            lines = lines,
            daily = daily,
            status = if (total == 0L) SnapshotStatus.PARTIAL else SnapshotStatus.OK,
            message = if (total == 0L) "该时间窗口内没有 Token 用量数据（检查地域 Region 是否正确）" else null,
        )
    }

    /** 兼容 ShowAll=false 时的 Timestamps + TopList[].Series 结构，把各对象曲线按时间点相加。 */
    private fun buildDailySeries(response: JsonObject, topList: List<kotlinx.serialization.json.JsonElement>): List<DayPoint> {
        val timestamps = response["Timestamps"].asArrayOrNull()?.mapNotNull { it.toString().trim('"').toLongOrNull() } ?: return emptyList()
        if (timestamps.isEmpty() || topList.isEmpty()) return emptyList()
        val sums = LongArray(timestamps.size)
        for (element in topList) {
            val series = element.asObjectOrNull()?.get("Series").asArrayOrNull() ?: continue
            series.forEachIndexed { index, value ->
                if (index < sums.size) {
                    sums[index] = sums[index] + (value.toString().trim('"').toLongOrNull() ?: 0L)
                }
            }
        }
        return timestamps.indices
            .map { index -> DayPoint(epochSecond = timestamps[index], tokens = sums[index]) }
            .filter { it.tokens > 0 }
            .takeLast(14)
    }

    private fun JsonObject?.metric(name: String): Long {
        val raw = this?.get(name)?.toString()?.trim('"') ?: return 0L
        return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong() ?: 0L
    }

    private fun periodLabel(period: Int): String = when (period) {
        60 -> "1 分钟"
        300 -> "5 分钟"
        3600 -> "1 小时"
        else -> "1 天"
    }

    private companion object {
        const val KEY_SECRET_ID = "secret_id"
        const val KEY_SECRET_KEY = "secret_key"
        const val KEY_REGION = "region"
        const val KEY_TARGET = "target"
        const val ACTION = "DescribeUsageRankList"
        const val VERSION = "2026-03-22"
        const val CONTENT_TYPE = "application/json; charset=utf-8"
        val RFC3339: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
