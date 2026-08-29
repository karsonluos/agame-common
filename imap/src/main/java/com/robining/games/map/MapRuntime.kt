package com.robining.games.map

import android.content.Context

object MapRuntime {
    fun initialize(context: Context) = MapModule.adapter.initialize(context.applicationContext)
}
