package io.apimeter.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.TileBuilders
import com.google.common.util.concurrent.ListenableFuture
import io.apimeter.wear.MainActivity
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Tile 公共基建：ProtoLayout 原生 LayoutElementBuilders 拼装；圆屏按圆形遮罩裁切，
 * 内容贴左上会被切掉，故根 Box 水平+垂直居中、留小内边距落在圆最宽中带。
 */

internal val TILE_TEXT_PRIMARY: Int = 0xFFFFFFFF.toInt()

internal val TILE_TEXT_SECONDARY: Int = 0xFFC4C9C6.toInt()

internal val TILE_TEXT_MUTED: Int = 0xFF8F9A96.toInt()

internal val TILE_DIVIDER: Int = 0x1FFFFFFF

/** 系统自动重拉 Tile 的间隔：15 分钟，与 complication 的 UPDATE_PERIOD_SECONDS 对齐。 */
internal const val TILE_FRESHNESS_MILLIS: Long = 15L * 60L * 1000L

/** 构造一段文本。字号用 sp，默认单行 + 末尾省略号，避免长厂商名撑破 Tile。 */
@Suppress("DEPRECATION") // protolayout 1.4.2 里 TEXT_OVERFLOW_* 只有已废弃的 Int 常量，没有替代 API
internal fun textElement(
    text: String,
    sizeSp: Float,
    color: Int = TILE_TEXT_PRIMARY,
    bold: Boolean = false,
    maxLines: Int = 1,
    center: Boolean = false,
    modifiers: ModifiersBuilders.Modifiers? = null,
): LayoutElementBuilders.Text {
    val fontStyle = LayoutElementBuilders.FontStyle.Builder()
        .setSize(DimensionBuilders.sp(sizeSp))
        .setColor(ColorBuilders.argb(color))
        .apply { if (bold) setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD) }
        .build()
    return LayoutElementBuilders.Text.Builder()
        .setText(text)
        .setFontStyle(fontStyle)
        .setMaxLines(maxLines)
        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
        .apply {
            if (center) setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        }
        .apply { if (modifiers != null) setModifiers(modifiers) }
        .build()
}

internal fun verticalSpacer(heightDp: Float): LayoutElementBuilders.Spacer =
    LayoutElementBuilders.Spacer.Builder()
        .setHeight(DimensionBuilders.dp(heightDp))
        .build()

internal fun expandingWidthSpacer(): LayoutElementBuilders.Spacer =
    LayoutElementBuilders.Spacer.Builder()
        .setWidth(DimensionBuilders.expand())
        .build()

internal fun divider(): LayoutElementBuilders.Box =
    LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.dp(1f))
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(TILE_DIVIDER))
                        .build(),
                )
                .build(),
        )
        .build()

internal fun metricRow(
    label: String,
    value: String,
    valueColor: Int = TILE_TEXT_PRIMARY,
): LayoutElementBuilders.Row =
    LayoutElementBuilders.Row.Builder()
        .setWidth(DimensionBuilders.expand())
        .addContent(textElement(label, 12f, TILE_TEXT_SECONDARY))
        .addContent(expandingWidthSpacer())
        .addContent(textElement(value, 12f, valueColor, bold = true))
        .build()

internal fun appClickable(context: Context): ModifiersBuilders.Clickable =
    ModifiersBuilders.Clickable.Builder()
        .setOnClick(ActionBuilders.launchAction(ComponentName(context, MainActivity::class.java)))
        .build()

/**
 * 根容器修饰：圆屏左右 14dp、上下 6dp 内边距，居中后落在最宽中带（≈178dp）不被圆弧切；
 * 同时绑定点击打开 App 与无障碍描述。
 */
internal fun rootModifiers(
    context: Context,
    contentDescription: String? = null,
): ModifiersBuilders.Modifiers =
    ModifiersBuilders.Modifiers.Builder()
        .setPadding(
            ModifiersBuilders.Padding.Builder()
                .setStart(DimensionBuilders.dp(14f))
                .setEnd(DimensionBuilders.dp(14f))
                .setTop(DimensionBuilders.dp(6f))
                .setBottom(DimensionBuilders.dp(6f))
                .build(),
        )
        .setClickable(appClickable(context))
        .apply {
            if (contentDescription != null) {
                setSemantics(
                    ModifiersBuilders.Semantics.Builder()
                        .setContentDescription(contentDescription)
                        .build(),
                )
            }
        }
        .build()

internal fun centeredColumn(): LayoutElementBuilders.Column.Builder =
    LayoutElementBuilders.Column.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

internal fun tileRoot(
    context: Context,
    contentDescription: String?,
    content: LayoutElementBuilders.LayoutElement,
): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(rootModifiers(context, contentDescription))
        .addContent(content)
        .build()

internal fun tileOf(root: LayoutElementBuilders.LayoutElement): TileBuilders.Tile =
    TileBuilders.Tile.Builder()
        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
        .setFreshnessIntervalMillis(TILE_FRESHNESS_MILLIS)
        .build()

/**
 * 极简 [ListenableFuture]：只支持一次性设置结果。onTileRequest 要求返回它，
 * 项目未引 tiles-ktx / Guava，故自行实现（系统只 addListener + get）。
 */
internal class SimpleListenableFuture<T> : ListenableFuture<T> {

    private val lock = Any()
    private val latch = CountDownLatch(1)
    private val listenerPairs = ArrayList<Pair<Runnable, Executor>>()

    private var result: T? = null
    private var done = false
    private var cancelled = false

    /** 完成这个 Future；重复调用会被忽略。 */
    fun set(value: T) {
        val pending = synchronized(lock) {
            if (done) return
            result = value
            done = true
            drainListenersLocked()
        }
        latch.countDown()
        pending.forEach { (listener, executor) -> runCatching { executor.execute(listener) } }
    }

    override fun addListener(listener: Runnable, executor: Executor) {
        val runImmediately = synchronized(lock) {
            if (done) {
                true
            } else {
                listenerPairs += listener to executor
                false
            }
        }
        if (runImmediately) runCatching { executor.execute(listener) }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        val pending = synchronized(lock) {
            if (done) return false
            cancelled = true
            done = true
            drainListenersLocked()
        }
        latch.countDown()
        pending.forEach { (listener, executor) -> runCatching { executor.execute(listener) } }
        return true
    }

    override fun isCancelled(): Boolean = synchronized(lock) { cancelled }

    override fun isDone(): Boolean = synchronized(lock) { done }

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun get(): T {
        latch.await()
        return resolve()
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun get(timeout: Long, unit: TimeUnit): T {
        if (!latch.await(timeout, unit)) {
            throw TimeoutException("Tile 布局未在超时时间内构建完成")
        }
        return resolve()
    }

    private fun drainListenersLocked(): List<Pair<Runnable, Executor>> {
        val pending = ArrayList(listenerPairs)
        listenerPairs.clear()
        return pending
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolve(): T {
        synchronized(lock) {
            if (cancelled) throw CancellationException("Tile 请求已取消")
            return result ?: throw ExecutionException(IllegalStateException("Tile 请求没有结果"))
        }
    }
}
