package com.example.xiancli_tools.data

import java.util.Calendar
import java.util.Locale

enum class NoteCategory(val label: String) {
    REMINDER("提醒"),
    TODO("待办"),
    DIARY("日志"),
    MOOD("心情"),
    IDEA("灵感"),
    OTHER("其他")
}

data class Note(
    val id: Long,
    val category: NoteCategory,
    val content: String,
    val timeMillis: Long
)

enum class NotePeriod(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年")
}

data class NoteCategorySummary(
    val category: NoteCategory,
    val count: Int
)

data class NoteTrendBucket(val label: String, val count: Int)

data class NoteSummary(
    val period: NotePeriod,
    val startMillis: Long,
    val endMillis: Long,
    val notes: List<Note>
) {
    val noteCount: Int
        get() = notes.size

    val categoryCount: Int
        get() = notes.map { it.category }.distinct().size

    val categorySummaries: List<NoteCategorySummary>
        get() = NoteCategory.entries.mapNotNull { category ->
            val count = notes.count { it.category == category }
            if (count == 0) null else NoteCategorySummary(category, count)
        }.sortedByDescending { it.count }

    val topCategory: NoteCategorySummary?
        get() = categorySummaries.firstOrNull()
}

object NoteAnalytics {

    fun summarize(
        notes: List<Note>,
        period: NotePeriod,
        now: Long = System.currentTimeMillis()
    ): NoteSummary {
        val start = startOfPeriod(period, now)
        val end = startOfNextPeriod(period, start)
        val inRange = notes
            .filter { it.timeMillis in start until end }
            .sortedByDescending { it.timeMillis }
        return NoteSummary(period, start, end, inRange)
    }

    fun startOfPeriod(period: NotePeriod, now: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            NotePeriod.DAY -> Unit
            NotePeriod.WEEK -> {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = (dayOfWeek + 5) % 7
                calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
            }
            NotePeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            NotePeriod.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun startOfNextPeriod(period: NotePeriod, startMillis: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = startMillis }
        when (period) {
            NotePeriod.DAY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            NotePeriod.WEEK -> calendar.add(Calendar.DAY_OF_MONTH, 7)
            NotePeriod.MONTH -> calendar.add(Calendar.MONTH, 1)
            NotePeriod.YEAR -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }

    fun trend(summary: NoteSummary): List<NoteTrendBucket> {
        val calendar = Calendar.getInstance()
        return when (summary.period) {
            NotePeriod.DAY -> {
                val buckets = IntArray(24)
                summary.notes.forEach { note ->
                    calendar.timeInMillis = note.timeMillis
                    buckets[calendar.get(Calendar.HOUR_OF_DAY)]++
                }
                (0..23).map { hour ->
                    NoteTrendBucket(if (hour % 6 == 0) "${hour}时" else "", buckets[hour])
                }
            }
            NotePeriod.WEEK -> {
                val buckets = IntArray(7)
                summary.notes.forEach { note ->
                    calendar.timeInMillis = note.timeMillis
                    val dayFromMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    buckets[dayFromMonday]++
                }
                val labels = listOf("一", "二", "三", "四", "五", "六", "日")
                (0..6).map { NoteTrendBucket(labels[it], buckets[it]) }
            }
            NotePeriod.MONTH -> {
                val days = daysInMonth(summary.startMillis)
                val buckets = IntArray(days)
                summary.notes.forEach { note ->
                    calendar.timeInMillis = note.timeMillis
                    val index = calendar.get(Calendar.DAY_OF_MONTH) - 1
                    if (index in buckets.indices) buckets[index]++
                }
                (0 until days).map { index ->
                    val day = index + 1
                    NoteTrendBucket(if (day == 1 || day % 5 == 0) "$day" else "", buckets[index])
                }
            }
            NotePeriod.YEAR -> {
                val buckets = IntArray(12)
                summary.notes.forEach { note ->
                    calendar.timeInMillis = note.timeMillis
                    buckets[calendar.get(Calendar.MONTH)]++
                }
                (0..11).map { NoteTrendBucket("${it + 1}月", buckets[it]) }
            }
        }
    }

    private fun daysInMonth(millis: Long): Int = Calendar.getInstance().apply {
        timeInMillis = millis
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    fun formatDateHeading(summary: NoteSummary): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = summary.startMillis }
        val year = calendar.get(Calendar.YEAR)
        return when (summary.period) {
            NotePeriod.DAY -> String.format(
                Locale.CHINA,
                "%d年%d月%d日",
                year,
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            NotePeriod.WEEK -> String.format(
                Locale.CHINA,
                "%d年%d月%d日 起",
                year,
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            NotePeriod.MONTH -> String.format(
                Locale.CHINA,
                "%d年%d月",
                year,
                calendar.get(Calendar.MONTH) + 1
            )
            NotePeriod.YEAR -> String.format(Locale.CHINA, "%d年", year)
        }
    }
}
