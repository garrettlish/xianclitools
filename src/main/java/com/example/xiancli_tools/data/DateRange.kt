package com.example.xiancli_tools.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 半开时间区间 [startMillis, endMillis)，两端都对齐到自然日零点。
 * 报表的「日 / 周 / 月 / 年 / 自定义」最终都归一成这个结构。
 */
data class DateRange(val startMillis: Long, val endMillis: Long) {

    /** 区间覆盖的天数，含首尾两天。 */
    val dayCount: Int
        get() = DateRanges.daysBetween(startMillis, endMillis)

    /** 最后一天的零点（endMillis 是开区间，回退 1 毫秒再取当天零点）。 */
    val lastDayMillis: Long
        get() = DateRanges.startOfDay(endMillis - 1)
}

object DateRanges {

    /** 自定义区间的默认长度：最近 7 天（含今天）。 */
    const val DEFAULT_CUSTOM_DAYS = 7

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfMonth(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(millis)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    fun startOfYear(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(millis)
        set(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    fun plusDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.DAY_OF_MONTH, days)
    }.timeInMillis

    fun plusHours(millis: Long, hours: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.HOUR_OF_DAY, hours)
    }.timeInMillis

    fun plusMonths(millis: Long, months: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.MONTH, months)
    }.timeInMillis

    /** 自然日：今天 00:00 起 1 天。 */
    fun day(now: Long): DateRange {
        val start = startOfDay(now)
        return DateRange(start, plusDays(start, 1))
    }

    /** 自然周：本周一 00:00 起 7 天。 */
    fun week(now: Long): DateRange {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val daysFromMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        val start = calendar.timeInMillis
        return DateRange(start, plusDays(start, 7))
    }

    /** 自然月：本月 1 日 00:00 起 1 个月。 */
    fun month(now: Long): DateRange {
        val start = startOfMonth(now)
        return DateRange(start, plusMonths(start, 1))
    }

    /** 自然年：本年 1 月 1 日 00:00 起 1 年。 */
    fun year(now: Long): DateRange {
        val start = startOfYear(now)
        return DateRange(start, plusMonths(start, 12))
    }

    /** 最近 [days] 天，含今天；默认最近 1 周。 */
    fun lastDays(now: Long = System.currentTimeMillis(), days: Int = DEFAULT_CUSTOM_DAYS): DateRange {
        val safeDays = days.coerceAtLeast(1)
        val end = plusDays(startOfDay(now), 1)
        return DateRange(plusDays(end, -safeDays), end)
    }

    /** 把用户选的两个「自然日」拼成区间：自动对齐零点、顺序颠倒也能纠正。 */
    fun ofDays(firstMillis: Long, secondMillis: Long): DateRange {
        val start = startOfDay(minOf(firstMillis, secondMillis))
        val end = startOfDay(maxOf(firstMillis, secondMillis))
        return DateRange(start, plusDays(end, 1))
    }

    fun daysBetween(startMillis: Long, endMillis: Long): Int {
        if (endMillis <= startMillis) return 0
        var cursor = startOfDay(startMillis)
        var days = 0
        while (cursor < endMillis && days < MAX_DAYS) {
            cursor = plusDays(cursor, 1)
            days++
        }
        return days
    }

    /** 例如 2026-10-17，用于日期选择按钮。 */
    fun formatDay(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(millis))

    /** 例如 2026年10月17日 – 10月23日，用于报表标题。 */
    fun formatHeading(range: DateRange): String {
        val start = Calendar.getInstance().apply { timeInMillis = range.startMillis }
        val end = Calendar.getInstance().apply { timeInMillis = range.endMillis - 1 }
        val sameYear = start.get(Calendar.YEAR) == end.get(Calendar.YEAR)
        val sameMonth = sameYear && start.get(Calendar.MONTH) == end.get(Calendar.MONTH)
        val sameDay = sameMonth && start.get(Calendar.DAY_OF_MONTH) == end.get(Calendar.DAY_OF_MONTH)
        return when {
            sameDay -> String.format(
                Locale.CHINA,
                "%d年%d月%d日",
                start.get(Calendar.YEAR),
                start.get(Calendar.MONTH) + 1,
                start.get(Calendar.DAY_OF_MONTH)
            )
            sameMonth -> String.format(
                Locale.CHINA,
                "%d年%d月%d日 – %d日",
                start.get(Calendar.YEAR),
                start.get(Calendar.MONTH) + 1,
                start.get(Calendar.DAY_OF_MONTH),
                end.get(Calendar.DAY_OF_MONTH)
            )
            sameYear -> String.format(
                Locale.CHINA,
                "%d年%d月%d日 – %d月%d日",
                start.get(Calendar.YEAR),
                start.get(Calendar.MONTH) + 1,
                start.get(Calendar.DAY_OF_MONTH),
                end.get(Calendar.MONTH) + 1,
                end.get(Calendar.DAY_OF_MONTH)
            )
            else -> String.format(
                Locale.CHINA,
                "%d年%d月%d日 – %d年%d月%d日",
                start.get(Calendar.YEAR),
                start.get(Calendar.MONTH) + 1,
                start.get(Calendar.DAY_OF_MONTH),
                end.get(Calendar.YEAR),
                end.get(Calendar.MONTH) + 1,
                end.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    private const val MAX_DAYS = 20_000
}

enum class BucketUnit(val label: String) {
    HOUR("时"),
    DAY("日"),
    WEEK("周"),
    MONTH("月")
}

/** 趋势图的分桶方案：每个桶的起始时间 + 轴上要显示的标签。 */
data class TrendBucketPlan(
    val unit: BucketUnit,
    val starts: List<Long>,
    val labels: List<String>
) {
    val size: Int
        get() = starts.size

    /** 记录时间落在哪个桶；落在区间外返回 -1。 */
    fun indexOf(millis: Long): Int {
        if (starts.isEmpty() || millis < starts.first()) return -1
        var low = 0
        var high = starts.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (starts[mid] <= millis) low = mid else high = mid - 1
        }
        return low
    }
}

/**
 * 自定义区间的趋势分桶：按跨度自动选粒度，既不让 90 天画 90 根柱子，
 * 也保证轴上标签窄到能塞进一个槽位。
 * 1 天以内按小时、2 周以内按天、17 周以内按周、更长按月。
 */
object TrendBuckets {

    fun plan(range: DateRange): TrendBucketPlan {
        val days = range.dayCount
        return when {
            days <= 1 -> hourly(range.startMillis)
            days <= 14 -> dayBuckets(range.startMillis, days, 1)
            days <= 119 -> dayBuckets(range.startMillis, days, 7)
            else -> monthly(range.startMillis, range.endMillis)
        }
    }

    private fun hourly(dayStart: Long): TrendBucketPlan {
        val starts = (0 until 24).map { DateRanges.plusHours(dayStart, it) }
        val labels = starts.mapIndexed { hour, _ -> if (hour % 6 == 0) "${hour}时" else "" }
        return TrendBucketPlan(BucketUnit.HOUR, starts, labels)
    }

    /** [spanDays] 为 1 时按天分桶，为 7 时按周分桶。 */
    private fun dayBuckets(startMillis: Long, days: Int, spanDays: Int): TrendBucketPlan {
        val count = (days + spanDays - 1) / spanDays
        val starts = (0 until count).map { DateRanges.plusDays(startMillis, it * spanDays) }
        val step = ((count + 6) / 7).coerceAtLeast(1)
        // 桶不多时用「月/日」，桶挤时退回纯日号，避免标签被槽位宽度裁掉
        val pattern = if (count <= 8) "M/d" else "d"
        val formatter = SimpleDateFormat(pattern, Locale.CHINA)
        val labels = starts.mapIndexed { index, millis ->
            if (index % step == 0) formatter.format(Date(millis)) else ""
        }
        val unit = if (spanDays == 1) BucketUnit.DAY else BucketUnit.WEEK
        return TrendBucketPlan(unit, starts, labels)
    }

    private fun monthly(startMillis: Long, endMillis: Long): TrendBucketPlan {
        val starts = mutableListOf<Long>()
        var cursor = DateRanges.startOfMonth(startMillis)
        while (cursor < endMillis && starts.size <= 240) {
            starts += cursor
            cursor = DateRanges.plusMonths(cursor, 1)
        }
        val step = if (starts.size > 13) 2 else 1
        val labels = starts.mapIndexed { index, millis ->
            if (index % step == 0) {
                val month = Calendar.getInstance().apply { timeInMillis = millis }
                "${month.get(Calendar.MONTH) + 1}月"
            } else {
                ""
            }
        }
        return TrendBucketPlan(BucketUnit.MONTH, starts, labels)
    }
}
