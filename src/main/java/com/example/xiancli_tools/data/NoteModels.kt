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
    YEAR("年"),
    CUSTOM("自定义")
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
    val range: DateRange
        get() = DateRange(startMillis, endMillis)

    /** 报表标题用词：本日 / 本周 / 本月 / 本年 / 所选区间。 */
    val rangeName: String
        get() = if (period == NotePeriod.CUSTOM) "所选区间" else "本${period.label}"

    /** 趋势峰值描述用词：单日最高 / 单周最高 / 单月最高 / 单年最高。 */
    val peakLabel: String
        get() = if (period == NotePeriod.CUSTOM) {
            "单${TrendBuckets.plan(range).unit.label}最高"
        } else {
            "单${period.label}最高"
        }

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
        now: Long = System.currentTimeMillis(),
        customRange: DateRange? = null
    ): NoteSummary {
        val range = rangeOf(period, now, customRange)
        val inRange = notes
            .filter { it.timeMillis in range.startMillis until range.endMillis }
            .sortedByDescending { it.timeMillis }
        return NoteSummary(period, range.startMillis, range.endMillis, inRange)
    }

    /** 把「日 / 周 / 月 / 年 / 自定义」统一折算成具体区间。 */
    fun rangeOf(
        period: NotePeriod,
        now: Long,
        customRange: DateRange? = null
    ): DateRange = when (period) {
        NotePeriod.DAY -> DateRanges.day(now)
        NotePeriod.WEEK -> DateRanges.week(now)
        NotePeriod.MONTH -> DateRanges.month(now)
        NotePeriod.YEAR -> DateRanges.year(now)
        NotePeriod.CUSTOM -> customRange ?: DateRanges.lastDays(now)
    }

    fun startOfPeriod(period: NotePeriod, now: Long): Long = rangeOf(period, now).startMillis

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
            NotePeriod.CUSTOM -> {
                val plan = TrendBuckets.plan(summary.range)
                val buckets = IntArray(plan.size)
                summary.notes.forEach { note ->
                    val index = plan.indexOf(note.timeMillis)
                    if (index in buckets.indices) buckets[index]++
                }
                plan.labels.mapIndexed { index, label -> NoteTrendBucket(label, buckets[index]) }
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
            NotePeriod.CUSTOM -> DateRanges.formatHeading(summary.range)
        }
    }
}
