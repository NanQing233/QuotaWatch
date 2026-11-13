package io.apimeter.wear.crypto

import io.apimeter.wear.core.FocusMetric
import io.apimeter.wear.core.RangePreset
import io.apimeter.wear.net.ProxyMode
import io.apimeter.wear.storage.CredentialMeta
import kotlinx.serialization.Serializable

/** 服务器上存放的密文容器：仅 KDF/AEAD 参数为明文，头部整体参与 AAD，version 用于后续升级。 */
@Serializable
data class BackupEnvelope(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val kdf: BackupKdfSpec,
    val aead: BackupAeadSpec,
    /** base64(ciphertext || GCM tag) */
    val ciphertext: String,
) {
    companion object {
        const val FORMAT = "apimeter-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupKdfSpec(
    val algo: String,
    val n: Int,
    val r: Int,
    val p: Int,
    /** base64，16 字节 */
    val salt: String,
)

@Serializable
data class BackupAeadSpec(
    val algo: String,
    /** base64，12 字节 */
    val nonce: String,
)

/** 解密后的明文负载（只在内存里存在）。 */
@Serializable
data class BackupPayload(
    /** 单调递增，用于识别服务器回滚。 */
    val revision: Long,
    val writtenAt: Long,
    /** 写入方标识（例如设备型号），仅用于排错。 */
    val writtenBy: String = "",
    val settings: BackupSettingsSnapshot,
    val credentials: List<BackupCredential> = emptyList(),
)

@Serializable
data class BackupSettingsSnapshot(
    val hapticsEnabled: Boolean = true,
    val backgroundSync: Boolean = true,
    val refreshIntervalMinutes: Int = 30,
    val rangePreset: RangePreset = RangePreset.TODAY,
    val focusCredentialId: String? = null,
    val focusMetric: FocusMetric = FocusMetric.TODAY_TOKENS,
    val proxyMode: ProxyMode = ProxyMode.AUTO,
    val proxyHost: String = "",
    val proxyPort: Int = 7890,
)

/** 一条凭据：元数据 + 密钥明文。 */
@Serializable
data class BackupCredential(
    val meta: CredentialMeta,
    val secrets: Map<String, String> = emptyMap(),
)
