package io.apimeter.wear.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

data class ProxyProbe(
    val ok: Boolean,
    val tookMs: Long,
    val message: String,
)

/** 设置页的「测试代理」：走代理访问 gstatic 204 探测端点。 */
class ProxyProber(private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun probe(config: ProxyConfig): ProxyProbe = withContext(Dispatchers.IO) {
        if (!config.configured) {
            return@withContext ProxyProbe(false, 0L, "未填写代理地址或端口")
        }
        val started = clock()
        val client = OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress(config.host, config.port)))
            .connectTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        try {
            client.newCall(Request.Builder().url(PROBE_URL).build()).execute().use { response ->
                val took = clock() - started
                if (response.code in 200..399) {
                    ProxyProbe(true, took, "代理可用（" + took + "ms）")
                } else {
                    ProxyProbe(false, took, "代理有响应但状态码为 " + response.code)
                }
            }
        } catch (t: Throwable) {
            ProxyProbe(false, clock() - started, "代理不可用：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    private companion object {
        const val PROBE_URL = "https://www.gstatic.com/generate_204"
    }
}
