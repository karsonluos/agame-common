package com.robining.games.mapboxmap

import com.robining.games.map.MapCoordinate
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapSnapshotOptions
import com.mapbox.maps.Size
import com.mapbox.maps.Snapshotter
import com.mapbox.maps.Style

internal object MapboxSnapshotRenderer {
    fun renderRoute(
        context: Context,
        points: List<MapCoordinate>,
        width: Int = 320,
        height: Int = 320,
        onResult: (Bitmap?) -> Unit,
    ) {
        val coordinates = points.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (coordinates.isEmpty()) return onResult(null)
        val snapshotter = Snapshotter(
            context.applicationContext,
            MapSnapshotOptions.Builder().size(Size(width.toFloat(), height.toFloat())).pixelRatio(1f).build(),
        )
        snapshotter.setStyleUri(Style.STANDARD)
        snapshotter.setCamera(
            snapshotter.cameraForCoordinates(coordinates, EdgeInsets(36.0, 36.0, 36.0, 36.0), 0.0, 0.0),
        )
        snapshotter.start(overlayCallback = { overlay ->
            if (coordinates.size < 2) return@start
            val path = Path()
            coordinates.forEachIndexed { index, point ->
                val screen = overlay.screenCoordinate(point)
                if (index == 0) path.moveTo(screen.x.toFloat(), screen.y.toFloat())
                else path.lineTo(screen.x.toFloat(), screen.y.toFloat())
            }
            overlay.canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(36, 114, 255)
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }) { image, _ ->
            snapshotter.destroy()
            Handler(Looper.getMainLooper()).post { onResult(image) }
        }
    }
}

