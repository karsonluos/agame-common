package com.robining.games.mapboxmap

import com.robining.games.map.MapCoordinate
import com.robining.games.map.MapRoute
import com.robining.games.map.MapRoutePoint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.mapbox.bindgen.Value
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMapComposable
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.ColorValue
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.StringValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineCapValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineJoinValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineLayer
import com.mapbox.maps.extension.compose.style.sources.GeoJSONData
import com.mapbox.maps.extension.compose.style.sources.generated.rememberGeoJsonSourceState

@Composable
@MapboxMapComposable
internal fun MapboxRoute(route: MapRoute) {
    val displayPoints = remember(route.points) { displayRoutePoints(route.points) }
    if (displayPoints.size < 2) return
    val source = rememberGeoJsonSourceState(sourceId = route.id) { lineMetrics = BooleanValue(true) }
    source.data = GeoJSONData(LineString.fromLngLats(displayPoints.map(DisplayRoutePoint::point)))
    LineLayer(source, "${route.id}-outline") {
        lineColor = ColorValue(Color.White.copy(alpha = .76f))
        lineColorUseTheme = StringValue("none")
        lineOpacity = DoubleValue(1.0)
        lineWidth = DoubleValue(10.0)
        lineCap = LineCapValue.ROUND
        lineJoin = LineJoinValue.ROUND
    }
    LineLayer(source, "${route.id}-main") {
        lineGradient = speedGradient(displayPoints, route.color)
        lineGradientUseTheme = StringValue("none")
        lineEmissiveStrength = DoubleValue(1.0)
        lineOpacity = DoubleValue(1.0)
        lineWidth = DoubleValue(6.8)
        lineCap = LineCapValue.ROUND
        lineJoin = LineJoinValue.ROUND
    }
}

private data class DisplayRoutePoint(val point: Point, val speed: Double, val distance: Double)

private fun displayRoutePoints(points: List<MapRoutePoint>) = buildList {
    var last: MapRoutePoint? = null
    var smoothLat = 0.0
    var smoothLng = 0.0
    var cumulative = 0.0
    points.forEach { item ->
        val previous = last
        if (previous == null) {
            add(DisplayRoutePoint(Point.fromLngLat(item.coordinate.longitude, item.coordinate.latitude), 0.0, 0.0))
            last = item
            smoothLat = item.coordinate.latitude
            smoothLng = item.coordinate.longitude
            return@forEach
        }
        val elapsed = item.recordedAtMillis - previous.recordedAtMillis
        if (elapsed <= 0L) return@forEach
        val distance = distanceMeters(previous.coordinate, item.coordinate)
        val speed = distance / (elapsed / 1_000.0)
        if (speed > 30.0 || distance < 2.0 && speed < .5) return@forEach
        val alpha = if (speed >= 2.0) .72 else .52
        smoothLat += alpha * (item.coordinate.latitude - smoothLat)
        smoothLng += alpha * (item.coordinate.longitude - smoothLng)
        cumulative += distance
        add(DisplayRoutePoint(Point.fromLngLat(smoothLng, smoothLat), speed, cumulative))
        last = item
    }
}

private fun speedGradient(points: List<DisplayRoutePoint>, fallback: Color): ColorValue {
    val total = points.last().distance
    if (total <= 0.0) return ColorValue(fallback)
    val expression = buildList<Any> {
        add("interpolate"); add(listOf("linear")); add(listOf("line-progress"))
        var lastDistance = Double.NEGATIVE_INFINITY
        var lastColor: String? = null
        points.forEachIndexed { index, point ->
            val color = when {
                point.speed < .8 -> "#4DA3FF"
                point.speed < 1.2 -> "#2BE7F5"
                point.speed < 1.7 -> "#4ADE80"
                point.speed < 2.2 -> "#FBBF24"
                else -> "#FF5B63"
            }
            if (index == 0 || index == points.lastIndex || color != lastColor || point.distance - lastDistance >= 20.0) {
                add((point.distance / total).coerceIn(0.0, 1.0)); add(color)
                lastDistance = point.distance; lastColor = color
            }
        }
    }
    return ColorValue(toValue(expression))
}

private fun toValue(value: Any): Value = when (value) {
    is String -> Value.valueOf(value)
    is Double -> Value.valueOf(value)
    is List<*> -> Value.valueOf(value.map { toValue(requireNotNull(it)) }.toMutableList())
    else -> error("Unsupported Mapbox expression value: $value")
}

private fun distanceMeters(first: MapCoordinate, second: MapCoordinate): Double {
    val lat = Math.toRadians(second.latitude - first.latitude)
    val lng = Math.toRadians(second.longitude - first.longitude)
    val value = kotlin.math.sin(lat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(first.latitude)) * kotlin.math.cos(Math.toRadians(second.latitude)) *
        kotlin.math.sin(lng / 2).let { it * it }
    return 6_371_000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(value.coerceIn(0.0, 1.0)))
}

