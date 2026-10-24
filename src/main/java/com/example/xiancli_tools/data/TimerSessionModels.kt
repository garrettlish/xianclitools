package com.example.xiancli_tools.data

import java.util.Calendar
import java.util.Locale

/**
 * 一次计时留下的痕迹：不管是自然结束还是中途取消，都会记一条。
 *
 * [completed] 为 true 表示闹钟真的响了（专注完成），false 表示用户中途取消。
 * 专注时长按「完成取计划时长、中断取实际时长」计算，这样 25 分钟番茄钟
 * 在第 3 分钟被取消时，不会白送 25 分钟。
 */
data class TimerSession(
    val id: Long,
    val label: String,
    val plannedMillis: Long,
    val startMillis: Long,
    val endMillis: Long,
    val completed: Boolean,
    /** 最后修改时间，多设备合并时比新旧（大者胜）。 */
    val updatedAt: Long = endMillis,
    /** 删除墓碑：非空表示已删。 */
    val deletedAt: Long? = null
) {
    val actualMillis: Long
        get() = (endMillis - startMillis).coerceAtLeast(0L)

    val focusMillis: Long
        get() = if (completed) plannedMillis else actualMillis
}

/** 消息/账本同款的周期切换：日 / 周 / 月 / 年 / 自定义。 */
enum class TimerPeriod(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    CUSTOM("自定义")
}

data class FocusSummary(
    val period: TimerPeriod,
    val startMillis: Long,
    val endMillis: Long,
    val sessions: List<TimerSession>
) {
    val range: DateRange
        get() = DateRange(startMillis, endMillis)

    /** 报表标题用词：本日 / 本周 / 本月 / 本年 / 所选区间。 */
    val rangeName: String
        get() = if (period == TimerPeriod.CUSTOM) "所选区间" else "本${period.label}"

    /** 趋势峰值描述用词：单日最高 / 单周最高 / 单月最高 / 单年最高。 */
    val peakLabel: String
        get() = if (period == TimerPeriod.CUSTOM) {
            "单${TrendBuckets.plan(range).unit.label}最高"
        } else {
            "单${period.label}最高"
        }

    val totalMillis: Long
        get() = sessions.sumOf { it.focusMillis }

    val plannedMillis: Long
        get() = sessions.sumOf { it.plannedMillis }

    val completedCount: Int
        get() = sessions.count { it.completed }

    val sessionCount: Int
        get() = sessions.size

    /** 中途取消的次数：点开就关掉的那些，和完成率是同一件事的两面。 */
    val interruptedCount: Int
        get() = sessionCount - completedCount

    /** 完成率：完成的次数 / 总次数，用于「本周 12 次里完成 9 次」。 */
    val completionRate: Float
        get() = if (sessions.isEmpty()) 0f else completedCount.toFloat() / sessions.size

    val averageMillis: Long
        get() = if (sessions.isEmpty()) 0L else totalMillis / sessions.size

    val longestMillis: Long
        get() = sessions.maxOfOrNull { it.focusMillis } ?: 0L

    /** 有专注记录的天数：同一天多次计时只算一天。 */
    val activeDays: Int
        get() = sessions.map { DateRanges.dayKey(it.startMillis) }.distinct().size

    /** 一天的专注分钟数，下标 0 点为 0。 */
    val hourMinutes: IntArray
        get() {
            val buckets = IntArray(24)
            val calendar = Calendar.getInstance()
            sessions.forEach { session ->
                calendar.timeInMillis = session.startMillis
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                buckets[hour] += (session.focusMillis / 60_000L).toInt()
            }
            return buckets
        }

    /** 一天里最专注的时段，-1 表示没有任何记录。 */
    val bestHour: Int
        get() {
            val buckets = hourMinutes
            var best = -1
            var bestValue = 0
            buckets.forEachIndexed { hour, minutes ->
                if (minutes > bestValue) {
                    bestValue = minutes
                    best = hour
                }
            }
            return best
        }
}

/** 趋势图的一根柱子：一个时间桶 + 桶内的专注分钟数。 */
data class TimerTrendBucket(val label: String, val minutes: Float)

object TimerAnalytics {

    fun summarize(
        sessions: List<TimerSession>,
        period: TimerPeriod,
        now: Long = System.currentTimeMillis(),
        customRange: DateRange? = null
    ): FocusSummary {
        val range = rangeOf(period, now, customRange)
        val inRange = sessions
            .filter { it.startMillis in range.startMillis until range.endMillis }
            .sortedByDescending { it.startMillis }
        return FocusSummary(period, range.startMillis, range.endMillis, inRange)
    }

    /** 把「日 / 周 / 月 / 年 / 自定义」统一折算成具体区间。 */
    fun rangeOf(
        period: TimerPeriod,
        now: Long,
        customRange: DateRange? = null
    ): DateRange = when (period) {
        TimerPeriod.DAY -> DateRanges.day(now)
        TimerPeriod.WEEK -> DateRanges.week(now)
        TimerPeriod.MONTH -> DateRanges.month(now)
        TimerPeriod.YEAR -> DateRanges.year(now)
        TimerPeriod.CUSTOM -> customRange ?: DateRanges.lastDays(now)
    }

    /**
     * 趋势的分桶口径和备忘、账本保持一致：一天按小时、一周按星期几、
     * 一月按日、一年按月，自定义区间交给 [TrendBuckets] 自动选粒度。
     */
    fun trend(summary: FocusSummary): List<TimerTrendBucket> {
        val calendar = Calendar.getInstance()
        return when (summary.period) {
            TimerPeriod.DAY -> {
                val buckets = FloatArray(24)
                summary.sessions.forEach { session ->
                    calendar.timeInMillis = session.startMillis
                    buckets[calendar.get(Calendar.HOUR_OF_DAY)] += session.focusMillis / 60_000f
                }
                (0..23).map { hour ->
                    TimerTrendBucket(if (hour % 6 == 0) "${hour}时" else "", buckets[hour])
                }
            }
            TimerPeriod.WEEK -> {
                val buckets = FloatArray(7)
                summary.sessions.forEach { session ->
                    calendar.timeInMillis = session.startMillis
                    val dayFromMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    buckets[dayFromMonday] += session.focusMillis / 60_000f
                }
                val labels = listOf("一", "二", "三", "四", "五", "六", "日")
                (0..6).map { TimerTrendBucket(labels[it], buckets[it]) }
            }
            TimerPeriod.MONTH -> {
                val days = daysInMonth(summary.startMillis)
                val buckets = FloatArray(days)
                summary.sessions.forEach { session ->
                    calendar.timeInMillis = session.startMillis
                    val index = calendar.get(Calendar.DAY_OF_MONTH) - 1
                    if (index in buckets.indices) buckets[index] += session.focusMillis / 60_000f
                }
                (0 until days).map { index ->
                    val day = index + 1
                    TimerTrendBucket(if (day == 1 || day % 5 == 0) "$day" else "", buckets[index])
                }
            }
            TimerPeriod.YEAR -> {
                val buckets = FloatArray(12)
                summary.sessions.forEach { session ->
                    calendar.timeInMillis = session.startMillis
                    buckets[calendar.get(Calendar.MONTH)] += session.focusMillis / 60_000f
                }
                (0..11).map { TimerTrendBucket("${it + 1}月", buckets[it]) }
            }
            TimerPeriod.CUSTOM -> {
                val plan = TrendBuckets.plan(summary.range)
                val buckets = FloatArray(plan.size)
                summary.sessions.forEach { session ->
                    val index = plan.indexOf(session.startMillis)
                    if (index in buckets.indices) buckets[index] += session.focusMillis / 60_000f
                }
                plan.labels.mapIndexed { index, label ->
                    TimerTrendBucket(label, buckets[index])
                }
            }
        }
    }

    /** 趋势里最忙的那个桶，「本周三 90 分钟」这种描述要用。 */
    fun peakBucket(summary: FocusSummary): TimerTrendBucket? {
        val buckets = trend(summary).filter { it.minutes > 0f }
        return buckets.maxByOrNull { it.minutes }
    }

    /**
     * 时段分布：把 24 小时压成 6 档，看的是「上午还是晚上更容易专注」。
     * 每档 4 小时，比 24 根柱子更容易读数。
     */
    fun hourSlots(summary: FocusSummary): List<TimerTrendBucket> {
        val hours = summary.hourMinutes
        val labels = listOf("0-4", "4-8", "8-12", "12-16", "16-20", "20-24")
        return labels.mapIndexed { index, label ->
            val minutes = (index * 4 until index * 4 + 4).sumOf { hours[it] }
            TimerTrendBucket(label, minutes.toFloat())
        }
    }

    /** 近 [days] 天每天的专注分钟数，用于「最近一周」这类固定窗口。 */
    fun dailyMinutes(
        sessions: List<TimerSession>,
        days: Int,
        now: Long = System.currentTimeMillis()
    ): List<Pair<String, Float>> {
        val today = DateRanges.startOfDay(now)
        return (days - 1 downTo 0).map { offset ->
            val dayStart = DateRanges.plusDays(today, -offset)
            val dayEnd = DateRanges.plusDays(dayStart, 1)
            val minutes = sessions
                .filter { it.startMillis in dayStart until dayEnd }
                .sumOf { it.focusMillis } / 60_000f
            val calendar = Calendar.getInstance().apply { timeInMillis = dayStart }
            val label = if (offset == 0) "今天" else "${calendar.get(Calendar.DAY_OF_MONTH)}"
            label to minutes
        }
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分"
            hours > 0 -> "$hours 小时"
            totalMinutes > 0 -> "$minutes 分钟"
            millis > 0 -> "不到 1 分钟"
            else -> "0 分钟"
        }
    }

    /** 首页/回顾里的紧凑写法：2h15m。 */
    fun formatCompact(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        if (totalMinutes <= 0) return "0 分"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) String.format(Locale.US, "%d小时%d分", hours, minutes)
        else "$minutes 分"
    }

    private fun daysInMonth(millis: Long): Int = Calendar.getInstance().apply {
        timeInMillis = millis
    }.getActualMaximum(Calendar.DAY_OF_MONTH)
}
