package com.example.xiancli_tools.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.DateRange
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.Identity
import com.example.xiancli_tools.data.MoodLevels
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteAnalytics
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.NotePeriod
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.data.NoteSummary
import com.example.xiancli_tools.data.NoteTrendBucket
import com.example.xiancli_tools.reminder.ReminderScheduler
import com.example.xiancli_tools.ui.components.BarChart
import com.example.xiancli_tools.ui.components.BarDatum
import com.example.xiancli_tools.ui.components.ChartSlice
import com.example.xiancli_tools.ui.components.ConfirmDeleteDialog
import com.example.xiancli_tools.ui.components.CustomRangeBar
import com.example.xiancli_tools.ui.components.DonutChart
import com.example.xiancli_tools.ui.components.EmptyReportCard
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.MetricTile
import com.example.xiancli_tools.ui.components.MoodCalendarCard
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.components.SegmentedTabs
import com.example.xiancli_tools.ui.components.showDatePicker
import com.example.xiancli_tools.ui.theme.ClayRed
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 备忘页的三块内容：记事、明细与统计，用顶部分段标签切换，不再靠滚动。 */
private enum class NoteTab(val label: String) {
    QUICK("记事"),
    DETAIL("明细"),
    REPORT("统计")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { NoteRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(NoteTab.QUICK) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    // 统计默认落在当月（而不是今天），进门就能看到整月的记录。
    var period by remember { mutableStateOf(NotePeriod.MONTH) }
    var customRange by remember { mutableStateOf(DateRanges.lastDays()) }
    var selectedCategory by remember { mutableStateOf(NoteCategory.REMINDER) }
    var content by remember { mutableStateOf("") }
    var entryTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    var remindAt by remember { mutableStateOf<Long?>(null) }
    var done by remember { mutableStateOf(false) }
    var moodLevel by remember { mutableStateOf<Int?>(null) }

    // 三个标签各留一份滚动位置，来回切换不会把阅读进度清零。
    val quickScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val reportScroll = rememberScrollState()

    LaunchedEffect(Unit) {
        notes = withContext(Dispatchers.IO) { repository.loadNotes() }
    }

    val summary = remember(notes, period, customRange) {
        NoteAnalytics.summarize(notes, period, customRange = customRange)
    }
    val trend = remember(summary) { NoteAnalytics.trend(summary) }
    // 未完成待办是「现在的状态」，不跟着区间变，所以从全量里取。
    val pendingTodos = remember(notes) {
        notes.filter { it.category == NoteCategory.TODO && !it.done }
            .sortedByDescending { it.timeMillis }
    }

    fun resetEditor() {
        editingId = null
        selectedCategory = NoteCategory.REMINDER
        content = ""
        entryTime = System.currentTimeMillis()
        remindAt = null
        done = false
        moodLevel = null
    }

    // 从明细里点一条记录，直接跳回记事页继续编辑。
    fun startEdit(note: Note) {
        editingId = note.id
        selectedCategory = note.category
        content = note.content
        entryTime = note.timeMillis
        remindAt = note.remindAt
        done = note.done
        moodLevel = note.moodLevel
        tab = NoteTab.QUICK
    }

    /** 一条记录保存/勾选后，把它的闹钟和本地状态对齐。 */
    fun syncReminder(note: Note) {
        if (note.remindAt != null && !note.done) {
            ReminderScheduler.scheduleNote(context, note.id, note.remindAt, note.content)
        } else {
            ReminderScheduler.cancelNote(context, note.id)
        }
    }

    fun save() {
        val text = content.trim()
        if (text.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("请输入内容") }
            return
        }
        // 已经过去的时间不值得再排闹钟，顺手丢掉，避免存成「永远不响的提醒」。
        val normalizedRemindAt = remindAt?.takeIf { it > System.currentTimeMillis() }
        val note = Note(
            id = editingId ?: Identity.newId(),
            category = selectedCategory,
            content = text,
            timeMillis = entryTime,
            remindAt = normalizedRemindAt,
            done = done,
            moodLevel = if (selectedCategory == NoteCategory.MOOD) moodLevel else null
        )
        val wasEditing = editingId != null
        scope.launch {
            withContext(Dispatchers.IO) { repository.saveNote(note) }
            notes = withContext(Dispatchers.IO) { repository.loadNotes() }
            syncReminder(note)
            resetEditor()
            snackbarHostState.showSnackbar(
                when {
                    normalizedRemindAt != null -> "已保存，到点会提醒你"
                    wasEditing -> "已更新"
                    else -> "已记录"
                }
            )
        }
    }

    fun toggleDone(note: Note) {
        val updated = note.copy(
            done = !note.done,
            remindAt = if (note.done) note.remindAt else null
        )
        scope.launch {
            withContext(Dispatchers.IO) { repository.saveNote(updated) }
            notes = withContext(Dispatchers.IO) { repository.loadNotes() }
            syncReminder(updated)
            if (updated.done) snackbarHostState.showSnackbar("已完成")
        }
    }

    fun delete(note: Note) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.deleteNote(note.id) }
            ReminderScheduler.cancelNote(context, note.id)
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

    /** 提醒时间：默认一小时后，和「记录时间」分开选——记录时间是「什么时候写的」，提醒是「什么时候叫你」。 */
    fun pickReminder() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = remindAt ?: (System.currentTimeMillis() + 60 * 60_000L)
        }
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
                        remindAt = calendar.timeInMillis
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

    // 编辑态下返回先退出编辑回到明细，而不是直接回首页。
    fun exitEditToDetail() {
        resetEditor()
        tab = NoteTab.DETAIL
    }

    BackHandler(enabled = editingId != null) { exitEditToDetail() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备忘", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (editingId != null) exitEditToDetail() else onBack()
                    }) {
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
                labels = NoteTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { tab = NoteTab.entries[it] },
                accent = NoteAccent,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
            )

            when (tab) {
                NoteTab.QUICK -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(quickScroll)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    QuickAddCard(
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        content = content,
                        onContentChange = { content = it },
                        entryTime = entryTime,
                        onPickTime = { pickTime() },
                        moodLevel = moodLevel,
                        onMoodChange = { moodLevel = it },
                        remindAt = remindAt,
                        onPickReminder = { pickReminder() },
                        onClearReminder = { remindAt = null },
                        done = done,
                        onToggleDone = { done = !done },
                        editing = editingId != null,
                        onCancelEdit = { resetEditor() },
                        onSave = { save() }
                    )
                }

                NoteTab.DETAIL -> Column(
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
                    NoteDetailList(
                        summary = summary,
                        pendingTodos = pendingTodos,
                        onEdit = { startEdit(it) },
                        onToggleDone = { toggleDone(it) },
                        onDelete = { pendingDelete = it }
                    )
                }

                NoteTab.REPORT -> Column(
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
                    ReportContent(summary = summary, trend = trend, allNotes = notes)
                }
            }
        }
    }

    pendingDelete?.let { note ->
        val preview = note.content.take(40) + if (note.content.length > 40) "…" else ""
        ConfirmDeleteDialog(
            title = "删除备忘",
            message = "确定删除这条备忘吗？删除后无法恢复。\n\n$preview",
            onConfirm = {
                delete(note)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
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
    moodLevel: Int?,
    onMoodChange: (Int?) -> Unit,
    remindAt: Long?,
    onPickReminder: () -> Unit,
    onClearReminder: () -> Unit,
    done: Boolean,
    onToggleDone: () -> Unit,
    editing: Boolean,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit
) {
    // 「提醒」「待办」才需要闹钟；心情才需要档位。其余类型保持界面干净。
    val needsReminder = selectedCategory == NoteCategory.REMINDER || selectedCategory == NoteCategory.TODO

    SectionCard {
        CategoryGrid(selected = selectedCategory, onSelect = onSelectCategory)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("具体事情") },
            minLines = 3
        )
        if (selectedCategory == NoteCategory.MOOD) {
            Spacer(Modifier.height(12.dp))
            MoodSelector(selected = moodLevel, onSelect = onMoodChange)
        }
        if (needsReminder) {
            Spacer(Modifier.height(10.dp))
            ReminderRow(
                remindAt = remindAt,
                onPick = onPickReminder,
                onClear = onClearReminder
            )
        }
        if (editing && selectedCategory != NoteCategory.MOOD) {
            Spacer(Modifier.height(10.dp))
            DoneRow(done = done, onToggle = onToggleDone)
        }
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
                        MaterialTheme.colorScheme.surfaceContainer,
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

/** 心情五档：点一下就有，不强迫写文字。 */
@Composable
private fun MoodSelector(selected: Int?, onSelect: (Int?) -> Unit) {
    Column {
        Text(
            text = "今天的心情",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (MoodLevels.MIN..MoodLevels.MAX).forEach { level ->
                val active = selected == level
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (active) TimerAccent.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(if (active) null else level) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$level",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (active) TimerAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoodLevels.label(level),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) TimerAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    remindAt: Long?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alarm),
            contentDescription = null,
            tint = if (remindAt != null) ClayRed else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = remindAt?.let { "提醒 ${formatEntryTime(it)}" } ?: "设置提醒时间（可选）",
            style = MaterialTheme.typography.bodyMedium,
            color = if (remindAt != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (remindAt != null) {
            TextButton(onClick = onClear) {
                Text("清除", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DoneRow(done: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = TrackAccent)
        )
        Text(
            text = "已完成",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            else MaterialTheme.colorScheme.surfaceContainer
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

/** 「日 / 周 / 月 / 年 / 自定义」周期切换：明细与统计两个标签共用同一份选择。 */
@Composable
private fun PeriodSelector(
    period: NotePeriod,
    onSelect: (NotePeriod) -> Unit,
    customRange: DateRange,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    SegmentedTabs(
        labels = NotePeriod.entries.map { it.label },
        selectedIndex = period.ordinal,
        onSelect = { onSelect(NotePeriod.entries[it]) }
    )
    if (period == NotePeriod.CUSTOM) {
        Spacer(Modifier.height(8.dp))
        CustomRangeBar(
            range = customRange,
            accent = NoteAccent,
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
private fun ReportContent(
    summary: NoteSummary,
    trend: List<NoteTrendBucket>,
    allNotes: List<Note>
) {
    if (summary.notes.isEmpty()) {
        EmptyReportCard(
            icon = R.drawable.ic_tool_note,
            text = "${summary.rangeName}还没有记事",
            accent = NoteAccent
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalsCard(summary)
        // 日历吃全量数据：切到「日」时也想看到整月的心情分布。
        MoodCalendarCard(notes = allNotes, accent = NoteAccent)
        TrendCard(summary, trend)
        CategoryBreakdownCard(summary)
    }
}

@Composable
private fun TotalsCard(summary: NoteSummary) {
    val activeDays = summary.notes
        .map { DateRanges.dayKey(it.timeMillis) }
        .distinct()
        .size
    SectionCard(title = "${summary.rangeName}汇总") {
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "类型数",
                value = "${summary.categoryCount} 种",
                accent = NoteAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "最常记",
                value = summary.topCategory?.category?.label ?: "-",
                accent = NoteAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "活跃天数",
                value = "$activeDays 天",
                accent = NoteAccent
            )
        }
        if (summary.todoCount > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "待办完成率",
                    value = "${summary.todoCompletionRate}%",
                    accent = TrackAccent
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "已完成",
                    value = "${summary.doneTodoCount} 条",
                    accent = TrackAccent
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "心情记录",
                    value = "${summary.notes.count { it.category == NoteCategory.MOOD }} 条",
                    accent = TimerAccent
                )
            }
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
                "${summary.rangeName}还没有记录"
            } else {
                "${summary.peakLabel} ${peak.count} 条"
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
                            MaterialTheme.colorScheme.surfaceContainerHighest,
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
private fun NoteDetailList(
    summary: NoteSummary,
    pendingTodos: List<Note>,
    onEdit: (Note) -> Unit,
    onToggleDone: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    if (summary.notes.isEmpty() && pendingTodos.isEmpty()) {
        EmptyReportCard(
            icon = R.drawable.ic_tool_note,
            text = "${summary.rangeName}还没有记事",
            accent = NoteAccent
        )
        return
    }

    // 明细按自然日分组：同一天收进一张卡，标题带上当天条数，翻起来比一条大长列清楚。
    val grouped = summary.notes.groupBy { DateRanges.dayKey(it.timeMillis) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (pendingTodos.isNotEmpty()) {
            SectionCard(title = "待办清单 · ${pendingTodos.size} 条未完成") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    pendingTodos.take(PENDING_PREVIEW).forEach { note ->
                        NoteRow(
                            note = note,
                            timeText = formatClock(note.timeMillis),
                            onToggleDone = { onToggleDone(note) },
                            onEdit = { onEdit(note) },
                            onDelete = { onDelete(note) }
                        )
                    }
                    if (pendingTodos.size > PENDING_PREVIEW) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "还有 ${pendingTodos.size - PENDING_PREVIEW} 条，切到更大区间看全部",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        grouped.forEach { (_, dayNotes) ->
            val heading = DateRanges.formatDayHeading(dayNotes.first().timeMillis)
            SectionCard(title = "$heading · ${dayNotes.size} 条") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNotes.forEach { note ->
                        NoteRow(
                            note = note,
                            timeText = formatClock(note.timeMillis),
                            onToggleDone = { onToggleDone(note) },
                            onEdit = { onEdit(note) },
                            onDelete = { onDelete(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    timeText: String,
    onToggleDone: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.category.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = note.category.accent
                )
                if (note.category == NoteCategory.MOOD && note.moodLevel != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· ${MoodLevels.label(note.moodLevel)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (note.done) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· 已完成",
                        style = MaterialTheme.typography.bodySmall,
                        color = TrackAccent
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                // 勾掉的待办划线，一眼能把「已经办完的」和「还没动的」区分开。
                textDecoration = if (note.done) TextDecoration.LineThrough else TextDecoration.None,
                color = if (note.done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (note.remindAt != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_alarm),
                        contentDescription = null,
                        tint = ClayRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = formatEntryTime(note.remindAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = ClayRed
                    )
                }
            }
        }
        if (note.category == NoteCategory.TODO) {
            Checkbox(
                checked = note.done,
                onCheckedChange = { onToggleDone() },
                colors = CheckboxDefaults.colors(checkedColor = TrackAccent)
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

private const val PENDING_PREVIEW = 8

private fun formatEntryTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))

/** 明细按天分组后，行内只需要时分——日期已经写在分组标题上了。 */
private fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
