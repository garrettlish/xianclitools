package com.example.xiancli_tools.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.example.xiancli_tools.data.BackupRepository
import com.example.xiancli_tools.data.lan.LanDiscovery
import com.example.xiancli_tools.data.lan.NearbySyncController

/**
 * 附近直传的界面侧会话：把 [NearbySyncController] 的回调收成 Compose 状态，
 * 并把每个入口都要重复的两件事集中到一处——Android 16 的本地网络授权、离开页面时收尾。
 *
 * 备份页与首页的「直传」下拉共用这一份，免得两边各写一套搜索 / 确认 / 授权逻辑。
 */
@Stable
class NearbySyncSession internal constructor(
    context: Context,
    repository: BackupRepository,
    private val onMessage: (String) -> Unit
) {
    var peers by mutableStateOf<List<LanDiscovery.Peer>>(emptyList())
        private set

    var status by mutableStateOf("正在准备…")
        private set

    /** 本机地址，形如 `192.168.1.7:49301`，手填兜底时念给对方听。 */
    var address by mutableStateOf<String?>(null)
        private set

    /** 非空表示有设备请求同步，界面弹确认框。 */
    var incoming by mutableStateOf<Incoming?>(null)
        private set

    private var startRequested by mutableStateOf(false)

    private val controller = NearbySyncController(
        context = context,
        repository = repository,
        callbacks = object : NearbySyncController.Callbacks {
            override fun onReady(address: String) {
                this@NearbySyncSession.address = address
            }

            override fun onPeersChanged(list: List<LanDiscovery.Peer>) {
                peers = list
            }

            override fun onIncomingRequest(from: String, payloadBytes: Int) {
                incoming = Incoming(from, payloadBytes)
            }

            override fun onStatus(message: String) {
                status = message
            }

            override fun onResult(message: String) {
                onMessage(message)
            }
        }
    )

    val deviceName: String get() = controller.deviceName

    val isSupported: Boolean get() = controller.isSupported

    /** 什么时候起服务由界面决定（进页面就起，或首次点开下拉才起），重复调用无副作用。 */
    val wantsStart: Boolean get() = startRequested

    fun start() {
        startRequested = true
    }

    internal fun begin() = controller.start()

    fun refresh() = controller.refresh()

    fun sendTo(peer: LanDiscovery.Peer) = controller.sendTo(peer)

    fun sendToAddress(raw: String): Boolean = controller.sendToAddress(raw)

    fun answer(accepted: Boolean) {
        incoming = null
        controller.answerIncoming(accepted)
    }

    fun stop() = controller.stop()

    data class Incoming(val from: String, val bytes: Int)
}

/**
 * 建一个会话并管好它的生命周期。
 *
 * [onMessage] 只接收同步结果（成功或失败）；「正在搜索」「正在连接」这类过程状态在 [NearbySyncSession.status] 里。
 */
@Composable
fun rememberNearbySyncSession(
    repository: BackupRepository,
    onMessage: (String) -> Unit
): NearbySyncSession {
    val context = LocalContext.current
    val session = remember(repository) {
        NearbySyncSession(context.applicationContext, repository, onMessage)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 无论给没给都继续：没给的话连接会失败，界面上会看到具体报错，比卡在这里好。
        session.begin()
    }

    LaunchedEffect(session.wantsStart) {
        if (!session.wantsStart) return@LaunchedEffect
        // Android 16 起访问本地网络要用户授权；低版本没有这个权限。
        val granted = Build.VERSION.SDK_INT < 36 ||
            ContextCompat.checkSelfPermission(
                context, LOCAL_NETWORK_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) session.begin() else permissionLauncher.launch(arrayOf(LOCAL_NETWORK_PERMISSION))
    }

    DisposableEffect(session) {
        onDispose { session.stop() }
    }

    return session
}

/** 有设备请求同步时的确认框。收不发由人决定，所以必须点一下才交换数据。 */
@Composable
fun IncomingSyncRequestDialog(session: NearbySyncSession) {
    val incoming = session.incoming ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { session.answer(false) },
        title = { Text("有设备请求同步", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "「${incoming.from}」想与这台设备交换数据" +
                    "（约 ${Formatter.formatFileSize(context, incoming.bytes.toLong())}）。\n" +
                    "确认是你自己的另一台设备后再接受。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = { session.answer(true) }) { Text("接受") }
        },
        dismissButton = {
            TextButton(onClick = { session.answer(false) }) { Text("拒绝") }
        }
    )
}

/** Android 16（API 36）起的本地网络权限；用字符串是为了不依赖编译期常量是否存在。 */
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
