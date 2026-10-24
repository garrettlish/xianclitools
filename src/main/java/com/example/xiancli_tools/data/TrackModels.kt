package com.example.xiancli_tools.data

import java.util.Calendar
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
    val points: List<TrackPoint>,
    /** 最后修改时间，多设备合并时比新旧（大者胜）。 */
    val updatedAt: Long = endMillis,
    /** 删除墓碑：非空表示已删。删掉轨迹时会把 points 清空，只留墓碑骨架。 */
    val deletedAt: Long? = null
) {
    val durationMillis: Long
        get() = when {
            endMillis > startMillis -> endMillis - startMillis
            points.size >= 2 -> points.last().timeMillis - points.first().timeMillis
            else -> 0L
        }

    fun toBrief(): TrackBrief = TrackBrief(
        id = id,
        mode = mode,
        startMillis = startMillis,
        endMillis = endMillis,
        distanceMeters = distanceMeters,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
}

/**
 * 不含点阵的轨迹骨架：列表、明细、统计、回顾、出行只用得到这些字段。
 *
 * 单独一个类型而不是「points 为空的 TrackSession」，是为了让「拿着摘要去画地图」
 * 这种错误在编译期就不可能——地图那条路走的是 `TrackRepository.loadSessions(ids)`。
 */
data class TrackBrief(
    val id: Long,
    val mode: TransportMode,
    val startMillis: Long,
    val endMillis: Long,
    val distanceMeters: Double,
    val updatedAt: Long = endMillis,
    val deletedAt: Long? = null
) {
    val durationMillis: Long
        get() = if (endMillis > startMillis) endMillis - startMillis else 0L
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

data class TrackTrendBucket(val label: String, val distanceMeters: Double)

data class PeriodSummary(
    val period: TrackPeriod,
    val startMillis: Long,
    val endMillis: Long,
    val sessions: List<TrackBrief>
) {
    val range: DateRange
        get() = DateRange(startMillis, endMillis)

    /** 报表标题用词：本日 / 本周 / 本月 / 本年 / 所选区间。 */
    val rangeName: String
        get() = if (period == TrackPeriod.CUSTOM) "所选区间" else "本${period.label}"

    /** 趋势峰值描述用词：单日最高 / 单周最高 / 单月最高 / 单年最高。 */
    val peakLabel: String
        get() = if (period == TrackPeriod.CUSTOM) {
            "单${TrendBuckets.plan(range).unit.label}最高"
        } else {
            "单${period.label}最高"
        }

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
        sessions: List<TrackBrief>,
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

    /** 每个时间桶里的总里程：日按小时、周按天、月按日、年按月，自定义交给 [TrendBuckets]。 */
    fun trend(summary: PeriodSummary): List<TrackTrendBucket> {
        val calendar = Calendar.getInstance()
        return when (summary.period) {
            TrackPeriod.DAY -> {
                val buckets = DoubleArray(24)
                summary.sessions.forEach { session ->
                    calendar.timeInMillis = session.startMillis
                    buckets[calendar.get(Calendar.HOUR_OF_DAY)] += session.distanceMeters
                }
                (0..23).map { hour ->
                    TrackTrendBucket(if (hour % 6 == 0) "${hour}时" else "", buckets[hour])
                }
            }
            TrackPeriod.WEEK -> {
                val buckets = DoubleArray(7)
                summary.sessions.forEach { session ->
                    calendar.timeInMillis = session.startMillis
                    val dayFromMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    buckets[dayFromMonday] += session.distanceMeters
                }
                val labels = listOf("一", "二", "三", "四", "五", "六", "日")
                (0..6).map { TrackTrendBucket(labels[it], buckets[it]) }
            }
            TrackPeriod.MONTH -> {
                val days = daysInMonth(summary.startMillis)
                val buckets = DoubleArray(days)
                summary.sessions.forEach { session ->
                    calendar.timeInMillis = session.startMillis
                    val index = calendar.get(Calendar.DAY_OF_MONTH) - 1
                    if (index in buckets.indices) buckets[index] += session.distanceMeters
                }
                (0 until days).map { index ->
                    val day = index + 1
                    TrackTrendBucket(if (day == 1 || day % 5 == 0) "$day" else "", buckets[index])
                }
            }
            TrackPeriod.YEAR -> {
                val buckets = DoubleArray(12)
                summary.sessions.forEach { session ->
                    calendar.timeInMillis = session.startMillis
                    buckets[calendar.get(Calendar.MONTH)] += session.distanceMeters
                }
                (0..11).map { TrackTrendBucket("${it + 1}月", buckets[it]) }
            }
            TrackPeriod.CUSTOM -> {
                val plan = TrendBuckets.plan(summary.range)
                val buckets = DoubleArray(plan.size)
                summary.sessions.forEach { session ->
                    val index = plan.indexOf(session.startMillis)
                    if (index in buckets.indices) buckets[index] += session.distanceMeters
                }
                plan.labels.mapIndexed { index, label ->
                    TrackTrendBucket(label, buckets[index])
                }
            }
        }
    }

    private fun daysInMonth(millis: Long): Int = Calendar.getInstance().apply {
        timeInMillis = millis
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

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
