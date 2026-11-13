package io.apimeter.wear.data

import android.content.Context
import io.apimeter.wear.core.UsageSnapshot
import io.apimeter.wear.storage.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachePayload(
    val snapshots: List<UsageSnapshot> = emptyList(),
    val lastRefreshAt: Long = 0L,
)

/**
 * 快照磁盘缓存：表盘与 Tile 可能在应用未启动时被系统唤起，必须能同步读到上次结果。
 */
class SnapshotCache(
    context: Context,
    private val json: Json = AppJson,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    suspend fun load(): CachePayload = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = cacheFile()
            if (!file.exists()) return@withContext CachePayload()
            val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@withContext CachePayload()
            runCatching { json.decodeFromString(CachePayload.serializer(), raw) }.getOrDefault(CachePayload())
        }
    }

    suspend fun save(payload: CachePayload) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val file = cacheFile()
                val temp = File(file.parentFile, file.name + ".tmp")
                runCatching {
                    temp.writeText(json.encodeToString(CachePayload.serializer(), payload), Charsets.UTF_8)
                    if (file.exists()) file.delete()
                    temp.renameTo(file)
                }
            }
        }
    }

    private fun cacheFile(): File {
        val dir = File(appContext.filesDir, "cache").apply { mkdirs() }
        return File(dir, "usage_snapshots.json")
    }
}
