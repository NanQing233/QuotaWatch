package io.apimeter.wear.sync

import io.apimeter.wear.crypto.BackupCredential
import io.apimeter.wear.crypto.BackupCrypto
import io.apimeter.wear.crypto.BackupCryptoException
import io.apimeter.wear.crypto.BackupPayload
import io.apimeter.wear.crypto.BackupSettingsSnapshot
import io.apimeter.wear.storage.CredentialMeta
import io.apimeter.wear.webdav.WebDavClient
import io.apimeter.wear.webdav.WebDavConflictException
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * 端到端测试：MockWebServer 起一个**真实端口**的服务，用有状态的 Dispatcher 模拟 WebDAV。
 *
 * 覆盖完整链路：MKCOL 建目录 → PUT 上传密文 → GET 拉回 → 解密 → 合并 → 冲突检测，
 * 顺便断言“服务器上的文件里不含明文密钥”。
 */
class BackupEndToEndTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    private val files = HashMap<String, ByteArray>()
    private val etags = HashMap<String, String>()
    private val collections = HashSet<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = FakeWebDav()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fullRoundTrip_createThenMerge() {
        val secret = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val fileUrl = "$baseUrl/dav/apimeter/backup.json"
        val client = WebDavClient(fileUrl, "tester", "secret", allowInsecureHttp = true)

        client.ensureCollection()
        assertTrue(collections.contains("/dav/apimeter"))

        // 首次：服务器上还没有文件
        assertNull(client.read())

        val first = payload(
            revision = 1,
            credentials = listOf(credential("a", updatedAt = 10, label = "首发账号")),
        )
        client.write(seal(first, secret), etag = null)

        val storedText = String(files["/dav/apimeter/backup.json"]!!, Charsets.UTF_8)
        // 服务器文件里只有密文：既要是我们的格式，又不能出现明文 key
        assertTrue(storedText.contains("apimeter-backup"))
        assertTrue("服务器文件里不能出现明文密钥", !storedText.contains("key-a"))

        // 拉回来能解开
        val document = client.read()
        assertNotNull(document)
        val loaded = BackupCrypto.decodePayload(BackupCrypto.open(document!!.text, secret))
        assertEquals(1L, loaded.revision)
        assertEquals("首发账号", loaded.credentials.single().meta.label)

        // 另一台设备：本地多一条凭据 → 合并后应写回
        val second = payload(
            revision = 1,
            credentials = listOf(
                credential("a", updatedAt = 10, label = "首发账号"),
                credential("b", updatedAt = 20, label = "第二台加的"),
            ),
        )
        val merged = BackupMerge.merge(local = second, remote = loaded, now = 2_000L)
        assertTrue(merged.localContributions)

        val next = merged.payload.copy(revision = merged.payload.revision + 1)
        client.write(seal(next, secret), etag = document.etag)

        val after = client.read()!!
        val finalPayload = BackupCrypto.decodePayload(BackupCrypto.open(after.text, secret))
        assertEquals(2L, finalPayload.revision)
        assertEquals(listOf("a", "b"), finalPayload.credentials.map { it.meta.id }.sorted())
    }

    @Test
    fun staleEtag_producesConflict() {
        val secret = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val client = WebDavClient("$baseUrl/dav/apimeter/backup.json", "tester", "secret", allowInsecureHttp = true)
        client.ensureCollection()

        client.write(seal(payload(revision = 1), secret), etag = null)
        val document = client.read()!!

        // 模拟另一台设备先写了一次，ETag 因此变化
        client.write(seal(payload(revision = 2), secret), etag = document.etag)

        try {
            client.write(seal(payload(revision = 3), secret), etag = document.etag)
            fail("用旧 ETag 写入应当抛 WebDavConflictException")
        } catch (expected: WebDavConflictException) {
            assertTrue(expected.message!!.contains("其他设备"))
        }
    }

    @Test
    fun wrongRecoveryCode_cannotOpenServerFile() {
        val good = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val bad = BackupCrypto.formatRecoveryCode(BackupCrypto.newRecoverySecret())
        val client = WebDavClient("$baseUrl/dav/apimeter/backup.json", "tester", "secret", allowInsecureHttp = true)
        client.ensureCollection()
        client.write(seal(payload(revision = 1), good), etag = null)

        val document = client.read()!!
        try {
            BackupCrypto.open(document.text, bad)
            fail("恢复码不对时不应解密成功")
        } catch (expected: BackupCryptoException) {
            assertTrue(expected.message!!.contains("恢复码"))
        }
    }

    /** 有状态的假 WebDAV：数据放内存里，支持 MKCOL / GET / PUT + ETag 并发控制。 */
    private inner class FakeWebDav : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path?.substringBefore('?') ?: "/"
            return when (request.method) {
                "MKCOL" -> {
                    collections.add(path)
                    MockResponse().setResponseCode(201)
                }

                "GET" -> {
                    val body = files[path]
                    if (body == null) {
                        MockResponse().setResponseCode(404)
                    } else {
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("ETag", etags[path] ?: "\"\"")
                            .setBody(String(body, Charsets.UTF_8))
                    }
                }

                "PUT" -> {
                    val ifNoneMatch = request.getHeader("If-None-Match")
                    val ifMatch = request.getHeader("If-Match")
                    val existing = files[path]
                    if (ifNoneMatch == "*" && existing != null) {
                        MockResponse().setResponseCode(412).setBody("precondition failed")
                    } else if (ifMatch != null && (existing == null || etags[path] != ifMatch)) {
                        MockResponse().setResponseCode(412).setBody("precondition failed")
                    } else {
                        val body = request.body.readByteArray()
                        files[path] = body
                        etags[path] = "\"" + sha(body) + "\""
                        MockResponse().setResponseCode(if (existing == null) 201 else 204)
                    }
                }

                else -> MockResponse().setResponseCode(405)
            }
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(16)

    // ---------- fixtures ----------

    private fun payload(
        revision: Long,
        credentials: List<BackupCredential> = emptyList(),
    ) = BackupPayload(
        revision = revision,
        writtenAt = 1_000L,
        settings = BackupSettingsSnapshot(),
        credentials = credentials,
    )

    private fun credential(id: String, updatedAt: Long, label: String) = BackupCredential(
        meta = CredentialMeta(
            id = id,
            providerKey = "deepseek",
            label = label,
            createdAt = 1,
            updatedAt = updatedAt,
        ),
        secrets = mapOf("api_key" to "key-$id"),
    )

    private fun seal(payload: BackupPayload, secret: String): String =
        BackupCrypto.seal(BackupCrypto.encodePayload(payload), secret)
}