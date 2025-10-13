package com.robining.minigames.base

enum class Direction {
    LEFT, TOP, RIGHT, BOTTOM;

    fun reverse(): Direction {
        return when (this) {
            TOP -> BOTTOM
            BOTTOM -> TOP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
    }
}