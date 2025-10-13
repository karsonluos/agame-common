package com.robining.games.frame.startup

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.robining.games.frame.utils.App
import com.robining.games.frame.utils.Net
import com.robining.games.frame.utils.theme.ThemeManager
import com.tencent.mmkv.MMKV

class StartUpContext : Initializer<Unit> {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun create(context: Context) {
        StartUpContext.context = context.applicationContext
        MMKV.initialize(context)
        ThemeManager.init(StartUpContext.context as Application)
        Net.init()
        App.init(StartUpContext.context as Application)
//        SurfaceSizeManager.init(StartUpContext.context as Application)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}