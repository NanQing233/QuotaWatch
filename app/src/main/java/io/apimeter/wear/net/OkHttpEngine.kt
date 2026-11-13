package io.apimeter.wear.net

import io.apimeter.wear.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * OkHttp + 链路自动切换。每条链路一个独立 client（代理必须固化在 client 上），成功链路记忆 10 分钟。
 */
class OkHttpEngine(
    private val proxySource: () -> ProxyConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) : HttpEngine {

    private val routeHealth = RouteHealth(ttlMillis = HEALTH_TTL_MILLIS)
    private val clients = ConcurrentHashMap<Route, OkHttpClient>()

    override suspend fun execute(
        method: String,
        url: String,
        providerKey: String,
        headers: Map<String, String>,
        body: ByteArray?,
        contentType: String?,
    ): HttpResult = withContext(Dispatchers.IO) {
        val proxy = proxySource()
        val candidates = routePlan(providerKey, proxy, routeHealth)
        var lastError: NetworkUnreachableException? = null
        for (route in candidates) {
            val started = clock()
            try {
                val result = callOnce(route, proxy, method, url, headers, body, contentType, started)
                routeHealth.record(providerKey, route, clock())
                return@withContext result
            } catch (io: IOException) {
                lastError = NetworkUnreachableException(route, describeFailure(io, route), io)
            } catch (illegal: IllegalArgumentException) {
                // URL 非法等，重试无意义
                throw NetworkUnreachableException(route, illegal.message ?: "请求构造失败", illegal)
            }
        }
        throw lastError ?: NetworkUnreachableException(Route.DIRECT, "没有可用网络链路")
    }

    private fun callOnce(
        route: Route,
        proxy: ProxyConfig,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        contentType: String?,
        started: Long,
    ): HttpResult {
        val client = clients.getOrPut(route) { buildClient(route, proxy) }
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        val requestBody = when {
            method.equals("GET", ignoreCase = true) -> null
            body != null -> body.toRequestBody(contentType?.toMediaTypeOrNull())
            else -> ByteArray(0).toRequestBody(contentType?.toMediaTypeOrNull())
        }
        builder.method(method.uppercase(), requestBody)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val outHeaders = response.headers.names().associateWith { name -> response.header(name).orEmpty() }
            return HttpResult(
                code = response.code,
                body = text,
                route = route,
                tookMs = clock() - started,
                headers = outHeaders,
            )
        }
    }

    private fun buildClient(route: Route, proxy: ProxyConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
        if (route == Route.PROXY && proxy.configured) {
            builder.proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port)))
        }
        return builder.build()
    }

    private fun describeFailure(io: IOException, route: Route): String {
        val link = if (route == Route.PROXY) "代理链路" else "直连链路"
        val reason = io.message?.take(80) ?: io.javaClass.simpleName
        return link + "请求失败：" + reason
    }

    companion object {
        private const val HEALTH_TTL_MILLIS = 10 * 60_000L

        internal fun other(route: Route): Route = if (route == Route.DIRECT) Route.PROXY else Route.DIRECT

        /** 计算链路候选顺序，纯函数便于单测。 */
        internal fun routePlan(providerKey: String, proxy: ProxyConfig, health: RouteHealth): List<Route> {
            if (!proxy.usable) return listOf(Route.DIRECT)
            if (proxy.mode == ProxyMode.ALWAYS) return listOf(Route.PROXY)
            health.lastGood(providerKey)?.let { remembered ->
                return listOf(remembered, other(remembered))
            }
            val domestic = ProviderId.fromKey(providerKey)?.isDomestic ?: false
            val preferred = if (domestic) Route.DIRECT else Route.PROXY
            return listOf(preferred, other(preferred))
        }
    }
}

/** 每个厂商最近一次成功的链路，避免每次都先撞一次超时。 */
class RouteHealth(
    private val ttlMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val route: Route, val at: Long)
    private val entries = ConcurrentHashMap<String, Entry>()

    fun record(providerKey: String, route: Route, now: Long = clock()) {
        entries[providerKey] = Entry(route, now)
    }

    fun lastGood(providerKey: String, now: Long = clock()): Route? {
        val entry = entries[providerKey] ?: return null
        return if (now - entry.at <= ttlMillis) entry.route else null
    }

    fun invalidate(providerKey: String) {
        entries.remove(providerKey)
    }

    fun clear() = entries.clear()
}
