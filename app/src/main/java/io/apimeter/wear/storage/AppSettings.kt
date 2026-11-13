package io.apimeter.wear.storage

import io.apimeter.wear.core.FocusMetric
import io.apimeter.wear.core.RangePreset
import io.apimeter.wear.net.ProxyConfig
import kotlinx.serialization.Serializable

@Serializable
data class CredentialMeta(
    val id: String,
    val providerKey: String,
    val label: String,
    val baseUrl: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** 额度上限（可选，0 = 未设置）；设置后信息位与 Tile 以「已用 / 上限」文本显示。 */
    val quotaAmount: Double = 0.0,
)

@Serializable
data class AppSettings(
    val credentials: List<CredentialMeta> = emptyList(),
    val proxy: ProxyConfig = ProxyConfig(),
    val hapticsEnabled: Boolean = true,
    val backgroundSync: Boolean = true,
    val refreshIntervalMinutes: Int = 30,
    val rangePreset: RangePreset = RangePreset.TODAY,
    val focusCredentialId: String? = null,
    val focusMetric: FocusMetric = FocusMetric.TODAY_TOKENS,
    val onboarded: Boolean = false,
    /** WebDAV 备份配置（不含密码，密码在 SecretStore）。 */
    val webdav: WebDavConfig = WebDavConfig(),
    /** 上一次成功同步的版本号，用于识别服务器回滚。 */
    val backupRevision: Long = 0L,
    /** 手动取色；关闭时跟随系统动态取色（取不到用 Wear 默认淡蓝）。 */
    val manualAccent: Boolean = false,
    /** 手动主色的 ARGB；0 表示未设置。 */
    val accentArgb: Int = 0,
) {
    fun credential(id: String?): CredentialMeta? = if (id == null) null else credentials.firstOrNull { it.id == id }

    fun enabledCredentials(): List<CredentialMeta> = credentials.filter { it.enabled }
}
