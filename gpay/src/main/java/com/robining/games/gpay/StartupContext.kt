package com.robining.games.gpay

import android.annotation.SuppressLint
import android.content.Context
import androidx.startup.Initializer
import com.robining.games.ipay.IPayManager

class StartupContext : Initializer<Unit> {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun create(context: Context) {
        Companion.context = context.applicationContext
        IPayManager.register(GPay)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}