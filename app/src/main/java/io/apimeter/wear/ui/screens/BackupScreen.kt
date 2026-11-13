package io.apimeter.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import io.apimeter.wear.AppContainer
import io.apimeter.wear.data.UsageUiState
import io.apimeter.wear.storage.WebDavConfig
import io.apimeter.wear.sync.BackupSyncResult
import io.apimeter.wear.ui.WearHaptics
import io.apimeter.wear.ui.components.Caption
import io.apimeter.wear.ui.components.InfoCard
import io.apimeter.wear.ui.components.PrimaryAction
import io.apimeter.wear.ui.components.SectionHeader
import io.apimeter.wear.ui.components.WearFormScreen
import io.apimeter.wear.ui.components.WearTextField
import io.apimeter.wear.webdav.WebDavClient
import kotlinx.coroutines.launch

/** WebDAV 备份页：恢复码 + 服务器配置 + 保存并同步；唯一直接持有 AppContainer 的页面（需读写 Keystore 并跑网络同步）；加密为 scrypt + AES-256-GCM，服务器上只有密文，恢复码不上传、不进日志。 */
@Composable
fun BackupScreen(
    ui: UsageUiState,
    haptics: WearHaptics,
    container: AppContainer,
) {
    val scope = rememberCoroutineScope()
    val config = ui.settings.webdav

    var fileUrl by remember { mutableStateOf(config.fileUrl) }
    var username by remember { mutableStateOf(config.username) }
    var password by remember { mutableStateOf(container.backupPassword()) }
    var recoveryCode by remember { mutableStateOf(container.backupRecoveryCode()) }
    var enabled by remember { mutableStateOf(config.enabled) }
    var allowTls by remember { mutableStateOf(config.allowInsecureTls) }
    var allowHttp by remember { mutableStateOf(config.allowInsecureHttp) }
    var showRecovery by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun currentConfig() = WebDavConfig(
        enabled = enabled,
        fileUrl = fileUrl.trim(),
        username = username.trim(),
        allowInsecureTls = allowTls,
        allowInsecureHttp = allowHttp,
    )

    /** 保存配置 + 跑一次同步：配置先落盘再同步，失败时用户刚填的地址密码不丢，改一下再点一次即可。 */
    fun saveAndSync() {
        if (busy) return
        val snapshot = currentConfig()
        val badConfig = WebDavClient(
            fileUrl = snapshot.fileUrl,
            username = snapshot.username,
            password = password.trim(),
            allowInsecureTls = snapshot.allowInsecureTls,
            allowInsecureHttp = snapshot.allowInsecureHttp,
        ).validate()
        if (badConfig != null) {
            message = badConfig
            isError = true
            haptics.reject()
            return
        }
        busy = true
        message = null
        haptics.tick()
        scope.launch {
            try {
                container.saveBackupSecrets(password.trim(), recoveryCode.trim())
                container.usageStore.setWebDav(snapshot)
                val result = container.backup.sync(
                    snapshot,
                    password.trim(),
                    recoveryCode.trim(),
                )
                message = describe(result)
                isError = false
                haptics.confirm()
                // 导入进来的凭据要立刻查一次，否则首页全是“尚未同步”。
                container.usageStore.refreshAll(force = true)
                container.refreshSurfaces()
            } catch (failure: Exception) {
                message = failure.message ?: "同步失败"
                isError = true
                haptics.reject()
            } finally {
                busy = false
            }
        }
    }

    WearFormScreen(haptics = haptics) {
        ListHeader {
            Text(text = "WebDAV 备份", style = MaterialTheme.typography.titleSmall)
        }
        Caption(
            text = (if (config.configured) "已配置" else "未配置") +
                " · 备份版本 " + ui.settings.backupRevision,
        )

        SectionHeader(text = "恢复码")
        if (recoveryCode.isBlank()) {
            InfoCard(
                title = "还没有恢复码",
                subtitle = "转移 API Key 时需使用恢复码解密",
                onClick = {
                    haptics.confirm()
                    recoveryCode = container.ensureRecoveryCode()
                    showRecovery = true
                },
            )
        } else {
            InfoCard(
                title = if (showRecovery) "点一下隐藏" else "点一下显示",
                subtitle = "转移 API Key 时需使用恢复码解密",
                onClick = { haptics.tick(); showRecovery = !showRecovery },
            )
            if (showRecovery) {
                Text(
                    text = recoveryCode,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Caption(text = "抄下来妥善保存：丢了就解不开已有备份")
            }
        }

        SectionHeader(text = "服务器")
        WearTextField(
            value = fileUrl,
            onValueChange = { fileUrl = it },
            label = "备份文件地址",
            placeholder = "https://dav.example.com/…/backup.json",
        )
        WearTextField(
            value = username,
            onValueChange = { username = it },
            label = "用户名",
            placeholder = "WebDAV 账号",
        )
        WearTextField(
            value = password,
            onValueChange = { password = it },
            label = "密码",
            placeholder = "WebDAV 密码",
            secret = true,
        )

        SwitchButton(
            checked = enabled,
            onCheckedChange = { next ->
                haptics.tick()
                enabled = next
            },
            modifier = Modifier.fillMaxWidth(),
            // 圆屏 384px 下标签只放得下约 6 个汉字，长标签会把「同步」挤到第二行，故用「启动时同步」。
            label = { Text(text = "启动时同步") },
        )

        SectionHeader(text = "高级")
        SwitchButton(
            checked = allowTls,
            onCheckedChange = { next ->
                haptics.tick()
                allowTls = next
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "信任自签名证书") },
        )
        SwitchButton(
            checked = allowHttp,
            onCheckedChange = { next ->
                haptics.tick()
                allowHttp = next
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "允许 http（不安全）") },
        )
        if (allowTls || allowHttp) {
            Text(
                text = "已降低传输安全，仅建议在自己的局域网内使用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PrimaryAction(
            text = if (busy) "同步中…" else "保存并同步",
            onClick = { saveAndSync() },
            enabled = !busy,
        )
    }
}

private fun describe(result: BackupSyncResult): String = when (result) {
    is BackupSyncResult.Created ->
        "已创建备份（v${result.revision}，${result.credentials} 个凭据）"
    is BackupSyncResult.Merged ->
        "已合并并上传（v${result.revision}，${result.credentials} 个凭据）"
    is BackupSyncResult.Downloaded ->
        "已从服务器拉取（v${result.revision}，${result.credentials} 个凭据）"
}