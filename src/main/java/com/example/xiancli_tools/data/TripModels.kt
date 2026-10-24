package com.example.xiancli_tools.data

import java.util.Calendar
import java.util.Locale

/**
 * 一次出行：只存「名字 + 起止日」，账目、轨迹、备忘都不搬动，
 * 查询时按时间区间现算。这样已有的记账/足迹数据自动就能被聚合进来，
 * 也不需要给三类数据各加一个 tripId 字段（备份格式反而更稳）。
 */
data class Trip(
    val id: Long,
    val name: String,
    /** 起始自然日零点。 */
    val startMillis: Long,
    /** 结束自然日零点（含当天）。 */
    val endMillis: Long,
    val note: String = ""
) {
    val range: DateRange
        get() = DateRange(startMillis, DateRanges.plusDays(endMillis, 1))

    val dayCount: Int
        get() = DateRanges.daysBetween(startMillis, DateRanges.plusDays(endMillis, 1))
}

/** 自动识别出来的候选出行，用户确认后才落库。 */
data class TripCandidate(
    val name: String,
    val startMillis: Long,
    val endMillis: Long,
    val reason: String
)

data class TripSummary(
    val trip: Trip,
    val expenseCents: Long,
    val expenseCount: Int,
    val categoryTotals: List<Pair<ExpenseCategory, Long>>,
    val distanceMeters: Double,
    val trackMillis: Long,
    val trackCount: Int,
    val noteCount: Int
) {
    val topCategory: Pair<ExpenseCategory, Long>?
        get() = categoryTotals.firstOrNull()
}

object TripAnalytics {

    fun summarize(
        trip: Trip,
        records: List<ExpenseRecord>,
        tracks: List<TrackSession>,
        notes: List<Note>
    ): TripSummary {
        val range = trip.range
        val tripRecords = records.filter { it.timeMillis in range.startMillis until range.endMillis }
        val tripTracks = tracks.filter { it.startMillis in range.startMillis until range.endMillis }
        val tripNotes = notes.filter {
            it.timeMillis in range.startMillis until range.endMillis &&
                it.category != NoteCategory.MOOD
        }
        val categories = tripRecords
            .groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
        return TripSummary(
            trip = trip,
            expenseCents = tripRecords.sumOf { it.amountCents },
            expenseCount = tripRecords.size,
            categoryTotals = categories,
            distanceMeters = tripTracks.sumOf { it.distanceMeters },
            trackMillis = tripTracks.sumOf { it.durationMillis },
            trackCount = tripTracks.size,
            noteCount = tripNotes.size
        )
    }

    /**
     * 从账本里倒推候选出行：酒店 / 门票 是旅行最明显的两个信号，
     * 相隔不超过 [GAP_DAYS] 天的这类消费会并成同一趟。
     */
    fun suggest(
        records: List<ExpenseRecord>,
        existing: List<Trip>,
        limit: Int = 5
    ): List<TripCandidate> {
        val signalDays = records
            .filter { it.category == ExpenseCategory.HOTEL || it.category == ExpenseCategory.TICKET }
            .map { DateRanges.startOfDay(it.timeMillis) }
            .distinct()
            .sorted()
        if (signalDays.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<Long>>()
        signalDays.forEach { day ->
            val last = clusters.lastOrNull()
            if (last != null && DateRanges.daysBetween(last.last(), DateRanges.plusDays(day, 1)) <= GAP_DAYS) {
                last.add(day)
            } else {
                clusters.add(mutableListOf(day))
            }
        }

        return clusters
            .map { days ->
                val start = days.first()
                val end = days.last()
                val daysInTrip = DateRanges.daysBetween(start, DateRanges.plusDays(end, 1))
                val inTrip = records.filter {
                    it.timeMillis in start until DateRanges.plusDays(end, 1)
                }
                val hotelDays = inTrip.count {
                    it.category == ExpenseCategory.HOTEL
                }
                TripCandidate(
                    name = suggestName(inTrip, start),
                    startMillis = start,
                    endMillis = end,
                    reason = buildString {
                        append("${daysInTrip} 天")
                        if (hotelDays > 0) append(" · $hotelDays 笔住宿")
                        append(" · ${inTrip.size} 笔账目")
                    }
                )
            }
            .filterNot { candidate ->
                existing.any { trip ->
                    candidate.startMillis <= trip.endMillis && candidate.endMillis >= trip.startMillis
                }
            }
            .sortedByDescending { it.startMillis }
            .take(limit)
    }

    private fun suggestName(records: List<ExpenseRecord>, startMillis: Long): String {
        val hotel = records.firstOrNull {
            it.category == ExpenseCategory.HOTEL && it.note.isNotBlank()
        }
        if (hotel != null) return hotel.note.trim().take(12)
        val calendar = Calendar.getInstance().apply { timeInMillis = startMillis }
        return String.format(
            Locale.CHINA,
            "%d月%d日 出行",
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatRange(trip: Trip): String {
        val start = Calendar.getInstance().apply { timeInMillis = trip.startMillis }
        val end = Calendar.getInstance().apply { timeInMillis = trip.endMillis }
        val startText = String.format(
            Locale.CHINA,
            "%d月%d日",
            start.get(Calendar.MONTH) + 1,
            start.get(Calendar.DAY_OF_MONTH)
        )
        return if (trip.startMillis == trip.endMillis) {
            startText
        } else {
            val endText = String.format(
                Locale.CHINA,
                "%d月%d日",
                end.get(Calendar.MONTH) + 1,
                end.get(Calendar.DAY_OF_MONTH)
            )
            "$startText – $endText"
        }
    }

    private const val GAP_DAYS = 3
}
