package io.apimeter.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.AppScaffold
import io.apimeter.wear.AppContainer
import io.apimeter.wear.core.ProviderCapability
import io.apimeter.wear.core.ProviderId
import io.apimeter.wear.providers.ProviderCatalog
import io.apimeter.wear.ui.components.InfoCard
import io.apimeter.wear.ui.components.WearScreen
import io.apimeter.wear.ui.screens.AboutScreen
import io.apimeter.wear.ui.screens.BackupScreen
import io.apimeter.wear.ui.screens.DetailScreen
import io.apimeter.wear.ui.screens.EditorScreen
import io.apimeter.wear.ui.screens.HomeScreen
import io.apimeter.wear.ui.screens.ProviderPickerScreen
import io.apimeter.wear.ui.screens.ProxyScreen
import io.apimeter.wear.ui.screens.SettingsScreen
import io.apimeter.wear.ui.screens.nextInterval
import io.apimeter.wear.ui.theme.ApiMeterTheme
import kotlinx.coroutines.launch

/** 应用根节点：手写 Screen 栈导航（手表只 4~5 层，避免 navigation-compose 的体积/冷启动开销）；AppScaffold 渲染固定 TimeText；返回统一走 BackHandler，页面不放返回按钮。 */
@Composable
fun ApiMeterApp(container: AppContainer) {
    val ui by container.usageStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // 打开应用就静默刷新一次；UsageStore 内部有 30 秒节流，不会重复打接口。
    LaunchedEffect(Unit) {
        runCatching { container.usageStore.refreshAll() }
    }

    ApiMeterTheme(
        manualAccent = ui.settings.manualAccent,
        accentArgb = ui.settings.accentArgb,
    ) {
        AppScaffold {
            val haptics = rememberWearHaptics(ui.settings.hapticsEnabled)
            when (val screen = stack.lastOrNull() ?: Screen.Home) {
                Screen.Home -> HomeScreen(
                    ui = ui,
                    haptics = haptics,
                    onRefresh = { scope.launch { container.usageStore.refreshAll(force = true) } },
                    onRefreshSurfaces = { container.refreshSurfaces() },
                    onCredential = { id -> push(Screen.Detail(id)) },
                    onAdd = { push(Screen.ProviderPicker) },
                    onSettings = { push(Screen.Settings) },
                )

                is Screen.Detail -> DetailScreen(
                    ui = ui,
                    credentialId = screen.credentialId,
                    haptics = haptics,
                    onEdit = {
                        val meta = ui.settings.credential(screen.credentialId)
                        if (meta != null) push(Screen.Editor(meta.providerKey, meta.id))
                    },
                    onRefresh = {
                        scope.launch { container.usageStore.refreshCredential(screen.credentialId) }
                    },
                )

                Screen.ProviderPicker -> ProviderPickerScreen(
                    haptics = haptics,
                    onPick = { provider: ProviderId -> push(Screen.Editor(provider.key, null)) },
                )

                is Screen.Editor -> {
                    val descriptor = ProviderId.fromKey(screen.providerKey)
                        ?.let { ProviderCatalog.descriptor(it) }
                    val meta = ui.settings.credential(screen.credentialId)
                    if (descriptor == null) {
                        credentlessFallback(haptics, ::pop)
                    } else {
                        val initialValues = remember(screen.credentialId) {
                            screen.credentialId?.let { container.secrets.get(it) } ?: emptyMap()
                        }
                        EditorScreen(
                            descriptor = descriptor,
                            fields = container.registry.fieldsFor(screen.providerKey),
                            initialLabel = meta?.label.orEmpty(),
                            initialBaseUrl = meta?.baseUrl.orEmpty(),
                            initialQuota = meta?.quotaAmount ?: 0.0,
                            initialValues = initialValues,
                            editing = screen.credentialId != null,
                            enabled = meta?.enabled ?: true,
                            showQuota = descriptor.capabilities.contains(ProviderCapability.USAGE_DETAIL),
                            haptics = haptics,
                            onSave = { label, baseUrl, quotaAmount, values ->
                                scope.launch {
                                    if (screen.credentialId == null) {
                                        container.usageStore.addCredential(
                                            providerKey = screen.providerKey,
                                            label = label,
                                            baseUrl = baseUrl,
                                            values = values,
                                            quotaAmount = quotaAmount,
                                        )
                                        // 新增成功后直接回首页看结果，不要退回到厂商选择页。
                                        stack = listOf(Screen.Home)
                                    } else {
                                        container.usageStore.updateCredential(
                                            credentialId = screen.credentialId,
                                            label = label,
                                            baseUrl = baseUrl,
                                            values = values,
                                            quotaAmount = quotaAmount,
                                        )
                                        pop()
                                    }
                                }
                            },
                            onToggleEnabled = { next ->
                                val id = screen.credentialId
                                if (id != null) {
                                    scope.launch { container.usageStore.setCredentialEnabled(id, next) }
                                }
                            },
                            onDelete = if (screen.credentialId == null) {
                                null
                            } else {
                                {
                                    scope.launch {
                                        container.usageStore.removeCredential(screen.credentialId)
                                        stack = listOf(Screen.Home)
                                    }
                                }
                            },
                        )
                    }
                }

                Screen.Settings -> SettingsScreen(
                    ui = ui,
                    haptics = haptics,
                    onToggleHaptics = { enabled ->
                        scope.launch { container.usageStore.setHapticsEnabled(enabled) }
                    },
                    onToggleBackgroundSync = { enabled ->
                        scope.launch { container.usageStore.setBackgroundSync(enabled) }
                    },
                    onCycleInterval = {
                        scope.launch {
                            container.usageStore.setRefreshIntervalMinutes(
                                nextInterval(ui.settings.refreshIntervalMinutes),
                            )
                        }
                    },
                    onCycleRange = {
                        scope.launch {
                            container.usageStore.setRangePreset(
                                nextRange(ui.settings.rangePreset),
                            )
                            container.usageStore.refreshAll(force = true)
                        }
                    },
                    onProxy = { push(Screen.Proxy) },
                    onBackup = { push(Screen.Backup) },
                    onAbout = { push(Screen.About) },
                    onToggleManualAccent = { enabled ->
                        scope.launch {
                            container.usageStore.setAccent(enabled, ui.settings.accentArgb)
                        }
                    },
                    onPickAccent = { argb ->
                        scope.launch {
                            container.usageStore.setAccent(manual = true, argb = argb)
                        }
                    },
                )

                Screen.About -> AboutScreen(haptics = haptics)

                Screen.Backup -> BackupScreen(
                    ui = ui,
                    haptics = haptics,
                    container = container,
                )

                Screen.Proxy -> ProxyScreen(
                    ui = ui,
                    haptics = haptics,
                    onApply = { config ->
                        scope.launch {
                            container.usageStore.setProxy(config)
                            pop()
                        }
                    },
                    onProbe = { scope.launch { container.usageStore.probeProxy() } },
                )
            }
        }
    }
}

/** 未知厂商 key 时的兜底页：可点提示卡，点一下退回上一页而不是白屏。 */
@Composable
private fun credentlessFallback(
    haptics: WearHaptics,
    onDone: () -> Unit,
) {
    WearScreen(haptics = haptics) {
        item {
            InfoCard(
                title = "厂商信息已失效",
                subtitle = "点一下返回",
                onClick = { haptics.tick(); onDone() },
            )
        }
    }
}
