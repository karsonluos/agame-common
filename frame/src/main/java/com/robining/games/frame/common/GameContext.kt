package com.robining.games.frame.common

import android.app.Activity
import com.robining.games.frame.startup.StartUpContext
import com.tencent.mmkv.MMKV

object GameContext {
    const val PACKAGE_NAME_PUZZLE_LOVER = "com.anjona.game.puzzlelover"
    const val PACKAGE_NAME_NUMBER_KLOTSKI = "saiwen.game.slidegame"
    const val PACKAGE_NAME_KLOTSKI = "saiwen.game.klotski"
    const val PACKAGE_NAME_1A2B = "saiwen.game.guessnumber.oatb"
    const val PACKAGE_NAME_MATH_24 = "saiwen.game.math24"
    const val PACKAGE_NAME_MONTANA = "saiwen.game.montana"
    const val PACKAGE_NAME_YUKON = "saiwen.game.yukon"
    const val PACKAGE_NAME_MINESWEEPER = "saiwen.game.minesweeper"
    val mmkv by lazy {
        MMKV.initialize(StartUpContext.context)
        MMKV.defaultMMKV()
    }
    var supportBgm = false
        private set

    var launchActivity : Class<out Activity>? = null

    fun init(supportBgm: Boolean = false) {
        this.supportBgm = supportBgm
    }

    fun <T : Activity> initLaunchActivity(launchActivityClazz: Class<T>) {
        launchActivity = launchActivityClazz
    }
}