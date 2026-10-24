package com.example.xiancli_tools.data

import kotlin.math.roundToInt

enum class ReviewPeriod(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月")
}

/**
 * 回顾：把五个工具的数据放进同一个区间里看。
 *
 * 这一层不落盘，纯计算——所有数字都从各自仓库现取现算，
 * 因此不存在「回顾数据和明细对不上」的可能。
 */
data class ReviewSummary(
    val period: ReviewPeriod,
    val range: DateRange,
    val expenseCents: Long,
    val expenseCount: Int,
    val categoryTotals: List<Pair<ExpenseCategory, Long>>,
    val noteCount: Int,
    val todoTotal: Int,
    val todoDone: Int,
    val pendingTodoCount: Int,
    val distanceMeters: Double,
    val trackMillis: Long,
    val trackCount: Int,
    val focusMillis: Long,
    val timerCount: Int,
    val timerCompletedCount: Int,
    val habitDone: Int,
    val habitTotal: Int,
    val activeDays: Int
) {
    val rangeName: String
        get() = when (period) {
            ReviewPeriod.DAY -> "今天"
            ReviewPeriod.WEEK -> "本周"
            ReviewPeriod.MONTH -> "本月"
        }

    val topCategory: Pair<ExpenseCategory, Long>?
        get() = categoryTotals.firstOrNull()

    val todoCompletionRate: Int
        get() = if (todoTotal == 0) 0 else (todoDone.toFloat() / todoTotal * 100).roundToInt()

    /** 有没有值得说一句的内容。 */
    val isEmpty: Boolean
        get() = expenseCount == 0 && noteCount == 0 && trackCount == 0 && timerCount == 0
}

object ReviewAnalytics {

    fun rangeOf(period: ReviewPeriod, now: Long = System.currentTimeMillis()): DateRange =
        when (period) {
            ReviewPeriod.DAY -> DateRanges.day(now)
            ReviewPeriod.WEEK -> DateRanges.week(now)
            ReviewPeriod.MONTH -> DateRanges.month(now)
        }

    fun summarize(
        period: ReviewPeriod,
        notes: List<Note>,
        records: List<ExpenseRecord>,
        tracks: List<TrackSession>,
        timers: List<TimerSession>,
        habits: List<Habit>,
        now: Long = System.currentTimeMillis()
    ): ReviewSummary {
        val range = rangeOf(period, now)
        val inRange: (Long) -> Boolean = { it in range.startMillis until range.endMillis }

        val periodRecords = records.filter { inRange(it.timeMillis) }
        val periodNotes = notes.filter { inRange(it.timeMillis) }
        val periodTracks = tracks.filter { inRange(it.startMillis) }
        val periodTimers = timers.filter { inRange(it.startMillis) }

        val categories = periodRecords
            .groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }

        val todos = periodNotes.filter { it.category == NoteCategory.TODO }
        val activeHabits = habits.filterNot { it.archived }
        val habitDone = activeHabits.count { HabitAnalytics.checkedToday(it, now) }

        val activeDays = (periodNotes.map { DateRanges.dayKey(it.timeMillis) } +
            periodRecords.map { DateRanges.dayKey(it.timeMillis) } +
            periodTracks.map { DateRanges.dayKey(it.startMillis) } +
            periodTimers.map { DateRanges.dayKey(it.startMillis) })
            .distinct()
            .size

        return ReviewSummary(
            period = period,
            range = range,
            expenseCents = periodRecords.sumOf { it.amountCents },
            expenseCount = periodRecords.size,
            categoryTotals = categories,
            noteCount = periodNotes.size,
            todoTotal = todos.size,
            todoDone = todos.count { it.done },
            pendingTodoCount = notes.count { it.category == NoteCategory.TODO && !it.done },
            distanceMeters = periodTracks.sumOf { it.distanceMeters },
            trackMillis = periodTracks.sumOf { it.durationMillis },
            trackCount = periodTracks.size,
            focusMillis = periodTimers.sumOf { it.focusMillis },
            timerCount = periodTimers.size,
            timerCompletedCount = periodTimers.count { it.completed },
            habitDone = habitDone,
            habitTotal = activeHabits.size,
            activeDays = activeDays
        )
    }

    /** 一句话总结：优先说钱，其次说专注，最后兜底一句鼓励。 */
    fun headline(summary: ReviewSummary): String {
        if (summary.isEmpty && summary.pendingTodoCount == 0 && summary.habitTotal == 0) {
            return "${summary.rangeName}还没有记录，随手记一笔就算开工。"
        }
        val parts = mutableListOf<String>()
        if (summary.expenseCount > 0) {
            val top = summary.topCategory
            parts += if (top != null) {
                "${summary.rangeName}花了 ${formatMoney(summary.expenseCents)}，${top.first.label}占大头"
            } else {
                "${summary.rangeName}花了 ${formatMoney(summary.expenseCents)}"
            }
        }
        if (summary.timerCount > 0) {
            parts += "专注 ${TimerAnalytics.formatCompact(summary.focusMillis)}"
        }
        if (summary.trackCount > 0) {
            parts += "走了 ${TrackAnalytics.formatDistance(summary.distanceMeters)}"
        }
        if (summary.noteCount > 0) {
            parts += "记了 ${summary.noteCount} 条"
        }
        if (parts.isEmpty()) {
            return "${summary.rangeName}还没有记录，随手记一笔就算开工。"
        }
        val tail = when {
            summary.pendingTodoCount > 0 -> "，还有 ${summary.pendingTodoCount} 条待办没清"
            summary.habitTotal > 0 && summary.habitDone == summary.habitTotal -> "，习惯全部打完卡了"
            summary.habitTotal > 0 -> "，习惯完成 ${summary.habitDone}/${summary.habitTotal}"
            else -> ""
        }
        return parts.joinToString("，") + tail + "。"
    }

    fun formatMoney(cents: Long): String {
        val yuan = cents / 100.0
        return if (cents % 100L == 0L) {
            "¥${cents / 100}"
        } else {
            String.format(java.util.Locale.US, "¥%.2f", yuan)
        }
    }
}
