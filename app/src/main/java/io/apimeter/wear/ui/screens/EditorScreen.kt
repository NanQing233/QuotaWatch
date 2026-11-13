package io.apimeter.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import io.apimeter.wear.core.CredentialField
import io.apimeter.wear.providers.ProviderDescriptor
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.components.Caption
import io.apimeter.wear.ui.components.OutlinedAction
import io.apimeter.wear.ui.components.PrimaryAction
import io.apimeter.wear.ui.components.WearFormScreen
import io.apimeter.wear.ui.components.WearTextField

/** 添加 / 编辑凭据：字段由各厂商 CredentialField 动态生成，新增厂商只需在 adapter 声明字段；用 WearFormScreen 非惰性滚动，键盘弹出不打断输入。 */
@Composable
fun EditorScreen(
    descriptor: ProviderDescriptor,
    fields: List<CredentialField>,
    initialLabel: String,
    initialBaseUrl: String,
    initialQuota: Double,
    initialValues: Map<String, String>,
    editing: Boolean,
    enabled: Boolean,
    /** 只有「有用量明细」的厂商才需要额度上限；余额类填了也没有意义。 */
    showQuota: Boolean,
    haptics: WearHaptics,
    onSave: (label: String, baseUrl: String, quotaAmount: Double, values: Map<String, String>) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var quota by remember {
        mutableStateOf(
            when {
                initialQuota <= 0.0 -> ""
                initialQuota == initialQuota.toLong().toDouble() -> initialQuota.toLong().toString()
                else -> initialQuota.toString()
            },
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { field ->
                put(field.key, initialValues[field.key] ?: field.defaultValue)
            }
        }
    }

    WearFormScreen(haptics = haptics) {
        ListHeader {
            Text(
                text = if (editing) "编辑凭据" else "添加凭据",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Caption(text = stringResource(descriptor.labelRes))
        Caption(text = stringResource(descriptor.noteRes))

        fields.forEach { field ->
            WearTextField(
                value = values[field.key].orEmpty(),
                onValueChange = { input -> values[field.key] = input },
                label = stringResource(field.labelRes) + if (field.required) " *" else "",
                placeholder = if (field.secret) "粘贴密钥" else "可选",
                secret = field.secret,
            )
        }

        WearTextField(
            value = label,
            onValueChange = { label = it },
            label = "备注名称（可选）",
            placeholder = "例如：主力账号",
        )

        WearTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = "自定义 Base URL（可选）",
            placeholder = descriptor.defaultBaseUrl,
        )

        // 额度上限：只有填了它，表盘的进度圈 / 条形进度才有真实含义（套餐、额度包场景）。
        if (showQuota) {
            WearTextField(
                value = quota,
                onValueChange = { input ->
                    quota = input.filter { it.isDigit() || it == '.' }.take(15)
                },
                label = "额度上限（Token，可选）",
                placeholder = "留空表示没有上限",
                number = true,
            )
        }

        if (editing) {
            SwitchButton(
                checked = enabled,
                onCheckedChange = { next ->
                    haptics.tick()
                    onToggleEnabled(next)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "启用此凭据") },
            )
        }

        error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        PrimaryAction(
            text = if (editing) "保存修改" else "保存并查询",
            onClick = {
                val missing = fields.firstOrNull { field ->
                    field.required && values[field.key].orEmpty().isBlank()
                }
                if (missing != null) {
                    error = "请填写：" + context.getString(missing.labelRes)
                    haptics.reject()
                } else {
                    error = null
                    haptics.confirm()
                    val payload = buildMap {
                        fields.forEach { field ->
                            val value = values[field.key].orEmpty().trim()
                            if (value.isNotEmpty()) put(field.key, value)
                        }
                    }
                    onSave(label.trim(), baseUrl.trim(), quota.toDoubleOrNull() ?: 0.0, payload)
                }
            },
        )

        if (onDelete != null) {
            OutlinedAction(
                text = "删除此凭据",
                onClick = {
                    haptics.reject()
                    onDelete()
                },
            )
        }
    }
}
