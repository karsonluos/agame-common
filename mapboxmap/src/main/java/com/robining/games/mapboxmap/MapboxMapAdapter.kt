package com.robining.games.mapboxmap

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.robining.games.map.AppMapController
import com.robining.games.map.MapAdapter
import com.robining.games.map.MapAppearance
import com.robining.games.map.MapCamera
import com.robining.games.map.MapCoordinate
import com.robining.games.map.MapMarker
import com.robining.games.map.MapRoute

class MapboxMapAdapter : MapAdapter {
    override val id: String = "mapbox"

    override fun initialize(context: Context) = MapboxRuntime.initialize(context)

    @Composable
    override fun rememberController(initialCamera: MapCamera): AppMapController =
        rememberMapboxMapController(initialCamera)

    @Composable
    override fun MapCanvas(
        modifier: Modifier,
        mapName: String,
        controller: AppMapController,
        appearance: MapAppearance,
        route: MapRoute?,
        markers: List<MapMarker>,
        onMapClick: ((MapCoordinate) -> Boolean)?,
    ) {
        val mapboxController = requireNotNull(controller as? MapboxMapController) {
            "Map controller was created by a different map adapter"
        }
        MapboxMapCanvas(
            modifier = modifier,
            mapName = mapName,
            controller = mapboxController,
            appearance = appearance,
            route = route,
            markers = markers,
            onMapClick = onMapClick,
        )
    }

    override fun renderRouteSnapshot(
        context: Context,
        points: List<MapCoordinate>,
        width: Int,
        height: Int,
        onResult: (Bitmap?) -> Unit,
    ) = MapboxSnapshotRenderer.renderRoute(context, points, width, height, onResult)
}
