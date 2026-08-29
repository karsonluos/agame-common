package com.robining.games.map

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max

data class MapCoordinate(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
)

data class MapCamera(
    val center: MapCoordinate? = null,
    val zoom: Double = 16.5,
    val pitch: Double = 0.0,
    val bearing: Double = 0.0,
)

enum class MapAppearance { LIGHT, DARK, SATELLITE }

data class MapMarker(
    val coordinate: MapCoordinate,
    val iconResourceId: Int,
    val size: Double = 0.55,
    val rotationDegrees: Double = 0.0,
)

data class MapRoutePoint(
    val coordinate: MapCoordinate,
    val recordedAtMillis: Long,
)

data class MapRoute(
    val points: List<MapRoutePoint>,
    val color: Color,
    val id: String,
)

fun cameraForRoute(points: List<MapRoutePoint>): MapCamera {
    if (points.isEmpty()) return MapCamera()
    val minLatitude = points.minOf { it.coordinate.latitude }
    val maxLatitude = points.maxOf { it.coordinate.latitude }
    val minLongitude = points.minOf { it.coordinate.longitude }
    val maxLongitude = points.maxOf { it.coordinate.longitude }
    val centerLatitude = (minLatitude + maxLatitude) / 2.0
    val centerLongitude = (minLongitude + maxLongitude) / 2.0
    val longitudeSpan = (maxLongitude - minLongitude).coerceAtLeast(0.00001)
    val latitudeSpan = ((maxLatitude - minLatitude) / cos(Math.toRadians(centerLatitude)).coerceAtLeast(.2))
        .coerceAtLeast(0.00001)
    val span = max(longitudeSpan, latitudeSpan * 1.35)
    val zoom = (ln(360.0 / span) / ln(2.0) - 1.35).coerceIn(2.0, 18.0)
    return MapCamera(MapCoordinate(centerLatitude, centerLongitude), zoom)
}

