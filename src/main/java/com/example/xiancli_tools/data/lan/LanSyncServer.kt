package com.example.xiancli_tools.data.lan

import java.io.BufferedInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 接收端的极小服务：监听一个随机端口，收 `POST /sync`（对方发来的备份），
 * 交给 [handler] 决定收不收、以及回什么。
 *
 * [handler] 里要等用户在界面上点「接受」，所以它会阻塞十几秒，这也就是每个连接
 * 单独占一个线程、而不是串行处理的原因——否则等待确认期间对方会连不进来。
 */
class LanSyncServer(private val handler: (Request) -> Reply) {

    data class Request(val deviceName: String, val payload: String)

    sealed interface Reply {
        /** 接受：回传本机合并后的备份，对方据此补上自己缺的那部分。 */
        data class Ok(val payload: String) : Reply

        /** 拒绝：回 403，对方界面上会显示「对方拒绝了本次同步」。 */
        data class Refused(val message: String) : Reply
    }

    private val running = AtomicBoolean(false)
    private val workers = Executors.newCachedThreadPool { task ->
        Thread(task, "lan-sync-worker").apply { isDaemon = true }
    }
    private var serverSocket: ServerSocket? = null

    /** 实际监听端口；没启动时为 0。 */
    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun start() {
        if (running.getAndSet(true)) return
        val socket = ServerSocket(0)
        socket.reuseAddress = true
        serverSocket = socket
        workers.execute { acceptLoop(socket) }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        workers.shutdownNow()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                // close() 会让 accept 抛异常，这是正常退出路径。
                break
            }
            workers.execute { handle(client) }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            val response = runCatching {
                socket.soTimeout = READ_TIMEOUT_MILLIS
                val request = LanProtocol.parseRequest(BufferedInputStream(socket.getInputStream()))
                respondTo(request)
            }.getOrElse { error ->
                val bad = error is LanProtocol.HttpError
                LanProtocol.response(
                    if (bad) 400 else 500,
                    if (bad) "Bad Request" else "Internal Server Error",
                    (error.message ?: "同步失败").toByteArray(Charsets.UTF_8)
                )
            }
            runCatching {
                socket.getOutputStream().apply {
                    write(response)
                    flush()
                }
            }
        }
    }

    private fun respondTo(request: LanProtocol.Request): ByteArray {
        if (request.method != "POST" || request.path != LanProtocol.PATH) {
            return LanProtocol.response(404, "Not Found")
        }
        val incoming = Request(request.deviceName, String(request.body, Charsets.UTF_8))
        return when (val reply = handler(incoming)) {
            is Reply.Ok -> LanProtocol.response(
                200, "OK", reply.payload.toByteArray(Charsets.UTF_8)
            )

            is Reply.Refused -> LanProtocol.response(
                403, "Forbidden", reply.message.toByteArray(Charsets.UTF_8)
            )
        }
    }

    private companion object {
        const val READ_TIMEOUT_MILLIS = 20_000
    }
}
