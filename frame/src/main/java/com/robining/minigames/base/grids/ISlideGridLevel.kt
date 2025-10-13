package com.robining.minigames.base.grids

interface ISlideGridLevel<T : ISlideGridItem> : IGridLevel {
    val blocks: Array<T>
}