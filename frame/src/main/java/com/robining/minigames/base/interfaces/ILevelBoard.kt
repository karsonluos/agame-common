package com.robining.minigames.base.interfaces

import org.json.JSONObject

interface ILevelBoard<L> {
    var levelStatusCallback: LevelStatusCallback?
    fun init(level: L)
    fun layout(canvasWidth: Float, canvasHeight: Float)
    fun onTouchDown(x: Float, y: Float): Boolean
    fun onTouchMove(x: Float, y: Float): Boolean
    fun onTouchUpOrCancel(x: Float, y: Float): Boolean
    fun loadSnapshot(snapshot: JSONObject) {}
    fun snapshot(snapshot: JSONObject) {}
    fun isPassed(): Boolean
    fun isFailed(): Boolean = false
    fun isReady(): Boolean = false
    fun isHinting(): Boolean = false
    fun hint(): Boolean = false
    fun undo(): Boolean = false
    fun canUndo(): Boolean = false
    fun isSupportUndo(): Boolean = false
    fun isSupportHint(): Boolean = false
    fun isSupportStepCounter(): Boolean = false
    fun steps(): Int = 0

    interface LevelStatusCallback {
        fun onMoved()
        fun onGameOver(isPass: Boolean)
    }
}