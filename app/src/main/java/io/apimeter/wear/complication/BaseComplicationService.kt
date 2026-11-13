package io.apimeter.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import io.apimeter.wear.MainActivity
import io.apimeter.wear.R
import kotlinx.coroutines.CancellationException

/** 表盘信息位统一展示模型（各类型共用）；[ceiling] 为 null 表示无上限（余额、普通用量）。 */
data class ComplicationValue(
    val title: String,
    val display: String,
    val detail: String,
    val description: String,
    val numeric: Double,
    val ceiling: Double?,
)

/**
 * 七个表盘信息位公共基类（[SuspendingComplicationDataSourceService]，回调一次即返回）。
 * 7 个 service 不声明 RANGED_VALUE：三星边缘弧槽读到 RANGED_VALUE 就画进度弧，
 * `rangedValueProgressHidden` 在表盘渲染器侧、数据源关不掉，只能不声明改请求文本；
 * 系统按槽位 supportedTypes 请求，返回不匹配类型会被丢弃。所有异常吞成占位内容保证回调一次。
 */
abstract class BaseComplicationService : SuspendingComplicationDataSourceService() {

    /** 信息位在 watch face 上显示的名字，用作短标题。 */
    protected abstract fun serviceLabel(): String

    /** 读取当前要展示的数据；实现内部可以 suspend 等待缓存恢复。 */
    protected abstract suspend fun currentValue(): ComplicationValue

    final override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val type = request.complicationType
        return try {
            dataFor(type, currentValue())
        } catch (cancel: CancellationException) {
            // 取消是正常销毁，向上抛，不当错误处理。
            throw cancel
        } catch (throwable: Throwable) {
            // 兜底：占位数据仍须匹配请求类型，避免系统校验失败。
            runCatching { dataFor(type, placeholderValue()) }.getOrNull()
                ?: runCatching { NoDataComplicationData() }.getOrNull()
        }
    }

    /** 表盘编辑器预览数据：固定文案，不含动态值。 */
    final override fun getPreviewData(type: ComplicationType): ComplicationData? = runCatching {
        dataFor(type, previewValue())
    }.getOrNull()

    /** 按请求的类型构造 [ComplicationData]；暂不支持的类型回 NO_DATA。 */
    private fun dataFor(type: ComplicationType, value: ComplicationValue): ComplicationData =
        when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                // 短文本 text 上限 7 字符：去掉「 / 额度」后缀并紧凑化数字，避免被表盘截断。
                text(shortComplicationText(value.display)),
                text(value.description),
            )
                // 标题同样受 7 字符上限约束，只有圆形槽需要缩写（DeepSeek → DS、TokenHub → 腾讯云）。
                .setTitle(text(circularTitle(value.title)))
                .setTapAction(openAppPendingIntent())
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                // LONG_TEXT 正文允许换行、无长度限制：第二行放同步时间，用完整名不缩写。
                text(value.display + "\n" + value.detail),
                text(value.description),
            )
                .setTitle(text(value.title))
                .setTapAction(openAppPendingIntent())
                .build()

            // 圆形 / 条形信息位会请求 RANGED_VALUE，必须返回同类型数据，否则槽位空白。
            ComplicationType.RANGED_VALUE -> rangedValueData(value)

            else -> NoDataComplicationData()
        }

    /**
     * RANGED_VALUE 构造，仅用于预览/极少数仍请求该类型的表盘：有真实上限按 min/max/value 画环，
     * 无上限用退化区间。**退化区间不能消弧**（实机已证）；要消弧只能撤销声明改请求文本。
     */
    private fun rangedValueData(value: ComplicationValue): ComplicationData {
        val ceiling = value.ceiling?.takeIf { it > 0.0 }
        val min = 0f
        val max = ceiling?.toFloat() ?: 0f
        val numeric = if (ceiling != null) {
            value.numeric.toFloat().coerceIn(min, max)
        } else {
            0f
        }
        return RangedValueComplicationData.Builder(
            numeric,
            min,
            max,
            text(value.description),
        )
            // 只给紧凑数值、不设标题：弧形槽极窄，设 title 会被截成「Deep…」。
            .setText(text(shortComplicationText(value.display)))
            .setTapAction(openAppPendingIntent())
            .build()
    }

    private fun text(raw: String): ComplicationText = PlainComplicationText.Builder(raw).build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun placeholderValue(): ComplicationValue {
        val label = runCatching { serviceLabel() }.getOrDefault(PLACEHOLDER_TEXT)
        return ComplicationValue(
            title = label,
            display = PLACEHOLDER_TEXT,
            detail = runCatching { getString(R.string.common_never) }.getOrDefault(""),
            description = label + " 暂无数据",
            numeric = 0.0,
            ceiling = null,
        )
    }

    private fun previewValue(): ComplicationValue {
        val label = runCatching { serviceLabel() }.getOrDefault(PLACEHOLDER_TEXT)
        return ComplicationValue(
            title = label,
            display = PREVIEW_DISPLAY,
            detail = "5 分钟前 同步",
            description = label + " 预览数据",
            numeric = PREVIEW_NUMERIC,
            ceiling = null,
        )
    }

    private companion object {
        const val PREVIEW_DISPLAY = "12.3K"
        const val PREVIEW_NUMERIC = 42.0
    }
}
