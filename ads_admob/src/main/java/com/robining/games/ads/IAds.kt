package com.robining.games.ads

import android.widget.FrameLayout
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.ads.AdRequest

interface IAds {
    val rewardAdState: MutableLiveData<Boolean>
    fun isReadyRewardAd(): Boolean {
        return rewardAdState.value == true
    }

    fun isReadyBannerAd(): Boolean
    fun isReadyNativeAd(): Boolean
    fun isReadyInterstitialAd(): Boolean
    fun getBannerAdHeightInPixel(): Int
    fun showBannerAdIn(container: FrameLayout, adUnitId : String, adRequest: AdRequest = AdRequest.Builder().build())
    fun showBannerAd(container: FrameLayout, alwaysPlaceHolder: Boolean = true): Boolean
    fun showNativeAd(
        container: FrameLayout,
        nativeAdSize: NativeAdSize,
        alwaysPlaceHolder: Boolean = true
    ): Boolean

    fun showRewardAd(callback: RewardAdCallback)
    fun showInterstitialAd(callback: InterstitialAdCallback)
    fun doOnInitCompleted(listener: (IAds) -> Unit)
    fun enableOpenAd(enable : Boolean)

    interface RewardAdCallback {
        fun onShowFailed() {}
        fun onShow() {}
        fun onWatchCompleted(gotReward: Boolean) {}
    }

    interface InterstitialAdCallback {
        fun onShowFailed() {}
        fun onShow() {}
        fun onWatchCompleted() {}
    }

    enum class NativeAdSize {
        Medium, Small
    }
}