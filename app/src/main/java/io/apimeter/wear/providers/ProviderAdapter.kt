package io.apimeter.wear.providers

import io.apimeter.wear.core.CredentialField
import io.apimeter.wear.core.ProviderId
import io.apimeter.wear.core.UsageRange
import io.apimeter.wear.core.UsageSnapshot
import io.apimeter.wear.net.HttpEngine
import java.time.ZoneId

class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ResolvedCredential(
    val credentialId: String,
    val providerKey: String,
    val label: String,
    val baseUrl: String = "",
    val values: Map<String, String> = emptyMap(),
) {
    fun raw(key: String): String = values[key]?.trim().orEmpty()

    fun required(key: String, label: String): String =
        raw(key).ifBlank { throw ProviderException("缺少必填字段：" + label) }
}

data class FetchContext(
    val credential: ResolvedCredential,
    val range: UsageRange,
    val http: HttpEngine,
    val nowMillis: Long,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val nowSec: Long get() = nowMillis / 1000L

    /** 支持用户自填 Base URL（自建网关 / 官方兼容代理），默认用官方端点。 */
    fun baseUrl(default: String): String =
        credential.baseUrl.ifBlank { default }.trimEnd('/')
}

interface ProviderAdapter {
    val id: ProviderId

    fun fields(): List<CredentialField>

    suspend fun fetch(ctx: FetchContext): UsageSnapshot
}
