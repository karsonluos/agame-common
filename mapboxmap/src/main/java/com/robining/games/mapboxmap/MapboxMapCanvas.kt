package com.robining.games.mapboxmap

import com.robining.games.map.AppMapController
import com.robining.games.map.MapAppearance
import com.robining.games.map.MapCamera
import com.robining.games.map.MapCoordinate
import com.robining.games.map.MapMarker
import com.robining.games.map.MapRoute
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardSatelliteStyle
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.gestures.removeOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location

internal class MapboxMapController internal constructor(internal val viewport: MapViewportState) : AppMapController {
    override val bearing: Double get() = ((viewport.cameraState?.bearing ?: 0.0) % 360.0 + 360.0) % 360.0

    override fun moveTo(camera: MapCamera) {
        viewport.setCameraOptions {
            camera.center?.let { center(Point.fromLngLat(it.longitude, it.latitude)) }
            zoom(camera.zoom)
            pitch(camera.pitch)
            bearing(camera.bearing)
        }
    }
}

@Composable
internal fun rememberMapboxMapController(initialCamera: MapCamera): MapboxMapController {
    val viewport = rememberMapViewportState {
        setCameraOptions {
            initialCamera.center?.let { center(Point.fromLngLat(it.longitude, it.latitude)) }
            zoom(initialCamera.zoom)
            pitch(initialCamera.pitch)
            bearing(initialCamera.bearing)
        }
    }
    return remember(viewport) { MapboxMapController(viewport) }
}

/** SDK-neutral Compose map surface. Mapbox is an internal implementation detail. */
@Composable
internal fun MapboxMapCanvas(
    modifier: Modifier = Modifier,
    mapName: String,
    controller: MapboxMapController,
    appearance: MapAppearance,
    route: MapRoute? = null,
    markers: List<MapMarker> = emptyList(),
    onMapClick: ((MapCoordinate) -> Boolean)? = null,
) {
    val density = LocalDensity.current.density
    val initOptions = remember(mapName, density) {
        ComposeMapInitOptions(pixelRatio = density, textureView = true, mapName = mapName)
    }
    MapboxMap(
        modifier = modifier,
        composeMapInitOptions = initOptions,
        mapViewportState = controller.viewport,
        compass = {},
        scaleBar = {},
        style = {
            when (appearance) {
                MapAppearance.SATELLITE -> MapboxStandardSatelliteStyle()
                MapAppearance.LIGHT, MapAppearance.DARK -> key(appearance) {
                    val preset = if (appearance == MapAppearance.LIGHT) LightPresetValue.DAY else LightPresetValue.NIGHT
                    MapboxStandardStyle(
                        standardStyleState = rememberStandardStyleState {
                            configurationsState.lightPreset = preset
                        },
                    )
                }
            }
        },
    ) {
        route?.takeIf { it.points.size >= 2 }?.let { MapboxRoute(it) }
        markers.forEachIndexed { index, marker ->
            key(index, marker.iconResourceId) {
                val icon = rememberIconImage(marker.iconResourceId)
                PointAnnotation(Point.fromLngLat(marker.coordinate.longitude, marker.coordinate.latitude)) {
                    iconImage = icon
                    iconSize = marker.size
                    iconRotate = marker.rotationDegrees
                }
            }
        }
        DisposableMapEffect(onMapClick) { mapView ->
            mapView.location.updateSettings { enabled = false }
            val listener = onMapClick?.let { callback ->
                com.mapbox.maps.plugin.gestures.OnMapClickListener { point ->
                    callback(MapCoordinate(point.latitude(), point.longitude(), point.altitude()))
                }
            }
            listener?.let(mapView.gestures::addOnMapClickListener)
            onDispose {
                listener?.let(mapView.gestures::removeOnMapClickListener)
                mapView.location.updateSettings { enabled = false }
            }
        }
    }
}

