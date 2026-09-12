package com.example.xiancli_tools.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteAnalytics
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.NotePeriod
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.data.NoteSummary
import com.example.xiancli_tools.data.NoteTrendBucket
import com.example.xiancli_tools.ui.components.BarChart
import com.example.xiancli_tools.ui.components.BarDatum
import com.example.xiancli_tools.ui.components.ChartSlice
import com.example.xiancli_tools.ui.components.DonutChart
import com.example.xiancli_tools.ui.components.IconBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val NoteAccent = Color(0xFF7C4DFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { NoteRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var period by remember { mutableStateOf(NotePeriod.DAY) }
    var selectedCategory by remember { mutableStateOf(NoteCategory.REMINDER) }
    var content by remember { mutableStateOf("") }
    var entryTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editingId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        notes = withContext(Dispatchers.IO) { repository.loadNotes() }
    }

    val summary = remember(notes, period) { NoteAnalytics.summarize(notes, period) }
    val trend = remember(summary) { NoteAnalytics.trend(summary) }

    fun resetEditor() {
        editingId = null
        selectedCategory = NoteCategory.REMINDER
        content = ""
        entryTime = System.currentTimeMillis()
    }

    fun save() {
        val text = content.trim()
        if (text.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("请输入内容") }
            return
        }
        val note = Note(
            id = editingId ?: System.currentTimeMillis(),
            category = selectedCategory,
            content = text,
            timeMillis = entryTime
        )
        val wasEditing = editingId != null
        scope.launch {
            withContext(Dispatchers.IO) { repository.saveNote(note) }
            notes = withContext(Dispatchers.IO) { repository.loadNotes() }
            resetEditor()
            snackbarHostState.showSnackbar(if (wasEditing) "已更新" else "已记录")
        }
    }

    fun delete(note: Note) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.deleteNote(note.id) }
            notes = withContext(Dispatchers.IO) { repository.loadNotes() }
            if (editingId == note.id) resetEditor()
            snackbarHostState.showSnackbar("已删除")
        }
    }

    fun pickTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = entryTime }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        entryTime = calendar.timeInMillis
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记事本", fontWeight = FontWeight.Bold) },
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
            QuickAddCard(
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                content = content,
                onContentChange = { content = it },
                entryTime = entryTime,
                onPickTime = { pickTime() },
                editing = editingId != null,
                onCancelEdit = { resetEditor() },
                onSave = { save() }
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "统计报告",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            PeriodSelector(selected = period, onSelect = { period = it })
            Spacer(Modifier.height(12.dp))
            ReportContent(
                summary = summary,
                trend = trend,
                onEdit = { note ->
                    editingId = note.id
                    selectedCategory = note.category
                    content = note.content
                    entryTime = note.timeMillis
                },
                onDelete = { delete(it) }
            )
        }
    }
}

@Composable
private fun QuickAddCard(
    selectedCategory: NoteCategory,
    onSelectCategory: (NoteCategory) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    entryTime: Long,
    onPickTime: () -> Unit,
    editing: Boolean,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit
) {
    SectionCard(title = if (editing) "编辑记事" else "快速记事") {
        CategoryGrid(selected = selectedCategory, onSelect = onSelectCategory)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("具体事情") },
            minLines = 3
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onPickTime)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_time),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatEntryTime(entryTime),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (editing) {
                TextButton(onClick = onCancelEdit) {
                    Text("取消")
                }
            }
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = NoteAccent)
            ) {
                Text(if (editing) "更新" else "记录")
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    selected: NoteCategory,
    onSelect: (NoteCategory) -> Unit
) {
    val columns = 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NoteCategory.entries.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = category == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(category) }
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: NoteCategory,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = category.accent
    Card(
        shape = RoundedCornerShape(14.dp),
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
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: NotePeriod,
    onSelect: (NotePeriod) -> Unit
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
        NotePeriod.entries.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
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
private fun ReportContent(
    summary: NoteSummary,
    trend: List<NoteTrendBucket>,
    onEdit: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    if (summary.notes.isEmpty()) {
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
                    icon = R.drawable.ic_tool_note,
                    tint = NoteAccent,
                    container = NoteAccent.copy(alpha = 0.14f),
                    size = 56.dp,
                    iconSize = 28.dp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "本${summary.period.label}还没有记事",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalsCard(summary)
        TrendCard(summary, trend)
        CategoryBreakdownCard(summary)
        NotesCard(summary, onEdit, onDelete)
    }
}

@Composable
private fun TotalsCard(summary: NoteSummary) {
    val activeDays = summary.notes
        .map { dayKey(it.timeMillis) }
        .distinct()
        .size
    SectionCard(title = "本${summary.period.label}汇总") {
        Text(
            text = NoteAnalytics.formatDateHeading(summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${summary.noteCount}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = NoteAccent
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "条记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportMetric(
                modifier = Modifier.weight(1f),
                label = "类型数",
                value = "${summary.categoryCount} 种"
            )
            ReportMetric(
                modifier = Modifier.weight(1f),
                label = "最常记",
                value = summary.topCategory?.category?.label ?: "-"
            )
            ReportMetric(
                modifier = Modifier.weight(1f),
                label = "活跃天数",
                value = "$activeDays 天"
            )
        }
    }
}

@Composable
private fun TrendCard(
    summary: NoteSummary,
    trend: List<NoteTrendBucket>
) {
    SectionCard(title = "记录趋势") {
        BarChart(
            bars = trend.map { BarDatum(it.label, it.count.toFloat()) },
            color = NoteAccent
        )
        Spacer(Modifier.height(8.dp))
        val peak = trend.maxByOrNull { it.count }
        Text(
            text = if (peak == null || peak.count == 0) {
                "本${summary.period.label}还没有记录"
            } else {
                "单${summary.period.label}最高 ${peak.count} 条"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryBreakdownCard(summary: NoteSummary) {
    val categories = summary.categorySummaries
    val maxCount = categories.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val total = summary.noteCount.coerceAtLeast(1)

    SectionCard(title = "类型占比") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = categories.map {
                    ChartSlice(it.count.toFloat(), it.category.accent)
                },
                modifier = Modifier.size(120.dp),
                strokeWidth = 20.dp,
                center = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${summary.noteCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NoteAccent
                        )
                        Text(
                            text = "总记录",
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
                categories.forEach { entry ->
                    val share = (entry.count.toFloat() / total * 100).roundToInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(entry.category.accent, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.category.label,
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
        categories.forEach { entry ->
            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(entry.category.iconRes),
                        contentDescription = null,
                        tint = entry.category.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.category.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${entry.count} 条",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = entry.category.accent
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
                                (entry.count.toFloat() / maxCount).coerceIn(0.04f, 1f)
                            )
                            .height(6.dp)
                            .background(entry.category.accent, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard(
    summary: NoteSummary,
    onEdit: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    SectionCard(title = "记事明细") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            summary.notes.forEach { note ->
                NoteRow(
                    note = note,
                    period = summary.period,
                    onEdit = { onEdit(note) },
                    onDelete = { onDelete(note) }
                )
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    period: NotePeriod,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            icon = note.category.iconRes,
            tint = note.category.accent,
            container = note.category.accent.copy(alpha = 0.14f),
            size = 40.dp,
            iconSize = 22.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.category.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = note.category.accent
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatRecordTime(note.timeMillis, period),
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
}

@Composable
private fun ReportMetric(
    label: String,
    value: String,
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NoteAccent
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

private fun formatEntryTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))

private fun formatRecordTime(millis: Long, period: NotePeriod): String {
    val pattern = when (period) {
        NotePeriod.DAY -> "HH:mm"
        NotePeriod.WEEK -> "E HH:mm"
        NotePeriod.MONTH -> "MM-dd HH:mm"
        NotePeriod.YEAR -> "MM-dd HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

private fun dayKey(millis: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(millis))
