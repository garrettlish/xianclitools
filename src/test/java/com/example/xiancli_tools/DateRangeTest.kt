package com.example.xiancli_tools

import com.example.xiancli_tools.data.BucketUnit
import com.example.xiancli_tools.data.DateRanges
import com.example.xiancli_tools.data.ExpenseCategory
import com.example.xiancli_tools.data.ExpenseRecord
import com.example.xiancli_tools.data.LedgerAnalytics
import com.example.xiancli_tools.data.LedgerPeriod
import com.example.xiancli_tools.data.TrendBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateRangeTest {

    @Test
    fun default_custom_range_covers_last_seven_days_including_today() {
        val now = millisOf(2026, Calendar.JUNE, 15, 12, 0)

        val range = DateRanges.lastDays(now)

        assertEquals(7, range.dayCount)
        assertEquals(millisOf(2026, Calendar.JUNE, 9, 0, 0), range.startMillis)
        // 半开区间：结束是明天零点，所以 6/15 全天都在区间内
        assertEquals(millisOf(2026, Calendar.JUNE, 16, 0, 0), range.endMillis)
        assertEquals(millisOf(2026, Calendar.JUNE, 15, 0, 0), range.lastDayMillis)
    }

    @Test
    fun of_days_aligns_to_midnight_and_swaps_reversed_input() {
        val range = DateRanges.ofDays(
            millisOf(2026, Calendar.JUNE, 18, 21, 30),
            millisOf(2026, Calendar.JUNE, 12, 6, 15)
        )

        assertEquals(millisOf(2026, Calendar.JUNE, 12, 0, 0), range.startMillis)
        assertEquals(millisOf(2026, Calendar.JUNE, 19, 0, 0), range.endMillis)
        assertEquals(7, range.dayCount)
    }

    @Test
    fun custom_period_filters_records_inside_the_selected_days() {
        val now = millisOf(2026, Calendar.JUNE, 20, 12, 0)
        val range = DateRanges.ofDays(
            millisOf(2026, Calendar.JUNE, 10, 0, 0),
            millisOf(2026, Calendar.JUNE, 16, 0, 0)
        )
        val firstDay = record(millisOf(2026, Calendar.JUNE, 10, 0, 0))
        val lastDay = record(millisOf(2026, Calendar.JUNE, 16, 23, 30))
        val beforeRange = record(millisOf(2026, Calendar.JUNE, 9, 23, 0))
        val afterRange = record(millisOf(2026, Calendar.JUNE, 17, 0, 10))

        val summary = LedgerAnalytics.summarize(
            listOf(firstDay, lastDay, beforeRange, afterRange),
            LedgerPeriod.CUSTOM,
            now,
            range
        )

        assertEquals(2, summary.recordCount)
        assertEquals("所选区间", summary.rangeName)
        assertEquals("2026年6月10日 – 16日", LedgerAnalytics.formatDateHeading(summary))
    }

    @Test
    fun custom_period_without_explicit_range_falls_back_to_last_week() {
        val now = millisOf(2026, Calendar.JUNE, 15, 12, 0)

        val summary = LedgerAnalytics.summarize(
            listOf(record(millisOf(2026, Calendar.JUNE, 9, 8, 0))),
            LedgerPeriod.CUSTOM,
            now
        )

        assertEquals(1, summary.recordCount)
        assertEquals(DateRanges.lastDays(now), summary.range)
    }

    @Test
    fun custom_trend_uses_one_bucket_per_day_for_short_ranges() {
        val range = DateRanges.ofDays(
            millisOf(2026, Calendar.JUNE, 10, 0, 0),
            millisOf(2026, Calendar.JUNE, 16, 0, 0)
        )
        val plan = TrendBuckets.plan(range)

        assertEquals(BucketUnit.DAY, plan.unit)
        assertEquals(7, plan.size)
        assertEquals(0, plan.indexOf(millisOf(2026, Calendar.JUNE, 10, 23, 0)))
        assertEquals(6, plan.indexOf(millisOf(2026, Calendar.JUNE, 16, 0, 0)))
        assertEquals("6/10", plan.labels.first())
        assertTrue(plan.labels.any { it.isNotEmpty() })
    }

    @Test
    fun custom_trend_falls_back_to_weekly_buckets_for_medium_ranges() {
        val range = DateRanges.ofDays(
            millisOf(2026, Calendar.JUNE, 10, 0, 0),
            millisOf(2026, Calendar.JULY, 20, 0, 0)
        )
        val plan = TrendBuckets.plan(range)

        assertEquals(BucketUnit.WEEK, plan.unit)
        assertEquals(6, plan.size)
        assertEquals("6/10", plan.labels.first())
        // 41 天里最后一天落在第 6 个（下标 5）周桶
        assertEquals(5, plan.indexOf(millisOf(2026, Calendar.JULY, 20, 12, 0)))
    }

    @Test
    fun custom_trend_falls_back_to_monthly_buckets_for_long_ranges() {
        val range = DateRanges.ofDays(
            millisOf(2026, Calendar.JANUARY, 5, 0, 0),
            millisOf(2026, Calendar.DECEMBER, 20, 0, 0)
        )
        val plan = TrendBuckets.plan(range)

        assertEquals(BucketUnit.MONTH, plan.unit)
        assertEquals(12, plan.size)
        assertEquals("1月", plan.labels.first())
        assertEquals("12月", plan.labels.last())
    }

    @Test
    fun custom_trend_places_amount_on_the_selected_day_bucket() {
        val now = millisOf(2026, Calendar.JUNE, 20, 12, 0)
        val range = DateRanges.ofDays(
            millisOf(2026, Calendar.JUNE, 10, 0, 0),
            millisOf(2026, Calendar.JUNE, 16, 0, 0)
        )
        val summary = LedgerAnalytics.summarize(
            listOf(record(millisOf(2026, Calendar.JUNE, 12, 9, 0))),
            LedgerPeriod.CUSTOM,
            now,
            range
        )

        val trend = LedgerAnalytics.trend(summary)

        assertEquals(7, trend.size)
        assertEquals(0L, trend[0].amountCents)
        assertEquals(2500L, trend[2].amountCents)
        assertEquals("单日最高", summary.peakLabel)
    }

    @Test
    fun day_key_groups_a_whole_natural_day_and_splits_neighbours() {
        val morning = millisOf(2026, Calendar.JUNE, 15, 0, 0)
        val night = millisOf(2026, Calendar.JUNE, 15, 23, 59)
        val nextDay = millisOf(2026, Calendar.JUNE, 16, 0, 0)

        assertEquals(DateRanges.dayKey(morning), DateRanges.dayKey(night))
        assertTrue(DateRanges.dayKey(morning) != DateRanges.dayKey(nextDay))
    }

    @Test
    fun day_heading_says_today_and_yesterday_then_falls_back_to_date() {
        val now = millisOf(2026, Calendar.JUNE, 15, 18, 0)

        assertEquals("今天", DateRanges.formatDayHeading(millisOf(2026, Calendar.JUNE, 15, 2, 0), now))
        assertEquals("昨天", DateRanges.formatDayHeading(millisOf(2026, Calendar.JUNE, 14, 23, 0), now))

        val older = DateRanges.formatDayHeading(millisOf(2026, Calendar.JUNE, 10, 9, 0), now)
        assertTrue(older.startsWith("6月10日"))
        // 跨年时补上年份，避免翻旧账时分不清哪一年
        val lastYear = DateRanges.formatDayHeading(millisOf(2025, Calendar.DECEMBER, 31, 9, 0), now)
        assertTrue(lastYear.startsWith("2025年12月31日"))
    }

    private fun record(timeMillis: Long) = ExpenseRecord(
        id = timeMillis,
        category = ExpenseCategory.FOOD,
        amountCents = 2500,
        note = "",
        timeMillis = timeMillis
    )

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
