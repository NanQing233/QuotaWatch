package io.apimeter.wear.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.apimeter.wear.core.DayPoint
import io.apimeter.wear.ui.WearHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicLong

/** 页面骨架：AppScaffold(顶层 TimeText) → ScreenScaffold → ScalingLazyColumn；TimeText 只能由顶层 AppScaffold 渲染，单独用 ScreenScaffold 不显示时间。 */
@Composable
fun WearScreen(
    haptics: WearHaptics,
    content: ScalingLazyListScope.() -> Unit,
) {
    val state: ScalingLazyListState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val lastRotaryAt = remember { AtomicLong(0L) }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // 触感只在表冠/表圈滚动时触发（记录最近一次旋转时间），手指滑动不震。
    LaunchedEffect(state, haptics.enabled) {
        if (!haptics.enabled) return@LaunchedEffect
        snapshotFlow { state.centerItemIndex }
            .distinctUntilChanged()
            .collect {
                val sinceRotary = nowMillis() - lastRotaryAt.get()
                if (sinceRotary in 0..ROTARY_HAPTIC_WINDOW_MILLIS) haptics.tick()
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreRotaryScrollEvent {
                lastRotaryAt.set(nowMillis())
                false
            },
    ) {
        ScreenScaffold(scrollState = state) { padding ->
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
                state = state,
                contentPadding = padding,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

private fun nowMillis(): Long = System.nanoTime() / 1_000_000L

private const val ROTARY_HAPTIC_WINDOW_MILLIS = 400L

/** 表单页骨架：Column + verticalScroll（非惰性，键盘弹出不会重建输入项）；列表页仍用 WearScreen 的 ScalingLazyColumn。 */
@Composable
fun WearFormScreen(
    haptics: WearHaptics,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(
        scrollableState = scrollState,
        hapticFeedbackEnabled = haptics.enabled,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .rotaryScrollable(behavior = rotaryBehavior, focusRequester = focusRequester)
                .padding(horizontal = 12.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
        ScrollIndicator(
            state = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp),
        )
    }
}

// 按钮三档标准变体：Button(主要,实心)/FilledTonalButton(次要)/OutlinedButton(一般)；整宽药丸，标签允许两行——圆屏约 176dp 宽，强制单行会截成省略号。

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        Text(text = text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        Text(text = text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun OutlinedAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        Text(text = text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** 单条「标签 + 数值」行，用于详情页的明细项。 */
@Composable
fun MetricRow(label: String, value: String, emphasis: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Text(
            text = value,
            style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// Card 有 64dp 最小高度且内容列 SpaceBetween，只放一行标题会上下留白不对称，故前后各塞零高度 Spacer 居中（不能用 weight(1f)，会把多行副标题裁掉）。
@Composable
fun InfoCard(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            if (footnote != null) {
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier)
    }
}

/** 状态灯：圆点表示该凭据同步成功 / 部分成功 / 失败 / 未同步。 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(8.dp)) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}

/** 近 N 天用量柱状图。圆屏宽度有限，只用纯色柱 + 最后一天高亮。 */
@Composable
fun DailyBars(
    daily: List<DayPoint>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (daily.size < 2) return
    val maxValue = daily.maxOfOrNull { it.tokens.coerceAtLeast(0L) } ?: 0L
    val dimColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.fillMaxWidth().height(44.dp)) {
        if (maxValue <= 0L) return@Canvas
        val slot = size.width / daily.size
        val barWidth = slot * 0.55f
        daily.forEachIndexed { index, point ->
            val ratio = (point.tokens.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
            val barHeight = (size.height - 2f).coerceAtLeast(1f) * ratio
            if (barHeight > 0f) {
                val left = index * slot + (slot - barWidth) / 2f
                drawRect(
                    color = if (index == daily.lastIndex) barColor else dimColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
    }
}