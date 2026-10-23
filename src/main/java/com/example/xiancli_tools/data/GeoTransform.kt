package com.example.xiancli_tools.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS-84 与 GCJ-02 之间的坐标转换。
 *
 * 手机定位（GPS）返回的是 WGS-84 坐标，而国内底图（高德、腾讯等）使用的是
 * 经过国测局偏移的 GCJ-02 坐标。两者在国内相差 300~700 米，直接叠加会出现
 * 整条轨迹偏离道路的情况，因此画到国内底图之前必须先做转换。
 */
object GeoTransform {

    private const val SEMI_MAJOR_AXIS = 6378245.0
    private const val ECCENTRICITY_SQUARED = 0.00669342162296594323

    /** 返回转换后的 (纬度, 经度)。境外坐标原样返回。 */
    fun wgs84ToGcj02(latitude: Double, longitude: Double): Pair<Double, Double> {
        if (isOutOfChina(latitude, longitude)) return latitude to longitude

        val deltaLatitude = transformLatitude(longitude - 105.0, latitude - 35.0)
        val deltaLongitude = transformLongitude(longitude - 105.0, latitude - 35.0)
        val radLatitude = latitude / 180.0 * Math.PI
        val magic = 1 - ECCENTRICITY_SQUARED * sin(radLatitude) * sin(radLatitude)
        val sqrtMagic = sqrt(magic)

        val dLat = deltaLatitude * 180.0 /
            (SEMI_MAJOR_AXIS * (1 - ECCENTRICITY_SQUARED) / (magic * sqrtMagic) * Math.PI)
        val dLng = deltaLongitude * 180.0 /
            (SEMI_MAJOR_AXIS / sqrtMagic * cos(radLatitude) * Math.PI)
        return latitude + dLat to longitude + dLng
    }

    fun isOutOfChina(latitude: Double, longitude: Double): Boolean =
        longitude !in 73.66..135.05 || latitude !in 3.86..53.55

    private fun transformLatitude(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
