package com.robining.games.map

import android.content.Context
import android.graphics.Bitmap

object MapSnapshotRenderer {
    fun renderRoute(
        context: Context,
        points: List<MapCoordinate>,
        width: Int = 320,
        height: Int = 320,
        onResult: (Bitmap?) -> Unit,
    ) = MapModule.adapter.renderRouteSnapshot(
        context = context.applicationContext,
        points = points,
        width = width,
        height = height,
        onResult = onResult,
    )
}
