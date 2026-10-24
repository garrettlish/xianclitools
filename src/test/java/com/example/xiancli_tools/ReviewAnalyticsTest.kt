package com.example.xiancli_tools

import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.Habit
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.ReviewAnalytics
import com.example.xiancli_tools.data.ReviewPeriod
import com.example.xiancli_tools.data.TimerSession
import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.data.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReviewAnalyticsTest {

    private fun at(month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(2026, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val minute = 60_000L

    // 2026-10-24 是周六，所在自然周为 10-19（周一）到 10-25（周日）。
    private val now = at(10, 24)

    @Test
    fun `本周回顾把五个工具的数据汇总到同一区间`() {
        val notes = listOf(
            Note(1L, NoteCategory.TODO, "买菜", at(10, 20), done = true),
            Note(2L, NoteCategory.TODO, "交房租", at(10, 21))
        )
        val records = listOf(
            ExpenseRecord(1L, ExpenseCategory.FOOD, 3_550L, "", at(10, 20)),
            ExpenseRecord(2L, ExpenseCategory.FOOD, 1_000L, "", at(10, 2))
        )
        val tracks = listOf(
            TrackSession(
                id = 1L,
                mode = TransportMode.BIKE,
                startMillis = at(10, 20),
                endMillis = at(10, 20) + 30 * minute,
                distanceMeters = 1_200.0,
                points = listOf(TrackPoint(31.2, 121.4, at(10, 20)))
            )
        )
        val timers = listOf(
            TimerSession(1L, "25 分钟", 25 * minute, at(10, 21), at(10, 21) + 25 * minute, true)
        )
        val habits = listOf(
            Habit(
                id = 1L,
                name = "喝水",
                iconKey = "water",
                createdMillis = at(10, 1),
                checkInDays = listOf(DateRanges.dayKey(now))
            )
        )

        val summary = ReviewAnalytics.summarize(
            period = ReviewPeriod.WEEK,
            notes = notes,
            records = records,
            tracks = tracks,
            timers = timers,
            habits = habits,
            now = now
        )

        // 10-02 那笔不在本周，应被排除。
        assertEquals(3_550L, summary.expenseCents)
        assertEquals(1, summary.expenseCount)
        assertEquals(2, summary.noteCount)
        assertEquals(1, summary.todoDone)
        assertEquals(2, summary.todoTotal)
        assertEquals(1, summary.pendingTodoCount)
        assertEquals(1_200.0, summary.distanceMeters, 0.01)
        assertEquals(25 * minute, summary.focusMillis)
        assertEquals(1, summary.habitDone)
        assertEquals(1, summary.habitTotal)
    }

    @Test
    fun `一句话总结会带上花销与待办`() {
        val summary = ReviewAnalytics.summarize(
            period = ReviewPeriod.DAY,
            notes = listOf(Note(1L, NoteCategory.TODO, "取快递", at(10, 24))),
            records = listOf(ExpenseRecord(1L, ExpenseCategory.FOOD, 2_000L, "", at(10, 24))),
            tracks = emptyList(),
            timers = emptyList(),
            habits = emptyList(),
            now = now
        )

        val headline = ReviewAnalytics.headline(summary)

        assertTrue(headline.contains("¥20"))
        assertTrue(headline.contains("待办"))
    }

    @Test
    fun `没有数据时给出引导而不是空洞的零`() {
        val summary = ReviewAnalytics.summarize(
            period = ReviewPeriod.DAY,
            notes = emptyList(),
            records = emptyList(),
            tracks = emptyList(),
            timers = emptyList(),
            habits = emptyList(),
            now = now
        )

        assertTrue(summary.isEmpty)
        assertTrue(ReviewAnalytics.headline(summary).contains("还没有记录"))
    }

    @Test
    fun `金额格式化`() {
        assertEquals("¥0", ReviewAnalytics.formatMoney(0))
        assertEquals("¥35", ReviewAnalytics.formatMoney(3_500))
        assertEquals("¥35.50", ReviewAnalytics.formatMoney(3_550))
    }
}
