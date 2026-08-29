package com.robining.games.amaplocation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object AmapLocationCoordinateTransform {
    fun gcj02ToWgs84(latitude: Double, longitude: Double): Pair<Double, Double> {
        if (longitude !in 72.004..137.8347 || latitude !in 0.8293..55.8271) return latitude to longitude
        var estimatedLat = latitude
        var estimatedLng = longitude
        repeat(6) {
            val (offsetLat, offsetLng) = delta(estimatedLat, estimatedLng)
            estimatedLat += latitude - (estimatedLat + offsetLat)
            estimatedLng += longitude - (estimatedLng + offsetLng)
        }
        return estimatedLat to estimatedLng
    }

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

    private fun transformLat(x: Double, y: Double): Double = -100.0 + 2.0 * x + 3.0 * y + .2 * y * y + .1 * x * y + .2 * sqrt(abs(x)) + (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2 / 3 + (20.0 * sin(y * PI) + 40.0 * sin(y / 3 * PI)) * 2 / 3 + (160.0 * sin(y / 12 * PI) + 320 * sin(y * PI / 30)) * 2 / 3
    private fun transformLng(x: Double, y: Double): Double = 300.0 + x + 2.0 * y + .1 * x * x + .1 * x * y + .1 * sqrt(abs(x)) + (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2 / 3 + (20.0 * sin(x * PI) + 40.0 * sin(x / 3 * PI)) * 2 / 3 + (150.0 * sin(x / 12 * PI) + 300.0 * sin(x / 30 * PI)) * 2 / 3
    private const val PI = 3.1415926535897932384626
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
}
