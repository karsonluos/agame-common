package com.robining.games.frame.common

import android.os.Bundle
import cn.karsonluos.aos.common.base.KsBaseActivity
import com.robining.games.frame.databinding.ActivityDefaultSplashBinding

open class SplashActivity : KsBaseActivity() {
    private val mView by lazy {
        ActivityDefaultSplashBinding.inflate(layoutInflater)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge(false)
        setContentView(mView.root)
    }
}