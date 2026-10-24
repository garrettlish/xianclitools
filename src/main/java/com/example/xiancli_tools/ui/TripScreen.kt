package com.example.xiancli_tools.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.Identity
import com.example.xiancli_tools.data.LedgerRepository
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.data.ReviewAnalytics
import com.example.xiancli_tools.data.TrackAnalytics
import com.example.xiancli_tools.data.TrackRepository
import com.example.xiancli_tools.data.TrackBrief
import com.example.xiancli_tools.data.Trip
import com.example.xiancli_tools.data.TripAnalytics
import com.example.xiancli_tools.data.TripCandidate
import com.example.xiancli_tools.data.TripRepository
import com.example.xiancli_tools.data.TripSummary
import com.example.xiancli_tools.ui.components.ConfirmDeleteDialog
import com.example.xiancli_tools.ui.components.EmptyReportCard
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.MetricTile
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.components.showDatePicker
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/**
 * 出行：不搬动账本和足迹，只按日期区间把它们框成一次行程。
 *
 * 这样「这次出去玩花了多少、走了多少路」是现算的，
 * 事后补记一笔账，行程里的数字会跟着变，不用手动同步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val tripRepository = remember { TripRepository(context) }
    val ledgerRepository = remember { LedgerRepository(context) }
    val trackRepository = remember { TrackRepository(context) }
    val noteRepository = remember { NoteRepository(context) }

    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var records by remember { mutableStateOf<List<ExpenseRecord>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<TrackBrief>>(emptyList()) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Trip?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            trips = tripRepository.loadTrips()
            records = ledgerRepository.loadRecords()
            sessions = trackRepository.loadSessionIndex()
            notes = noteRepository.loadNotes()
        }
    }

    fun reloadTrips() {
        scope.launch {
            trips = withContext(Dispatchers.IO) { tripRepository.loadTrips() }
        }
    }

    fun save(name: String, startMillis: Long, endMillis: Long) {
        val trip = Trip(
            id = Identity.newId(),
            name = name.ifBlank { defaultTripName(startMillis) },
            startMillis = DateRanges.startOfDay(startMillis),
            endMillis = DateRanges.startOfDay(endMillis)
        )
        scope.launch {
            withContext(Dispatchers.IO) { tripRepository.saveTrip(trip) }
            reloadTrips()
            showCreate = false
            snackbarHostState.showSnackbar("已创建「${trip.name}」")
        }
    }

    fun saveCandidate(candidate: TripCandidate) {
        val trip = Trip(
            id = Identity.newId(),
            name = candidate.name,
            startMillis = candidate.startMillis,
            endMillis = candidate.endMillis
        )
        scope.launch {
            withContext(Dispatchers.IO) { tripRepository.saveTrip(trip) }
            reloadTrips()
            snackbarHostState.showSnackbar("已保存「${trip.name}」")
        }
    }

    fun delete(trip: Trip) {
        scope.launch {
            withContext(Dispatchers.IO) { tripRepository.deleteTrip(trip.id) }
            reloadTrips()
            snackbarHostState.showSnackbar("已删除")
        }
    }

    val summaries = remember(trips, records, sessions, notes) {
        trips.map { TripAnalytics.summarize(it, records, sessions, notes) }
    }
    val candidates = remember(records, trips) { TripAnalytics.suggest(records, trips) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("出行", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = "新建出行"
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            if (summaries.isNotEmpty()) {
                TripTotalsCard(summaries)
                Spacer(Modifier.height(12.dp))
            }

            if (candidates.isNotEmpty()) {
                CandidateCard(
                    candidates = candidates,
                    onSave = { saveCandidate(it) }
                )
                Spacer(Modifier.height(12.dp))
            }

            if (summaries.isEmpty()) {
                EmptyReportCard(
                    icon = R.drawable.ic_tool_trip,
                    text = "还没有出行记录。记过酒店或门票的话，会自动认出候选行程",
                    accent = TrackAccent
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    summaries.forEach { summary ->
                        TripCard(
                            summary = summary,
                            onDelete = { pendingDelete = summary.trip }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        TripEditorDialog(
            onDismiss = { showCreate = false },
            onSave = { name, start, end -> save(name, start, end) }
        )
    }

    pendingDelete?.let { trip ->
        ConfirmDeleteDialog(
            title = "删除出行",
            message = "只删除这次出行的记录，账本、轨迹和备忘里的原始数据不会动。",
            onConfirm = {
                delete(trip)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun TripTotalsCard(summaries: List<TripSummary>) {
    val totalCents = summaries.sumOf { it.expenseCents }
    val totalMeters = summaries.sumOf { it.distanceMeters }
    SectionCard(title = "出行总览") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${summaries.size}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TrackAccent
            )
            Text(
                text = " 次出行",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "总花费",
                value = ReviewAnalytics.formatMoney(totalCents),
                accent = TrackAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "总里程",
                value = TrackAnalytics.formatDistance(totalMeters),
                accent = TrackAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "总天数",
                value = "${summaries.sumOf { it.trip.dayCount }} 天",
                accent = TrackAccent
            )
        }
    }
}

@Composable
private fun CandidateCard(
    candidates: List<TripCandidate>,
    onSave: (TripCandidate) -> Unit
) {
    SectionCard(title = "识别到 ${candidates.size} 次可能的出行") {
        Text(
            text = "按酒店、门票的消费时间推断，确认一下就能存成行程。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        candidates.forEach { candidate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    icon = R.drawable.ic_tool_trip,
                    tint = TrackAccent,
                    container = TrackAccent.copy(alpha = 0.14f),
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = candidate.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onSave(candidate) }) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun TripCard(
    summary: TripSummary,
    onDelete: () -> Unit
) {
    val trip = summary.trip
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = R.drawable.ic_tool_trip,
                    tint = TrackAccent,
                    container = TrackAccent.copy(alpha = 0.14f),
                    size = 44.dp,
                    iconSize = 22.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${TripAnalytics.formatRange(trip)} · ${trip.dayCount} 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "花费",
                    value = ReviewAnalytics.formatMoney(summary.expenseCents),
                    accent = TrackAccent
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "里程",
                    value = TrackAnalytics.formatDistance(summary.distanceMeters),
                    accent = TrackAccent
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "账目",
                    value = "${summary.expenseCount} 笔",
                    accent = TrackAccent
                )
            }
            if (summary.categoryTotals.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val top = summary.categoryTotals.take(3)
                val maxAmount = top.first().second.coerceAtLeast(1L)
                top.forEach { (category, amount) ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(48.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    RoundedCornerShape(3.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        (amount.toFloat() / maxAmount).coerceIn(0.04f, 1f)
                                    )
                                    .height(6.dp)
                                    .background(category.accent, RoundedCornerShape(3.dp))
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = ReviewAnalytics.formatMoney(amount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (summary.trackCount > 0 || summary.noteCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        if (summary.trackCount > 0) {
                            append("${summary.trackCount} 段轨迹 ${TrackAnalytics.formatDistance(summary.distanceMeters)}")
                        }
                        if (summary.noteCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${summary.noteCount} 条备忘")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trip.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = trip.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TripEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, Long) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var start by remember { mutableLongStateOf(DateRanges.startOfDay(System.currentTimeMillis())) }
    var end by remember { mutableLongStateOf(DateRanges.startOfDay(System.currentTimeMillis())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建出行") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("行程名称") },
                    placeholder = { Text("比如 西安三天") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateChip(
                        label = "开始",
                        millis = start,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDatePicker(context, start) { picked ->
                                start = DateRanges.startOfDay(picked)
                                if (end < start) end = start
                            }
                        }
                    )
                    DateChip(
                        label = "结束",
                        millis = end,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDatePicker(context, end) { picked ->
                                end = DateRanges.startOfDay(maxOf(picked, start))
                            }
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "区间内的账目、轨迹和备忘会自动算进来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), start, end) }) {
                Text("创建")
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
private fun DateChip(
    label: String,
    millis: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = DateRanges.formatDay(millis),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun defaultTripName(startMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = startMillis }
    return String.format(
        Locale.CHINA,
        "%d月%d日 出行",
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}
