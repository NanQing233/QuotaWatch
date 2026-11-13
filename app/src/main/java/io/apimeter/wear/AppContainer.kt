package io.apimeter.wear

import android.content.Context
import android.os.Build
import io.apimeter.wear.complication.ComplicationRefresher
import io.apimeter.wear.crypto.BackupCrypto
import io.apimeter.wear.data.SnapshotCache
import io.apimeter.wear.data.UsageStore
import io.apimeter.wear.net.HttpEngine
import io.apimeter.wear.net.OkHttpEngine
import io.apimeter.wear.net.ProxyProber
import io.apimeter.wear.providers.AnthropicAdapter
import io.apimeter.wear.providers.DeepSeekAdapter
import io.apimeter.wear.providers.MoonshotAdapter
import io.apimeter.wear.providers.OpenAiAdapter
import io.apimeter.wear.providers.ProviderRegistry
import io.apimeter.wear.providers.TencentTokenHubAdapter
import io.apimeter.wear.providers.VolcArkAdapter
import io.apimeter.wear.storage.BackupSecrets
import io.apimeter.wear.storage.SecretStore
import io.apimeter.wear.storage.SettingsRepository
import io.apimeter.wear.sync.BackupSyncEngine
import io.apimeter.wear.sync.UsageSync
import io.apimeter.wear.tile.TileRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** 手写依赖注入容器：全进程一个实例，界面 / 表盘 / Tile 共用同一份状态。 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository = SettingsRepository(appContext, scope)
    val secrets: SecretStore = SecretStore(appContext)
    val http: HttpEngine = OkHttpEngine(proxySource = { settings.state.value.proxy })
    val registry: ProviderRegistry = ProviderRegistry(
        listOf(
            OpenAiAdapter(),
            AnthropicAdapter(),
            VolcArkAdapter(),
            TencentTokenHubAdapter(),
            DeepSeekAdapter(),
            MoonshotAdapter(),
        ),
    )
    val cache: SnapshotCache = SnapshotCache(appContext)
    val prober: ProxyProber = ProxyProber()
    val usageStore: UsageStore = UsageStore(
        settings = settings,
        secrets = secrets,
        registry = registry,
        cache = cache,
        http = http,
        prober = prober,
        scope = scope,
        stringProvider = { resId -> runCatching { appContext.getString(resId) }.getOrDefault("") },
    )

    val backup: BackupSyncEngine = BackupSyncEngine(
        settings = settings,
        secrets = secrets,
        deviceName = runCatching { Build.MODEL }.getOrDefault("wear"),
    )

    fun backupPassword(): String =
        runCatching { secrets.get(BackupSecrets.ID)?.get(BackupSecrets.PASSWORD) }.getOrNull().orEmpty()

    fun backupRecoveryCode(): String =
        runCatching { secrets.get(BackupSecrets.ID)?.get(BackupSecrets.RECOVERY_CODE) }.getOrNull().orEmpty()

    fun saveBackupSecrets(password: String, recoveryCode: String) {
        val payload = buildMap {
            if (password.isNotEmpty()) put(BackupSecrets.PASSWORD, password)
            if (recoveryCode.isNotEmpty()) put(BackupSecrets.RECOVERY_CODE, recoveryCode)
        }
        if (payload.isEmpty()) return
        secrets.put(BackupSecrets.ID, payload)
    }

    /** 已有则原样返回；刻意不提供重置——改了它，服务器上已有密文就再也解不开。 */
    fun ensureRecoveryCode(): String {
        val existing = backupRecoveryCode()
        if (existing.isNotBlank()) return existing
        val code = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        saveBackupSecrets(backupPassword(), code)
        return code
    }

    fun start() {
        // 刷新完主动通知表盘与 Tile，否则要等系统下一次轮询。
        usageStore.onDataRefreshed = { refreshSurfaces() }
        scope.launch {
            settings.state.collect { value -> UsageSync.ensureScheduled(appContext, value) }
        }
        scope.launch { autoBackupIfEnabled() }
    }

    /** 启动后延迟几秒自动同步一次（避开首屏刷新），失败静默；不做常驻后台。 */
    private suspend fun autoBackupIfEnabled() {
        kotlinx.coroutines.delay(AUTO_BACKUP_DELAY_MILLIS)
        val state = settings.state.value
        val config = state.webdav
        if (!config.enabled || !config.configured || state.credentials.isEmpty()) return
        val password = backupPassword()
        val recovery = backupRecoveryCode()
        if (password.isBlank() || recovery.isBlank()) return
        runCatching {
            backup.sync(config, password, recovery)
            usageStore.refreshAll(force = true)
        }
    }

    fun scheduleSyncNow() = UsageSync.ensureScheduled(appContext, settings.state.value)

    /** 通知所有表盘 complication 与 Tile 重新渲染。 */
    fun refreshSurfaces() {
        runCatching { ComplicationRefresher.requestUpdates(appContext) }
        runCatching { TileRefresher.requestUpdates(appContext) }
    }

    private companion object {
        const val AUTO_BACKUP_DELAY_MILLIS = 4_000L
    }
}
