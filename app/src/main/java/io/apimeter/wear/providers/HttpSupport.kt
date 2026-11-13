package io.apimeter.wear.providers

import io.apimeter.wear.net.HttpFailureException
import io.apimeter.wear.net.HttpResult

/** 厂商适配层共用 HTTP 结果处理（冻结文件，直接用，不要重写）。 */
object HttpSupport {

    fun requireSuccess(result: HttpResult) {
        if (!result.isSuccess) {
            throw HttpFailureException(result.code, snippet(result.body), friendlyError(result.code, result.body))
        }
    }

    fun snippet(body: String, max: Int = 240): String =
        body.replace(Regex("\\s+"), " ").trim().take(max)

    fun friendlyError(code: Int, body: String): String = when (code) {
        401 -> "鉴权失败：API Key 无效或没有调用该接口的权限"
        403 -> "拒绝访问：该 Key 没有组织级 Admin 权限"
        404 -> "接口不存在：请检查 Base URL 或接口版本"
        429 -> "请求过于频繁（已被限流），请稍后重试"
        in 500..599 -> "服务端错误（HTTP " + code + "），请稍后重试"
        else -> "请求失败（HTTP " + code + "）" + if (body.isBlank()) "" else "：" + snippet(body, 120)
    }
}
