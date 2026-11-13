package io.apimeter.wear.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "apimeter_settings")

/** 设置与凭据元数据（不含密钥）；密钥走 [SecretStore] 的 Keystore 加密存储。 */
class SettingsRepository(
    context: Context,
    scope: CoroutineScope,
    private val json: Json = AppJson,
) {
    private val appContext = context.applicationContext
    private val key = stringPreferencesKey("settings_json")
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val flow: Flow<AppSettings> = appContext.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decode(prefs[key]) }

    init {
        scope.launch {
            flow.collect { value -> _state.value = value }
        }
    }

    suspend fun update(block: (AppSettings) -> AppSettings): AppSettings {
        val prefs = appContext.settingsDataStore.edit { current ->
            val before = decode(current[key])
            val after = block(before)
            current[key] = json.encodeToString(AppSettings.serializer(), after)
        }
        val next = decode(prefs[key])
        _state.value = next
        return next
    }

    suspend fun awaitLoaded(): AppSettings = state.value

    private fun decode(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrDefault(AppSettings())
    }
}

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}
