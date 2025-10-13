package com.robining.minigames.base.interfaces

interface ILevelDrawer<CTX, BOARD : ILevelBoard<*>> {
    fun draw(canvas: CTX, board: BOARD) : Boolean
}