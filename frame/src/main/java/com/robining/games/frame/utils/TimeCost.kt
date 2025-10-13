package com.robining.games.frame.utils

import android.os.SystemClock
import android.util.Log

fun <T> timeCost(name: String, block: () -> T) : T{
    val start = SystemClock.elapsedRealtime()
    try {
        return block.invoke()
    }finally {
        Log.d("TimeCost",">>>$name cost ${SystemClock.elapsedRealtime() - start}ms")
    }
}