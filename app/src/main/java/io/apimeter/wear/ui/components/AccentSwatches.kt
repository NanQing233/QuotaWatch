package io.apimeter.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.theme.AccentPalettes

/** 主色选择：一排预设色点（圆屏上色点比拖色环更好按）；选中项加描边。 */
@Composable
fun AccentSwatches(
    selectedArgb: Int,
    onPick: (Int) -> Unit,
    haptics: WearHaptics,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentPalettes.presets.forEach { (_, argb) ->
            val selected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable {
                        haptics.tick()
                        onPick(argb)
                    },
            )
        }
    }
}