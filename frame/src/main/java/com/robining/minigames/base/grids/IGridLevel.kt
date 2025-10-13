package com.robining.minigames.base.grids

import com.robining.minigames.base.interfaces.ISavable

interface IGridLevel : ISavable {
    val boardCols: Int
    val boardRows: Int
}