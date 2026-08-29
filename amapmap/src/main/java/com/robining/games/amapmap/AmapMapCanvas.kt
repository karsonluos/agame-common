package com.robining.games.amapmap

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.robining.games.map.AppMapController
import com.robining.games.map.MapAppearance
import com.robining.games.map.MapCamera
import com.robining.games.map.MapCoordinate
import com.robining.games.map.MapMarker
import com.robining.games.map.MapRoute

internal class AmapMapController(initialCamera: MapCamera) : AppMapController {
    private var map: AMap? = null
    private var requestedCamera = initialCamera
    private var bearingState by mutableStateOf(initialCamera.bearing)
    override val bearing: Double get() = bearingState

    fun attach(map: AMap) {
        this.map = map
        moveTo(requestedCamera)
    }

    fun detach() { map = null }

    override fun moveTo(camera: MapCamera) {
        requestedCamera = camera
        bearingState = camera.bearing
        val center = camera.center ?: return
        val gcj = AmapCoordinateTransform.wgs84ToGcj02(center)
        map?.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition(LatLng(gcj.latitude, gcj.longitude), camera.zoom.toFloat(), camera.pitch.toFloat(), camera.bearing.toFloat()),
            ),
        )
    }
}

@Composable
internal fun AmapMapCanvas(
    modifier: Modifier,
    controller: AmapMapController,
    appearance: MapAppearance,
    route: MapRoute?,
    markers: List<MapMarker>,
    onMapClick: ((MapCoordinate) -> Boolean)?,
) {
    val renderState = remember(controller) { AmapRenderState(controller) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, renderState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> renderState.onResume()
                Lifecycle.Event.ON_PAUSE -> renderState.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderState.destroy()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context -> renderState.create(context) },
        update = { renderState.render(appearance, route, markers, onMapClick) },
    )
}

private class AmapRenderState(private val controller: AmapMapController) {
    private var mapView: TextureMapView? = null
    private var map: AMap? = null
    private var routeLine: Polyline? = null
    private val renderedMarkers = mutableListOf<Marker>()

    fun create(context: Context): TextureMapView = TextureMapView(context).also { view ->
        view.onCreate(null)
        mapView = view
        map = view.map.also(controller::attach)
    }

    fun render(
        appearance: MapAppearance,
        route: MapRoute?,
        markers: List<MapMarker>,
        onMapClick: ((MapCoordinate) -> Boolean)?,
    ) {
        val activeMap = map ?: return
        activeMap.mapType = when (appearance) {
            MapAppearance.LIGHT -> AMap.MAP_TYPE_NORMAL
            MapAppearance.DARK -> AMap.MAP_TYPE_NIGHT
            MapAppearance.SATELLITE -> AMap.MAP_TYPE_SATELLITE
        }
        activeMap.setOnMapClickListener { position ->
            onMapClick?.invoke(AmapCoordinateTransform.gcj02ToWgs84(MapCoordinate(position.latitude, position.longitude)))
        }
        routeLine?.remove()
        routeLine = route?.points?.takeIf { it.size > 1 }?.let { points ->
            activeMap.addPolyline(
                PolylineOptions()
                    .addAll(points.map { AmapCoordinateTransform.wgs84ToGcj02(it.coordinate).toLatLng() })
                    .width(10f)
                    .color(route.color.toArgb()),
            )
        }
        renderedMarkers.forEach(Marker::remove)
        renderedMarkers.clear()
        markers.forEach { marker ->
            val option = MarkerOptions()
                .position(AmapCoordinateTransform.wgs84ToGcj02(marker.coordinate).toLatLng())
                .anchor(.5f, .5f)
                .rotateAngle(marker.rotationDegrees.toFloat())
            if (marker.iconResourceId != 0) option.icon(BitmapDescriptorFactory.fromResource(marker.iconResourceId))
            renderedMarkers += activeMap.addMarker(option)
        }
    }

    fun onResume() = mapView?.onResume() ?: Unit
    fun onPause() = mapView?.onPause() ?: Unit
    fun destroy() {
        renderedMarkers.clear()
        routeLine = null
        controller.detach()
        mapView?.onDestroy()
        map = null
        mapView = null
    }

    private fun MapCoordinate.toLatLng() = LatLng(latitude, longitude)
}
