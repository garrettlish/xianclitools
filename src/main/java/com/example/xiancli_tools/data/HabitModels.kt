package com.example.xiancli_tools.data

import java.util.Calendar

/**
 * 习惯：和「待办」的区别是它按天连续，看的是连续天数而不是某一条做没做。
 *
 * [checkInDays] 存的是 `yyyyMMdd` 字符串（和明细分组用的是同一套键），
 * 这样跨时区、跨夏令时都不会算错天；重复打卡同一天只留一份。
 */
data class Habit(
    val id: Long,
    val name: String,
    val iconKey: String,
    val createdMillis: Long,
    val checkInDays: List<String> = emptyList(),
    /** 每日提醒时间，`分钟数 = 时 × 60 + 分`；null 表示不提醒。 */
    val reminderMinuteOfDay: Int? = null,
    val archived: Boolean = false
) {
    val hasReminder: Boolean
        get() = reminderMinuteOfDay != null

    fun isCheckedIn(dayKey: String): Boolean = checkInDays.contains(dayKey)
}

data class HabitDay(
    val dayKey: String,
    val label: String,
    val checkedIn: Boolean
)

object HabitAnalytics {

    /** 连续天数：今天打卡就从今天往回数，今天没打卡则从昨天往回数。 */
    fun streak(habit: Habit, now: Long = System.currentTimeMillis()): Int {
        val days = habit.checkInDays.toHashSet()
        var cursor = DateRanges.startOfDay(now)
        if (!days.contains(DateRanges.dayKey(cursor))) {
            cursor = DateRanges.plusDays(cursor, -1)
        }
        var count = 0
        while (days.contains(DateRanges.dayKey(cursor)) && count < MAX_STREAK) {
            count++
            cursor = DateRanges.plusDays(cursor, -1)
        }
        return count
    }

    fun checkedToday(habit: Habit, now: Long = System.currentTimeMillis()): Boolean =
        habit.isCheckedIn(DateRanges.dayKey(now))

    /** 打卡 / 取消今天的打卡。 */
    fun toggleToday(habit: Habit, now: Long = System.currentTimeMillis()): Habit {
        val key = DateRanges.dayKey(now)
        val days = habit.checkInDays.toMutableList()
        if (days.contains(key)) days.remove(key) else days.add(key)
        return habit.copy(checkInDays = days.sortedDescending())
    }

    fun checkIn(habit: Habit, now: Long = System.currentTimeMillis()): Habit {
        val key = DateRanges.dayKey(now)
        if (habit.checkInDays.contains(key)) return habit
        return habit.copy(checkInDays = (habit.checkInDays + key).sortedDescending())
    }

    fun totalCount(habit: Habit): Int = habit.checkInDays.size

    /** 近 [days] 天的打卡情况，最后一个元素是今天。 */
    fun recentDays(habit: Habit, days: Int, now: Long = System.currentTimeMillis()): List<HabitDay> {
        val today = DateRanges.startOfDay(now)
        return (days - 1 downTo 0).map { offset ->
            val dayStart = DateRanges.plusDays(today, -offset)
            val key = DateRanges.dayKey(dayStart)
            val calendar = Calendar.getInstance().apply { timeInMillis = dayStart }
            HabitDay(
                dayKey = key,
                label = "${calendar.get(Calendar.DAY_OF_MONTH)}",
                checkedIn = habit.isCheckedIn(key)
            )
        }
    }

    /** 近 [days] 天的完成率，0..1。 */
    fun completionRate(habit: Habit, days: Int, now: Long = System.currentTimeMillis()): Float {
        val recent = recentDays(habit, days, now)
        if (recent.isEmpty()) return 0f
        return recent.count { it.checkedIn }.toFloat() / recent.size
    }

    /** 今日整体完成情况，用于首页与回顾页的「习惯 3/5」。 */
    fun todayProgress(habits: List<Habit>, now: Long = System.currentTimeMillis()): Pair<Int, Int> {
        val active = habits.filterNot { it.archived }
        if (active.isEmpty()) return 0 to 0
        val done = active.count { checkedToday(it, now) }
        return done to active.size
    }

    fun formatReminder(minuteOfDay: Int?): String {
        if (minuteOfDay == null) return "不提醒"
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        return String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
    }

    /** 下一次提醒的时间点：今天还没到就今天，否则明天。 */
    fun nextReminderAt(minuteOfDay: Int, now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = DateRanges.startOfDay(now)
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }

    /** 明天这个点的提醒。今天已经打过卡时用它，避免晚上又催一次。 */
    fun tomorrowReminderAt(minuteOfDay: Int, now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = DateRanges.startOfDay(now)
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

    private const val MAX_STREAK = 3_650
}
