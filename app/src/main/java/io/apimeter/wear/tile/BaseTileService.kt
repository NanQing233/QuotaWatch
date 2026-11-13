package io.apimeter.wear.tile

import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import io.apimeter.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tile 服务基类：`onTileRequest` 跑主线程且须 10 秒内完成，故立即返回 Future，布局在 [Dispatchers.Default] 构建。 */
abstract class BaseTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    protected abstract suspend fun buildTile(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile

    final override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val future = SimpleListenableFuture<TileBuilders.Tile>()
        val job = scope.launch {
            // 用 NonCancellable 包住：即使服务在处理中被销毁也要把结果交回，否则系统会等到 10 秒超时。
            withContext(NonCancellable) {
                val tile = try {
                    buildTile(requestParams)
                } catch (throwable: Throwable) {
                    fallbackTile()
                }
                future.set(tile)
            }
        }
        // 服务正在销毁时协程不会执行，仍要给系统一个结果，避免 10 秒超时。
        if (job.isCancelled) future.set(fallbackTile())
        return future
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 兜底布局：任何异常都退回这段静态文案。 */
    protected fun fallbackTile(): TileBuilders.Tile = runCatching {
        tileOf(
            LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(rootModifiers(this))
                .addContent(textElement(getString(R.string.app_name), 13f, bold = true))
                .addContent(verticalSpacer(4f))
                .addContent(textElement("暂时无法读取数据", 11f, TILE_TEXT_MUTED))
                .build(),
        )
    }.getOrElse {
        // 连兜底布局都构造失败时，返回一个最小的空布局，绝不让异常冒到系统。
        tileOf(LayoutElementBuilders.Column.Builder().build())
    }
}
