package com.robining.games.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface AppMapController {
    val bearing: Double
    fun moveTo(camera: MapCamera)

    /** 用户手势开始移动地图时回调（拖拽/双指缩放/旋转/双指推移）；程序化 moveTo 不触发。回调在主线程。 */
    fun setOnUserCameraMoved(listener: (() -> Unit)?)
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
