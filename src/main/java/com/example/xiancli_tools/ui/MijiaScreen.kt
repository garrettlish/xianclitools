package com.example.xiancli_tools.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.mijia.MijiaAuthException
import com.example.xiancli_tools.data.mijia.MijiaDevice
import com.example.xiancli_tools.data.mijia.MijiaDeviceConfig
import com.example.xiancli_tools.data.mijia.MijiaLabels
import com.example.xiancli_tools.data.mijia.MijiaPropKey
import com.example.xiancli_tools.data.mijia.MijiaPropSpec
import com.example.xiancli_tools.data.mijia.MijiaQrTicket
import com.example.xiancli_tools.data.mijia.MijiaRepository
import com.example.xiancli_tools.data.mijia.MijiaSpec
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.SegmentedTabs
import com.example.xiancli_tools.ui.theme.MijiaAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设备列表的两种看法：按米家的房间，或按品类归成的大类。 */
private enum class MijiaTab(val label: String) {
    ROOM("房间"),
    KIND("分类")
}

/** 扫码登录这一屏的全部状态。 */
private data class LoginState(
    val ticket: MijiaQrTicket? = null,
    val bitmap: Bitmap? = null,
    val remaining: Int = 0,
    val error: String? = null,
    val busy: Boolean = true
)

/**
 * 米家：开关灯、调亮度、一键全关。
 *
 * 和网页版同一个思路，但这里没有本地服务可用：登录态、协议加密全在 App 里自己做
 * （见 `data/mijia`）。所以首屏多了一步扫码，扫码之后设备列表和网页版长得一样。
 *
 * 设备列表的加载是分层的，别指望一次全齐：
 * 1. 先出设备（两三个请求）；
 * 2. 再补每个型号的规格（第一次要联网取，之后走磁盘缓存）；
 * 3. 最后批量读主属性，卡片上的开关才有状态。
 * 任何一步失败都只是少一块内容，不会把整页变成错误页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MijiaScreen(
    onBack: () -> Unit,
    initialTabOrdinal: Int = MijiaTab.ROOM.ordinal,
    onTabChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { MijiaRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember {
        mutableStateOf(MijiaTab.entries.getOrElse(initialTabOrdinal) { MijiaTab.ROOM })
    }
    var devices by remember { mutableStateOf<List<MijiaDevice>>(emptyList()) }
    var specs by remember { mutableStateOf<Map<String, MijiaSpec>>(emptyMap()) }
    var values by remember { mutableStateOf<Map<MijiaPropKey, Any?>>(emptyMap()) }
    var config by remember { mutableStateOf<Map<String, MijiaDeviceConfig>>(emptyMap()) }
    var busyKeys by remember { mutableStateOf<Set<MijiaPropKey>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var login by remember { mutableStateOf<LoginState?>(null) }
    var openedDevice by remember { mutableStateOf<MijiaDevice?>(null) }
    var configSheetOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun describeError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "米家没响应，稍后再试"

    // 登录和加载互相调用（登录成功后要加载，加载发现过期又要重新登录），
    // 局部函数不能前向引用，所以先用变量占位，等两个函数都写完再赋值。
    lateinit var startLogin: (Boolean) -> Unit

    /** 读设备、规格、主属性。`refresh` 为 true 时绕过客户端缓存。 */
    fun loadAll(refresh: Boolean) {
        scope.launch {
            loading = true
            error = null
            try {
                devices = withContext(Dispatchers.IO) { repository.devices(refresh) }
                config = withContext(Dispatchers.IO) { repository.deviceConfig() }
                loading = false

                val types = devices.map { it.specType }.filter { it.isNotBlank() }
                val loadedSpecs = withContext(Dispatchers.IO) { repository.specs(types) }
                specs = loadedSpecs

                val keys = devices.mapNotNull { device ->
                    loadedSpecs[device.specType]?.primary?.let {
                        MijiaPropKey(device.did, it.siid, it.piid)
                    }
                }
                val read = withContext(Dispatchers.IO) { repository.readProps(keys) }
                values = read.filterValues { it.ok }.mapValues { it.value.value }
            } catch (auth: MijiaAuthException) {
                // 登录态过期：直接切回扫码，别让人对着一张空列表猜。
                repository.logout()
                startLogin(true)
            } catch (failure: Exception) {
                loading = false
                error = describeError(failure)
            }
        }
    }

    fun pollLogin(ticket: MijiaQrTicket) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.awaitLogin(ticket) }
                login = null
                loadAll(refresh = true)
            } catch (failure: Exception) {
                login = login?.copy(busy = false, error = describeError(failure))
            }
        }
    }

    startLogin = { force ->
        scope.launch {
            login = LoginState(busy = true)
            loading = false
            try {
                val ticket = withContext(Dispatchers.IO) { repository.requestQr(force) }
                if (ticket == null) {
                    // passToken 还能换到新 token，压根不用扫。
                    login = null
                    loadAll(refresh = true)
                } else {
                    val bitmap = withContext(Dispatchers.IO) { repository.qrBitmap(ticket.imageUrl) }
                    login = LoginState(ticket = ticket, bitmap = bitmap, remaining = 120, busy = false)
                    pollLogin(ticket)
                }
            } catch (failure: Exception) {
                login = LoginState(busy = false, error = describeError(failure))
            }
        }
    }

    fun toggle(device: MijiaDevice, siid: Int, piid: Int) {
        val key = MijiaPropKey(device.did, siid, piid)
        val current = values[key] == true
        // 先翻界面再发请求：设备离线时也不会卡住；失败再翻回来。
        values = values + (key to !current)
        busyKeys = busyKeys + key
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.writeProp(device.did, siid, piid, !current) }
            } catch (failure: Exception) {
                values = values + (key to current)
                notify(describeError(failure))
            } finally {
                busyKeys = busyKeys - key
            }
        }
    }

    fun turnOffLights(targets: List<Pair<MijiaDevice, Int>>) {
        if (targets.isEmpty()) return
        scope.launch {
            var failed = 0
            targets.forEach { (device, piid) ->
                val spec = specs[device.specType]?.primary ?: return@forEach
                val key = MijiaPropKey(device.did, spec.siid, piid)
                values = values + (key to false)
                busyKeys = busyKeys + key
                try {
                    withContext(Dispatchers.IO) {
                        repository.writeProp(device.did, spec.siid, piid, false)
                    }
                } catch (failure: Exception) {
                    values = values + (key to true)
                    failed++
                } finally {
                    busyKeys = busyKeys - key
                }
            }
            notify(if (failed == 0) "灯都关了" else "$failed 盏没关掉")
        }
    }

    LaunchedEffect(Unit) {
        if (repository.isLoggedIn()) loadAll(refresh = false) else startLogin(false)
    }

    // 二维码倒计时：米家的码两分钟就失效，到期把「重新获取」露出来。
    LaunchedEffect(login?.ticket) {
        if (login?.ticket == null) return@LaunchedEffect
        while (true) {
            delay(1_000)
            val current = login ?: break
            if (current.remaining <= 0) break
            login = current.copy(remaining = current.remaining - 1)
        }
    }

    val homes = remember(devices) { devices.map { it.homeName }.distinct().filter { it.isNotBlank() } }
    val multiHome = homes.size > 1

    fun displayName(device: MijiaDevice): String =
        config[device.did]?.name ?: device.name

    fun displayRoom(device: MijiaDevice): String {
        val override = config[device.did]
        val room = if (override != null && override.room != null) override.room else device.room
        val label = room?.takeIf { it.isNotBlank() } ?: "未分组"
        return if (multiHome && device.homeName.isNotBlank()) "${device.homeName} · $label" else label
    }

    fun displayKind(device: MijiaDevice): String =
        config[device.did]?.kind ?: MijiaLabels.kindOf(device.category)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("米家", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadAll(refresh = true) }, enabled = login == null) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = "刷新"
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = login == null) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = "更多"
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("改名 / 分组") },
                                onClick = {
                                    menuOpen = false
                                    configSheetOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("退出登录") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repository.logout() }
                                        devices = emptyList()
                                        specs = emptyMap()
                                        values = emptyMap()
                                        startLogin(true)
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val current = login
        when {
            current != null -> LoginCard(
                state = current,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRefresh = { startLogin(true) }
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                SegmentedTabs(
                    labels = MijiaTab.entries.map { it.label },
                    selectedIndex = tab.ordinal,
                    onSelect = {
                        tab = MijiaTab.entries[it]
                        onTabChanged(it)
                    },
                    accent = MijiaAccent,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp)
                )

                when {
                    loading && devices.isEmpty() -> HintCard("正在读取设备…")
                    error != null && devices.isEmpty() -> HintCard(error.orEmpty())
                    devices.isEmpty() -> HintCard("米家里还没有设备，先去米家 App 里添加")
                    else -> DeviceList(
                        tab = tab,
                        devices = devices,
                        specs = specs,
                        values = values,
                        busyKeys = busyKeys,
                        nameOf = ::displayName,
                        roomOf = ::displayRoom,
                        kindOf = ::displayKind,
                        onOpen = { openedDevice = it },
                        onToggle = { device, siid, piid -> toggle(device, siid, piid) },
                        onTurnOff = ::turnOffLights
                    )
                }
            }
        }
    }

    openedDevice?.let { device ->
        MijiaDeviceSheet(
            device = device,
            spec = specs[device.specType],
            title = displayName(device),
            subtitle = buildString {
                append(MijiaLabels.categoryLabel(device.category))
                if (device.model.isNotBlank()) append(" · ").append(device.model)
                if (!device.online) append(" · 离线")
            },
            values = values,
            repository = repository,
            onValues = { values = values + it },
            onDismiss = { openedDevice = null },
            onEditConfig = {
                openedDevice = null
                configSheetOpen = true
            }
        )
    }

    if (configSheetOpen) {
        MijiaConfigSheet(
            devices = devices,
            config = config,
            nameOf = ::displayName,
            onRename = { did, name ->
                repository.rename(did, name)
                config = repository.deviceConfig()
            },
            onRoom = { did, room ->
                repository.setRoom(did, room)
                config = repository.deviceConfig()
            },
            onKind = { did, kind ->
                repository.setKind(did, kind)
                config = repository.deviceConfig()
            },
            onReset = { did ->
                repository.resetConfig(did)
                config = repository.deviceConfig()
            },
            onDismiss = { configSheetOpen = false }
        )
    }
}

/* ------------------------------ 扫码登录 ------------------------------ */

/**
 * 登录卡：二维码 + 倒计时 + 出错时的重试。
 *
 * 二维码图片直接用米家给的地址（`/pass/qr/login`），不自己生成——
 * 省掉一个二维码库，也保证码里的内容和服务端一致。
 */
@Composable
private fun LoginCard(
    state: LoginState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "用米家 App 扫码登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.ticket?.tips?.takeIf { it.isNotBlank() }
                        ?: "登录态只存在这台手机上，不进备份包，也不会直传给别的设备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = state.bitmap
                    when {
                        state.busy -> Text(
                            text = "正在获取二维码…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        bitmap != null -> Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "登录二维码",
                            modifier = Modifier.size(200.dp)
                        )

                        else -> Text(
                            text = "二维码没取到，点下面重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else if (!state.busy && state.ticket != null) {
                    Text(
                        text = if (state.remaining > 0) {
                            "二维码 ${state.remaining} 秒后失效"
                        } else {
                            "二维码已失效"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRefresh, enabled = !state.busy) {
                    Text("重新获取二维码")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "扫码后米家会把这台手机当作一个普通客户端；随时可以在右上角退出登录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun HintCard(text: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(
                icon = R.drawable.ic_tool_mijia,
                tint = MijiaAccent,
                container = MijiaAccent.copy(alpha = 0.12f),
                size = 52.dp,
                iconSize = 26.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/* ------------------------------ 设备列表 ------------------------------ */

@Composable
private fun DeviceList(
    tab: MijiaTab,
    devices: List<MijiaDevice>,
    specs: Map<String, MijiaSpec>,
    values: Map<MijiaPropKey, Any?>,
    busyKeys: Set<MijiaPropKey>,
    nameOf: (MijiaDevice) -> String,
    roomOf: (MijiaDevice) -> String,
    kindOf: (MijiaDevice) -> String,
    onOpen: (MijiaDevice) -> Unit,
    onToggle: (MijiaDevice, Int, Int) -> Unit,
    onTurnOff: (List<Pair<MijiaDevice, Int>>) -> Unit
) {
    // 分组视图按房间，分类视图按大类；组内都按名字排，免得每次刷新顺序都在跳。
    // 不 remember：分组依赖本机覆盖（改名 / 改分组），那是一个会被就地改掉的 Map，
    // 记成 key 反而容易忘了跟着变；几十台设备重算一次不值一提。
    val groups: List<Pair<String, List<MijiaDevice>>> = if (tab == MijiaTab.ROOM) {
        devices.groupBy { roomOf(it) }.toList().sortedBy { it.first }
    } else {
        val byKind = devices.groupBy { kindOf(it) }
        MijiaLabels.KINDS.mapNotNull { kind ->
            byKind[kind.key]?.takeIf { it.isNotEmpty() }?.let { kind.label to it }
        }
    }
    // 二级分组做成 tab：分组多、设备多时不用长距离滚动。
    // 跟着一级 tab 重置到第一组；设备刷新后组数变少时兜底夹回最后一组。
    var groupIndex by remember(tab) { mutableStateOf(0) }
    val index = groupIndex.coerceAtMost(groups.lastIndex)
    val (title, list) = groups[index]

    Column(modifier = Modifier.fillMaxSize()) {
        if (groups.size > 1) {
            GroupTabs(
                labels = groups.map { it.first },
                selectedIndex = index,
                onSelect = { groupIndex = it },
                accent = MijiaAccent,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "head-$title") {
                // 灯类才有「全关」：别的品类没有「全部关掉」这种说法。
                // 在线、拿到主属性的灯都能关，不管现在亮没亮——对已关的灯写 false 无害。
                val lights = if (tab == MijiaTab.KIND) {
                    list.filter { device ->
                        kindOf(device) == "light" && device.online &&
                            specs[device.specType]?.primary != null
                    }
                } else {
                    emptyList()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 组名已经在 tab 上，这里只留台数和「全关」。
                    Text(
                        text = "${list.size} 台",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    if (lights.isNotEmpty()) {
                        Text(
                            text = "全关",
                            style = MaterialTheme.typography.labelLarge,
                            color = MijiaAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onTurnOff(
                                        lights.mapNotNull { device ->
                                            specs[device.specType]?.primary?.let { device to it.piid }
                                        }
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            items(list, key = { it.did }) { device ->
                DeviceRow(
                    device = device,
                    title = nameOf(device),
                    subtitle = if (tab == MijiaTab.KIND) {
                        roomOf(device)
                    } else {
                        MijiaLabels.categoryLabel(device.category)
                    },
                    primary = specs[device.specType]?.primary,
                    value = specs[device.specType]?.primary?.let { primary ->
                        values[MijiaPropKey(device.did, primary.siid, primary.piid)]
                    },
                    specReady = specs.containsKey(device.specType),
                    busy = specs[device.specType]?.primary?.let { primary ->
                        MijiaPropKey(device.did, primary.siid, primary.piid) in busyKeys
                    } ?: false,
                    onOpen = { onOpen(device) },
                    onToggle = { siid, piid -> onToggle(device, siid, piid) }
                )
            }
        }
    }
}

/**
 * 二级分组 tab：横滑的胶囊行。分组多时一级 SegmentedTabs 那种等分挤不下，
 * 这里让每个胶囊按内容取宽，放不下就左右滑。
 */
@Composable
private fun GroupTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) {
                            accent.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: MijiaDevice,
    title: String,
    subtitle: String,
    primary: MijiaPropSpec?,
    value: Any?,
    specReady: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onToggle: (Int, Int) -> Unit
) {
    val on = value == true
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = categoryIcon(device.category),
                tint = if (on && device.online) MijiaAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                container = if (on && device.online) {
                    MijiaAccent.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                size = 40.dp,
                iconSize = 20.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!device.online) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "离线",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                primary != null -> Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = on,
                        onCheckedChange = { onToggle(primary.siid, primary.piid) },
                        enabled = device.online && !busy
                    )
                    Text(
                        text = when {
                            busy -> "发送中"
                            value == null -> ""
                            on -> "已开"
                            else -> "已关"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                else -> Text(
                    text = if (!specReady) "读取中" else "查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 品类 → 图标。分不到具体品类的都退成「房子」，比一堆问号好认。 */
private fun categoryIcon(category: String): Int = when (category) {
    "light", "switch" -> R.drawable.ic_mijia_bulb
    "plug", "outlet", "power-strip" -> R.drawable.ic_mijia_plug
    "television", "tv", "projector", "speaker", "set-top-box" -> R.drawable.ic_mijia_tv
    "sensor", "thermometer", "hygrometer", "temperature-humidity-sensor" -> R.drawable.ic_mijia_gauge
    "lock", "camera", "doorbell" -> R.drawable.ic_mijia_lock
    "gateway", "router", "wifi-repeater" -> R.drawable.ic_mijia_wifi
    else -> R.drawable.ic_mijia_home
}
