package io.apimeter.wear.tile

import android.content.Context
import androidx.wear.tiles.TileService

/** 数据刷新后主动请求 Tile 重新构建；未放到表盘的 Tile 会自动忽略，重复调用幂等。 */
object TileRefresher {

    /** 与 AndroidManifest.xml 中声明的 tile service 一一对应。 */
    private val services = listOf(
        OverviewTileService::class.java,
    )

    /** 请求所有 Tile 刷新；失败不影响其他 Tile。 */
    fun requestUpdates(context: Context) {
        val appContext = context.applicationContext
        val updater = runCatching { TileService.getUpdater(appContext) }.getOrNull() ?: return
        services.forEach { service ->
            runCatching { updater.requestUpdate(service) }
        }
    }
}
