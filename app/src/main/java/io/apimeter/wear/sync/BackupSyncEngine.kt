package io.apimeter.wear.sync

import io.apimeter.wear.crypto.BackupCredential
import io.apimeter.wear.crypto.BackupCrypto
import io.apimeter.wear.crypto.BackupPayload
import io.apimeter.wear.crypto.BackupSettingsSnapshot
import io.apimeter.wear.storage.SettingsRepository
import io.apimeter.wear.storage.SecretStore
import io.apimeter.wear.storage.WebDavConfig
import io.apimeter.wear.webdav.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 同步过程中的可预期失败（配置错、恢复码错、服务器拒绝等）。 */
class BackupSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 一次同步的结果。 */
sealed interface BackupSyncResult {
    /** 服务器上原本没有文件，这次是首次上传。 */
    data class Created(val revision: Long, val credentials: Int) : BackupSyncResult

    /** 拉取并合并了远端内容，且本地有新内容写回。 */
    data class Merged(val revision: Long, val credentials: Int) : BackupSyncResult

    /** 只是拉取（本地没有可上传的新内容）。 */
    data class Downloaded(val revision: Long, val credentials: Int) : BackupSyncResult
}

/**
 * WebDAV 备份同步：先拉取 → 解密 → 防回滚校验 → 合并（凭据取并集、设置以远端为准）→ 有新内容才带 ETag 回写。
 * 解密失败即为恢复码错误，绝不覆盖远端。
 */
class BackupSyncEngine(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val deviceName: String = "",
) {

    suspend fun sync(
        config: WebDavConfig,
        password: String,
        recoveryCode: String,
    ): BackupSyncResult = withContext(Dispatchers.IO) {
        validateInputs(config, password, recoveryCode)

        val client = WebDavClient(
            fileUrl = config.fileUrl.trim(),
            username = config.username.trim(),
            password = password,
            allowInsecureTls = config.allowInsecureTls,
            allowInsecureHttp = config.allowInsecureHttp,
        )
        client.validate()?.let { throw BackupSyncException(it) }

        try {
            client.ensureCollection()
            val now = clock()
            val localState = settings.state.value
            val remoteDocument = client.read()

            if (remoteDocument == null) {
                val payload = buildLocalPayload(localState, revision = localState.backupRevision + 1, now = now)
                client.write(seal(payload, recoveryCode), etag = null)
                settings.update { it.copy(backupRevision = payload.revision) }
                return@withContext BackupSyncResult.Created(payload.revision, payload.credentials.size)
            }

            val remotePayload = BackupCrypto.decodePayload(
                BackupCrypto.open(remoteDocument.text, recoveryCode),
            )

            if (remotePayload.revision < localState.backupRevision) {
                throw BackupSyncException(
                    "服务器上的备份比本机记录的版本更旧（可能被回滚）。为避免覆盖较新的数据，已中止。",
                )
            }

            val localPayload = buildLocalPayload(localState, revision = localState.backupRevision, now = now)
            val merged = BackupMerge.merge(
                local = localPayload,
                remote = remotePayload,
                now = now,
                writtenBy = deviceName,
            )
            applyMerged(merged.payload)

            if (merged.localContributions) {
                val next = merged.payload.copy(revision = merged.payload.revision + 1, writtenAt = now)
                client.write(seal(next, recoveryCode), etag = remoteDocument.etag)
                settings.update { it.copy(backupRevision = next.revision) }
                BackupSyncResult.Merged(next.revision, next.credentials.size)
            } else {
                settings.update { it.copy(backupRevision = merged.payload.revision) }
                BackupSyncResult.Downloaded(merged.payload.revision, merged.payload.credentials.size)
            }
        } catch (sync: BackupSyncException) {
            throw sync
        } catch (crypto: io.apimeter.wear.crypto.BackupCryptoException) {
            throw BackupSyncException(crypto.message ?: "解密失败", crypto)
        } catch (dav: io.apimeter.wear.webdav.WebDavException) {
            throw BackupSyncException(dav.message ?: "WebDAV 请求失败", dav)
        }
    }

    private fun validateInputs(config: WebDavConfig, password: String, recoveryCode: String) {
        if (config.fileUrl.isBlank()) throw BackupSyncException("请先填写 WebDAV 文件地址")
        if (config.username.isBlank()) throw BackupSyncException("请填写 WebDAV 用户名")
        if (password.isEmpty()) throw BackupSyncException("请填写 WebDAV 密码")
        if (recoveryCode.isBlank()) throw BackupSyncException("请先设置恢复码")
        if (BackupCrypto.parseRecoveryCode(recoveryCode) == null) {
            throw BackupSyncException("恢复码格式不对")
        }
    }

    private fun seal(payload: BackupPayload, recoveryCode: String): String =
        BackupCrypto.seal(BackupCrypto.encodePayload(payload), recoveryCode)

    private fun buildLocalPayload(
        state: io.apimeter.wear.storage.AppSettings,
        revision: Long,
        now: Long,
    ): BackupPayload {
        val credentials = state.credentials.map { meta ->
            BackupCredential(meta = meta, secrets = secrets.get(meta.id).orEmpty())
        }
        return BackupPayload(
            revision = revision,
            writtenAt = now,
            writtenBy = deviceName,
            settings = BackupSettingsSnapshot(
                hapticsEnabled = state.hapticsEnabled,
                backgroundSync = state.backgroundSync,
                refreshIntervalMinutes = state.refreshIntervalMinutes,
                rangePreset = state.rangePreset,
                focusCredentialId = state.focusCredentialId,
                focusMetric = state.focusMetric,
                proxyMode = state.proxy.mode,
                proxyHost = state.proxy.host,
                proxyPort = state.proxy.port,
            ),
            credentials = credentials,
        )
    }

    private suspend fun applyMerged(payload: BackupPayload) {
        // 先写密钥，再写元数据：中途失败时不会出现“有元数据却没有密钥”的坏状态。
        payload.credentials.forEach { credential ->
            if (credential.secrets.isNotEmpty()) {
                secrets.put(credential.meta.id, credential.secrets)
            }
        }
        val snapshot = payload.settings
        settings.update { before ->
            val ids = payload.credentials.map { it.meta.id }.toSet()
            before.copy(
                credentials = payload.credentials.map { it.meta },
                hapticsEnabled = snapshot.hapticsEnabled,
                backgroundSync = snapshot.backgroundSync,
                refreshIntervalMinutes = snapshot.refreshIntervalMinutes,
                rangePreset = snapshot.rangePreset,
                focusCredentialId = snapshot.focusCredentialId?.takeIf { it in ids }
                    ?: ids.firstOrNull(),
                focusMetric = snapshot.focusMetric,
                proxy = before.proxy.copy(
                    mode = snapshot.proxyMode,
                    host = snapshot.proxyHost,
                    port = snapshot.proxyPort,
                ),
            )
        }
    }
}
