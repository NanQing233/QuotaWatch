package io.apimeter.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** Wear 文本输入：单行、圆角容器、光标高亮，密钥字段默认打码；官方建议尽量避免键盘输入，但 API Key 只能手输。 */
@Composable
fun WearTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secret: Boolean = false,
    number: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                visualTransformation =
                    if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}
