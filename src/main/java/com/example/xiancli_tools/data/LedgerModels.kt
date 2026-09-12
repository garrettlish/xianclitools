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
    YEAR("年")
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
        now: Long = System.currentTimeMillis()
    ): LedgerSummary {
        val start = startOfPeriod(period, now)
        val end = startOfNextPeriod(period, start)
        val inRange = records
            .filter { it.timeMillis in start until end }
            .sortedByDescending { it.timeMillis }
        return LedgerSummary(period, start, end, inRange)
    }

    fun startOfPeriod(period: LedgerPeriod, now: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            LedgerPeriod.DAY -> Unit
            LedgerPeriod.WEEK -> {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = (dayOfWeek + 5) % 7
                calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
            }
            LedgerPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            LedgerPeriod.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun startOfNextPeriod(period: LedgerPeriod, startMillis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = startMillis
        }
        when (period) {
            LedgerPeriod.DAY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            LedgerPeriod.WEEK -> calendar.add(Calendar.DAY_OF_MONTH, 7)
            LedgerPeriod.MONTH -> calendar.add(Calendar.MONTH, 1)
            LedgerPeriod.YEAR -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }

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
        }
    }
}
