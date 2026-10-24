package com.example.xiancli_tools.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.DateRange
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.FocusSummary
import com.example.xiancli_tools.data.RingtoneResolver
import com.example.xiancli_tools.data.SettingsRepository
import com.example.xiancli_tools.data.TimerAnalytics
import com.example.xiancli_tools.data.TimerPeriod
import com.example.xiancli_tools.data.TimerSession
import com.example.xiancli_tools.data.TimerSessionRepository
import com.example.xiancli_tools.timer.TimerScheduler
import com.example.xiancli_tools.ui.components.BarChart
import com.example.xiancli_tools.ui.components.BarDatum
import com.example.xiancli_tools.ui.components.ConfirmDeleteDialog
import com.example.xiancli_tools.ui.components.CustomRangeBar
import com.example.xiancli_tools.ui.components.EmptyReportCard
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.MetricTile
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.components.SegmentedTabs
import com.example.xiancli_tools.ui.components.showDatePicker
import com.example.xiancli_tools.ui.theme.ClayRed
import com.example.xiancli_tools.ui.theme.MutedTeal
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.StatsAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.WarmGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class TimerOption(
    val label: String,
    val minutes: Int,
    val caption: String,
    val accent: Color,
    val icon: Int = R.drawable.ic_alarm,
    val custom: Boolean = false
)

private val timerOptions = listOf(
    TimerOption("10 分钟", 10, "冥想 · 放松", StatsAccent),
    TimerOption("25 分钟", 25, "番茄专注", TimerAccent),
    TimerOption("50 分钟", 50, "深度工作", NoteAccent),
    TimerOption("自定义", 0, "自由设置", MutedTeal, R.drawable.ic_add, custom = true)
)

/** 计时页的三块内容：开始计时、明细与统计。 */
private enum class TimerTab(val label: String) {
    QUICK("计时"),
    DETAIL("明细"),
    REPORT("统计")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(TimerTab.QUICK) }
    var endAt by remember { mutableLongStateOf(repository.scheduledEndAt) }
    var activeLabel by remember { mutableStateOf(repository.scheduledLabel) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCustomDialog by remember { mutableStateOf(false) }

    val sessionRepository = remember { TimerSessionRepository(context) }
    var sessions by remember { mutableStateOf<List<TimerSession>>(emptyList()) }
    val reloadSessions: () -> Unit = {
        scope.launch {
            sessions = withContext(Dispatchers.IO) { sessionRepository.loadSessions() }
        }
    }
    LaunchedEffect(Unit) { reloadSessions() }

    // 明细与统计共用一份周期选择；计时记录比较稀，默认看本周比看今天有用。
    var period by remember { mutableStateOf(TimerPeriod.WEEK) }
    var customRange by remember { mutableStateOf(DateRanges.lastDays()) }
    var pendingDelete by remember { mutableStateOf<TimerSession?>(null) }

    val summary = remember(sessions, period, customRange) {
        TimerAnalytics.summarize(sessions, period, customRange = customRange)
    }

    val quickScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val reportScroll = rememberScrollState()

    val scheduleDuration: (Long, String) -> Unit = { millis, label ->
        TimerScheduler.schedule(context, millis, label)
        endAt = System.currentTimeMillis() + millis
        activeLabel = label
        now = System.currentTimeMillis()
        scope.launch { snackbarHostState.showSnackbar("已设置 $label 定时") }
    }
    val alarmManager = remember {
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }
    var exactAllowed by remember { mutableStateOf(TimerScheduler.canScheduleExact(alarmManager)) }

    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        exactAllowed = TimerScheduler.canScheduleExact(alarmManager)
    }

    LaunchedEffect(endAt) {
        if (endAt <= 0L) return@LaunchedEffect
        while (isActive) {
            now = System.currentTimeMillis()
            if (now >= endAt) {
                endAt = 0L
                activeLabel = null
                // 计时自然走完时记录刚落库，顺手刷新一下明细和统计。
                reloadSessions()
                break
            }
            delay(500)
        }
    }

    val vibrationEnabled = repository.vibrationEnabled
    val ringtoneTitle = RingtoneResolver.resolveTitle(context, repository)

    fun deleteSession(session: TimerSession) {
        scope.launch {
            withContext(Dispatchers.IO) { sessionRepository.deleteSession(session.id) }
            sessions = withContext(Dispatchers.IO) { sessionRepository.loadSessions() }
            snackbarHostState.showSnackbar("已删除这条记录")
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
                title = { Text("计时", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "通知设置"
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
                labels = TimerTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { tab = TimerTab.entries[it] },
                accent = TimerAccent,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
            )

            when (tab) {
                TimerTab.QUICK -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(quickScroll)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    if (!exactAllowed) {
                        ExactAlarmBanner(
                            onEnable = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    exactAlarmLauncher.launch(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                    )
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (endAt > 0L) {
                        ActiveTimerCard(
                            label = activeLabel ?: "定时",
                            remainingMillis = (endAt - now).coerceAtLeast(0L),
                            onCancel = {
                                TimerScheduler.cancel(context)
                                endAt = 0L
                                activeLabel = null
                                reloadSessions()
                                scope.launch { snackbarHostState.showSnackbar("已取消定时") }
                            }
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    Text(
                        text = "选择定时时长",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))

                    timerOptions.chunked(2).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowOptions.forEach { option ->
                                DurationCard(
                                    option = option,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (option.custom) {
                                            showCustomDialog = true
                                        } else {
                                            scheduleDuration(option.minutes * 60_000L, option.label)
                                        }
                                    }
                                )
                            }
                            if (rowOptions.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Spacer(Modifier.height(8.dp))
                    NotificationMethodCard(
                        vibrationEnabled = vibrationEnabled,
                        ringtoneTitle = ringtoneTitle,
                        onConfigure = onOpenSettings
                    )

                    if (showCustomDialog) {
                        CustomDurationDialog(
                            onDismiss = { showCustomDialog = false },
                            onConfirm = { millis, label ->
                                showCustomDialog = false
                                scheduleDuration(millis, label)
                            }
                        )
                    }
                }

                TimerTab.DETAIL -> Column(
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
                    SessionDetailList(
                        summary = summary,
                        onDelete = { pendingDelete = it }
                    )
                }

                TimerTab.REPORT -> Column(
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
                    ReportContent(summary = summary)
                }
            }
        }
    }

    pendingDelete?.let { session ->
        val start = formatClock(session.startMillis)
        ConfirmDeleteDialog(
            title = "删除专注记录",
            message = "确定删除「${session.label} · $start」这条记录吗？删除后无法恢复。",
            onConfirm = {
                deleteSession(session)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun DurationCard(
    option: TimerOption,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = option.accent.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(
                icon = option.icon,
                tint = option.accent,
                container = Color.White.copy(alpha = 0.75f),
                size = 56.dp,
                iconSize = 30.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = option.accent
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = option.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveTimerCard(
    label: String,
    remainingMillis: Long,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = R.drawable.ic_tool_timer,
                tint = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                size = 52.dp,
                iconSize = 28.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$label 定时进行中",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = formatRemaining(remainingMillis),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            OutlinedButton(onClick = onCancel) {
                Text("取消")
            }
        }
    }
}

/** 「日 / 周 / 月 / 年 / 自定义」周期切换：明细与统计两个标签共用同一份选择。 */
@Composable
private fun PeriodSelector(
    period: TimerPeriod,
    onSelect: (TimerPeriod) -> Unit,
    customRange: DateRange,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    SegmentedTabs(
        labels = TimerPeriod.entries.map { it.label },
        selectedIndex = period.ordinal,
        onSelect = { onSelect(TimerPeriod.entries[it]) }
    )
    if (period == TimerPeriod.CUSTOM) {
        Spacer(Modifier.height(8.dp))
        CustomRangeBar(
            range = customRange,
            accent = TimerAccent,
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
private fun SessionDetailList(
    summary: FocusSummary,
    onDelete: (TimerSession) -> Unit
) {
    if (summary.sessions.isEmpty()) {
        EmptyReportCard(
            icon = R.drawable.ic_tool_timer,
            text = "${summary.rangeName}还没有专注记录",
            accent = TimerAccent
        )
        return
    }

    // 和账本、备忘一样按自然日分组，标题带上当天总时长，不用自己一条条加。
    val grouped = summary.sessions.groupBy { DateRanges.dayKey(it.startMillis) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (_, daySessions) ->
            val heading = DateRanges.formatDayHeading(daySessions.first().startMillis)
            val dayTotal = daySessions.sumOf { it.focusMillis }
            SectionCard(title = "$heading · ${TimerAnalytics.formatCompact(dayTotal)}") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    daySessions.forEach { session ->
                        SessionRow(
                            session = session,
                            onDelete = { onDelete(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: TimerSession,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            icon = R.drawable.ic_tool_timer,
            tint = if (session.completed) TimerAccent else WarmGray,
            container = (if (session.completed) TimerAccent else WarmGray).copy(alpha = 0.14f),
            size = 40.dp,
            iconSize = 22.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatClock(session.startMillis)} – ${formatClock(session.endMillis)}" +
                    " · " + if (session.completed) "已完成" else "中断",
                style = MaterialTheme.typography.bodySmall,
                color = if (session.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    ClayRed
                }
            )
        }
        Text(
            text = TimerAnalytics.formatCompact(session.focusMillis),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (session.completed) TimerAccent else MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun ReportContent(summary: FocusSummary) {
    if (summary.sessions.isEmpty()) {
        EmptyReportCard(
            icon = R.drawable.ic_tool_timer,
            text = "${summary.rangeName}还没有专注记录",
            accent = TimerAccent
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalsCard(summary)
        TrendCard(summary)
        HourSlotCard(summary)
    }
}

@Composable
private fun TotalsCard(summary: FocusSummary) {
    SectionCard(title = "${summary.rangeName}专注") {
        Text(
            text = DateRanges.formatHeading(summary.range),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = TimerAnalytics.formatDuration(summary.totalMillis),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TimerAccent
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${summary.activeDays} 天有记录 · 完成 ${summary.completedCount} / " +
                "${summary.sessionCount} 次",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "完成率",
                value = "${(summary.completionRate * 100).roundToInt()}%",
                accent = TimerAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "平均每次",
                value = TimerAnalytics.formatCompact(summary.averageMillis),
                accent = StatsAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "最长一次",
                value = TimerAnalytics.formatCompact(summary.longestMillis),
                accent = NoteAccent
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "中断",
                value = "${summary.interruptedCount} 次",
                accent = if (summary.interruptedCount > 0) ClayRed else TimerAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "活跃天数",
                value = "${summary.activeDays} 天",
                accent = TimerAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "计划总时长",
                value = TimerAnalytics.formatCompact(summary.plannedMillis),
                accent = TimerAccent
            )
        }
    }
}

@Composable
private fun TrendCard(summary: FocusSummary) {
    val trend = remember(summary) { TimerAnalytics.trend(summary) }
    SectionCard(title = "专注趋势（分钟）") {
        BarChart(
            bars = trend.map { BarDatum(it.label, it.minutes) },
            color = TimerAccent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = peakDescription(summary) ?: "这段时间还没有专注记录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HourSlotCard(summary: FocusSummary) {
    val slots = remember(summary) { TimerAnalytics.hourSlots(summary) }
    SectionCard(title = "时段分布（分钟）") {
        BarChart(
            bars = slots.map { BarDatum(it.label, it.minutes) },
            color = StatsAccent,
            height = 96.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (summary.bestHour >= 0) {
                "最专注的时段在 ${summary.bestHour} 点前后"
            } else {
                "开始一次计时，这里会记下你的专注时段"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 趋势里最高的那根柱子，翻译成「周三 90 分钟」这种能读懂的话。 */
private fun peakDescription(summary: FocusSummary): String? {
    val trend = TimerAnalytics.trend(summary)
    val index = trend.indices.maxByOrNull { trend[it].minutes } ?: return null
    val peak = trend[index]
    if (peak.minutes <= 0f) return null
    val name = when (summary.period) {
        TimerPeriod.DAY -> "${index} 点前后"
        TimerPeriod.WEEK -> "周${peak.label}"
        TimerPeriod.MONTH -> "${index + 1} 日"
        TimerPeriod.YEAR -> "${index + 1} 月"
        TimerPeriod.CUSTOM -> peak.label.ifEmpty { "所选区间里" }
    }
    return "$name 最专注 · ${TimerAnalytics.formatCompact((peak.minutes * 60_000f).toLong())}"
}

@Composable
private fun NotificationMethodCard(
    vibrationEnabled: Boolean,
    ringtoneTitle: String,
    onConfigure: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "通知方式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onConfigure) {
                    Text("更改")
                }
            }
            Spacer(Modifier.height(8.dp))
            MethodRow(
                icon = R.drawable.ic_vibration,
                title = "震动",
                value = if (vibrationEnabled) "已开启" else "已关闭"
            )
            Spacer(Modifier.height(8.dp))
            MethodRow(
                icon = R.drawable.ic_ringtone,
                title = "铃声",
                value = ringtoneTitle
            )
        }
    }
}

@Composable
private fun MethodRow(icon: Int, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExactAlarmBanner(onEnable: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "未开启精确闹钟，定时可能略有延迟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEnable) {
                Text("去开启", color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun CustomDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }

    val hourValue = hours.toIntOrNull() ?: 0
    val minuteValue = minutes.toIntOrNull() ?: 0
    val secondValue = seconds.toIntOrNull() ?: 0
    val totalSeconds = hourValue * 3600 + minuteValue * 60 + secondValue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义时长") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DurationField(
                        value = hours,
                        suffix = "时",
                        modifier = Modifier.weight(1f),
                        onValueChange = { hours = it }
                    )
                    DurationField(
                        value = minutes,
                        suffix = "分",
                        modifier = Modifier.weight(1f),
                        onValueChange = { minutes = it }
                    )
                    DurationField(
                        value = seconds,
                        suffix = "秒",
                        modifier = Modifier.weight(1f),
                        onValueChange = { seconds = it }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (totalSeconds > 0) {
                        "共计 ${formatCustomLabel(hourValue, minuteValue, secondValue)}"
                    } else {
                        "至少填写一项，例如「30 分」"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        totalSeconds * 1000L,
                        formatCustomLabel(hourValue, minuteValue, secondValue)
                    )
                },
                enabled = totalSeconds > 0
            ) {
                Text("开始")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DurationField(
    value: String,
    suffix: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter(Char::isDigit).take(3))
        },
        modifier = modifier,
        singleLine = true,
        label = { Text(suffix) },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun formatCustomLabel(hours: Int, minutes: Int, seconds: Int): String {
    val parts = buildList {
        if (hours > 0) add("$hours 小时")
        if (minutes > 0) add("$minutes 分钟")
        if (seconds > 0) add("$seconds 秒")
    }
    return parts.joinToString(" ")
}

private fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** 明细按天分组后，行内只需要时分——日期已经写在分组标题上了。 */
private fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
