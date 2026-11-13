package io.apimeter.wear.net

enum class Route { DIRECT, PROXY }

data class HttpResult(
    val code: Int,
    val body: String,
    val route: Route,
    val tookMs: Long,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccess: Boolean get() = code in 200..299
}

/** 服务端返回了 HTTP 响应，但状态码非 2xx。 */
class HttpFailureException(
    val code: Int,
    val bodySnippet: String,
    message: String,
) : IllegalStateException(message)

/** 网络层彻底不可达（DNS / 连接 / TLS / 超时）。 */
class NetworkUnreachableException(
    val route: Route,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

interface HttpEngine {
    suspend fun execute(
        method: String,
        url: String,
        providerKey: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
        contentType: String? = null,
    ): HttpResult

    suspend fun get(
        url: String,
        providerKey: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult = execute("GET", url, providerKey, headers)

    suspend fun post(
        url: String,
        providerKey: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray,
        contentType: String = "application/json; charset=utf-8",
    ): HttpResult = execute("POST", url, providerKey, headers, body, contentType)
}
