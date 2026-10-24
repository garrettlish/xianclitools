package com.example.xiancli_tools

import com.example.xiancli_tools.data.BackupCodec
import com.example.xiancli_tools.data.BackupPayload
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.lan.LanProtocol
import com.example.xiancli_tools.data.lan.LanSyncServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * 局域网直传协议层的真机替代品：在 JVM 里真的起一个 [LanSyncServer]，
 * 用 [HttpURLConnection] 打一个来回。
 *
 * 这里刻意不碰 Android API（NsdManager 那层没法在单测里跑），
 * 但「发出去的东西能被正确解析、合并结果能被对方拿回」这条主链路是可以验证的。
 */
class LanSyncTest {

    private var server: LanSyncServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun `同步一次能把对方的记录合并进来并把合并结果回传`() {
        val local = payloadOf(note(id = 1L, content = "本机的记录", updatedAt = 100L))
        val received = CopyOnWriteArrayList<String>()

        val port = startServer { request ->
            received += request.deviceName
            val incoming = BackupCodec.decode(request.payload)
            LanSyncServer.Reply.Ok(BackupCodec.encode(incoming.mergedWith(local)))
        }

        val remote = payloadOf(note(id = 2L, content = "对方的记录", updatedAt = 200L))
        val (status, body) = post(port, BackupCodec.encode(remote), device = "Pixel 7")

        assertEquals("正常同步应返回 200", 200, status)
        assertEquals("服务端拿到的应是对方设备名", listOf("Pixel 7"), received.toList())

        val returned = BackupCodec.decode(body)
        assertEquals(
            "回传的应是两边的并集，对方据此补齐自己缺的部分",
            setOf("本机的记录", "对方的记录"),
            returned.notes.map { it.content }.toSet()
        )
        assertEquals("两台各一条，并集就是两条", 2, returned.notes.size)
    }

    @Test
    fun `同一条记录按修改时间取新，晚的压过早的`() {
        val local = payloadOf(note(id = 9L, content = "本机改过的新内容", updatedAt = 500L))

        val port = startServer { request ->
            val incoming = BackupCodec.decode(request.payload)
            LanSyncServer.Reply.Ok(BackupCodec.encode(incoming.mergedWith(local)))
        }

        val remote = payloadOf(note(id = 9L, content = "对方那台的老内容", updatedAt = 100L))
        val (status, body) = post(port, BackupCodec.encode(remote))

        assertEquals(200, status)
        assertEquals(
            "updatedAt 更新的版本应当胜出",
            "本机改过的新内容",
            BackupCodec.decode(body).notes.single().content
        )
    }

    @Test
    fun `路径不对时返回 404 而不是照单全收`() {
        val port = startServer { LanSyncServer.Reply.Ok("{}") }

        val (status, _) = post(port, "{}", path = "/other")

        assertEquals(404, status)
    }

    @Test
    fun `用户拒绝时返回 403，对方据此提示被拒绝`() {
        val port = startServer { LanSyncServer.Reply.Refused("已拒绝") }

        val (status, body) = post(port, BackupCodec.encode(payloadOf(note(1L, "x", 1L))))

        assertEquals(403, status)
        assertTrue("拒绝原因要带回去，界面才说得清", body.contains("拒绝"))
    }

    @Test
    fun `中文设备名经过请求头编码后能原样还原`() {
        val seen = AtomicReference<String?>(null)
        val port = startServer { request ->
            seen.set(request.deviceName)
            LanSyncServer.Reply.Ok("{}")
        }

        post(port, "{}", device = "程程的 小米 14")

        assertEquals("程程的 小米 14", seen.get())
    }

    private fun startServer(handler: (LanSyncServer.Request) -> LanSyncServer.Reply): Int {
        val instance = LanSyncServer(handler)
        instance.start()
        server = instance
        return instance.port
    }

    private fun post(
        port: Int,
        body: String,
        path: String = LanProtocol.PATH,
        device: String = "测试机"
    ): Pair<Int, String> {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val connection = (
            URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            ).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 3_000
            readTimeout = 5_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty(LanProtocol.HEADER_DEVICE, LanProtocol.encodeHeader(device))
            setFixedLengthStreamingMode(bytes.size)
        }
        return try {
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            status to (stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun payloadOf(vararg notes: Note) = BackupPayload(notes = notes.toList())

    private fun note(id: Long, content: String, updatedAt: Long) = Note(
        id = id,
        category = NoteCategory.DIARY,
        content = content,
        timeMillis = updatedAt,
        updatedAt = updatedAt
    )
}
