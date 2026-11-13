package io.apimeter.wear.net

import kotlinx.serialization.Serializable

enum class ProxyMode { OFF, AUTO, ALWAYS }

/**
 * AUTO：境外端点优先代理、国内端点优先直连，失败自动换链路，并记住最近一次成功链路 10 分钟。
 */
@Serializable
data class ProxyConfig(
    val mode: ProxyMode = ProxyMode.AUTO,
    val host: String = "",
    val port: Int = 7890,
) {
    val configured: Boolean
        get() = host.isNotBlank() && port in 1..65535

    val usable: Boolean
        get() = mode != ProxyMode.OFF && configured

    fun describe(): String = when {
        mode == ProxyMode.OFF -> "直连"
        // 未填地址时不能说成「直连」：代理页里明明选的是自动/始终，两边对不上。
        !configured -> if (mode == ProxyMode.ALWAYS) "始终（未配置地址）" else "自动（未配置地址）"
        else -> if (mode == ProxyMode.ALWAYS) "始终代理 " + host + ":" + port else "自动 " + host + ":" + port
    }
}
