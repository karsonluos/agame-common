package com.robining.minigames.base

import kotlin.math.pow
import kotlin.math.sqrt

data class XY<T : Number>(var x: T, var y: T) {
    fun setX(x: T): XY<T> {
        this.x = x
        return this
    }

    fun setY(y: T): XY<T> {
        this.y = y
        return this
    }

    fun setXY(x: T, y: T): XY<T> {
        this.x = x
        this.y = y
        return this
    }

    fun distance(x: T, y: T): Double {
        return sqrt(
            (x.toDouble() - this.x.toDouble()).pow(2) + (y.toDouble() - this.y.toDouble()).pow(
                2
            )
        )
    }

    fun <N : Number> distance(xy: XY<N>): Double {
        return sqrt(
            (x.toDouble() - this.x.toDouble()).pow(2) + (y.toDouble() - this.y.toDouble()).pow(
                2
            )
        )
    }
}