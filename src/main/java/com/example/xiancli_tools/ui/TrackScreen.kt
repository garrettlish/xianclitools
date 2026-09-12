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
import androidx.compose.foundation.layout.ColumnScope
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
import com.example.xiancli_tools.data.TrackAnalytics
import com.example.xiancli_tools.data.TrackPeriod
import com.example.xiancli_tools.data.TrackRepository
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.data.TransportMode
import com.example.xiancli_tools.track.RecorderSnapshot
import com.example.xiancli_tools.track.TrackRecorderService
import com.example.xiancli_tools.track.TrackRecorderState
import com.example.xiancli_tools.ui.components.ChartSlice
import com.example.xiancli_tools.ui.components.DonutChart
import com.example.xiancli_tools.ui.components.IconBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val TrackAccent = Color(0xFF00897B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    onBack: () -> Unit,
    onOpenMap: (List<TrackSession>) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TrackRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val snapshot by TrackRecorderState.state.collectAsState()
    var sessions by remember { mutableStateOf<List<TrackSession>>(emptyList()) }
    var selectedMode by remember { mutableStateOf(TransportMode.WALK) }
    var period by remember { mutableStateOf(TrackPeriod.DAY) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var pendingMode by remember { mutableStateOf<TransportMode?>(null) }

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
            repo.loadSessions() to (active != null)
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

    val summary = remember(sessions, period) {
        TrackAnalytics.summarize(sessions, period)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("轨迹记录", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(20.dp))
            Text(
                text = "统计总结",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            PeriodSelector(selected = period, onSelect = { period = it })
            Spacer(Modifier.height(12.dp))
            SummaryContent(
                summary = summary,
                period = period,
                onOpenMap = onOpenMap
            )
        }
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceVariant
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
        shape = RoundedCornerShape(20.dp),
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
                        .background(Color(0xFFEA4335), CircleShape)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecordingMetric(
                    modifier = Modifier.weight(1f),
                    label = "时长",
                    value = TrackAnalytics.formatClock(elapsed),
                    accent = accent
                )
                RecordingMetric(
                    modifier = Modifier.weight(1f),
                    label = "里程",
                    value = TrackAnalytics.formatDistance(snapshot.distanceMeters),
                    accent = accent
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

@Composable
private fun RecordingMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

@Composable
private fun PeriodSelector(
    selected: TrackPeriod,
    onSelect: (TrackPeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(14.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TrackPeriod.entries.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) MaterialTheme.colorScheme.surface
                        else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryContent(
    summary: com.example.xiancli_tools.data.PeriodSummary,
    period: TrackPeriod,
    onOpenMap: (List<TrackSession>) -> Unit
) {
    if (summary.sessions.isEmpty()) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconBadge(
                    icon = R.drawable.ic_tool_route,
                    tint = TrackAccent,
                    container = TrackAccent.copy(alpha = 0.14f),
                    size = 56.dp,
                    iconSize = 28.dp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "本${period.label}还没有轨迹记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalsCard(summary)
        ModeBreakdownCard(summary)
        SessionListCard(summary, onOpenMap)
    }
}

@Composable
private fun TotalsCard(summary: com.example.xiancli_tools.data.PeriodSummary) {
    SectionCard(title = "本${summary.period.label}汇总") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecordingMetric(
                modifier = Modifier.weight(1f),
                label = "总里程",
                value = TrackAnalytics.formatDistance(summary.totalDistanceMeters),
                accent = TrackAccent
            )
            RecordingMetric(
                modifier = Modifier.weight(1f),
                label = "总时长",
                value = TrackAnalytics.formatDuration(summary.totalDurationMillis),
                accent = TrackAccent
            )
            RecordingMetric(
                modifier = Modifier.weight(1f),
                label = "记录次数",
                value = "${summary.sessions.size} 次",
                accent = TrackAccent
            )
        }
    }
}

@Composable
private fun ModeBreakdownCard(summary: com.example.xiancli_tools.data.PeriodSummary) {
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
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
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
private fun SessionListCard(
    summary: com.example.xiancli_tools.data.PeriodSummary,
    onOpenMap: (List<TrackSession>) -> Unit
) {
    SectionCard(title = "轨迹明细") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.sessions.forEach { session ->
                SessionRow(
                    session = session,
                    period = summary.period,
                    onClick = { onOpenMap(listOf(session)) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onOpenMap(summary.sessions) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_map),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("在地图上查看本${summary.period.label}全部轨迹")
        }
    }
}

@Composable
private fun SessionRow(
    session: TrackSession,
    period: TrackPeriod,
    onClick: () -> Unit
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
                text = "${session.mode.label} · ${formatSessionTime(session.startMillis, period)}",
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
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun formatSessionTime(startMillis: Long, period: TrackPeriod): String {
    val pattern = when (period) {
        TrackPeriod.DAY -> "HH:mm"
        TrackPeriod.WEEK -> "E HH:mm"
        TrackPeriod.MONTH -> "MM-dd HH:mm"
        TrackPeriod.YEAR -> "MM-dd HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(startMillis))
}
