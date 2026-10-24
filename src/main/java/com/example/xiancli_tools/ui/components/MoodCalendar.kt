package com.example.xiancli_tools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xiancli_tools.data.DateRange
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.MoodLevels
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteAnalytics
import java.util.Calendar
import java.util.Locale

/**
 * 心情日历：一个月一张格子图，有记录的那天按心情深浅上色。
 *
 * 只画「有没有」和「好不好」，不画具体文字，所以一屏就能看出一整个月的走势；
 * 想看当天写了什么，回明细里翻同一天即可。
 */
@Composable
fun MoodCalendarCard(
    notes: List<Note>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    var monthStart by remember {
        mutableLongStateOf(DateRanges.startOfMonth(System.currentTimeMillis()))
    }
    val range = remember(monthStart) { DateRange(monthStart, DateRanges.plusMonths(monthStart, 1)) }
    val levels = remember(notes, range) { NoteAnalytics.moodByDay(notes, range) }
    val average = remember(notes, range) { NoteAnalytics.averageMood(notes, range) }

    val calendar = remember(monthStart) {
        Calendar.getInstance().apply { timeInMillis = monthStart }
    }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leadingBlanks = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val todayKey = DateRanges.dayKey(System.currentTimeMillis())

    SectionCard(title = "心情日历", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthArrow(text = "‹", onClick = { monthStart = DateRanges.plusMonths(monthStart, -1) })
            Text(
                text = "${year}年${month}月",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            MonthArrow(text = "›", onClick = { monthStart = DateRanges.plusMonths(monthStart, 1) })
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (levels.isEmpty()) {
                "这个月还没有心情记录"
            } else {
                "${levels.size} 天有记录 · 平均 ${String.format(Locale.US, "%.1f", average)} 分"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        val cells = leadingBlanks + daysInMonth
        val rows = (cells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(7) { column ->
                        val index = row * 7 + column
                        val day = index - leadingBlanks + 1
                        if (day in 1..daysInMonth) {
                            val dayCalendar = Calendar.getInstance().apply {
                                timeInMillis = monthStart
                                set(Calendar.DAY_OF_MONTH, day)
                            }
                            val dayKey = DateRanges.dayKey(dayCalendar.timeInMillis)
                            DayCell(
                                day = day,
                                level = levels[dayKey],
                                isToday = dayKey == todayKey,
                                accent = accent,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "低落",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            (MoodLevels.MIN..MoodLevels.MAX).forEach { level ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = levelAlpha(level)))
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "很好",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthArrow(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    level: Int?,
    isToday: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(9.dp)
    val background = if (level == null) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        accent.copy(alpha = levelAlpha(level))
    }
    val textColor = when {
        level != null && level >= 4 -> Color.White
        level != null -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderModifier = if (isToday) {
        Modifier.border(1.5.dp, accent, shape)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(background)
            .then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$day",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

private fun levelAlpha(level: Int): Float = 0.16f + (level - MoodLevels.MIN) * 0.18f
