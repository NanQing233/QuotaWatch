package io.apimeter.wear.net

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePlanTest {

    private val proxy = ProxyConfig(mode = ProxyMode.AUTO, host = "10.0.0.2", port = 7890)

    @Test
    fun noProxy_alwaysDirect() {
        val plan = OkHttpEngine.routePlan("openai", ProxyConfig(), RouteHealth(60_000))
        assertEquals(listOf(Route.DIRECT), plan)
    }

    @Test
    fun auto_overseasPrefersProxy() {
        val plan = OkHttpEngine.routePlan("openai", proxy, RouteHealth(60_000))
        assertEquals(listOf(Route.PROXY, Route.DIRECT), plan)
    }

    @Test
    fun auto_domesticPrefersDirect() {
        val plan = OkHttpEngine.routePlan("volc_ark", proxy, RouteHealth(60_000))
        assertEquals(listOf(Route.DIRECT, Route.PROXY), plan)
    }

    @Test
    fun always_prefersProxyOnly() {
        val plan = OkHttpEngine.routePlan("deepseek", proxy.copy(mode = ProxyMode.ALWAYS), RouteHealth(60_000))
        assertEquals(listOf(Route.PROXY), plan)
    }

    @Test
    fun rememberedRoute_goesFirst() {
        // 注入固定时钟：之前这里用「记录时间 1000L + 读取用系统当前时间」的比较，
        // 两个时间域不一致，导致该用例在任何实现下都不可能通过。
        val health = RouteHealth(60_000) { 1_000L }
        health.record("openai", Route.DIRECT)
        val plan = OkHttpEngine.routePlan("openai", proxy, health)
        assertEquals(listOf(Route.DIRECT, Route.PROXY), plan)
    }

    @Test
    fun staleRoute_isIgnored() {
        val health = RouteHealth(1_000) { 5_000L }
        health.record("openai", Route.DIRECT, now = 1_000L)
        val plan = OkHttpEngine.routePlan("openai", proxy, health)
        assertEquals(listOf(Route.PROXY, Route.DIRECT), plan)
    }
}
