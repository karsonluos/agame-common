package com.robining.games.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface AppMapController {
    val bearing: Double
    fun moveTo(camera: MapCamera)
}

/** Implemented by a concrete map SDK module and discovered at runtime. */
interface MapAdapter {
    val id: String
    fun initialize(context: Context)

    @Composable
    fun rememberController(initialCamera: MapCamera): AppMapController

    @Composable
    fun MapCanvas(
        modifier: Modifier,
        mapName: String,
        controller: AppMapController,
        appearance: MapAppearance,
        route: MapRoute?,
        markers: List<MapMarker>,
        onMapClick: ((MapCoordinate) -> Boolean)?,
    )

    fun renderRouteSnapshot(
        context: Context,
        points: List<MapCoordinate>,
        width: Int,
        height: Int,
        onResult: (Bitmap?) -> Unit,
    )
}
