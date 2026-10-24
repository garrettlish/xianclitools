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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.DrinkCategory
import com.example.xiancli_tools.data.DrinkRecord
import com.example.xiancli_tools.data.DrinkRepository
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.LedgerRepository
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.data.ReviewAnalytics
import com.example.xiancli_tools.data.ReviewPeriod
import com.example.xiancli_tools.data.ReviewSummary
import com.example.xiancli_tools.data.TimerAnalytics
import com.example.xiancli_tools.data.TimerSession
import com.example.xiancli_tools.data.TimerSessionRepository
import com.example.xiancli_tools.data.TrackAnalytics
import com.example.xiancli_tools.data.TrackRepository
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.ui.components.EmptyReportCard
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.components.MetricTile
import com.example.xiancli_tools.ui.components.SectionCard
import com.example.xiancli_tools.ui.components.SegmentedTabs
import com.example.xiancli_tools.ui.components.parseHexColor
import com.example.xiancli_tools.ui.theme.LedgerAccent
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.StatsAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 回顾：五个工具的数据放进同一个区间里看一遍。
 *
 * 这一页不新增任何数据，只是把已经记下的东西并排摆出来——
 * 「这周钱花在哪、专注了多久、走了多少路、还有什么没办」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenDrink: () -> Unit
) {
    val context = LocalContext.current
    var period by remember { mutableStateOf(ReviewPeriod.WEEK) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var records by remember { mutableStateOf<List<ExpenseRecord>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<TrackSession>>(emptyList()) }
    var timers by remember { mutableStateOf<List<TimerSession>>(emptyList()) }
    var drinks by remember { mutableStateOf<List<DrinkRecord>>(emptyList()) }
    var drinkCategories by remember { mutableStateOf<List<DrinkCategory>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            notes = NoteRepository(context).loadNotes()
            records = LedgerRepository(context).loadRecords()
            sessions = TrackRepository(context).loadSessions()
            timers = TimerSessionRepository(context).loadSessions()
            val drinkRepository = DrinkRepository(context)
            drinks = drinkRepository.loadRecords()
            drinkCategories = drinkRepository.loadCategories()
        }
    }

    val summary = remember(period, notes, records, sessions, timers, drinks) {
        ReviewAnalytics.summarize(
            period = period,
            notes = notes,
            records = records,
            tracks = sessions,
            timers = timers,
            drinks = drinks
        )
    }
    val pendingTodos = remember(notes) {
        notes.filter { it.category == NoteCategory.TODO && !it.done }
            .sortedByDescending { it.timeMillis }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回顾", fontWeight = FontWeight.Bold) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            SegmentedTabs(
                labels = ReviewPeriod.entries.map { it.label },
                selectedIndex = period.ordinal,
                onSelect = { period = ReviewPeriod.entries[it] },
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            HeadlineCard(summary)
            Spacer(Modifier.height(12.dp))

            if (summary.isEmpty) {
                EmptyReportCard(
                    icon = R.drawable.ic_tool_review,
                    text = "${summary.rangeName}还没有数据，记一笔账或者跑一次计时就有了",
                    accent = StatsAccent
                )
                Spacer(Modifier.height(12.dp))
            } else {
                SummaryTiles(summary)
                Spacer(Modifier.height(12.dp))
            }

            if (summary.categoryTotals.isNotEmpty()) {
                ExpenseBreakdownCard(summary)
                Spacer(Modifier.height(12.dp))
            }

            if (summary.timerCount > 0) {
                FocusCard(summary)
                Spacer(Modifier.height(12.dp))
            }

            TodoCard(
                pendingTodos = pendingTodos,
                summary = summary,
                onOpenNote = onOpenNote
            )
            Spacer(Modifier.height(12.dp))

            DrinkSummaryCard(
                categories = drinkCategories,
                summary = summary,
                hasAnyRecord = drinks.isNotEmpty(),
                onOpenDrink = onOpenDrink
            )
        }
    }
}

@Composable
private fun HeadlineCard(summary: ReviewSummary) {
    SectionCard(title = summary.rangeName) {
        Text(
            text = ReviewAnalytics.headline(summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (summary.activeDays > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "有记录的天数 ${summary.activeDays} 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryTiles(summary: ReviewSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "花费",
                value = ReviewAnalytics.formatMoney(summary.expenseCents),
                accent = LedgerAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "专注",
                value = TimerAnalytics.formatCompact(summary.focusMillis),
                accent = TimerAccent
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "里程",
                value = TrackAnalytics.formatDistance(summary.distanceMeters),
                accent = TrackAccent
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "记录",
                value = "${summary.noteCount} 条",
                accent = NoteAccent
            )
        }
    }
}

@Composable
private fun ExpenseBreakdownCard(summary: ReviewSummary) {
    val top = summary.categoryTotals.take(4)
    val maxAmount = top.first().second.coerceAtLeast(1L)
    SectionCard(title = "钱花在哪") {
        top.forEach { (category, amount) ->
            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(category.iconRes),
                        contentDescription = null,
                        tint = category.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = ReviewAnalytics.formatMoney(amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = category.accent
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
                                (amount.toFloat() / maxAmount).coerceIn(0.04f, 1f)
                            )
                            .height(6.dp)
                            .background(category.accent, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
        if (summary.categoryTotals.size > top.size) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "另有 ${summary.categoryTotals.size - top.size} 类支出",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FocusCard(summary: ReviewSummary) {
    SectionCard(title = "专注") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = TimerAnalytics.formatCompact(summary.focusMillis),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TimerAccent
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "共 ${summary.timerCount} 次，完成 ${summary.timerCompletedCount} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TodoCard(
    pendingTodos: List<Note>,
    summary: ReviewSummary,
    onOpenNote: () -> Unit
) {
    SectionCard(title = "待办") {
        if (pendingTodos.isEmpty()) {
            Text(
                text = "没有未完成的待办，清爽。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "还有 ${pendingTodos.size} 条没清" +
                    if (summary.todoTotal > 0) "，${summary.rangeName}完成率 ${summary.todoCompletionRate}%" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            pendingTodos.take(5).forEach { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenNote)
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(
                        icon = R.drawable.ic_cat_todo,
                        tint = TrackAccent,
                        container = TrackAccent.copy(alpha = 0.14f),
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    note.remindAt?.let {
                        Icon(
                            painter = painterResource(R.drawable.ic_alarm),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            if (pendingTodos.size > 5) {
                Text(
                    text = "还有 ${pendingTodos.size - 5} 条…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpenNote) {
                Text("去备忘处理")
            }
        }
    }
}

@Composable
private fun DrinkSummaryCard(
    categories: List<DrinkCategory>,
    summary: ReviewSummary,
    hasAnyRecord: Boolean,
    onOpenDrink: () -> Unit
) {
    SectionCard(title = "饮记") {
        if (!hasAnyRecord) {
            Text(
                text = "还没有记录，喝一杯就点一下。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpenDrink) {
                Text("去记录")
            }
            return@SectionCard
        }
        Text(
            text = "今天 ${summary.drinkTodayCount} 次 · ${summary.rangeName} ${summary.drinkCount} 次",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (summary.drinkTop.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            summary.drinkTop.take(4).forEach { (categoryId, count) ->
                val category = categories.firstOrNull { it.id == categoryId }
                val color = parseHexColor(category?.colorHex ?: "#9C948A")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(color, RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = category?.name ?: "已删除类别",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$count 次",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            }
        }
        TextButton(onClick = onOpenDrink) {
            Text("去记录")
        }
    }
}
