package com.robining.minigames.base.utils

class Point2D {
    var x = 0f
    var y = 0f

    constructor() {}
    constructor(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun isSame(x: Float, y: Float): Boolean {
        return this.x == x && this.y == y
    }

    fun isSame(point: Point2D): Boolean {
        return isSame(point.x, point.y)
    }

    companion object {
        /**
         * Returns the square of the distance between two points.
         *
         * @param x1 the X coordinate of the first specified point
         * @param y1 the Y coordinate of the first specified point
         * @param x2 the X coordinate of the second specified point
         * @param y2 the Y coordinate of the second specified point
         * @return the square of the distance between the two
         * sets of specified coordinates.
         * @since 1.2
         */
        fun distanceSq(
            x1: Double, y1: Double,
            x2: Double, y2: Double
        ): Double {
            var x1 = x1
            var y1 = y1
            x1 -= x2
            y1 -= y2
            return x1 * x1 + y1 * y1
        }

        /**
         * Returns the distance between two points.
         *
         * @param x1 the X coordinate of the first specified point
         * @param y1 the Y coordinate of the first specified point
         * @param x2 the X coordinate of the second specified point
         * @param y2 the Y coordinate of the second specified point
         * @return the distance between the two sets of specified
         * coordinates.
         * @since 1.2
         */
        fun distance(
            x1: Double, y1: Double,
            x2: Double, y2: Double
        ): Double {
            var x1 = x1
            var y1 = y1
            x1 -= x2
            y1 -= y2
            return Math.sqrt(x1 * x1 + y1 * y1)
        }
    }
}