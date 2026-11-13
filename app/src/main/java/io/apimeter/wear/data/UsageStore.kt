package io.apimeter.wear.data

import io.apimeter.wear.core.FocusMetric
import io.apimeter.wear.core.ProviderId
import io.apimeter.wear.core.RangePreset
import io.apimeter.wear.core.TimeRanges
import io.apimeter.wear.core.UsageSnapshot
import io.apimeter.wear.net.HttpEngine
import io.apimeter.wear.net.HttpFailureException
import io.apimeter.wear.net.NetworkUnreachableException
import io.apimeter.wear.net.ProxyConfig
import io.apimeter.wear.net.ProxyProbe
import io.apimeter.wear.net.ProxyProber
import io.apimeter.wear.providers.FetchContext
import io.apimeter.wear.providers.ProviderCatalog
import io.apimeter.wear.providers.ProviderException
import io.apimeter.wear.providers.ProviderRegistry
import io.apimeter.wear.providers.ResolvedCredential
import io.apimeter.wear.storage.AppSettings
import io.apimeter.wear.storage.CredentialMeta
import io.apimeter.wear.storage.SecretStore
import io.apimeter.wear.storage.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.ZoneId
import java.util.UUID

data class UsageUiState(
    val settings: AppSettings = AppSettings(),
    val snapshots: Map<String, UsageSnapshot> = emptyMap(),
    val refreshing: Set<String> = emptySet(),
    val lastRefreshAt: Long = 0L,
    val proxyProbe: ProxyProbe? = null,
    val notice: String? = null,
) {
    val credentials: List<CredentialMeta> get() = settings.credentials

    fun snapshot(credentialId: String?): UsageSnapshot? =
        if (credentialId == null) null else snapshots[credentialId]

    fun snapshotForProvider(providerKey: String): UsageSnapshot? {
        val ids = settings.credentials.filter { it.providerKey == providerKey }.map { it.id }
        return ids.firstNotNullOfOrNull { snapshots[it]?.takeIf { snapshot -> snapshot.isUsable } }
            ?: ids.firstNotNullOfOrNull { snapshots[it] }
    }

    val focusCredential: CredentialMeta?
        get() = settings.credential(settings.focusCredentialId) ?: settings.credentials.firstOrNull()

    val isRefreshing: Boolean get() = refreshing.isNotEmpty()
}

/**
 * 应用唯一数据入口：凭据增删改、刷新、缓存、代理探测都在这里；界面 / 表盘 / Tile 只观察 [state]。
 */
class UsageStore(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val registry: ProviderRegistry,
    private val cache: SnapshotCache,
    private val http: HttpEngine,
    private val prober: ProxyProber,
    private val scope: CoroutineScope,
    private val stringProvider: (Int) -> String = { "" },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    /** 刷新成功后的回调，由 Application 接到表盘 / Tile 刷新上。 */
    var onDataRefreshed: (suspend () -> Unit)? = null

    private val refreshMutex = Mutex()

    init {
        scope.launch {
            settings.state.collect { value ->
                _state.update { it.copy(settings = value) }
            }
        }
        scope.launch { restoreCache() }
    }

    suspend fun refreshAll(force: Boolean = false) {
        val current = _state.value
        val targets = current.settings.enabledCredentials()
        if (targets.isEmpty()) {
            // 没有任何凭据时首页本来就有专门的空状态引导，这里不再重复提示。
            _state.update { it.copy(notice = null) }
            return
        }
        if (!force && current.snapshots.isNotEmpty() && clock() - current.lastRefreshAt < MIN_REFRESH_GAP_MILLIS) {
            return
        }
        refreshMutex.withLock {
            _state.update { it.copy(refreshing = targets.map { meta -> meta.id }.toSet(), notice = null) }
            coroutineScope {
                targets.map { meta -> async { refreshInternal(meta) } }.awaitAll()
            }
            _state.update { it.copy(refreshing = emptySet(), lastRefreshAt = clock()) }
            persist()
            notifySurfaces()
        }
    }

    suspend fun refreshCredential(credentialId: String) {
        val meta = settings.state.value.credentials.firstOrNull { it.id == credentialId } ?: return
        _state.update { it.copy(refreshing = it.refreshing + credentialId) }
        try {
            refreshInternal(meta)
        } finally {
            _state.update { it.copy(refreshing = it.refreshing - credentialId, lastRefreshAt = clock()) }
        }
        persist()
        notifySurfaces()
    }

    suspend fun addCredential(
        providerKey: String,
        label: String,
        baseUrl: String,
        values: Map<String, String>,
        quotaAmount: Double = 0.0,
    ): CredentialMeta {
        val providerId = ProviderId.fromKey(providerKey) ?: throw ProviderException("未知厂商：" + providerKey)
        val now = clock()
        val meta = CredentialMeta(
            id = UUID.randomUUID().toString(),
            providerKey = providerKey,
            label = label.trim().ifBlank { stringProvider(ProviderCatalog.descriptor(providerId).labelRes) },
            baseUrl = baseUrl.trim(),
            enabled = true,
            createdAt = now,
            updatedAt = now,
            quotaAmount = quotaAmount.coerceAtLeast(0.0),
        )
        secrets.put(meta.id, values)
        settings.update { before ->
            before.copy(
                credentials = before.credentials + meta,
                focusCredentialId = before.focusCredentialId ?: meta.id,
            )
        }
        syncState()
        refreshCredential(meta.id)
        return meta
    }

    suspend fun updateCredential(
        credentialId: String,
        label: String,
        baseUrl: String,
        values: Map<String, String>? = null,
        quotaAmount: Double = 0.0,
    ) {
        if (values != null && values.isNotEmpty()) {
            secrets.put(credentialId, values)
        }
        settings.update { before ->
            before.copy(
                credentials = before.credentials.map { meta ->
                    if (meta.id == credentialId) {
                        meta.copy(
                            label = label.trim(),
                            baseUrl = baseUrl.trim(),
                            updatedAt = clock(),
                            quotaAmount = quotaAmount.coerceAtLeast(0.0),
                        )
                    } else {
                        meta
                    }
                },
            )
        }
        syncState()
        refreshCredential(credentialId)
    }

    suspend fun removeCredential(credentialId: String) {
        secrets.remove(credentialId)
        settings.update { before ->
            before.copy(
                credentials = before.credentials.filterNot { it.id == credentialId },
                focusCredentialId = before.focusCredentialId?.takeIf { it != credentialId },
            )
        }
        _state.update { it.copy(snapshots = it.snapshots - credentialId) }
        syncState()
        persist()
        notifySurfaces()
    }

    suspend fun setCredentialEnabled(credentialId: String, enabled: Boolean) {
        settings.update { before ->
            before.copy(
                credentials = before.credentials.map { meta ->
                    if (meta.id == credentialId) meta.copy(enabled = enabled, updatedAt = clock()) else meta
                },
            )
        }
        syncState()
    }

    suspend fun setProxy(config: ProxyConfig) {
        settings.update { it.copy(proxy = config) }
        syncState()
    }

    /** 保存 WebDAV 备份配置（不含密码，密码在 SecretStore）。 */
    suspend fun setWebDav(config: io.apimeter.wear.storage.WebDavConfig) {
        settings.update { it.copy(webdav = config) }
        syncState()
    }

    /** 手动主色开关与色值；关闭开关即回到系统自动取色。 */
    suspend fun setAccent(manual: Boolean, argb: Int) {
        settings.update { it.copy(manualAccent = manual, accentArgb = argb) }
        syncState()
    }

    suspend fun probeProxy() {
        val probe = prober.probe(settings.state.value.proxy)
        _state.update { it.copy(proxyProbe = probe) }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        settings.update { it.copy(hapticsEnabled = enabled) }
        syncState()
    }

    suspend fun setBackgroundSync(enabled: Boolean) {
        settings.update { it.copy(backgroundSync = enabled) }
        syncState()
    }

    suspend fun setRefreshIntervalMinutes(minutes: Int) {
        settings.update { it.copy(refreshIntervalMinutes = minutes.coerceIn(15, 1440)) }
        syncState()
    }

    suspend fun setRangePreset(preset: RangePreset) {
        settings.update { it.copy(rangePreset = preset) }
        syncState()
    }

    suspend fun setFocus(credentialId: String?, metric: FocusMetric = _state.value.settings.focusMetric) {
        settings.update { it.copy(focusCredentialId = credentialId, focusMetric = metric) }
        syncState()
        notifySurfaces()
    }

    suspend fun markOnboarded() {
        settings.update { it.copy(onboarded = true) }
        syncState()
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    private suspend fun refreshInternal(meta: CredentialMeta) {
        val snapshot = fetchSnapshot(meta)
        _state.update { it.copy(snapshots = it.snapshots + (meta.id to snapshot)) }
    }

    private suspend fun fetchSnapshot(meta: CredentialMeta): UsageSnapshot {
        val current = settings.state.value
        val zone = ZoneId.systemDefault()
        val range = TimeRanges.preset(current.rangePreset, zone, clock())
        val providerId = ProviderId.fromKey(meta.providerKey)
            ?: return failure(meta, range, "未知厂商：" + meta.providerKey)
        val adapter = registry.adapter(providerId)
            ?: return failure(meta, range, "该厂商暂不支持")
        val values = secrets.get(meta.id) ?: return failure(meta, range, "本地密钥丢失，请重新添加")
        val credential = ResolvedCredential(
            credentialId = meta.id,
            providerKey = meta.providerKey,
            label = meta.label,
            baseUrl = meta.baseUrl,
            values = values,
        )
        return try {
            withTimeout(REQUEST_TIMEOUT_MILLIS) {
                adapter.fetch(FetchContext(credential, range, http, clock(), zone))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (network: NetworkUnreachableException) {
            failure(meta, range, network.message ?: "网络不可达")
        } catch (httpFailure: HttpFailureException) {
            failure(meta, range, httpFailure.message ?: ("HTTP " + httpFailure.code))
        } catch (provider: ProviderException) {
            failure(meta, range, provider.message ?: "厂商接口返回异常")
        } catch (other: Throwable) {
            failure(meta, range, other.message ?: other.javaClass.simpleName)
        }
    }

    private fun failure(meta: CredentialMeta, range: io.apimeter.wear.core.UsageRange, message: String): UsageSnapshot =
        UsageSnapshot.failure(
            providerKey = meta.providerKey,
            credentialId = meta.id,
            credentialLabel = meta.label,
            message = message,
            fetchedAt = clock(),
            windowStartSec = range.startSec,
            windowEndSec = range.endSec,
        )

    private suspend fun persist() {
        val current = _state.value
        runCatching { cache.save(CachePayload(current.snapshots.values.toList(), current.lastRefreshAt)) }
    }

    private suspend fun restoreCache() {
        val payload = runCatching { cache.load() }.getOrNull() ?: return
        if (payload.snapshots.isEmpty()) return
        _state.update { current ->
            val merged = current.snapshots.toMutableMap()
            payload.snapshots.forEach { snapshot ->
                if (!merged.containsKey(snapshot.credentialId)) merged[snapshot.credentialId] = snapshot
            }
            current.copy(snapshots = merged, lastRefreshAt = maxOf(current.lastRefreshAt, payload.lastRefreshAt))
        }
    }

    private suspend fun notifySurfaces() {
        runCatching { onDataRefreshed?.invoke() }
    }

    private suspend fun syncState() {
        _state.update { it.copy(settings = settings.state.value) }
    }

    private companion object {
        const val MIN_REFRESH_GAP_MILLIS = 30_000L
        const val REQUEST_TIMEOUT_MILLIS = 40_000L
    }
}
