package com.robining.games.amapmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.robining.games.map.MapCoordinate

/** Lightweight fallback for history thumbnails; the interactive surface uses the native AMap renderer. */
internal object AmapRouteSnapshotRenderer {
    fun render(points: List<MapCoordinate>, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(239, 244, 239))
            if (points.size < 2) return@also
            val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
            val minLng = points.minOf { it.longitude }; val maxLng = points.maxOf { it.longitude }
            val latSpan = (maxLat - minLat).coerceAtLeast(0.00001)
            val lngSpan = (maxLng - minLng).coerceAtLeast(0.00001)
            val padding = minOf(width, height) * .12f
            fun x(point: MapCoordinate) = padding + ((point.longitude - minLng) / lngSpan * (width - 2 * padding)).toFloat()
            fun y(point: MapCoordinate) = height - padding - ((point.latitude - minLat) / latSpan * (height - 2 * padding)).toFloat()
            val path = Path().apply { moveTo(x(points.first()), y(points.first())); points.drop(1).forEach { lineTo(x(it), y(it)) } }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 120, 78); style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
        }
    }
}
