package io.apimeter.wear.tile

import android.content.Context
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import io.apimeter.wear.R
import io.apimeter.wear.complication.accentColorOf
import io.apimeter.wear.complication.awaitUsageState
import io.apimeter.wear.complication.compactDisplay
import io.apimeter.wear.complication.lastSyncMillis
import io.apimeter.wear.complication.providerLabel
import io.apimeter.wear.complication.quotaDisplay
import io.apimeter.wear.complication.syncLine
import io.apimeter.wear.data.UsageUiState

/** 一屏最多展示 4 行，超出的用 “+N” 收起（手表 Tile 高度有限）。 */
private const val MAX_OVERVIEW_ROWS = 4

/** 总览 Tile：按圆形安全区排版，整体垂直居中，标题居中、数据行左右对齐，Material 3 层次。 */
class OverviewTileService : BaseTileService() {

    override suspend fun buildTile(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val state = awaitUsageState(this)
        return tileOf(overviewLayout(this, state))
    }
}

private fun overviewLayout(
    context: Context,
    state: UsageUiState?,
): LayoutElementBuilders.LayoutElement {
    val column = centeredColumn()
    column.addContent(
        textElement(
            text = context.getString(R.string.tile_overview_label),
            sizeSp = 15f,
            bold = true,
            center = true,
        ),
    )
    column.addContent(
        textElement(
            text = syncLine(context, lastSyncMillis(state)),
            sizeSp = 11f,
            color = TILE_TEXT_MUTED,
            center = true,
        ),
    )

    val credentials = state?.credentials.orEmpty()
    if (credentials.isEmpty()) {
        column.addContent(verticalSpacer(8f))
        column.addContent(divider())
        column.addContent(verticalSpacer(8f))
        column.addContent(
            textElement("还没有添加 API Key", 12f, TILE_TEXT_SECONDARY, center = true),
        )
        column.addContent(textElement("打开应用添加", 10f, TILE_TEXT_MUTED, center = true))
        return tileRoot(context, context.getString(R.string.app_name) + "，还没有添加 API Key", column.build())
    }

    column.addContent(verticalSpacer(8f))
    column.addContent(divider())
    column.addContent(verticalSpacer(6f))

    val snapshots = state?.snapshots.orEmpty()
    val visible = credentials.take(MAX_OVERVIEW_ROWS)
    // 和 App 用同一套强调色规则，避免 Tile 与 App 撞色
    val accent = accentColorOf(context, state)
    visible.forEachIndexed { index, meta ->
        val snapshot = snapshots[meta.id]
        val usable = snapshot != null && snapshot.isUsable
        column.addContent(
            metricRow(
                label = providerLabel(context, meta.providerKey, short = true),
                value = compactDisplay(snapshot) + quotaDisplay(snapshot, meta.quotaAmount),
                valueColor = when {
                    usable -> accent
                    else -> TILE_TEXT_MUTED
                },
            ),
        )
        if (index != visible.lastIndex) {
            column.addContent(verticalSpacer(4f))
        }
    }

    val remaining = credentials.size - MAX_OVERVIEW_ROWS
    if (remaining > 0) {
        column.addContent(verticalSpacer(2f))
        column.addContent(
            textElement("还有 $remaining 个", 10f, TILE_TEXT_MUTED, center = true),
        )
    }

    val summary = visible.joinToString("，") { meta ->
        providerLabel(context, meta.providerKey, short = true) +
            " " + compactDisplay(snapshots[meta.id])
    }
    return tileRoot(
        context,
        context.getString(R.string.tile_overview_label) + "，$summary",
        column.build(),
    )
}
