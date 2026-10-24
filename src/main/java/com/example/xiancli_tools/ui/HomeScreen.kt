package com.example.xiancli_tools.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.R
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.LedgerRepository
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.NoteRepository
import com.example.xiancli_tools.data.ReviewAnalytics
import com.example.xiancli_tools.data.TimerAnalytics
import com.example.xiancli_tools.data.TimerSessionRepository
import com.example.xiancli_tools.ui.components.IconBadge
import com.example.xiancli_tools.ui.theme.BrandDawn
import com.example.xiancli_tools.ui.theme.BrandDusk
import com.example.xiancli_tools.ui.theme.LedgerAccent
import com.example.xiancli_tools.ui.theme.MutedRose
import com.example.xiancli_tools.ui.theme.MutedTeal
import com.example.xiancli_tools.ui.theme.NoteAccent
import com.example.xiancli_tools.ui.theme.TimerAccent
import com.example.xiancli_tools.ui.theme.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 「朝夕」字标的渐变：左「朝」晨光，右「夕」暮色；两端点与 `ic_brand_mark` 上下两半同色。 */
private val BrandBrush = Brush.linearGradient(listOf(BrandDawn, BrandDusk))

@Composable
fun HomeScreen(
    onOpenTimer: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenTrack: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenTrip: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val context = LocalContext.current
    var todayExpense by remember { mutableLongStateOf(0L) }
    var todayFocus by remember { mutableLongStateOf(0L) }
    var pendingTodos by remember { mutableIntStateOf(0) }

    // 首页只取三个数，进页面时读一次就够，不做常驻监听。
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val range = DateRanges.day(System.currentTimeMillis())
            todayExpense = LedgerRepository(context).loadRecords()
                .filter { it.timeMillis in range.startMillis until range.endMillis }
                .sumOf { it.amountCents }
            todayFocus = TimerSessionRepository(context).loadSessions()
                .filter { it.startMillis in range.startMillis until range.endMillis }
                .sumOf { it.focusMillis }
            pendingTodos = NoteRepository(context).loadNotes()
                .count { it.category == NoteCategory.TODO && !it.done }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
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
            }
            Spacer(Modifier.height(12.dp))
            TodayOverviewCard(
                expenseCents = todayExpense,
                focusMillis = todayFocus,
                pendingTodos = pendingTodos,
                onClick = onOpenReview
            )
            Spacer(Modifier.height(10.dp))
            ToolCard(
                title = "计时",
                subtitle = "10 / 25 / 50 分钟，也可自定义",
                icon = R.drawable.ic_tool_timer,
                accent = TimerAccent,
                onClick = onOpenTimer
            )
            Spacer(Modifier.height(10.dp))
            ToolCard(
                title = "备忘",
                subtitle = "随手记，到点提醒，待办能勾掉",
                icon = R.drawable.ic_tool_note,
                accent = NoteAccent,
                onClick = onOpenNote
            )
            Spacer(Modifier.height(10.dp))
            ToolCard(
                title = "账本",
                subtitle = "快速记账，区间支出报告",
                icon = R.drawable.ic_tool_ledger,
                accent = LedgerAccent,
                onClick = onOpenLedger
            )
            Spacer(Modifier.height(10.dp))
            ToolCard(
                title = "足迹",
                subtitle = "步行 · 骑车 · 开车轨迹地图",
                icon = R.drawable.ic_tool_route,
                accent = TrackAccent,
                onClick = onOpenTrack
            )
            Spacer(Modifier.height(10.dp))
            ToolCard(
                title = "习惯",
                subtitle = "每天打卡，看连续天数",
                icon = R.drawable.ic_tool_habit,
                accent = MutedTeal,
                onClick = onOpenHabits
            )
            Spacer(Modifier.height(10.dp))
            ToolCard(
                title = "出行",
                subtitle = "一次行程的花费、里程与备忘",
                icon = R.drawable.ic_tool_trip,
                accent = MutedRose,
                onClick = onOpenTrip
            )
        }
    }
}

/** 首页顶部的「今天」：三个最常看的数，点一下进回顾页看全貌。 */
@Composable
private fun TodayOverviewCard(
    expenseCents: Long,
    focusMillis: Long,
    pendingTodos: Int,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverviewCell(
                    modifier = Modifier.weight(1f),
                    label = "今日支出",
                    value = ReviewAnalytics.formatMoney(expenseCents),
                    accent = LedgerAccent
                )
                OverviewCell(
                    modifier = Modifier.weight(1f),
                    label = "专注",
                    value = TimerAnalytics.formatCompact(focusMillis),
                    accent = TimerAccent
                )
                OverviewCell(
                    modifier = Modifier.weight(1f),
                    label = "待办",
                    value = "$pendingTodos 条",
                    accent = TrackAccent
                )
            }
        }
    }
}

@Composable
private fun OverviewCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column {
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
            color = accent
        )
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: Int,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconBadge(
                icon = icon,
                tint = accent,
                container = accent.copy(alpha = 0.14f),
                size = 56.dp,
                iconSize = 28.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
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
