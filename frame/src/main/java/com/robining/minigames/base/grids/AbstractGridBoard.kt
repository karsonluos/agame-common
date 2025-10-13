package com.robining.minigames.base.grids

import com.robining.minigames.base.GridLayout
import com.robining.minigames.base.SimpleLevelBoard


abstract class AbstractGridBoard<L : IGridLevel> : SimpleLevelBoard<L, GridLayout.LayoutContext>() {
    protected lateinit var bordGrid: Array<Array<Int>>
    protected open var padding = 0f
    protected open var spacing = 0f

    override fun init(level: L) {
        super.init(level)
        this.bordGrid = Array(level.boardRows) { Array(level.boardCols) { -1 } }
    }

    override fun onLayout(canvasWidth: Float, canvasHeight: Float): GridLayout.LayoutContext {
        return GridLayout.layout(
            canvasWidth,
            canvasHeight,
            level.boardCols,
            level.boardRows,
            padding = padding,
            spacing = spacing
        )
    }
}