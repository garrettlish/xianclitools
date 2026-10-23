package com.example.xiancli_tools.data

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

enum class TransportMode(val label: String) {
    WALK("步行"),
    BIKE("骑车"),
    DRIVE("开车")
}

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long
)

data class TrackSession(
    val id: Long,
    val mode: TransportMode,
    val startMillis: Long,
    val endMillis: Long,
    val distanceMeters: Double,
    val points: List<TrackPoint>
) {
    val durationMillis: Long
        get() = when {
            endMillis > startMillis -> endMillis - startMillis
            points.size >= 2 -> points.last().timeMillis - points.first().timeMillis
            else -> 0L
        }
}

enum class TrackPeriod(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    CUSTOM("自定义")
}

data class ModeSummary(
    val mode: TransportMode,
    val distanceMeters: Double,
    val durationMillis: Long,
    val sessionCount: Int
)

data class PeriodSummary(
    val period: TrackPeriod,
    val startMillis: Long,
    val endMillis: Long,
    val sessions: List<TrackSession>
) {
    val range: DateRange
        get() = DateRange(startMillis, endMillis)

    /** 报表标题用词：本日 / 本周 / 本月 / 本年 / 所选区间。 */
    val rangeName: String
        get() = if (period == TrackPeriod.CUSTOM) "所选区间" else "本${period.label}"

    val totalDistanceMeters: Double
        get() = sessions.sumOf { it.distanceMeters }

    val totalDurationMillis: Long
        get() = sessions.sumOf { it.durationMillis }

    val modeSummaries: List<ModeSummary>
        get() = TransportMode.entries.map { mode ->
            val ofMode = sessions.filter { it.mode == mode }
            ModeSummary(
                mode = mode,
                distanceMeters = ofMode.sumOf { it.distanceMeters },
                durationMillis = ofMode.sumOf { it.durationMillis },
                sessionCount = ofMode.size
            )
        }.filter { it.sessionCount > 0 }
}

object TrackAnalytics {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(a: TrackPoint, b: TrackPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
    }

    fun summarize(
        sessions: List<TrackSession>,
        period: TrackPeriod,
        now: Long = System.currentTimeMillis(),
        customRange: DateRange? = null
    ): PeriodSummary {
        val range = rangeOf(period, now, customRange)
        val inRange = sessions
            .filter { it.startMillis in range.startMillis until range.endMillis }
            .sortedByDescending { it.startMillis }
        return PeriodSummary(period, range.startMillis, range.endMillis, inRange)
    }

    /** 把「日 / 周 / 月 / 年 / 自定义」统一折算成具体区间。 */
    fun rangeOf(
        period: TrackPeriod,
        now: Long,
        customRange: DateRange? = null
    ): DateRange = when (period) {
        TrackPeriod.DAY -> DateRanges.day(now)
        TrackPeriod.WEEK -> DateRanges.week(now)
        TrackPeriod.MONTH -> DateRanges.month(now)
        TrackPeriod.YEAR -> DateRanges.year(now)
        TrackPeriod.CUSTOM -> customRange ?: DateRanges.lastDays(now)
    }

    fun startOfPeriod(period: TrackPeriod, now: Long): Long = rangeOf(period, now).startMillis

    fun formatDistance(meters: Double): String = when {
        meters <= 0 -> "0 米"
        meters < 1000 -> "${meters.roundToLong()} 米"
        else -> String.format(Locale.US, "%.2f 公里", meters / 1000.0)
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分"
            hours > 0 -> "$hours 小时"
            totalMinutes > 0 -> "$minutes 分钟"
            millis > 0 -> "不到 1 分钟"
            else -> "0 分钟"
        }
    }

    fun formatClock(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
