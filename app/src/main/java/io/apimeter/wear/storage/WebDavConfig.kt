package io.apimeter.wear.storage

import kotlinx.serialization.Serializable

/** WebDAV 配置。只放非敏感信息：密码与恢复码走 [SecretStore]（Keystore 加密）。 */
@Serializable
data class WebDavConfig(
    val enabled: Boolean = false,
    val fileUrl: String = "",
    val username: String = "",
    /** 用户自建 NAS 常用自签名证书；默认不信任。 */
    val allowInsecureTls: Boolean = false,
    /** 明文 http 只应出现在局域网调试；默认拒绝。 */
    val allowInsecureHttp: Boolean = false,
) {
    val configured: Boolean get() = fileUrl.isNotBlank()

    fun describe(): String = when {
        !configured -> "未配置"
        !enabled -> "已配置（未启用）"
        else -> fileUrl.substringAfter("://").take(28)
    }
}

/** [SecretStore] 里备份相关条目的固定 key。 */
object BackupSecrets {
    /** 复用按 id 存放的保险箱条目。 */
    const val ID = "__apimeter_backup__"
    const val PASSWORD = "webdav_password"
    const val RECOVERY_CODE = "recovery_code"
}
