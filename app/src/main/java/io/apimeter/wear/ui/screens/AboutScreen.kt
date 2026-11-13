package io.apimeter.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.apimeter.wear.BuildConfig
import io.apimeter.wear.R
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.components.Caption
import io.apimeter.wear.ui.components.SectionHeader
import io.apimeter.wear.ui.components.WearFormScreen

/** 「关于应用」：版本、备份加密说明、版权与开源协议。 */
@Composable
fun AboutScreen(haptics: WearHaptics) {
    WearFormScreen(haptics = haptics) {
        ListHeader {
            Text(text = "关于应用", style = MaterialTheme.typography.titleSmall)
        }
        Caption(text = stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
        Body("早期预览版本，功能与界面仍可能变动。")

        SectionHeader(text = "备份加密")
        Body("先用 scrypt 派生密钥、AES-256-GCM 加密再上传，服务器上只有密文。")
        Body("恢复码只保存在本机 Keystore，丢失后无法解开已有备份。")

        Caption(text = "© 南清 · MIT Licence")
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    )
}