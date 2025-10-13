package com.robining.games.frame.ktx

import android.graphics.Canvas
import android.graphics.Paint

enum class Align {
    START, CENTER, END, BASE_LINE
}

fun Canvas.drawTextEx(
    paint: Paint,
    content: String,
    x: Float,
    y: Float,
    xAlign: Align = Align.START,
    yAlign: Align = Align.BASE_LINE
) {
    val startX = when (xAlign) {
        Align.START -> x
        Align.CENTER -> x - paint.measureText(content) / 2f
        Align.END -> x - paint.measureText(content)
        Align.BASE_LINE -> throw IllegalArgumentException("not support base_line mode with x")
    }

    val h = paint.fontMetrics.descent - paint.fontMetrics.ascent
    val startY = when (yAlign) {
        Align.START -> {
            val centerY = y + h / 2f
            centerY + h / 2f - paint.fontMetrics.descent
        }
        Align.CENTER -> {
            y + h / 2f - paint.fontMetrics.descent
        }
        Align.END -> {
            val centerY = y - h / 2f
            centerY + h / 2f - paint.fontMetrics.descent
        }
        Align.BASE_LINE -> y
    }
    drawText(content, startX, startY, paint)
}