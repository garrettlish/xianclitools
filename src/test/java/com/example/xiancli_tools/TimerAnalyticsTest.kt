package com.example.xiancli_tools

import com.example.xiancli_tools.data.TimerAnalytics
import com.example.xiancli_tools.data.TimerPeriod
import com.example.xiancli_tools.data.TimerSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TimerAnalyticsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val minute = 60_000L

    @Test
    fun `完成的计时按计划时长算专注`() {
        val start = at(2026, 10, 24)
        val session = TimerSession(
            id = start,
            label = "25 分钟",
            plannedMillis = 25 * minute,
            startMillis = start,
            endMillis = start + 25 * minute,
            completed = true
        )

        assertEquals(25 * minute, session.focusMillis)
    }

    @Test
    fun `中途取消只算实际时长，不白送计划时长`() {
        val start = at(2026, 10, 24)
        val session = TimerSession(
            id = start,
            label = "25 分钟",
            plannedMillis = 25 * minute,
            startMillis = start,
            endMillis = start + 5 * minute,
            completed = false
        )

        assertEquals(5 * minute, session.focusMillis)
    }

    @Test
    fun `按区间汇总只算区间内的计时`() {
        val today = at(2026, 10, 24)
        val yesterday = at(2026, 10, 23)
        val sessions = listOf(
            TimerSession(1L, "25 分钟", 25 * minute, today, today + 25 * minute, true),
            TimerSession(2L, "25 分钟", 25 * minute, yesterday, yesterday + 25 * minute, true)
        )

        val summary = TimerAnalytics.summarize(sessions, TimerPeriod.DAY, now = today)

        assertEquals(1, summary.sessionCount)
        assertEquals(25 * minute, summary.totalMillis)
        assertEquals(1, summary.completedCount)
    }

    @Test
    fun `最专注时段取分钟数最多的那一小时`() {
        val base = at(2026, 10, 24, hour = 9)
        val sessions = listOf(
            TimerSession(1L, "50 分钟", 50 * minute, base, base + 50 * minute, true),
            TimerSession(2L, "10 分钟", 10 * minute, base + 2 * 60 * minute, base + 2 * 60 * minute + 10 * minute, true)
        )

        val summary = TimerAnalytics.summarize(sessions, TimerPeriod.DAY, now = base)

        assertEquals(9, summary.bestHour)
    }

    @Test
    fun `近七天序列最后一个永远是今天`() {
        val now = at(2026, 10, 24)
        val sessions = listOf(
            TimerSession(1L, "25 分钟", 25 * minute, now, now + 25 * minute, true)
        )

        val daily = TimerAnalytics.dailyMinutes(sessions, 7, now)

        assertEquals(7, daily.size)
        assertEquals("今天", daily.last().first)
        assertEquals(25f, daily.last().second, 0.01f)
    }

    @Test
    fun `一周趋势七根柱子按星期几归位`() {
        // 2026-10-24 是星期六，落在「六」那一格。
        val saturday = at(2026, 10, 24, hour = 15)
        val sessions = listOf(
            TimerSession(1L, "25 分钟", 25 * minute, saturday, saturday + 25 * minute, true)
        )

        val summary = TimerAnalytics.summarize(sessions, TimerPeriod.WEEK, now = saturday)
        val trend = TimerAnalytics.trend(summary)

        assertEquals(7, trend.size)
        assertEquals("六", trend[5].label)
        assertEquals(25f, trend[5].minutes, 0.01f)
        assertEquals(0f, trend[0].minutes, 0.01f)
    }

    @Test
    fun `时段分布把二十四小时压成六档`() {
        val morning = at(2026, 10, 24, hour = 9)
        val evening = at(2026, 10, 24, hour = 21)
        val sessions = listOf(
            TimerSession(1L, "50 分钟", 50 * minute, morning, morning + 50 * minute, true),
            TimerSession(2L, "10 分钟", 10 * minute, evening, evening + 10 * minute, true)
        )

        val summary = TimerAnalytics.summarize(sessions, TimerPeriod.DAY, now = morning)
        val slots = TimerAnalytics.hourSlots(summary)

        assertEquals(6, slots.size)
        assertEquals(50f, slots[2].minutes, 0.01f) // 8-12
        assertEquals(10f, slots[5].minutes, 0.01f) // 20-24
    }

    @Test
    fun `一周内多天计时会统计活跃天数与中断次数`() {
        val saturday = at(2026, 10, 24)
        val friday = at(2026, 10, 23)
        val sessions = listOf(
            TimerSession(1L, "25 分钟", 25 * minute, saturday, saturday + 25 * minute, true),
            TimerSession(2L, "25 分钟", 25 * minute, saturday + 60 * minute, saturday + 65 * minute, false),
            TimerSession(3L, "25 分钟", 25 * minute, friday, friday + 25 * minute, true)
        )

        val summary = TimerAnalytics.summarize(sessions, TimerPeriod.WEEK, now = saturday)

        assertEquals(3, summary.sessionCount)
        assertEquals(2, summary.completedCount)
        assertEquals(1, summary.interruptedCount)
        assertEquals(2, summary.activeDays)
        // 中断那次只算实际 5 分钟，不白送 25 分钟。
        assertEquals(55 * minute, summary.totalMillis)
    }

    @Test
    fun `时长格式化`() {
        assertEquals("0 分钟", TimerAnalytics.formatDuration(0))
        assertEquals("25 分钟", TimerAnalytics.formatDuration(25 * minute))
        assertEquals("1 小时 5 分", TimerAnalytics.formatDuration(65 * minute))
        assertEquals("1小时5分", TimerAnalytics.formatCompact(65 * minute))
    }
}
