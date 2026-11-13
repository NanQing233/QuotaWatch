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
import io.apimeter.wear.providers.sign.VolcSigner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 火山方舟查询推理用量：POST ?Action=GetInferenceUsage&Version=2024-01-01，Access Key V4 签名；新 API Key 只能按 ApiKeyID（资源 ID）过滤。 */
class VolcArkAdapter : ProviderAdapter {

    override val id: ProviderId = ProviderId.VOLC_ARK

    override fun fields(): List<CredentialField> = listOf(
        CredentialField(
            key = KEY_AK,
            labelRes = R.string.field_access_key,
            hintRes = R.string.provider_volc_ark_note,
            kind = FieldKind.SECRET,
            secret = true,
        ),
        CredentialField(
            key = KEY_SK,
            labelRes = R.string.field_secret_key,
            hintRes = R.string.provider_volc_ark_note,
            kind = FieldKind.SECRET,
            secret = true,
        ),
        CredentialField(
            key = KEY_API_KEY_ID,
            labelRes = R.string.field_api_key_id,
            hintRes = R.string.provider_volc_ark_note,
            kind = FieldKind.TEXT,
            required = false,
        ),
        CredentialField(
            key = KEY_ENDPOINT,
            labelRes = R.string.field_endpoint,
            hintRes = R.string.provider_volc_ark_note,
            kind = FieldKind.TEXT,
            required = false,
        ),
        CredentialField(
            key = KEY_REGION,
            labelRes = R.string.field_region,
            hintRes = R.string.provider_volc_ark_note,
            kind = FieldKind.TEXT,
            required = false,
            defaultValue = "cn-beijing",
        ),
    )

    override suspend fun fetch(ctx: FetchContext): UsageSnapshot {
        val accessKey = ctx.credential.required(KEY_AK, "Access Key")
        val secretKey = ctx.credential.required(KEY_SK, "Secret Key")
        val region = ctx.credential.raw(KEY_REGION).ifBlank { "cn-beijing" }
        val apiKeyId = ctx.credential.raw(KEY_API_KEY_ID)
        val endpoint = ctx.credential.raw(KEY_ENDPOINT)
        val base = ctx.baseUrl(ProviderCatalog.descriptor(id).defaultBaseUrl)

        val zone = ZoneId.of("Asia/Shanghai")
        val startDate = LocalDate.ofInstant(Instant.ofEpochSecond(ctx.range.startSec), zone)
        val endDate = LocalDate.ofInstant(Instant.ofEpochSecond(ctx.range.endSec), zone)
        val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

        val bodyJson = buildJsonObject {
            put("StartTime", dateFormat.format(startDate))
            put("EndTime", dateFormat.format(endDate))
            put("QueryInterval", "Day")
            put("ProjectName", "default")
            if (apiKeyId.isNotBlank() || endpoint.isNotBlank()) {
                putJsonArray("Filters") {
                    if (apiKeyId.isNotBlank()) {
                        addJsonObject {
                            put("Key", "ApiKeyID")
                            putJsonArray("Values") { add(apiKeyId) }
                        }
                    }
                    if (endpoint.isNotBlank()) {
                        addJsonObject {
                            put("Key", "ModelEndpoint")
                            putJsonArray("Values") { add(endpoint) }
                        }
                    }
                }
            }
        }
        val body = bodyJson.toString().toByteArray(Charsets.UTF_8)

        val uri = URI(base)
        val host = uri.host ?: "ark.cn-beijing.volcengineapi.com"
        val scheme = uri.scheme ?: "https"
        val path = uri.path.ifBlank { "/" }
        val query = mapOf("Action" to "GetInferenceUsage", "Version" to "2024-01-01")
        val signatureHeaders = VolcSigner.sign(
            VolcSigner.Input(
                method = "POST",
                host = host,
                path = path,
                query = query,
                headers = mapOf("content-type" to CONTENT_TYPE),
                body = body,
                accessKey = accessKey,
                secretKey = secretKey,
                region = region,
                service = "ark",
            ),
        )
        val url = scheme + "://" + host + path +
            "?Action=GetInferenceUsage&Version=2024-01-01"
        val result = ctx.http.post(
            url = url,
            providerKey = id.key,
            headers = signatureHeaders + mapOf("Content-Type" to CONTENT_TYPE),
            body = body,
            contentType = CONTENT_TYPE,
        )
        HttpSupport.requireSuccess(result)
        val root = runCatching { LENIENT.parseToJsonElement(result.body).jsonObject }
            .getOrElse { throw ProviderException("火山方舟返回了无法解析的内容：" + HttpSupport.snippet(result.body)) }

        root["ResponseMetadata"].asObjectOrNull()?.get("Error").asObjectOrNull()?.let { error ->
            val code = error.text("Code") ?: ""
            val message = error.text("Message") ?: ""
            throw ProviderException("火山方舟返回错误：" + code + " " + message)
        }

        val payload = root["Result"].asObjectOrNull() ?: root
        val columns = payload["Fields"].asArrayOrNull()?.mapNotNull { element ->
            element.asObjectOrNull()?.text("Name")
        }.orEmpty()
        val rows = payload["Data"].asArrayOrNull().orEmpty()

        var input = 0L
        var output = 0L
        var total = 0L
        var requests = 0L
        val daily = ArrayList<DayPoint>()
        val indexOf = { name: String -> columns.indexOfFirst { it.equals(name, ignoreCase = true) } }
        val inputIndex = indexOf("InputTokens")
        val outputIndex = indexOf("OutputTokens")
        val totalIndex = indexOf("TotalTokens")
        val requestIndex = indexOf("ReqCnt")
        val dayIndex = maxOf(indexOf("Day"), indexOf("Hour"))

        for (row in rows) {
            val cells = row.asArrayOrNull().orEmpty()
            fun num(index: Int): Long {
                if (index < 0 || index >= cells.size) return 0L
                return cells[index].toString().trim().trim('"').toLongOrNull() ?: 0L
            }
            val rowInput = num(inputIndex)
            val rowOutput = num(outputIndex)
            val rowTotal = num(totalIndex).takeIf { it > 0 } ?: (rowInput + rowOutput)
            input += rowInput
            output += rowOutput
            total += rowTotal
            requests += num(requestIndex)
            if (dayIndex >= 0 && dayIndex < cells.size) {
                val raw = cells[dayIndex].toString().trim().trim('"')
                val epoch = parseBucketEpoch(raw, zone)
                if (epoch != null) daily += DayPoint(epochSecond = epoch, tokens = rowTotal)
            }
        }

        val lines = buildList {
            add(MetricLine("输入 Token", Fmt.exactCount(input)))
            add(MetricLine("输出 Token", Fmt.exactCount(output)))
            if (requests > 0) add(MetricLine("请求数", Fmt.exactCount(requests)))
            add(MetricLine("数据行数", rows.size.toString()))
            if (apiKeyId.isNotBlank()) add(MetricLine("ApiKeyID", apiKeyId))
            if (endpoint.isNotBlank()) add(MetricLine("接入点", endpoint))
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
            secondary = if (requests > 0) Fmt.exactCount(requests) + " 次请求" else null,
            lines = lines,
            daily = daily,
            status = if (rows.isEmpty()) SnapshotStatus.PARTIAL else SnapshotStatus.OK,
            message = if (rows.isEmpty()) "该时间窗口内没有推理用量数据（检查 ApiKeyID / 接入点是否正确）" else null,
        )
    }

    /** Day 列是 yyyy-MM-dd（北京时间），Hour 列可能是 yyyy-MM-dd HH:mm:ss。 */
    private fun parseBucketEpoch(raw: String, zone: ZoneId): Long? {
        if (raw.isBlank()) return null
        val dayOnly = runCatching {
            LocalDate.parse(raw.take(10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone).toEpochSecond()
        }.getOrNull()
        return dayOnly
    }

    private companion object {
        const val KEY_AK = "access_key"
        const val KEY_SK = "secret_key"
        const val KEY_API_KEY_ID = "api_key_id"
        const val KEY_ENDPOINT = "model_endpoint"
        const val KEY_REGION = "region"
        const val CONTENT_TYPE = "application/json; charset=utf-8"
        val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
