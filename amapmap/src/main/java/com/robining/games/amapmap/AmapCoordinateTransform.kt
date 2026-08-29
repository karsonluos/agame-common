package com.robining.games.amapmap

import com.robining.games.map.MapCoordinate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The app's module contract is WGS-84; AMap draws Chinese mainland data in GCJ-02. */
internal object AmapCoordinateTransform {
    fun wgs84ToGcj02(value: MapCoordinate): MapCoordinate {
        if (outsideChina(value.latitude, value.longitude)) return value
        val delta = delta(value.latitude, value.longitude)
        return value.copy(latitude = value.latitude + delta.first, longitude = value.longitude + delta.second)
    }

    fun gcj02ToWgs84(value: MapCoordinate): MapCoordinate {
        if (outsideChina(value.latitude, value.longitude)) return value
        var estimate = value
        repeat(6) {
            val converted = wgs84ToGcj02(estimate)
            estimate = estimate.copy(
                latitude = estimate.latitude + value.latitude - converted.latitude,
                longitude = estimate.longitude + value.longitude - converted.longitude,
            )
        }
        return estimate
    }

    private fun outsideChina(latitude: Double, longitude: Double): Boolean =
        longitude !in 72.004..137.8347 || latitude !in 0.8293..55.8271

    private fun delta(latitude: Double, longitude: Double): Pair<Double, Double> {
        var lat = transformLat(longitude - 105.0, latitude - 35.0)
        var lng = transformLng(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        lat = lat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        lng = lng * 180.0 / (A / sqrtMagic * cos(radLat) * PI)
        return lat to lng
    }

    private fun transformLat(x: Double, y: Double): Double =
        -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x)) +
            (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0 +
            (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0 +
            (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0

    private fun transformLng(x: Double, y: Double): Double =
        300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x)) +
            (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0 +
            (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0 +
            (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0

    private const val PI = 3.1415926535897932384626
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
}
