package com.example.xiancli_tools.data.lan

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.xiancli_tools.data.BackupCodec
import com.example.xiancli_tools.data.BackupRepository
import com.example.xiancli_tools.data.describe
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 同一 Wi-Fi 下的双向同步：本机既是服务端也是客户端。
 *
 * 一次同步做两件事：
 * 1. 把本机备份 POST 给对方，对方合并后回传它合并完的全量；
 * 2. 本机把回传的内容再合并一次。
 * 因为合并是「按 updatedAt 取新」，两边各自跑完这一轮，数据就一致了——
 * 不需要谁主谁从，也不需要重试。
 *
 * 收不发由人决定：对端连上来后先弹确认，[answerIncoming] 点了才真正交换。
 */
class NearbySyncController(
    context: Context,
    private val repository: BackupRepository,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        /** 服务起好了，[address] 形如 `192.168.1.7:49301`，用于手填地址兜底。 */
        fun onReady(address: String)

        fun onPeersChanged(list: List<LanDiscovery.Peer>)

        /** 有设备请求同步，等界面弹窗确认，确认后调 [answerIncoming]。 */
        fun onIncomingRequest(from: String, payloadBytes: Int)

        fun onStatus(message: String)

        /** 同步有结果（成功或失败都走这里），界面用 snackbar 展示。 */
        fun onResult(message: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val discovery = LanDiscovery(appContext)
    private val server = LanSyncServer { request -> handleIncoming(request) }
    private val sender = Executors.newSingleThreadExecutor { task ->
        Thread(task, "lan-sync-send").apply { isDaemon = true }
    }

    private val peers = LinkedHashMap<String, LanDiscovery.Peer>()
    private var pendingApproval: ArrayBlockingQueue<Boolean>? = null
    private var started = false

    /** 本机在局域网里报出的名字，对方确认弹窗里看到的就是它。 */
    val deviceName: String = (Build.MODEL ?: "Android").trim().ifBlank { "Android" }

    val isSupported: Boolean
        get() = discovery.isAvailable

    fun start() {
        if (started) return
        started = true

        runCatching { server.start() }.onFailure { error ->
            post { callbacks.onStatus("接收服务启动失败：${error.message ?: "未知原因"}") }
            return
        }
        post { callbacks.onReady(localAddress() ?: "端口 ${server.port}") }

        discovery.advertise(
            port = server.port,
            deviceName = deviceName,
            onRegistered = { post { callbacks.onStatus("已就绪，正在搜索附近设备…") } },
            onError = { message -> post { callbacks.onStatus(message) } }
        )
        browse()
    }

    /** 「重新搜索」：清掉旧列表再扫一遍，用于对方刚打开页面、之前没搜到的情况。 */
    fun refresh() {
        if (!started) return
        synchronized(peers) { peers.clear() }
        post { callbacks.onPeersChanged(emptyList()) }
        browse()
    }

    fun sendTo(peer: LanDiscovery.Peer) = send(peer)

    /** 手填 `192.168.1.7:49301` 的兜底路径：路由器挡了组播时还能直连。 */
    fun sendToAddress(address: String): Boolean {
        val peer = parseAddress(address) ?: return false
        send(peer)
        return true
    }

    fun answerIncoming(accepted: Boolean) {
        pendingApproval?.offer(accepted)
    }

    fun stop() {
        if (!started) return
        started = false
        // 放一个「拒绝」进去，让还卡在等确认的那条连接立刻收尾，而不是等超时。
        pendingApproval?.offer(false)
        pendingApproval = null
        runCatching { discovery.stop() }
        runCatching { server.stop() }
        sender.shutdownNow()
        synchronized(peers) { peers.clear() }
    }

    private fun browse() {
        discovery.browse(
            onFound = { peer ->
                synchronized(peers) { peers[peer.name] = peer }
                post { callbacks.onPeersChanged(snapshotPeers()) }
            },
            onLost = { name ->
                synchronized(peers) { peers.remove(name) }
                post { callbacks.onPeersChanged(snapshotPeers()) }
            },
            onError = { message -> post { callbacks.onStatus(message) } }
        )
    }

    private fun snapshotPeers(): List<LanDiscovery.Peer> =
        synchronized(peers) { peers.values.toList() }

    private fun send(peer: LanDiscovery.Peer) {
        post { callbacks.onStatus("正在连接「${peer.name}」…") }
        sender.execute {
            val result = runCatching { exchange(peer) }
            post {
                result.onSuccess { callbacks.onResult(it) }
                    .onFailure { callbacks.onResult("同步失败：${friendly(it)}") }
            }
        }
    }

    /** 把本机备份推过去，等对方合并后回传，再并回本地。返回一句可读的结果。 */
    private fun exchange(peer: LanDiscovery.Peer): String {
        val before = repository.snapshot()
        val body = BackupCodec.encode(before).toByteArray(Charsets.UTF_8)
        val connection = (
            URL("http://${peer.host}:${peer.port}${LanProtocol.PATH}").openConnection()
                as HttpURLConnection
            ).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty(LanProtocol.HEADER_DEVICE, LanProtocol.encodeHeader(deviceName))
            setFixedLengthStreamingMode(body.size)
        }

        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw LanSyncException("对方拒绝了这次同步")
            }
            if (status != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?.take(80)
                    .orEmpty()
                throw LanSyncException("对方返回 $status" + if (detail.isBlank()) "" else "：$detail")
            }

            val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val incoming = BackupCodec.decode(text)
            val diff = incoming.diffAgainst(before)
            repository.apply(incoming.mergedWith(before), true)
            return "已与「${peer.name}」双向同步：本机${diff.describe()}"
        } finally {
            connection.disconnect()
        }
    }

    private fun handleIncoming(request: LanSyncServer.Request): LanSyncServer.Reply {
        val gate = ArrayBlockingQueue<Boolean>(1)
        pendingApproval = gate
        post { callbacks.onIncomingRequest(request.deviceName, request.payload.length) }

        val accepted = try {
            gate.poll(APPROVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS) == true
        } catch (e: InterruptedException) {
            false
        } finally {
            pendingApproval = null
        }
        if (!accepted) return LanSyncServer.Reply.Refused("已拒绝")

        val before = repository.snapshot()
        val incoming = BackupCodec.decode(request.payload)
        val diff = incoming.diffAgainst(before)
        repository.apply(incoming.mergedWith(before), true)
        post { callbacks.onResult("「${request.deviceName}」已同步：本机${diff.describe()}") }

        // 回传合并后的全量，对方据此补齐它缺的那部分（含它自己没见过的删除墓碑）。
        return LanSyncServer.Reply.Ok(BackupCodec.encode(repository.snapshot()))
    }

    private fun parseAddress(raw: String): LanDiscovery.Peer? {
        val text = raw.trim().removePrefix("http://").removeSuffix("/")
        val host = text.substringBeforeLast(':', "")
        val port = text.substringAfterLast(':', "").toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) return null
        return LanDiscovery.Peer("${host}:${port}", host, port)
    }

    private fun friendly(error: Throwable): String = when (error) {
        is LanSyncException -> error.message ?: "同步失败"
        is ConnectException -> "连不上对方，确认两台设备在同一个 Wi-Fi 下"
        is SocketTimeoutException -> "对方没有响应，可能是没打开「附近直传」页面"
        is UnknownHostException -> "地址解析失败，检查填的 IP"
        else -> error.message ?: "未知错误"
    }

    private fun localAddress(): String? {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val properties = manager.getLinkProperties(manager.activeNetwork) ?: return null
        val ip = properties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
            ?: return null
        return "$ip:${server.port}"
    }

    private fun post(block: () -> Unit) {
        main.post(block)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 6_000
        const val READ_TIMEOUT_MILLIS = 90_000
        const val APPROVAL_TIMEOUT_SECONDS = 30L
    }
}

class LanSyncException(message: String) : Exception(message)
