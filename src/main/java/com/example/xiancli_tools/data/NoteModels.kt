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

/**
 * 一条备忘。
 *
 * - [remindAt]：到点提醒的时间；null 表示不提醒。只有「提醒」「待办」两类会用到。
 * - [done]：待办是否已勾掉。其他类型也可以勾，语义是「这件事处理完了」。
 * - [moodLevel]：心情等级 1..5（1 低落、5 很好），只有「心情」类会用到。
 */
data class Note(
    val id: Long,
    val category: NoteCategory,
    val content: String,
    val timeMillis: Long,
    val remindAt: Long? = null,
    val done: Boolean = false,
    val moodLevel: Int? = null
) {
    val hasPendingReminder: Boolean
        get() = remindAt != null && remindAt > System.currentTimeMillis()
}

/** 心情等级的可选档位，UI 与日历取色共用同一套定义。 */
object MoodLevels {

    const val MIN = 1
    const val MAX = 5

    private val labels = mapOf(
        1 to "低落",
        2 to "疲惫",
        3 to "平静",
        4 to "不错",
        5 to "很好"
    )

    fun label(level: Int?): String = labels[level] ?: "未记录"

    fun sanitize(level: Int?): Int? = level?.takeIf { it in MIN..MAX }
}

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

    /** 还没勾掉的待办，最近记的排前面。 */
    val pendingTodos: List<Note>
        get() = notes
            .filter { it.category == NoteCategory.TODO && !it.done }
            .sortedByDescending { it.timeMillis }

    val todoCount: Int
        get() = notes.count { it.category == NoteCategory.TODO }

    val doneTodoCount: Int
        get() = notes.count { it.category == NoteCategory.TODO && it.done }

    /** 待办完成率，0..100；没有待办时返回 0。 */
    val todoCompletionRate: Int
        get() = if (todoCount == 0) 0 else (doneTodoCount * 100 / todoCount)
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

    /**
     * 区间内每天的心情等级，供日历上色。同一天记了多条时取最新一条，
     * 因为心情是「此刻状态」，后记的更能代表这一天结束时的样子。
     */
    fun moodByDay(notes: List<Note>, range: DateRange): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        notes.filter {
            it.category == NoteCategory.MOOD &&
                it.moodLevel != null &&
                it.timeMillis in range.startMillis until range.endMillis
        }.sortedBy { it.timeMillis }.forEach { note ->
            note.moodLevel?.let { result[DateRanges.dayKey(note.timeMillis)] = it }
        }
        return result
    }

    fun averageMood(notes: List<Note>, range: DateRange): Float {
        val levels = notes.filter {
            it.category == NoteCategory.MOOD &&
                it.moodLevel != null &&
                it.timeMillis in range.startMillis until range.endMillis
        }.mapNotNull { it.moodLevel }
        return if (levels.isEmpty()) 0f else levels.average().toFloat()
    }

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
