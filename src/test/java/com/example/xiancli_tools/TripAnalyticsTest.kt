package com.example.xiancli_tools

import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.TrackBrief
import com.example.xiancli_tools.data.TransportMode
import com.example.xiancli_tools.data.Trip
import com.example.xiancli_tools.data.TripAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TripAnalyticsTest {

    private fun at(month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(2026, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun record(id: Long, category: ExpenseCategory, amount: Long, day: Long, note: String = "") =
        ExpenseRecord(id = id, category = category, amountCents = amount, note = note, timeMillis = day)

    @Test
    fun `酒店与门票会被并成一次出行`() {
        val records = listOf(
            record(1L, ExpenseCategory.HOTEL, 30_000, at(10, 10), "西安钟楼酒店"),
            record(2L, ExpenseCategory.TICKET, 12_000, at(10, 12)),
            record(3L, ExpenseCategory.FOOD, 3_000, at(10, 11))
        )

        val candidates = TripAnalytics.suggest(records, emptyList())

        assertEquals(1, candidates.size)
        assertEquals(DateRanges.dayKey(at(10, 10)), DateRanges.dayKey(candidates.first().startMillis))
        assertEquals(DateRanges.dayKey(at(10, 12)), DateRanges.dayKey(candidates.first().endMillis))
        assertEquals("西安钟楼酒店", candidates.first().name)
    }

    @Test
    fun `相隔太远的两次行程不会被并在一起`() {
        val records = listOf(
            record(1L, ExpenseCategory.HOTEL, 30_000, at(10, 10)),
            record(2L, ExpenseCategory.HOTEL, 30_000, at(11, 20))
        )

        val candidates = TripAnalytics.suggest(records, emptyList())

        assertEquals(2, candidates.size)
    }

    @Test
    fun `已有行程覆盖的候选不再提示`() {
        val records = listOf(
            record(1L, ExpenseCategory.HOTEL, 30_000, at(10, 10)),
            record(2L, ExpenseCategory.TICKET, 12_000, at(10, 12))
        )
        val existing = listOf(
            Trip(
                id = 1L,
                name = "西安",
                startMillis = DateRanges.startOfDay(at(10, 9)),
                endMillis = DateRanges.startOfDay(at(10, 13))
            )
        )

        assertTrue(TripAnalytics.suggest(records, existing).isEmpty())
    }

    @Test
    fun `没有酒店门票就不猜行程`() {
        val records = listOf(record(1L, ExpenseCategory.FOOD, 3_000, at(10, 10)))

        assertTrue(TripAnalytics.suggest(records, emptyList()).isEmpty())
    }

    @Test
    fun `行程汇总只算区间内的账目轨迹与备忘`() {
        val trip = Trip(
            id = 1L,
            name = "西安",
            startMillis = DateRanges.startOfDay(at(10, 10)),
            endMillis = DateRanges.startOfDay(at(10, 12))
        )
        val records = listOf(
            record(1L, ExpenseCategory.HOTEL, 30_000, at(10, 10)),
            record(2L, ExpenseCategory.FOOD, 3_000, at(10, 11)),
            record(3L, ExpenseCategory.FOOD, 9_900, at(10, 20))
        )
        val tracks = listOf(
            TrackBrief(
                id = 1L,
                mode = TransportMode.WALK,
                startMillis = at(10, 11),
                endMillis = at(10, 11) + 3_600_000L,
                distanceMeters = 2_500.0
            ),
            TrackBrief(
                id = 2L,
                mode = TransportMode.WALK,
                startMillis = at(10, 20),
                endMillis = at(10, 20) + 3_600_000L,
                distanceMeters = 9_000.0
            )
        )
        val notes = listOf(
            Note(1L, NoteCategory.DIARY, "第一天", at(10, 11)),
            Note(2L, NoteCategory.MOOD, "很好", at(10, 11), moodLevel = 5),
            Note(3L, NoteCategory.DIARY, "回家后", at(10, 20))
        )

        val summary = TripAnalytics.summarize(trip, records, tracks, notes)

        assertEquals(33_000L, summary.expenseCents)
        assertEquals(2, summary.expenseCount)
        assertEquals(2_500.0, summary.distanceMeters, 0.01)
        assertEquals(1, summary.trackCount)
        // 心情记录不算行程里的「备忘条数」，否则一次出行会被心情刷屏。
        assertEquals(1, summary.noteCount)
        assertEquals(ExpenseCategory.HOTEL, summary.topCategory?.first)
        assertEquals(3, trip.dayCount)
    }
}
