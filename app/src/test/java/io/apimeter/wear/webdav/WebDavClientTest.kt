package io.apimeter.wear.webdav

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * WebDAV 客户端行为测试（用 MockWebServer 模拟服务器）。
 *
 * 重点验证“乐观并发”和错误归类：ETag 头是否正确、412 是否转成冲突、
 * 401 是否转成认证失败、MKCOL 405（目录已存在）是否被容忍。
 */
class WebDavClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun read_returnsNullOn404() {
        server.enqueue(MockResponse().setResponseCode(404))
        val client = client("/dav/apimeter/backup.json")
        assertNull(client.read())
    }

    @Test
    fun read_returnsTextAndEtag() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("ETag", "\"abc123\"")
                .setBody("""{"format":"apimeter-backup"}"""),
        )
        val document = client("/dav/apimeter/backup.json").read()
        assertNotNull(document)
        assertEquals("\"abc123\"", document!!.etag)
        assertTrue(document.text.contains("apimeter-backup"))
    }

    @Test
    fun write_usesIfMatchWhenEtagKnown() {
        server.enqueue(MockResponse().setResponseCode(201))
        client("/dav/apimeter/backup.json").write("{}", etag = "\"abc123\"")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("\"abc123\"", request.getHeader("If-Match"))
        assertNull(request.getHeader("If-None-Match"))
    }

    @Test
    fun write_usesIfNoneMatchStarWhenCreating() {
        server.enqueue(MockResponse().setResponseCode(201))
        client("/dav/apimeter/backup.json").write("{}", etag = null)

        val request = server.takeRequest()
        assertEquals("*", request.getHeader("If-None-Match"))
        assertNull(request.getHeader("If-Match"))
    }

    @Test
    fun write_throwsConflictOn412() {
        server.enqueue(MockResponse().setResponseCode(412))
        try {
            client("/dav/apimeter/backup.json").write("{}", etag = "\"old\"")
            fail("412 应当抛 WebDavConflictException")
        } catch (expected: WebDavConflictException) {
            assertTrue(expected.message!!.contains("其他设备"))
        }
    }

    @Test
    fun read_throwsAuthErrorOn401() {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            client("/dav/apimeter/backup.json").read()
            fail("401 应当抛 WebDavAuthException")
        } catch (expected: WebDavAuthException) {
            assertTrue(expected.message!!.contains("认证失败"))
        }
    }

    @Test
    fun ensureCollection_tolerates405() {
        // 目录已存在时多数服务器返回 405
        server.enqueue(MockResponse().setResponseCode(405))
        client("/dav/apimeter/backup.json").ensureCollection()

        val request = server.takeRequest()
        assertEquals("MKCOL", request.method)
        // MKCOL 打的是父目录，不需要结尾斜杠
        assertEquals("/dav/apimeter", request.path)
    }

    @Test
    fun validate_rejectsPlainHttpByDefault() {
        // 注意：不能用下面的 helper，它为了让 MockWebServer 跑起来打开了 allowInsecureHttp
        val client = WebDavClient(
            fileUrl = server.url("/dav/backup.json").toString(),
            username = "me",
            password = "secret",
        )
        val error = client.validate()
        assertNotNull(error)
        assertTrue(error!!.contains("https"))
    }

    @Test
    fun validate_acceptsHttps() {
        val client = WebDavClient(
            fileUrl = "https://dav.example.com/remote.php/dav/files/me/apimeter/backup.json",
            username = "me",
            password = "secret",
        )
        assertNull(client.validate())
    }

    @Test
    fun validate_allowsHttpWhenExplicitlyPermitted() {
        val client = WebDavClient(
            fileUrl = "http://192.168.1.5:5005/dav/backup.json",
            username = "me",
            password = "secret",
            allowInsecureHttp = true,
        )
        assertNull(client.validate())
    }

    private fun client(path: String): WebDavClient = WebDavClient(
        fileUrl = server.url(path).toString(),
        username = "me",
        password = "secret",
        allowInsecureHttp = true,
    )
}
