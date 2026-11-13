package io.apimeter.wear.sync

import io.apimeter.wear.core.RangePreset
import io.apimeter.wear.crypto.BackupCredential
import io.apimeter.wear.crypto.BackupPayload
import io.apimeter.wear.crypto.BackupSettingsSnapshot
import io.apimeter.wear.storage.CredentialMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {

    @Test
    fun remoteOnly_isUnchanged() {
        val remote = payload(revision = 3, credentials = listOf(credential("a", updatedAt = 10)))
        val result = BackupMerge.merge(local = payload(revision = 0), remote = remote, now = 99)

        assertEquals(3L, result.payload.revision)
        assertFalse(result.localContributions)
        assertEquals(listOf("a"), result.payload.credentials.map { it.meta.id })
    }

    @Test
    fun localExtraCredential_isAddedAndFlagged() {
        val remote = payload(revision = 2, credentials = listOf(credential("a", updatedAt = 10)))
        val local = payload(revision = 2, credentials = listOf(credential("b", updatedAt = 20)))

        val result = BackupMerge.merge(local = local, remote = remote, now = 99)

        assertTrue(result.localContributions)
        assertEquals(listOf("a", "b"), result.payload.credentials.map { it.meta.id }.sorted())
    }

    @Test
    fun newerLocalEditWins() {
        val remote = payload(credentials = listOf(credential("a", updatedAt = 10, label = "旧名字")))
        val local = payload(credentials = listOf(credential("a", updatedAt = 20, label = "新名字")))

        val result = BackupMerge.merge(local = local, remote = remote, now = 99)

        assertTrue(result.localContributions)
        assertEquals("新名字", result.payload.credentials.single().meta.label)
    }

    @Test
    fun olderLocalEditLoses() {
        val remote = payload(credentials = listOf(credential("a", updatedAt = 30, label = "远端更新")))
        val local = payload(credentials = listOf(credential("a", updatedAt = 20, label = "本地更旧")))

        val result = BackupMerge.merge(local = local, remote = remote, now = 99)

        assertFalse(result.localContributions)
        assertEquals("远端更新", result.payload.credentials.single().meta.label)
    }

    @Test
    fun sameTimestampButDifferentContent_prefersLocal() {
        val remote = payload(credentials = listOf(credential("a", updatedAt = 10, label = "远端")))
        val local = payload(credentials = listOf(credential("a", updatedAt = 10, label = "本地")))

        val result = BackupMerge.merge(local = local, remote = remote, now = 99)

        assertTrue(result.localContributions)
        assertEquals("本地", result.payload.credentials.single().meta.label)
    }

    @Test
    fun settingsFollowRemote() {
        val remote = payload(settings = BackupSettingsSnapshot(rangePreset = RangePreset.THIS_MONTH))
        val local = payload(settings = BackupSettingsSnapshot(rangePreset = RangePreset.TODAY))

        val result = BackupMerge.merge(local = local, remote = remote, now = 99)

        assertEquals(RangePreset.THIS_MONTH, result.payload.settings.rangePreset)
    }

    @Test
    fun revisionIsMaxOfBoth() {
        val result = BackupMerge.merge(
            local = payload(revision = 8),
            remote = payload(revision = 5),
            now = 99,
        )
        assertEquals(8L, result.payload.revision)
    }

    @Test
    fun deletionIsNotPropagated_byDesign() {
        // 有意保留：远端删掉一条，合并后仍会复活，避免把用户唯一的 Key 弄丢。
        val remote = payload(credentials = emptyList())
        val local = payload(credentials = listOf(credential("a", updatedAt = 10)))

        val result = BackupMerge.merge(local = local, remote = remote, now = 99)

        assertTrue(result.localContributions)
        assertEquals(1, result.payload.credentials.size)
    }

    private fun payload(
        revision: Long = 1,
        settings: BackupSettingsSnapshot = BackupSettingsSnapshot(),
        credentials: List<BackupCredential> = emptyList(),
    ) = BackupPayload(
        revision = revision,
        writtenAt = 0,
        settings = settings,
        credentials = credentials,
    )

    private fun credential(
        id: String,
        updatedAt: Long,
        label: String = "账号",
    ) = BackupCredential(
        meta = CredentialMeta(
            id = id,
            providerKey = "deepseek",
            label = label,
            createdAt = 1,
            updatedAt = updatedAt,
        ),
        secrets = mapOf("api_key" to "key-$id"),
    )
}
