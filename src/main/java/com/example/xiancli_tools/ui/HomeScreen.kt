package com.example.xiancli_tools.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.BackupRepository
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.DrinkRepository
import com.example.xiancli_tools.data.LedgerRepository
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.data.ReviewAnalytics
import com.example.xiancli_tools.data.TrackRepository
import com.example.xiancli_tools.data.lan.LanDiscovery
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.theme.BrandDawn
import com.example.xiancli_tools.ui.theme.BrandDusk
import com.example.xiancli_tools.ui.theme.Dimens
import com.example.xiancli_tools.ui.theme.DrinkAccent
import com.example.xiancli_tools.ui.theme.LedgerAccent
import com.example.xiancli_tools.ui.theme.MijiaAccent
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToLong

/** 「朝夕」字标的渐变：左「朝」晨光，右「夕」暮色；两端点与 `ic_brand_mark` 上下两半同色。 */
private val BrandBrush = Brush.linearGradient(listOf(BrandDawn, BrandDusk))

@Composable
fun HomeScreen(
    onOpenTimer: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenTrack: () -> Unit,
    onOpenDrink: () -> Unit,
    onOpenMijia: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenNearby: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var todayExpense by remember { mutableLongStateOf(0L) }
    var pendingTodos by remember { mutableIntStateOf(0) }
    var todayDistance by remember { mutableDoubleStateOf(0.0) }
    var todayDrinks by remember { mutableIntStateOf(0) }
    // 直传会把两边数据合到一起，「今天」这几格得跟着重算。
    var dataVersion by remember { mutableIntStateOf(0) }
    var peersMenuOpen by remember { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch {
            // 新消息顶掉还在显示的那条：从「正在同步」到结果不用干等几秒。
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val nearby = rememberNearbySyncSession(remember { BackupRepository(context) }) { message ->
        // 同步结束（成功或失败）都重读一次今天的数据：成功时数字变了，失败时重读也无害。
        dataVersion++
        notify(message)
    }

    // 首页不主动起服务，点开「直传」下拉才起：省掉一进 App 就占网络，也把本地网络授权留到用户真用的时候。
    fun openPeersMenu() {
        peersMenuOpen = true
        nearby.start()
    }

    fun sendTo(peer: LanDiscovery.Peer) {
        peersMenuOpen = false
        notify("正在与「${peer.name}」同步…")
        nearby.sendTo(peer)
    }

    // 首页只取今天这几个数，进页面时读一次就够，不做常驻监听。
    LaunchedEffect(dataVersion) {
        withContext(Dispatchers.IO) {
            val range = DateRanges.day(System.currentTimeMillis())
            todayExpense = LedgerRepository(context).loadRecords()
                .filter { it.timeMillis in range.startMillis until range.endMillis }
                .sumOf { it.amountCents }
            pendingTodos = NoteRepository(context).loadNotes()
                .count { it.category == NoteCategory.TODO && !it.done }
            todayDistance = TrackRepository(context).loadSessionIndex()
                .filter { it.startMillis in range.startMillis until range.endMillis }
                .sumOf { it.distanceMeters }
            todayDrinks = DrinkRepository(context).loadRecords()
                .count { it.timeMillis in range.startMillis until range.endMillis }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceL)
                .padding(bottom = Dimens.SpaceXL)
        ) {
            Spacer(Modifier.height(Dimens.SpaceM))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 用 Image 而不是 Icon：Icon 会套上 LocalContentColor 做 tint，
                    // 把矢量里的墨底和晨昏渐变一起刷成纯黑。
                    Image(
                        painter = painterResource(R.drawable.ic_brand_mark),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "朝夕",
                        style = MaterialTheme.typography.titleLarge.copy(
                            brush = BrandBrush,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 「直传」紧挨在「备份」左边：点开就是同 Wi-Fi 下已发现的设备名，
                    // 点名字直接推过去，不用先进备份页再找附近的机器。
                    Box {
                        Surface(
                            onClick = { openPeersMenu() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_nearby),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "直传",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_down),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        NearbyPeersMenu(
                            expanded = peersMenuOpen,
                            session = nearby,
                            onSend = { sendTo(it) },
                            onOpenNearbyPage = {
                                peersMenuOpen = false
                                onOpenNearby()
                            },
                            onDismiss = { peersMenuOpen = false }
                        )
                    }
                    Surface(
                        onClick = onOpenBackup,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_backup),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "备份",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 备份右边紧跟一枚圆形切换钮：图标画的是「点了会变成什么」，
                    // 亮底给月亮、暗底给太阳，切过去时淡入淡出，不会突然跳一下。
                    Surface(
                        onClick = onToggleTheme,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = darkTheme,
                                animationSpec = tween(durationMillis = 220),
                                label = "theme-toggle"
                            ) { dark ->
                                Icon(
                                    painter = painterResource(
                                        if (dark) R.drawable.ic_theme_light else R.drawable.ic_theme_dark
                                    ),
                                    contentDescription = if (dark) "切换到浅色" else "切换到深色",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.SpaceM))
            TodayOverviewCard(
                expenseCents = todayExpense,
                pendingTodos = pendingTodos,
                distanceMeters = todayDistance,
                drinkCount = todayDrinks,
                onClick = onOpenReview
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            ToolCard(
                title = "计时",
                subtitle = "10 / 25 / 50 分钟，也可自定义",
                icon = R.drawable.ic_tool_timer,
                accent = TimerAccent,
                onClick = onOpenTimer
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            ToolCard(
                title = "备忘",
                subtitle = "随手记，到点提醒，待办能勾掉",
                icon = R.drawable.ic_tool_note,
                accent = NoteAccent,
                onClick = onOpenNote
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            ToolCard(
                title = "账本",
                subtitle = "快速记账，区间支出报告",
                icon = R.drawable.ic_tool_ledger,
                accent = LedgerAccent,
                onClick = onOpenLedger
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            ToolCard(
                title = "足迹",
                subtitle = "步行 · 骑车 · 开车轨迹地图",
                icon = R.drawable.ic_tool_route,
                accent = TrackAccent,
                onClick = onOpenTrack
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            ToolCard(
                title = "饮记",
                subtitle = "喝一次点一下，看每天的各类次数",
                icon = R.drawable.ic_tool_drink,
                accent = DrinkAccent,
                onClick = onOpenDrink
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            ToolCard(
                title = "米家",
                subtitle = "开关灯、调亮度、一键全关",
                icon = R.drawable.ic_tool_mijia,
                accent = MijiaAccent,
                onClick = onOpenMijia
            )
        }
    }

    // 对方从首页或直传页点到这台设备时，这里也得弹确认，不能只在直传页里接。
    IncomingSyncRequestDialog(nearby)
}

/**
 * 首页「直传」下拉：列出同一 Wi-Fi 下已发现的设备，点名字直接推过去。
 *
 * 列表只会有「正在搜索」这一种空态，所以状态文案直接复用会话里的 [NearbySyncSession.status]，
 * 不用再编一套「搜索中 / 已就绪 / 失败」的措辞。
 */
@Composable
private fun NearbyPeersMenu(
    expanded: Boolean,
    session: NearbySyncSession,
    onSend: (LanDiscovery.Peer) -> Unit,
    onOpenNearbyPage: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (session.peers.isEmpty()) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = session.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "另一台设备也要点一下「直传」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                onClick = {},
                enabled = false
            )
        } else {
            session.peers.forEach { peer ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = peer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${peer.host}:${peer.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { onSend(peer) }
                )
            }
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("重新搜索") },
            onClick = { session.refresh() }
        )
        DropdownMenuItem(
            text = { Text("打开附近直传页…") },
            onClick = onOpenNearbyPage
        )
    }
}

/** 首页「今天」：四个最常看的数，点一下进回顾页看全貌。 */
@Composable
private fun TodayOverviewCard(
    expenseCents: Long,
    pendingTodos: Int,
    distanceMeters: Double,
    drinkCount: Int,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(Dimens.CornerL),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(Dimens.CornerL)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "看回顾",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(Dimens.SpaceM))
            // 四格一行：每格垫一层工具色的淡底，一眼把数字和对应工具对上；
            // 值都按最紧凑的写法给（分 / 公里 / 次 / 条），免得长串在窄列里折行。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
            ) {
                OverviewCell(
                    modifier = Modifier.weight(1f),
                    label = "支出",
                    value = compactMoney(expenseCents),
                    accent = LedgerAccent
                )
                OverviewCell(
                    modifier = Modifier.weight(1f),
                    label = "待办",
                    value = "$pendingTodos 条",
                    accent = NoteAccent
                )
                OverviewCell(
                    modifier = Modifier.weight(1f),
                    label = "足迹",
                    value = compactDistance(distanceMeters),
                    accent = TrackAccent
                )
                OverviewCell(
                    modifier = Modifier.weight(1f),
                    label = "饮记",
                    value = "$drinkCount 次",
                    accent = DrinkAccent
                )
            }
        }
    }
}

/** 首页五格一行放不下「¥1234.50」，上万后折成「¥1.2万」。 */
private fun compactMoney(cents: Long): String =
    if (cents >= 1_000_000L) {
        String.format(Locale.US, "¥%.1f万", cents / 1_000_000.0)
    } else {
        ReviewAnalytics.formatMoney(cents)
    }

/** 里程一位小数就够，`formatDistance` 的两位在这里会折行。 */
private fun compactDistance(meters: Double): String = when {
    meters < 1 -> "0 米"
    meters < 1000 -> "${meters.roundToLong()} 米"
    else -> String.format(Locale.US, "%.1f 公里", meters / 1000.0)
}

@Composable
private fun OverviewCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(Dimens.CornerS))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1
        )
    }
}

/**
 * 工具卡：单列通栏，横排布局（徽章在左、名称与说明在右、箭头收尾）。
 * 徽章用工具色 18% 淡底的圆角方块，一卡一色。
 */
@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: Int,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(Dimens.CornerL),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(Dimens.CornerL)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
        ) {
            IconBadge(
                icon = icon,
                tint = accent,
                container = accent.copy(alpha = 0.18f),
                size = 48.dp,
                iconSize = 24.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(Dimens.SpaceXS))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
