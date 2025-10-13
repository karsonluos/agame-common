package com.robining.games.frame.common

import androidx.annotation.StringRes

object SettingConfig {
    val datas = mutableListOf<Data>()
    fun register(@StringRes nameResId : Int, url : String){
        datas.add(Data(nameResId, url))
    }
    data class Data(@StringRes val nameResId : Int, val url : String)
}