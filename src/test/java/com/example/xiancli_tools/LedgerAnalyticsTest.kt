package com.example.xiancli_tools

import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.LedgerAnalytics
import com.example.xiancli_tools.data.LedgerPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class LedgerAnalyticsTest {

    @Test
    fun summarize_only_counts_records_inside_period() {
        val now = millisOf(2026, Calendar.JUNE, 15, 12, 0)
        val today = record(ExpenseCategory.FOOD, 2500, millisOf(2026, Calendar.JUNE, 15, 8, 0))
        val lastMonth = record(
            ExpenseCategory.TRANSPORT,
            300,
            millisOf(2026, Calendar.MAY, 20, 8, 0)
        )

        val day = LedgerAnalytics.summarize(listOf(today, lastMonth), LedgerPeriod.DAY, now)
        assertEquals(1, day.recordCount)
        assertEquals(2500L, day.totalCents)

        val month = LedgerAnalytics.summarize(listOf(today, lastMonth), LedgerPeriod.MONTH, now)
        assertEquals(1, month.recordCount)

        val year = LedgerAnalytics.summarize(listOf(today, lastMonth), LedgerPeriod.YEAR, now)
        assertEquals(2, year.recordCount)
        assertEquals(2800L, year.totalCents)
    }

    @Test
    fun category_summaries_aggregate_and_sort_by_amount() {
        val now = millisOf(2026, Calendar.JUNE, 15, 12, 0)
        val food1 = record(ExpenseCategory.FOOD, 2000, millisOf(2026, Calendar.JUNE, 15, 7, 0))
        val food2 = record(ExpenseCategory.FOOD, 1000, millisOf(2026, Calendar.JUNE, 15, 9, 0))
        val hotel = record(ExpenseCategory.HOTEL, 50000, millisOf(2026, Calendar.JUNE, 15, 10, 0))

        val day = LedgerAnalytics.summarize(listOf(food1, food2, hotel), LedgerPeriod.DAY, now)
        val summaries = day.categorySummaries

        assertEquals(ExpenseCategory.HOTEL, summaries.first().category)
        assertEquals(50000L, summaries.first().amountCents)

        val food = summaries.first { it.category == ExpenseCategory.FOOD }
        assertEquals(3000L, food.amountCents)
        assertEquals(2, food.count)

        assertEquals(53000L, day.totalCents)
        assertEquals(17666L, day.averageCents)
        assertEquals(50000L, day.maxRecordCents)
    }

    @Test
    fun week_trend_places_amount_on_weekday_bucket() {
        val now = millisOf(2026, Calendar.JUNE, 17, 12, 0)
        val wednesday = record(ExpenseCategory.FOOD, 4000, millisOf(2026, Calendar.JUNE, 17, 9, 0))

        val summary = LedgerAnalytics.summarize(listOf(wednesday), LedgerPeriod.WEEK, now)
        val trend = LedgerAnalytics.trend(summary)

        assertEquals(7, trend.size)
        assertEquals("三", trend[2].label)
        assertEquals(4000L, trend[2].amountCents)
        assertEquals(0L, trend[0].amountCents)
    }

    @Test
    fun year_trend_has_twelve_monthly_buckets() {
        val now = millisOf(2026, Calendar.JUNE, 17, 12, 0)
        val june = record(ExpenseCategory.TICKET, 12000, millisOf(2026, Calendar.JUNE, 5, 9, 0))
        val march = record(ExpenseCategory.MEDICAL, 8000, millisOf(2026, Calendar.MARCH, 5, 9, 0))

        val summary = LedgerAnalytics.summarize(listOf(june, march), LedgerPeriod.YEAR, now)
        val trend = LedgerAnalytics.trend(summary)

        assertEquals(12, trend.size)
        assertEquals(12000L, trend[5].amountCents)
        assertEquals(8000L, trend[2].amountCents)
    }

    @Test
    fun parse_amount_to_cents_handles_valid_and_invalid_input() {
        assertEquals(1250L, LedgerAnalytics.parseAmountToCents("12.5"))
        assertEquals(1200L, LedgerAnalytics.parseAmountToCents("12"))
        assertEquals(0L, LedgerAnalytics.parseAmountToCents("0"))
        assertNull(LedgerAnalytics.parseAmountToCents(""))
        assertNull(LedgerAnalytics.parseAmountToCents("abc"))
        assertNull(LedgerAnalytics.parseAmountToCents("-3"))
    }

    @Test
    fun format_yuan_renders_two_decimals() {
        assertEquals("¥12.50", LedgerAnalytics.formatYuan(1250))
        assertEquals("¥0.00", LedgerAnalytics.formatYuan(0))
    }

    private fun record(
        category: ExpenseCategory,
        cents: Long,
        timeMillis: Long
    ) = ExpenseRecord(
        id = timeMillis,
        category = category,
        amountCents = cents,
        note = "",
        timeMillis = timeMillis
    )

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
