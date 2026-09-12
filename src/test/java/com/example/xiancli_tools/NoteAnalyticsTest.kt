package com.example.xiancli_tools

import com.example.xiancli_tools.data.Note
import com.example.xiancli_tools.data.NoteAnalytics
import com.example.xiancli_tools.data.NoteCategory
import com.example.xiancli_tools.data.NotePeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class NoteAnalyticsTest {

    @Test
    fun summarize_only_counts_notes_inside_period() {
        val now = millisOf(2026, Calendar.JUNE, 15, 12, 0)
        val today = note(NoteCategory.REMINDER, millisOf(2026, Calendar.JUNE, 15, 8, 0))
        val lastMonth = note(NoteCategory.DIARY, millisOf(2026, Calendar.MAY, 20, 8, 0))

        val day = NoteAnalytics.summarize(listOf(today, lastMonth), NotePeriod.DAY, now)
        assertEquals(1, day.noteCount)

        val month = NoteAnalytics.summarize(listOf(today, lastMonth), NotePeriod.MONTH, now)
        assertEquals(1, month.noteCount)

        val year = NoteAnalytics.summarize(listOf(today, lastMonth), NotePeriod.YEAR, now)
        assertEquals(2, year.noteCount)
    }

    @Test
    fun category_summaries_aggregate_and_sort_by_count() {
        val now = millisOf(2026, Calendar.JUNE, 15, 12, 0)
        val reminder1 = note(NoteCategory.REMINDER, millisOf(2026, Calendar.JUNE, 15, 7, 0))
        val reminder2 = note(NoteCategory.REMINDER, millisOf(2026, Calendar.JUNE, 15, 9, 0))
        val mood = note(NoteCategory.MOOD, millisOf(2026, Calendar.JUNE, 15, 10, 0))

        val day = NoteAnalytics.summarize(listOf(reminder1, reminder2, mood), NotePeriod.DAY, now)

        assertEquals(2, day.categoryCount)
        assertEquals(NoteCategory.REMINDER, day.topCategory?.category)
        assertEquals(2, day.topCategory?.count)

        val moodSummary = day.categorySummaries.first { it.category == NoteCategory.MOOD }
        assertEquals(1, moodSummary.count)
    }

    @Test
    fun week_trend_places_note_on_weekday_bucket() {
        val now = millisOf(2026, Calendar.JUNE, 17, 12, 0)
        val wednesday = note(NoteCategory.IDEA, millisOf(2026, Calendar.JUNE, 17, 9, 0))

        val summary = NoteAnalytics.summarize(listOf(wednesday), NotePeriod.WEEK, now)
        val trend = NoteAnalytics.trend(summary)

        assertEquals(7, trend.size)
        assertEquals("三", trend[2].label)
        assertEquals(1, trend[2].count)
        assertEquals(0, trend[0].count)
    }

    @Test
    fun year_trend_has_twelve_monthly_buckets() {
        val now = millisOf(2026, Calendar.JUNE, 17, 12, 0)
        val june = note(NoteCategory.TODO, millisOf(2026, Calendar.JUNE, 5, 9, 0))
        val march = note(NoteCategory.OTHER, millisOf(2026, Calendar.MARCH, 5, 9, 0))

        val summary = NoteAnalytics.summarize(listOf(june, march), NotePeriod.YEAR, now)
        val trend = NoteAnalytics.trend(summary)

        assertEquals(12, trend.size)
        assertEquals(1, trend[5].count)
        assertEquals(1, trend[2].count)
    }

    private fun note(
        category: NoteCategory,
        timeMillis: Long
    ) = Note(
        id = timeMillis,
        category = category,
        content = "test",
        timeMillis = timeMillis
    )

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
