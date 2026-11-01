package com.example.xiancli_tools.data.mijia

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * 米家接口用到的那一点点 HTTP：表单 POST、GET、手抄 Cookie。
 *
 * 不引第三方库是延续这个项目的做法（局域网直传也是手写 HTTP）。
 * 两处必须自己来的细节：
 * - **重定向要手动跟**：登录回调靠 302 一路下发 serviceToken，
 *   用 `HttpURLConnection` 的自动跳转会把中间那几跳的 Set-Cookie 全吞掉。
 * - **响应可能被 gzip**：`HttpURLConnection` 不会自动解压，这里按魔数判断。
 */
internal object MijiaHttp {

    data class Response(
        val status: Int,
        val body: String,
        val cookies: Map<String, String>
    )

    fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Int = 30_000
    ): Response {
        var current = url
        val cookies = LinkedHashMap<String, String>()
        var hops = 0
        while (true) {
            val hop = exchange("GET", current, headers, null, timeoutMs, followRedirects = false)
            cookies.putAll(hop.cookies)
            val location = hop.headers["location"]
            if (hop.status in REDIRECTS && !location.isNullOrBlank() && hops < MAX_REDIRECTS) {
                current = resolve(current, location)
                hops++
                continue
            }
            return Response(hop.status, hop.body, cookies)
        }
    }

    fun postForm(
        url: String,
        headers: Map<String, String>,
        form: Map<String, String>,
        timeoutMs: Int = 30_000
    ): Response {
        val body = form.entries.joinToString("&") { (key, value) ->
            encode(key) + "=" + encode(value)
        }.toByteArray(Charsets.UTF_8)
        val hop = exchange("POST", url, headers, body, timeoutMs, followRedirects = false)
        return Response(hop.status, hop.body, hop.cookies)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun resolve(base: String, location: String): String =
        if (location.startsWith("http://") || location.startsWith("https://")) {
            location
        } else {
            URL(URL(base), location).toString()
        }

    private class Hop(
        val status: Int,
        val body: String,
        val cookies: Map<String, String>,
        val headers: Map<String, String>
    )

    private fun exchange(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeoutMs: Int,
        followRedirects: Boolean
    ): Hop {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw MijiaNetworkException("地址不对：$url", error)
        }
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = followRedirects
            connection.connectTimeout = timeoutMs.coerceAtMost(20_000)
            connection.readTimeout = timeoutMs
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.setRequestProperty("Content-Length", body.size.toString())
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val raw = stream?.use { readAll(it) } ?: ByteArray(0)
            val text = String(if (isGzip(raw)) gunzip(raw) else raw, Charsets.UTF_8)
            return Hop(status, text, readCookies(connection), readHeaders(connection))
        } catch (error: MijiaException) {
            throw error
        } catch (error: Exception) {
            throw MijiaNetworkException("连不上米家：${error.message ?: error.javaClass.simpleName}", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun readAll(stream: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count <= 0) break
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { readAll(it) }

    /** 多值头要整串取：`getHeaderField` 只会给最后一个 Set-Cookie。 */
    private fun readCookies(connection: HttpURLConnection): Map<String, String> {
        val cookies = LinkedHashMap<String, String>()
        connection.headerFields.forEach { (name, values) ->
            if (name == null || !name.equals("Set-Cookie", ignoreCase = true)) return@forEach
            values.orEmpty().forEach { line ->
                val pair = line.substringBefore(';')
                val index = pair.indexOf('=')
                if (index > 0) {
                    val key = pair.substring(0, index).trim()
                    val value = pair.substring(index + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) cookies[key] = value
                }
            }
        }
        return cookies
    }

    private fun readHeaders(connection: HttpURLConnection): Map<String, String> {
        val headers = HashMap<String, String>()
        connection.headerFields.forEach { (name, values) ->
            if (name != null && !values.isNullOrEmpty()) {
                headers[name.lowercase()] = values.first()
            }
        }
        return headers
    }

    private const val MAX_REDIRECTS = 5
    private val REDIRECTS = setOf(301, 302, 303, 307, 308)
}
