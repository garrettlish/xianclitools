package com.example.xiancli_tools

import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.Habit
import com.example.xiancli_tools.data.HabitAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HabitAnalyticsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun habitWith(days: List<Long>): Habit = Habit(
        id = 1L,
        name = "喝水",
        iconKey = "water",
        createdMillis = at(2026, 10, 1),
        checkInDays = days.map { DateRanges.dayKey(it) }.sortedDescending()
    )

    @Test
    fun `连续天数从今天往回数`() {
        val now = at(2026, 10, 24)
        val habit = habitWith(listOf(at(2026, 10, 22), at(2026, 10, 23), at(2026, 10, 24)))

        assertEquals(3, HabitAnalytics.streak(habit, now))
    }

    @Test
    fun `今天没打卡时从昨天开始数，不算断`() {
        val now = at(2026, 10, 24)
        val habit = habitWith(listOf(at(2026, 10, 22), at(2026, 10, 23)))

        assertEquals(2, HabitAnalytics.streak(habit, now))
    }

    @Test
    fun `中间断一天就重新计数`() {
        val now = at(2026, 10, 24)
        val habit = habitWith(listOf(at(2026, 10, 19), at(2026, 10, 23), at(2026, 10, 24)))

        assertEquals(2, HabitAnalytics.streak(habit, now))
    }

    @Test
    fun `打卡与取消打卡可以来回切换`() {
        val now = at(2026, 10, 24)
        val habit = habitWith(emptyList())

        val checked = HabitAnalytics.toggleToday(habit, now)
        assertTrue(HabitAnalytics.checkedToday(checked, now))
        assertEquals(1, checked.checkInDays.size)

        val unchecked = HabitAnalytics.toggleToday(checked, now)
        assertFalse(HabitAnalytics.checkedToday(unchecked, now))
        assertEquals(0, unchecked.checkInDays.size)
    }

    @Test
    fun `重复打卡同一天不会写两条`() {
        val now = at(2026, 10, 24)
        val habit = habitWith(listOf(at(2026, 10, 24)))

        val again = HabitAnalytics.checkIn(habit, now)

        assertEquals(1, again.checkInDays.size)
    }

    @Test
    fun `提醒时间已过就排到明天`() {
        val now = at(2026, 10, 24, hour = 9)
        val next = HabitAnalytics.nextReminderAt(8 * 60, now)

        assertEquals(DateRanges.dayKey(at(2026, 10, 25)), DateRanges.dayKey(next))
    }

    @Test
    fun `明天提醒始终落在明天`() {
        val now = at(2026, 10, 24, hour = 9)
        val next = HabitAnalytics.tomorrowReminderAt(21 * 60, now)

        assertEquals(DateRanges.dayKey(at(2026, 10, 25)), DateRanges.dayKey(next))
    }
}
