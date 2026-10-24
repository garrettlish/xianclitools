package com.example.xiancli_tools.ui

import android.app.TimePickerDialog
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
import com.example.xiancli_tools.data.Habit
import com.example.xiancli_tools.data.HabitAnalytics
import com.example.xiancli_tools.data.HabitRepository
import com.example.xiancli_tools.reminder.ReminderScheduler
import com.example.xiancli_tools.ui.components.EmptyReportCard
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 习惯打卡：一天一格，连续天数靠「昨天有没有打」自然延续，
 * 不设「失败惩罚」——断一天就从零开始，压力太大的打卡没人坚持得下去。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { HabitRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var showEditor by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        habits = withContext(Dispatchers.IO) { repository.loadHabits() }
        // 每次进页面都把提醒重挂一遍，避免上次打卡取消后一直不响。
        habits.forEach { habit ->
            habit.reminderMinuteOfDay?.let { minute ->
                ReminderScheduler.scheduleHabit(context, habit.id, minute, habit.name)
            }
        }
    }

    fun delete(habit: Habit) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.deleteHabit(habit.id) }
            ReminderScheduler.cancelHabit(context, habit.id)
            habits = withContext(Dispatchers.IO) { repository.loadHabits() }
            snackbarHostState.showSnackbar("已删除「${habit.name}」")
        }
    }

    fun toggle(habit: Habit) {
        val updated = HabitAnalytics.toggleToday(habit)
        val checked = HabitAnalytics.checkedToday(updated)
        scope.launch {
            withContext(Dispatchers.IO) { repository.saveHabit(updated) }
            habits = withContext(Dispatchers.IO) { repository.loadHabits() }
            updated.reminderMinuteOfDay?.let { minute ->
                if (checked) {
                    ReminderScheduler.scheduleHabitTomorrow(context, updated.id, minute, updated.name)
                } else {
                    ReminderScheduler.scheduleHabit(context, updated.id, minute, updated.name)
                }
            }
            if (checked) {
                val streak = HabitAnalytics.streak(updated)
                snackbarHostState.showSnackbar("打卡成功，连续 $streak 天")
            }
        }
    }

    fun save(name: String, iconKey: String, reminderMinute: Int?) {
        val existing = habits.firstOrNull { it.id == editingId }
        val habit = existing?.copy(
            name = name,
            iconKey = iconKey,
            reminderMinuteOfDay = reminderMinute
        ) ?: Habit(
            id = System.currentTimeMillis(),
            name = name,
            iconKey = iconKey,
            createdMillis = System.currentTimeMillis(),
            reminderMinuteOfDay = reminderMinute
        )
        scope.launch {
            withContext(Dispatchers.IO) { repository.saveHabit(habit) }
            habits = withContext(Dispatchers.IO) { repository.loadHabits() }
            if (reminderMinute != null) {
                ReminderScheduler.scheduleHabit(context, habit.id, reminderMinute, habit.name)
            } else {
                ReminderScheduler.cancelHabit(context, habit.id)
            }
            showEditor = false
            editingId = null
            snackbarHostState.showSnackbar(if (existing == null) "已添加「$name」" else "已更新")
        }
    }

    val doneToday = habits.count { HabitAnalytics.checkedToday(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("习惯", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingId = null
                        showEditor = true
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = "添加习惯"
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
            TodayProgressCard(
                done = doneToday,
                total = habits.size,
                habits = habits
            )
            Spacer(Modifier.height(12.dp))

            if (habits.isEmpty()) {
                EmptyReportCard(
                    icon = R.drawable.ic_tool_habit,
                    text = "还没有习惯，点右上角加一个，比如喝水、吃药、读书",
                    accent = TrackAccent
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    habits.forEach { habit ->
                        HabitCard(
                            habit = habit,
                            onToggle = { toggle(habit) },
                            onEdit = {
                                editingId = habit.id
                                showEditor = true
                            },
                            onDelete = { delete(habit) }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        HabitEditorDialog(
            initial = habits.firstOrNull { it.id == editingId },
            onDismiss = {
                showEditor = false
                editingId = null
            },
            onSave = { name, iconKey, minute -> save(name, iconKey, minute) }
        )
    }
}

@Composable
private fun TodayProgressCard(done: Int, total: Int, habits: List<Habit>) {
    val rate = if (total == 0) 0f else done.toFloat() / total
    val bestStreak = habits.maxOfOrNull { HabitAnalytics.streak(it) } ?: 0
    SectionCard(title = "今天") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$done",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TrackAccent
            )
            Text(
                text = " / $total 个习惯打过卡",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(rate.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(TrackAccent, RoundedCornerShape(4.dp))
            )
        }
        if (bestStreak > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "最长连续 $bestStreak 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = habitAccent(habit.iconKey)
    val checked = HabitAnalytics.checkedToday(habit)
    val streak = HabitAnalytics.streak(habit)
    val recent = HabitAnalytics.recentDays(habit, 14)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = habitIcon(habit.iconKey),
                    tint = accent,
                    container = accent.copy(alpha = 0.14f),
                    size = 44.dp,
                    iconSize = 22.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(if (streak > 0) "连续 $streak 天" else "今天还没打卡")
                            append(" · 累计 ${HabitAnalytics.totalCount(habit)} 次")
                            habit.reminderMinuteOfDay?.let {
                                append(" · ${HabitAnalytics.formatReminder(it)} 提醒")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    onClick = onToggle,
                    shape = CircleShape,
                    color = if (checked) accent else accent.copy(alpha = 0.14f)
                ) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                if (checked) R.drawable.ic_check else R.drawable.ic_add
                            ),
                            contentDescription = if (checked) "取消打卡" else "打卡",
                            tint = if (checked) Color.White else accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                recent.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(
                                if (day.checkedIn) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "近两周",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "完成率 ${(HabitAnalytics.completionRate(habit, 30) * 100).toInt()}%（30 天）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HabitEditorDialog(
    initial: Habit?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: "water") }
    var reminderMinute by remember { mutableStateOf(initial?.reminderMinuteOfDay) }
    val columns = 3

    fun pickTime() {
        val minute = reminderMinute ?: (8 * 60)
        TimePickerDialog(
            context,
            { _, hour, pickedMinute -> reminderMinute = hour * 60 + pickedMinute },
            minute / 60,
            minute % 60,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加习惯" else "编辑习惯") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("习惯名称") },
                    placeholder = { Text("比如 喝水八杯") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "选个类型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                habitPresets.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { preset ->
                            val active = iconKey == preset.iconKey
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (active) preset.accent.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceContainer,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { iconKey = preset.iconKey }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(preset.icon),
                                    contentDescription = null,
                                    tint = if (active) preset.accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) preset.accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { pickTime() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_alarm),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "每日提醒 ${HabitAnalytics.formatReminder(reminderMinute)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (reminderMinute != null) {
                        TextButton(onClick = { reminderMinute = null }) {
                            Text("关掉")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), iconKey, reminderMinute) },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
