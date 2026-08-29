package com.robining.games.amapmap

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.amap.api.maps.MapsInitializer
import com.robining.games.map.AppMapController
import com.robining.games.map.MapAdapter
import com.robining.games.map.MapAppearance
import com.robining.games.map.MapCamera
import com.robining.games.map.MapCoordinate
import com.robining.games.map.MapMarker
import com.robining.games.map.MapRoute

class AmapMapAdapter : MapAdapter {
    override val id: String = "amap"

    override fun initialize(context: Context) {
        MapsInitializer.updatePrivacyShow(context.applicationContext, true, true)
        MapsInitializer.updatePrivacyAgree(context.applicationContext, true)
    }

    @Composable
    override fun rememberController(initialCamera: MapCamera): AppMapController =
        remember { AmapMapController(initialCamera) }

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
        AmapMapCanvas(
            modifier = modifier,
            controller = controller as AmapMapController,
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
    ) = onResult(AmapRouteSnapshotRenderer.render(points, width, height))
}
