package com.robining.minigames.base.interfaces

import org.json.JSONObject

interface ISavable {
    fun saveState(outState: JSONObject)
    fun restoreState(inState: JSONObject)
}