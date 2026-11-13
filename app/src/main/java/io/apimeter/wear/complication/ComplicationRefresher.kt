package io.apimeter.wear.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/** 数据刷新后主动通知系统重拉七个信息位；只更新正被表盘使用的信息位，可放心每次调用。 */
object ComplicationRefresher {

    /** 与 AndroidManifest.xml 中声明的 7 个 service 一一对应。 */
    private val services = listOf(
        OverviewComplicationService::class.java,
        OpenAiComplicationService::class.java,
        AnthropicComplicationService::class.java,
        VolcArkComplicationService::class.java,
        TencentTokenHubComplicationService::class.java,
        DeepSeekComplicationService::class.java,
        MoonshotComplicationService::class.java,
    )

    /** 请求所有信息位重新渲染；任何一步失败都不影响其余信息位。 */
    fun requestUpdates(context: Context) {
        val appContext = context.applicationContext
        services.forEach { service ->
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(appContext, ComponentName(appContext, service))
                    .requestUpdateAll()
            }
        }
    }
}
