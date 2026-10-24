package com.example.xiancli_tools.data.lan

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 局域网直传用的「HTTP 方言」：只有一个端点、只认一个方法。
 *
 * 不引入 HTTP 库是有意的——这里需求就这么点，`HttpURLConnection` 加几十行解析就够，
 * 反而能让协议层是纯 Kotlin（不碰 Android），可以真的在 JVM 单测里起服务跑一个来回。
 */
object LanProtocol {

    /** 唯一的端点：POST 备份进来，返回对方合并后的备份。 */
    const val PATH = "/sync"

    /** 设备名放在请求头里，值经过百分号编码，中文型号名也能原样过去。 */
    const val HEADER_DEVICE = "X-Xiancli-Device"

    /** 单个请求体上限。足迹点阵是体积大头，一小时轨迹几 MB，64MB 足够且能挡住乱来的请求。 */
    const val MAX_BODY_BYTES = 64 * 1024 * 1024

    private const val MAX_HEADER_BYTES = 8 * 1024

    class HttpError(message: String) : Exception(message)

    data class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray
    ) {
        /** 对端设备名；拿不到就退回一个中性称呼，界面上不至于空白。 */
        val deviceName: String
            get() = headers[HEADER_DEVICE.lowercase()]
                ?.takeIf { it.isNotBlank() }
                ?.let(::decodeHeader)
                ?: "另一台设备"
    }

    fun encodeHeader(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun decodeHeader(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)

    /**
     * 读一个请求。只支持 `Content-Length` 定长的请求体：
     * 我们自己的客户端发的就是这个，不需要 chunked。
     */
    fun parseRequest(input: InputStream): Request {
        val head = readHeaderBlock(input)
        val lines = head.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw HttpError("空请求")

        val requestLine = lines.first().split(' ')
        if (requestLine.size < 2) throw HttpError("请求行不完整")

        val headers = HashMap<String, String>()
        lines.drop(1).forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] =
                    line.substring(colon + 1).trim()
            }
        }

        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        if (length > MAX_BODY_BYTES) throw HttpError("数据过大（$length 字节）")
        // transfer-encoding: chunked 明确不支持，直接拒掉，免得把分块当成正文。
        if (headers["transfer-encoding"] != null && length == 0L) throw HttpError("不支持分块传输")

        return Request(
            method = requestLine[0].uppercase(),
            path = requestLine[1].substringBefore('?'),
            headers = headers,
            body = readFully(input, length.toInt())
        )
    }

    fun response(
        status: Int,
        reason: String,
        body: ByteArray = ByteArray(0),
        contentType: String = "application/json; charset=utf-8"
    ): ByteArray {
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        return head.toByteArray(Charsets.UTF_8) + body
    }

    private fun readHeaderBlock(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        val terminator = byteArrayOf(
            '\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte()
        )
        while (true) {
            val byte = input.read()
            if (byte == -1) throw HttpError("连接中途断开了")
            buffer.write(byte)
            matched = when (byte.toByte()) {
                terminator[matched] -> matched + 1
                terminator[0] -> 1
                else -> 0
            }
            if (matched == terminator.size) break
            if (buffer.size() > MAX_HEADER_BYTES) throw HttpError("请求头过长")
        }
        return String(buffer.toByteArray(), Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count == -1) throw HttpError("数据不完整")
            read += count
        }
        return body
    }
}
