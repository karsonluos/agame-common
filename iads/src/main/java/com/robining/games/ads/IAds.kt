package com.robining.games.ads

import android.widget.FrameLayout
import android.app.Application
import android.app.Activity
import androidx.lifecycle.MutableLiveData

/** Contract implemented by individual ad SDK modules. */
interface AdsProvider {
    fun preInit(application: Application, config: IAds.Config): AdsProvider
    fun ready(): AdsProvider
    val rewardAdState: MutableLiveData<Boolean>
    fun isReadyRewardAd(): Boolean {
        return rewardAdState.value == true
    }

    fun isReadyBannerAd(): Boolean
    fun isReadyNativeAd(): Boolean
    fun isReadyInterstitialAd(): Boolean
    fun getBannerAdHeightInPixel(): Int
    /** Shows an inline banner. Provider-specific request objects stay in the implementation module. */
    fun showBannerAdIn(container: FrameLayout, adUnitId: String, request: IAds.RequestOptions = IAds.RequestOptions())
    fun showBannerAd(container: FrameLayout, alwaysPlaceHolder: Boolean = true): Boolean
    fun showNativeAd(
        container: FrameLayout,
        nativeAdSize: IAds.NativeAdSize,
        alwaysPlaceHolder: Boolean = true
    ): Boolean

    fun showRewardAd(callback: IAds.RewardAdCallback)
    fun showInterstitialAd(callback: IAds.InterstitialAdCallback)
    fun doOnInitCompleted(listener: (AdsProvider) -> Unit)
    fun enableOpenAd(enable : Boolean)
    fun showOpenAd()
    fun isWatchingAd(): Boolean
}

/**
 * Business-facing static ad API. Install exactly one [AdsProvider] during app
 * startup; all later calls are provider-neutral.
 */
object IAds {
    /** Shared configuration understood by every bundled ad provider. */
    data class Config(
        val appId: String? = null,
        val bannerId: String? = null,
        val interstitialId: String? = null,
        val nativeId: String? = null,
        val openId: String? = null,
        val rewardId: String? = null,
        val minIntervalInterstitialAd: Int = 60_000,
        val interstitialWithOpenAd: Boolean = false,
        val interstitialWithRewardAd: Boolean = false,
        val setIntervalOnInit: Boolean = true,
        val interstitialAdIgnoreCount: Int = 0,
        val minIntervalOpenId: Int = 10_000,
        val isValidOpenAdActivity: (Activity) -> Boolean = { true },
    )

    data class RequestOptions(
        val keywords: Set<String> = emptySet(),
        val contentUrl: String? = null,
        val neighboringContentUrls: Set<String> = emptySet(),
        val requestAgent: String? = null,
        val categoryExclusions: Set<String> = emptySet(),
        val customTargeting: Map<String, String> = emptyMap(),
    )
    private val unavailableRewardState = MutableLiveData(false)

    @Volatile
    private var provider: AdsProvider? = null

    val rewardAdState: MutableLiveData<Boolean>
        get() = provider?.rewardAdState ?: unavailableRewardState

    /** Selects the single ad SDK implementation used by this process. */
    fun setProvider(provider: AdsProvider): IAds {
        this.provider = provider
        return this
    }

    fun preInit(application: Application, config: Config): IAds {
        requireProvider().preInit(application, config)
        return this
    }

    /** Convenience entry matching the legacy API. Prefer separate [preInit]/[ready] calls for consent flows. */
    fun init(application: Application, config: Config): IAds = preInit(application, config).ready()

    fun ready(): IAds {
        requireProvider().ready()
        return this
    }

    fun isReadyRewardAd() = provider?.isReadyRewardAd() ?: false
    fun isReadyBannerAd() = provider?.isReadyBannerAd() ?: false
    fun isReadyNativeAd() = provider?.isReadyNativeAd() ?: false
    fun isReadyInterstitialAd() = provider?.isReadyInterstitialAd() ?: false
    fun getBannerAdHeightInPixel() = provider?.getBannerAdHeightInPixel() ?: 0
    fun showBannerAdIn(container: FrameLayout, adUnitId: String, request: RequestOptions = RequestOptions()) =
        provider?.showBannerAdIn(container, adUnitId, request)
    fun showBannerAd(container: FrameLayout, alwaysPlaceHolder: Boolean = true) =
        provider?.showBannerAd(container, alwaysPlaceHolder) ?: false
    fun showNativeAd(container: FrameLayout, nativeAdSize: NativeAdSize, alwaysPlaceHolder: Boolean = true) =
        provider?.showNativeAd(container, nativeAdSize, alwaysPlaceHolder) ?: false
    fun showRewardAd(callback: RewardAdCallback) {
        val current = provider
        if (current == null) callback.onShowFailed() else current.showRewardAd(callback)
    }
    fun showInterstitialAd(callback: InterstitialAdCallback) {
        val current = provider
        if (current == null) callback.onShowFailed() else current.showInterstitialAd(callback)
    }
    fun doOnInitCompleted(listener: () -> Unit) { provider?.doOnInitCompleted { listener() } }
    fun doOnInitCompleted(listener: (IAds) -> Unit) { provider?.doOnInitCompleted { listener(this) } }
    fun enableOpenAd(enable: Boolean) { provider?.enableOpenAd(enable) }
    fun showOpenAd() { provider?.showOpenAd() }
    fun isWatchingAd() = provider?.isWatchingAd() ?: false

    private fun requireProvider(): AdsProvider =
        requireNotNull(provider) { "Call IAds.setProvider(...) before using IAds." }

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
