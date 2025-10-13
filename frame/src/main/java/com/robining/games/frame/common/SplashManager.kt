package com.robining.games.frame.common

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.lifecycle.MutableLiveData

object SplashManager {
    /**
     * MutableLiveData<Float> 初始化进度提示
     */
    var initAfterPrivacyPolicy: ((Activity, MutableLiveData<Float>, MutableLiveData<CharSequence>) -> Unit)? = null

    @DrawableRes
    var overrideLogoResId: Int? = null

    var launchActivity: Class<out Activity>? = null
}