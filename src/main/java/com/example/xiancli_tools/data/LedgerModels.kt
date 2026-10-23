package com.example.xiancli_tools.data

import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

enum class ExpenseCategory(val label: String) {
    FOOD("餐饮"),
    TRANSPORT("交通"),
    HOTEL("酒店"),
    TICKET("门票"),
    SHOPPING("购物"),
    ENTERTAINMENT("娱乐"),
    MEDICAL("医疗"),
    OTHER("其他")
}

data class ExpenseRecord(
    val id: Long,
    val category: ExpenseCategory,
    val amountCents: Long,
    val note: String,
    val timeMillis: Long
)

enum class LedgerPeriod(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    CUSTOM("自定义")
}

data class CategorySummary(
    val category: ExpenseCategory,
    val amountCents: Long,
    val count: Int
)

data class TrendBucket(val label: String, val amountCents: Long)

data class LedgerSummary(
    val period: LedgerPeriod,
    val startMillis: Long,
    val endMillis: Long,
    val records: List<ExpenseRecord>
) {
    val range: DateRange
        get() = DateRange(startMillis, endMillis)

    /** 报表标题用词：本日 / 本周 / 本月 / 本年 / 所选区间。 */
    val rangeName: String
        get() = if (period == LedgerPeriod.CUSTOM) "所选区间" else "本${period.label}"

    /** 趋势峰值描述用词：单日最高 / 单周最高 / 单月最高 / 单年最高。 */
    val peakLabel: String
        get() = if (period == LedgerPeriod.CUSTOM) {
            "单${TrendBuckets.plan(range).unit.label}最高"
        } else {
            "单${period.label}最高"
        }

    val totalCents: Long
        get() = records.sumOf { it.amountCents }

    val recordCount: Int
        get() = records.size

    val maxRecordCents: Long
        get() = records.maxOfOrNull { it.amountCents } ?: 0L

    val averageCents: Long
        get() = if (records.isEmpty()) 0L else totalCents / records.size

    val categorySummaries: List<CategorySummary>
        get() = ExpenseCategory.entries.mapNotNull { category ->
            val ofCategory = records.filter { it.category == category }
            if (ofCategory.isEmpty()) {
                null
            } else {
                CategorySummary(
                    category = category,
                    amountCents = ofCategory.sumOf { it.amountCents },
                    count = ofCategory.size
                )
            }
        }.sortedByDescending { it.amountCents }
}

object LedgerAnalytics {

    fun summarize(
        records: List<ExpenseRecord>,
        period: LedgerPeriod,
        now: Long = System.currentTimeMillis(),
        customRange: DateRange? = null
    ): LedgerSummary {
        val range = rangeOf(period, now, customRange)
        val inRange = records
            .filter { it.timeMillis in range.startMillis until range.endMillis }
            .sortedByDescending { it.timeMillis }
        return LedgerSummary(period, range.startMillis, range.endMillis, inRange)
    }

    /** 把「日 / 周 / 月 / 年 / 自定义」统一折算成具体区间。 */
    fun rangeOf(
        period: LedgerPeriod,
        now: Long,
        customRange: DateRange? = null
    ): DateRange = when (period) {
        LedgerPeriod.DAY -> DateRanges.day(now)
        LedgerPeriod.WEEK -> DateRanges.week(now)
        LedgerPeriod.MONTH -> DateRanges.month(now)
        LedgerPeriod.YEAR -> DateRanges.year(now)
        LedgerPeriod.CUSTOM -> customRange ?: DateRanges.lastDays(now)
    }

    fun startOfPeriod(period: LedgerPeriod, now: Long): Long = rangeOf(period, now).startMillis

    fun trend(summary: LedgerSummary): List<TrendBucket> {
        val calendar = Calendar.getInstance()
        return when (summary.period) {
            LedgerPeriod.DAY -> {
                val buckets = LongArray(24)
                summary.records.forEach { record ->
                    calendar.timeInMillis = record.timeMillis
                    buckets[calendar.get(Calendar.HOUR_OF_DAY)] += record.amountCents
                }
                (0..23).map { hour ->
                    TrendBucket(if (hour % 6 == 0) "${hour}时" else "", buckets[hour])
                }
            }
            LedgerPeriod.WEEK -> {
                val buckets = LongArray(7)
                summary.records.forEach { record ->
                    calendar.timeInMillis = record.timeMillis
                    val dayFromMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    buckets[dayFromMonday] += record.amountCents
                }
                val labels = listOf("一", "二", "三", "四", "五", "六", "日")
                (0..6).map { TrendBucket(labels[it], buckets[it]) }
            }
            LedgerPeriod.MONTH -> {
                val days = daysInMonth(summary.startMillis)
                val buckets = LongArray(days)
                summary.records.forEach { record ->
                    calendar.timeInMillis = record.timeMillis
                    val index = calendar.get(Calendar.DAY_OF_MONTH) - 1
                    if (index in buckets.indices) {
                        buckets[index] += record.amountCents
                    }
                }
                (0 until days).map { index ->
                    val day = index + 1
                    TrendBucket(if (day == 1 || day % 5 == 0) "$day" else "", buckets[index])
                }
            }
            LedgerPeriod.YEAR -> {
                val buckets = LongArray(12)
                summary.records.forEach { record ->
                    calendar.timeInMillis = record.timeMillis
                    buckets[calendar.get(Calendar.MONTH)] += record.amountCents
                }
                (0..11).map { TrendBucket("${it + 1}月", buckets[it]) }
            }
            LedgerPeriod.CUSTOM -> {
                val plan = TrendBuckets.plan(summary.range)
                val buckets = LongArray(plan.size)
                summary.records.forEach { record ->
                    val index = plan.indexOf(record.timeMillis)
                    if (index in buckets.indices) buckets[index] += record.amountCents
                }
                plan.labels.mapIndexed { index, label -> TrendBucket(label, buckets[index]) }
            }
        }
    }

    private fun daysInMonth(millis: Long): Int = Calendar.getInstance().apply {
        timeInMillis = millis
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    fun parseAmountToCents(input: String): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toDoubleOrNull() ?: return null
        if (value < 0 || value.isNaN() || value.isInfinite()) return null
        return (value * 100).roundToLong()
    }

    fun formatYuan(cents: Long): String =
        String.format(Locale.CHINA, "¥%.2f", cents / 100.0)

    fun formatAmount(cents: Long): String =
        String.format(Locale.CHINA, "%.2f", cents / 100.0)

    fun formatDateHeading(summary: LedgerSummary): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = summary.startMillis }
        val year = calendar.get(Calendar.YEAR)
        return when (summary.period) {
            LedgerPeriod.DAY -> String.format(
                Locale.CHINA,
                "%d年%d月%d日",
                year,
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            LedgerPeriod.WEEK -> String.format(
                Locale.CHINA,
                "%d年%d月%d日 起",
                year,
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            LedgerPeriod.MONTH -> String.format(
                Locale.CHINA,
                "%d年%d月",
                year,
                calendar.get(Calendar.MONTH) + 1
            )
            LedgerPeriod.YEAR -> String.format(Locale.CHINA, "%d年", year)
            LedgerPeriod.CUSTOM -> DateRanges.formatHeading(summary.range)
        }
    }
}
