package com.example.xiancli_tools

import com.example.xiancli_tools.data.TrackAnalytics
import com.example.xiancli_tools.data.TrackPeriod
import com.example.xiancli_tools.data.TrackPoint
import com.example.xiancli_tools.data.TrackSession
import com.example.xiancli_tools.data.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TrackAnalyticsTest {

    @Test
    fun haversine_matches_known_distance() {
        // 北京天安门 -> 北京西站，约 6.6 公里
        val tiananmen = TrackPoint(39.9087, 116.3975, 0L)
        val westStation = TrackPoint(39.8949, 116.3220, 0L)
        val meters = TrackAnalytics.haversineMeters(tiananmen, westStation)
        assertTrue("expected ~6.5km, got $meters", meters in 6_300.0..6_900.0)
    }

    @Test
    fun summarize_only_counts_sessions_inside_period() {
        val now = millisOf(2026, Calendar.JUNE, 15, 12, 0)
        val today = session(TransportMode.WALK, millisOf(2026, Calendar.JUNE, 15, 8, 0), 3000.0)
        val lastMonth = session(TransportMode.BIKE, millisOf(2026, Calendar.MAY, 20, 8, 0), 9000.0)

        val day = TrackAnalytics.summarize(listOf(today, lastMonth), TrackPeriod.DAY, now)
        assertEquals(1, day.sessions.size)
        assertEquals(3000.0, day.totalDistanceMeters, 0.001)

        val month = TrackAnalytics.summarize(listOf(today, lastMonth), TrackPeriod.MONTH, now)
        assertEquals(1, month.sessions.size)

        val year = TrackAnalytics.summarize(listOf(today, lastMonth), TrackPeriod.YEAR, now)
        assertEquals(2, year.sessions.size)
        assertEquals(12_000.0, year.totalDistanceMeters, 0.001)
    }

    @Test
    fun mode_summaries_aggregate_distance_and_count() {
        val now = millisOf(2026, Calendar.JUNE, 15, 12, 0)
        val walk1 = session(TransportMode.WALK, millisOf(2026, Calendar.JUNE, 15, 7, 0), 1000.0)
        val walk2 = session(TransportMode.WALK, millisOf(2026, Calendar.JUNE, 15, 9, 0), 2000.0)
        val drive = session(TransportMode.DRIVE, millisOf(2026, Calendar.JUNE, 15, 10, 0), 5000.0)

        val day = TrackAnalytics.summarize(listOf(walk1, walk2, drive), TrackPeriod.DAY, now)
        val walk = day.modeSummaries.first { it.mode == TransportMode.WALK }
        val driveSummary = day.modeSummaries.first { it.mode == TransportMode.DRIVE }

        assertEquals(2, walk.sessionCount)
        assertEquals(3000.0, walk.distanceMeters, 0.001)
        assertEquals(1, driveSummary.sessionCount)
        assertEquals(5000.0, driveSummary.distanceMeters, 0.001)
    }

    @Test
    fun week_starts_on_monday() {
        val wednesday = millisOf(2026, Calendar.JUNE, 17, 10, 0)
        val start = TrackAnalytics.startOfPeriod(TrackPeriod.WEEK, wednesday)
        val calendar = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH))
    }

    private fun session(mode: TransportMode, startMillis: Long, meters: Double) = TrackSession(
        id = startMillis,
        mode = mode,
        startMillis = startMillis,
        endMillis = startMillis + 30 * 60 * 1000L,
        distanceMeters = meters,
        points = listOf(
            TrackPoint(39.90, 116.39, startMillis),
            TrackPoint(39.91, 116.40, startMillis + 30 * 60 * 1000L)
        )
    )

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
