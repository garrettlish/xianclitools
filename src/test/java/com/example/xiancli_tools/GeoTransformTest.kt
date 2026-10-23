package com.example.xiancli_tools

import com.example.xiancli_tools.data.GeoTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GeoTransformTest {

    @Test
    fun wgs84_to_gcj02_matches_known_offset() {
        // 西安钟楼附近，公开算法的参考输出
        val (lat, lng) = GeoTransform.wgs84ToGcj02(34.2610, 108.9450)
        assertEquals(34.2594179770848, lat, 1e-9)
        assertEquals(108.94966352747596, lng, 1e-9)
    }

    @Test
    fun offset_is_a_few_hundred_meters_inside_china() {
        // 偏移量级是重点：几十米说明没转换，几公里说明转换错了
        val wgsLat = 34.2610
        val wgsLng = 108.9450
        val (lat, lng) = GeoTransform.wgs84ToGcj02(wgsLat, wgsLng)
        val meters = haversineMeters(wgsLat, wgsLng, lat, lng)
        assertTrue("expected 300~700m offset, got $meters m", meters in 300.0..700.0)
    }

    @Test
    fun coordinates_outside_china_are_untouched() {
        val (lat, lng) = GeoTransform.wgs84ToGcj02(35.6812, 139.7671)
        assertEquals(35.6812, lat, 0.0)
        assertEquals(139.7671, lng, 0.0)
    }

    @Test
    fun out_of_china_check_covers_the_bounds() {
        assertTrue(GeoTransform.isOutOfChina(35.6812, 139.7671))
        assertTrue(GeoTransform.isOutOfChina(-33.8688, 151.2093))
        assertTrue(!GeoTransform.isOutOfChina(34.2610, 108.9450))
        assertTrue(!GeoTransform.isOutOfChina(22.5431, 114.0579))
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
        return 2 * 6_371_000.0 * asin(sqrt(h).coerceAtMost(1.0))
    }
}
