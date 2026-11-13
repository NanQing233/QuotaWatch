package io.apimeter.wear.sync

import io.apimeter.wear.crypto.BackupCredential
import io.apimeter.wear.crypto.BackupPayload

/**
 * 合并规则（纯函数）：凭据取并集，同 id 取 updatedAt 新者；设置以远端为准；revision 取较大值。
 * 删除不传播——对密钥备份而言宁可多留一条，也不能丢掉用户唯一的 Key。
 */
object BackupMerge {

    data class Result(
        val payload: BackupPayload,
        /** 本地有新内容，需要回写。 */
        val localContributions: Boolean,
    )

    fun merge(
        local: BackupPayload,
        remote: BackupPayload,
        now: Long,
        writtenBy: String = "",
    ): Result {
        val byId = LinkedHashMap<String, BackupCredential>()
        remote.credentials.forEach { byId[it.meta.id] = it }

        var contributed = false
        local.credentials.forEach { localCredential ->
            val existing = byId[localCredential.meta.id]
            when {
                existing == null -> {
                    byId[localCredential.meta.id] = localCredential
                    contributed = true
                }
                preferLocal(localCredential, existing) -> {
                    byId[localCredential.meta.id] = localCredential
                    contributed = true
                }
            }
        }

        // 稳定排序：否则内容相同也会因顺序不同而反复写出新文件。
        val merged = byId.values.sortedWith(compareBy({ it.meta.createdAt }, { it.meta.id }))

        return Result(
            payload = BackupPayload(
                revision = maxOf(local.revision, remote.revision),
                writtenAt = now,
                writtenBy = writtenBy,
                settings = remote.settings,
                credentials = merged,
            ),
            localContributions = contributed,
        )
    }

    private fun preferLocal(local: BackupCredential, remote: BackupCredential): Boolean {
        if (local.meta.updatedAt != remote.meta.updatedAt) {
            return local.meta.updatedAt > remote.meta.updatedAt
        }
        // 时间戳一样但内容不同：认为本地是刚改过的。
        return local != remote
    }
}
