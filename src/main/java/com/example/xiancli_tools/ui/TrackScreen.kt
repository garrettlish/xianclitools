package com.example.xiancli_tools.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.DateRange
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.PeriodSummary
import com.example.xiancli_tools.data.TrackAnalytics
import com.example.xiancli_tools.data.TrackBrief
import com.example.xiancli_tools.data.TrackPeriod
import com.example.xiancli_tools.data.TrackRepository
import com.example.xiancli_tools.data.TransportMode
import com.example.xiancli_tools.track.RecorderSnapshot
import com.example.xiancli_tools.track.TrackRecorderService
import com.example.xiancli_tools.track.TrackRecorderState
import com.example.xiancli_tools.ui.components.ChartSlice
import com.example.xiancli_tools.ui.components.ConfirmDeleteDialog
import com.example.xiancli_tools.ui.components.CustomRangeBar
import com.example.xiancli_tools.ui.components.DonutChart
import com.example.xiancli_tools.ui.components.EmptyReportCard
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.MetricTile
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.components.SegmentedTabs
import com.example.xiancli_tools.ui.components.showDatePicker
import com.example.xiancli_tools.ui.theme.ClayRed
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 足迹页的三块内容：记录、明细与统计。 */
private enum class TrackTab(val label: String) {
    RECORD("记录"),
    DETAIL("明细"),
    REPORT("统计")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    onBack: () -> Unit,
    /** 只传 id：地图页自己去仓库取点阵，这里不该把坐标读进内存。 */
    onOpenMap: (List<Long>) -> Unit,
    /** 足迹页会被地图页进出打断，tab 由外层记住，返回时恢复原标签。 */
    initialTabOrdinal: Int = TrackTab.RECORD.ordinal,
    onTabChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { TrackRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val snapshot by TrackRecorderState.state.collectAsState()
    var tab by remember {
        mutableStateOf(TrackTab.entries.getOrElse(initialTabOrdinal) { TrackTab.RECORD })
    }
    var sessions by remember { mutableStateOf<List<TrackBrief>>(emptyList()) }
    var selectedMode by remember { mutableStateOf(TransportMode.WALK) }
    // 统计默认落在当月（而不是今天），进门就能看到整月的轨迹。
    var period by remember { mutableStateOf(TrackPeriod.MONTH) }
    var customRange by remember { mutableStateOf(DateRanges.lastDays()) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var pendingMode by remember { mutableStateOf<TransportMode?>(null) }
    var pendingDelete by remember { mutableStateOf<TrackBrief?>(null) }

    val recordScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val reportScroll = rememberScrollState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        val mode = pendingMode
        pendingMode = null
        if (granted && mode != null) {
            startRecording(context, mode, scope, snackbarHostState)
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("需要定位权限才能记录轨迹") }
        }
    }

    LaunchedEffect(snapshot.recording) {
        if (snapshot.recording) return@LaunchedEffect
        delay(200)
        val (loaded, recovered) = withContext(Dispatchers.IO) {
            val repo = TrackRepository(context)
            val active = repo.activeSession()
            if (active != null && !TrackRecorderState.state.value.recording) {
                repo.appendSession(
                    active.copy(
                        endMillis = active.points.lastOrNull()?.timeMillis
                            ?: active.startMillis
                    )
                )
                repo.clearActiveSession()
            }
            repo.loadSessionIndex() to (active != null)
        }
        sessions = loaded
        if (recovered) {
            snackbarHostState.showSnackbar("已保存上次未完成的记录")
        }
    }

    LaunchedEffect(snapshot.recording, snapshot.startMillis) {
        if (snapshot.recording) {
            val start = snapshot.startMillis
            while (isActive) {
                elapsed = System.currentTimeMillis() - start
                delay(500)
            }
        } else {
            elapsed = 0L
        }
    }

    val summary = remember(sessions, period, customRange) {
        TrackAnalytics.summarize(sessions, period, customRange = customRange)
    }

    fun delete(session: TrackBrief) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.deleteSession(session.id) }
            sessions = withContext(Dispatchers.IO) { repository.loadSessionIndex() }
            snackbarHostState.showSnackbar("已删除")
        }
    }

    fun pickRangeStart() {
        showDatePicker(context, customRange.startMillis) { picked ->
            customRange = DateRanges.ofDays(picked, maxOf(picked, customRange.lastDayMillis))
        }
    }

    fun pickRangeEnd() {
        showDatePicker(context, customRange.lastDayMillis) { picked ->
            customRange = DateRanges.ofDays(minOf(picked, customRange.startMillis), picked)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("足迹", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SegmentedTabs(
                labels = TrackTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = {
                    tab = TrackTab.entries[it]
                    onTabChanged(it)
                },
                accent = TrackAccent,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
            )

            when (tab) {
                TrackTab.RECORD -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(recordScroll)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    if (snapshot.recording) {
                        ActiveRecordingCard(
                            snapshot = snapshot,
                            elapsed = elapsed,
                            onStop = {
                                stopRecording(context)
                                scope.launch { snackbarHostState.showSnackbar("已结束记录") }
                            }
                        )
                    } else {
                        ModeSelector(
                            selected = selectedMode,
                            onSelect = { selectedMode = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val fine = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val coarse = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (fine || coarse) {
                                    startRecording(context, selectedMode, scope, snackbarHostState)
                                } else {
                                    pendingMode = selectedMode
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = selectedMode.accent
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play_arrow),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("开始记录${selectedMode.label}")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "记录中可锁屏或切到后台，轨迹会继续记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TrackTab.DETAIL -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(detailScroll)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    PeriodSelector(
                        period = period,
                        onSelect = { period = it },
                        customRange = customRange,
                        onPickStart = { pickRangeStart() },
                        onPickEnd = { pickRangeEnd() }
                    )
                    Spacer(Modifier.height(12.dp))
                    TrackDetailList(
                        summary = summary,
                        onOpenMap = onOpenMap,
                        onDelete = { pendingDelete = it }
                    )
                }

                TrackTab.REPORT -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(reportScroll)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    PeriodSelector(
                        period = period,
                        onSelect = { period = it },
                        customRange = customRange,
                        onPickStart = { pickRangeStart() },
                        onPickEnd = { pickRangeEnd() }
                    )
                    Spacer(Modifier.height(12.dp))
                    SummaryContent(summary = summary)
                }
            }
        }
    }

    pendingDelete?.let { session ->
        val label = "${session.mode.label} ${formatSessionTime(session.startMillis, period)}"
        ConfirmDeleteDialog(
            title = "删除轨迹",
            message = "确定删除 $label 的轨迹吗？删除后无法恢复。",
            onConfirm = {
                delete(session)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

private fun startRecording(
    context: android.content.Context,
    mode: TransportMode,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val intent = Intent(context, TrackRecorderService::class.java).apply {
        action = TrackRecorderService.ACTION_START
        putExtra(TrackRecorderService.EXTRA_MODE, mode.name)
    }
    runCatching {
        ContextCompat.startForegroundService(context, intent)
    }.onFailure {
        scope.launch { snackbarHostState.showSnackbar("无法启动记录服务") }
    }
}

private fun stopRecording(context: android.content.Context) {
    val intent = Intent(context, TrackRecorderService::class.java).apply {
        action = TrackRecorderService.ACTION_STOP
    }
    runCatching { context.startService(intent) }
}

@Composable
private fun ModeSelector(
    selected: TransportMode,
    onSelect: (TransportMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TransportMode.entries.forEach { mode ->
            ModeCard(
                mode = mode,
                selected = mode == selected,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(mode) }
            )
        }
    }
}

@Composable
private fun ModeCard(
    mode: TransportMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = mode.accent
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(
                icon = mode.iconRes,
                tint = if (selected) Color.White else accent,
                container = if (selected) accent else accent.copy(alpha = 0.14f),
                size = 48.dp,
                iconSize = 26.dp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = mode.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActiveRecordingCard(
    snapshot: RecorderSnapshot,
    elapsed: Long,
    onStop: () -> Unit
) {
    val accent = snapshot.mode.accent
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = snapshot.mode.iconRes,
                    tint = Color.White,
                    container = accent,
                    size = 48.dp,
                    iconSize = 26.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "正在记录${snapshot.mode.label}轨迹",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "已采集 ${snapshot.pointCount} 个定位点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ClayRed, CircleShape)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "时长",
                    value = TrackAnalytics.formatClock(elapsed),
                    accent = accent,
                    container = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "里程",
                    value = TrackAnalytics.formatDistance(snapshot.distanceMeters),
                    accent = accent,
                    container = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stop),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("结束记录")
            }
        }
    }
}

/** 「日 / 周 / 月 / 年 / 自定义」周期切换：明细与统计两个标签共用同一份选择。 */
@Composable
private fun PeriodSelector(
    period: TrackPeriod,
    onSelect: (TrackPeriod) -> Unit,
    customRange: DateRange,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    SegmentedTabs(
        labels = TrackPeriod.entries.map { it.label },
        selectedIndex = period.ordinal,
        onSelect = { onSelect(TrackPeriod.entries[it]) }
    )
    if (period == TrackPeriod.CUSTOM) {
        Spacer(Modifier.height(8.dp))
        CustomRangeBar(
            range = customRange,
            accent = TrackAccent,
            onPickStart = onPickStart,
            onPickEnd = onPickEnd
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "共 ${customRange.dayCount} 天",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SummaryContent(summary: PeriodSummary) {
    if (summary.sessions.isEmpty()) {
        EmptyReportCard(
            icon = R.drawable.ic_tool_route,
            text = "${summary.rangeName}还没有轨迹记录",
            accent = TrackAccent
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalsCard(summary)
        ModeBreakdownCard(summary)
    }
}

@Composable
private fun TotalsCard(summary: PeriodSummary) {
    SectionCard(title = "${summary.rangeName}汇总") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "总里程",
                value = TrackAnalytics.formatDistance(summary.totalDistanceMeters),
                accent = TrackAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "总时长",
                value = TrackAnalytics.formatDuration(summary.totalDurationMillis),
                accent = TrackAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "记录次数",
                value = "${summary.sessions.size} 次",
                accent = TrackAccent
            )
        }
    }
}

@Composable
private fun ModeBreakdownCard(summary: PeriodSummary) {
    val modes = summary.modeSummaries
    val maxDistance = modes.maxOfOrNull { it.distanceMeters }?.takeIf { it > 0 } ?: 1.0
    val totalDuration = summary.totalDurationMillis.coerceAtLeast(1L)

    SectionCard(title = "按交通方式") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = modes.map {
                    ChartSlice(it.durationMillis.toFloat(), it.mode.accent)
                },
                modifier = Modifier.size(120.dp),
                strokeWidth = 20.dp,
                center = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${summary.sessions.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "次记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                modes.forEach { modeSummary ->
                    val share = (modeSummary.durationMillis.toFloat() / totalDuration * 100)
                        .roundToInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(modeSummary.mode.accent, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = modeSummary.mode.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$share%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        modes.forEach { modeSummary ->
            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(modeSummary.mode.iconRes),
                        contentDescription = null,
                        tint = modeSummary.mode.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = modeSummary.mode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = TrackAnalytics.formatDistance(modeSummary.distanceMeters),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = modeSummary.mode.accent
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                (modeSummary.distanceMeters / maxDistance)
                                    .toFloat()
                                    .coerceIn(0.02f, 1f)
                            )
                            .height(6.dp)
                            .background(modeSummary.mode.accent, RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${TrackAnalytics.formatDuration(modeSummary.durationMillis)} · " +
                        "${modeSummary.sessionCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrackDetailList(
    summary: PeriodSummary,
    onOpenMap: (List<Long>) -> Unit,
    onDelete: (TrackBrief) -> Unit
) {
    if (summary.sessions.isEmpty()) {
        EmptyReportCard(
            icon = R.drawable.ic_tool_route,
            text = "${summary.rangeName}还没有轨迹记录",
            accent = TrackAccent
        )
        return
    }

    // 明细按自然日分组，标题给出当天的里程与次数，正好对上「今天走了多少」的直觉。
    val grouped = summary.sessions.groupBy { DateRanges.dayKey(it.startMillis) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (_, daySessions) ->
            val heading = DateRanges.formatDayHeading(daySessions.first().startMillis)
            val dayDistance = daySessions.sumOf { it.distanceMeters }
            SectionCard(
                title = "$heading · ${TrackAnalytics.formatDistance(dayDistance)} · " +
                    "${daySessions.size} 次"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    daySessions.forEach { session ->
                        SessionRow(
                            session = session,
                            timeText = formatClock(session.startMillis),
                            onClick = { onOpenMap(listOf(session.id)) },
                            onDelete = { onDelete(session) }
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onOpenMap(summary.sessions.map { it.id }) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_map),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("在地图上查看${summary.rangeName}全部轨迹")
        }
    }
}

@Composable
private fun SessionRow(
    session: TrackBrief,
    timeText: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            icon = session.mode.iconRes,
            tint = session.mode.accent,
            container = session.mode.accent.copy(alpha = 0.14f),
            size = 40.dp,
            iconSize = 22.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${session.mode.label} · $timeText",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${TrackAnalytics.formatDistance(session.distanceMeters)} · " +
                    TrackAnalytics.formatDuration(session.durationMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = "查看轨迹",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatSessionTime(startMillis: Long, period: TrackPeriod): String {
    val pattern = when (period) {
        TrackPeriod.DAY -> "HH:mm"
        TrackPeriod.WEEK -> "E HH:mm"
        TrackPeriod.MONTH -> "MM-dd HH:mm"
        TrackPeriod.YEAR -> "MM-dd HH:mm"
        TrackPeriod.CUSTOM -> "MM-dd HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(startMillis))
}

/** 明细按天分组后，行内只需要时分——日期已经写在分组标题上了。 */
private fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
