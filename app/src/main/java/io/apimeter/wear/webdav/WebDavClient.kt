package io.apimeter.wear.webdav

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** WebDAV 交互中的可预期失败。 */
open class WebDavException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 服务器上的文件比本地预期的版本新（或 ETag 不匹配），需要用户决定怎么办。 */
class WebDavConflictException(message: String) : WebDavException(message)

/** 认证失败（401/403）。 */
class WebDavAuthException(message: String) : WebDavException(message)

/** 服务器上还没有备份文件。 */
class WebDavNotFoundException(url: String) : WebDavException("服务器上没有找到备份文件：$url")

/** 读到的一份远程备份。 */
data class RemoteDocument(
    val text: String,
    val etag: String?,
)

/** 最简 WebDAV 客户端：MKCOL / GET / PUT + ETag 乐观并发。默认只允许 https，http 与自签名证书须显式开启。 */
class WebDavClient(
    private val fileUrl: String,
    private val username: String,
    private val password: String,
    private val allowInsecureTls: Boolean = false,
    private val allowInsecureHttp: Boolean = false,
    private val client: OkHttpClient? = null,
) {

    private val http: OkHttpClient by lazy { client ?: buildClient() }

    fun validate(): String? {
        val trimmed = fileUrl.trim()
        if (trimmed.isEmpty()) return "请填写 WebDAV 地址"
        if (trimmed.startsWith("https://", ignoreCase = true)) return null
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            return if (allowInsecureHttp) null else "为了安全只能使用 https；确实要用 http 请先打开「允许不安全连接」"
        }
        return "地址必须以 https:// 开头"
    }

    /** 目录地址：文件地址去掉最后一段。 */
    private val parentUrl: String
        get() = fileUrl.trim().trimEnd('/').substringBeforeLast('/', "")

    /** MKCOL 在目录已存在时返回 405（个别服务器 200/301/302），均视为成功。 */
    fun ensureCollection() {
        val parent = parentUrl
        if (parent.isEmpty()) return
        val request = baseRequest(parent)
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        execute(request) { code, _ ->
            when (code) {
                in 200..299, 301, 302, 405 -> Unit
                401, 403 -> throw WebDavAuthException("WebDAV 认证失败（HTTP $code）")
                else -> throw WebDavException("创建目录失败（HTTP $code）")
            }
        }
    }

    /** 读取备份文件；不存在返回 null。 */
    fun read(): RemoteDocument? {
        val request = baseRequest(fileUrl.trim()).get().build()
        return try {
            execute(request) { code, response ->
                when (code) {
                    200 -> RemoteDocument(
                        text = response.body?.string().orEmpty(),
                        etag = response.header("ETag"),
                    )
                    404 -> null
                    401, 403 -> throw WebDavAuthException("WebDAV 认证失败（HTTP $code）")
                    else -> throw WebDavException("读取备份失败（HTTP $code）")
                }
            }
        } catch (notFound: WebDavNotFoundException) {
            null
        }
    }

    /** etag 为上次读到的值；传 null 表示只允许新建（If-None-Match: *）。被他人改写时 412 → [WebDavConflictException]。 */
    fun write(content: String, etag: String?) {
        val body = content.toRequestBody(JSON_MEDIA_TYPE)
        val builder = baseRequest(fileUrl.trim()).put(body)
        if (etag == null) {
            builder.header("If-None-Match", "*")
        } else {
            builder.header("If-Match", etag)
        }
        execute(builder.build()) { code, _ ->
            when (code) {
                in 200..299 -> Unit
                412 -> throw WebDavConflictException("服务器上的备份已被其他设备更新，请先拉取再上传")
                401, 403 -> throw WebDavAuthException("WebDAV 认证失败（HTTP $code）")
                404 -> throw WebDavException("目标目录不存在，且自动创建失败")
                else -> throw WebDavException("上传备份失败（HTTP $code）")
            }
        }
    }

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .header("Accept", "application/json, text/plain, */*")

    private fun <T> execute(
        request: Request,
        handle: (code: Int, response: okhttp3.Response) -> T,
    ): T = try {
        http.newCall(request).execute().use { response ->
            handle(response.code, response)
        }
    } catch (webDav: WebDavException) {
        throw webDav
    } catch (timeout: SocketTimeoutException) {
        throw WebDavException("连接 WebDAV 超时，请检查地址与网络", timeout)
    } catch (io: IOException) {
        throw WebDavException("连接 WebDAV 失败：" + (io.message ?: io.javaClass.simpleName), io)
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
        if (allowInsecureTls) {
            val trustAll = arrayOf<X509TrustManager>(TrustAllManager)
            val context = SSLContext.getInstance("TLS")
            context.init(null, trustAll, java.security.SecureRandom())
            builder.sslSocketFactory(context.socketFactory, TrustAllManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    /** 仅在用户显式选择「信任自签名证书」时启用。 */
    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
