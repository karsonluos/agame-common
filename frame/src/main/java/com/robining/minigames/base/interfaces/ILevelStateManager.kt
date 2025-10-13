package com.robining.minigames.base.interfaces

interface ILevelStateManager<LEVEL_ID_TYPE, DATA_INPUT, SNAPSHOT, LEVEL : ISavable, BOARD : ILevelBoard<LEVEL>, CTX, DRAWER : ILevelDrawer<CTX, BOARD>> {
    var time: Long
    fun create(dat: DATA_INPUT)
    fun restore(snapshot: SNAPSHOT)
    fun levelId(): LEVEL_ID_TYPE
}