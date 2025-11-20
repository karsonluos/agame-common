package com.robining.games.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.robining.games.ads.templates.NativeTemplateStyle
import com.robining.games.ads.templates.TemplateView
import com.robining.games.frame.common.Ref
import com.robining.games.frame.managers.PrivacyManager
import com.robining.games.frame.utils.App
import com.robining.games.frame.utils.AppLifeCycleListener
import com.robining.games.frame.utils.Net

object Ads : IAds {
    data class Config(
        val bannerId: String? = null,
        val interstitialId: String? = null,
        val nativeId: String? = null,
        val openId: String? = null,
        val rewardId: String? = null,
        val minIntervalInterstitialAd: Int = 60000, //60s最小播放插屏间隔
        val interstitialWithOpenAd: Boolean = false, //插屏播放的间隔时间是否收到开屏广告影响
        val interstitialWithRewardAd: Boolean = false, //插屏播放的间隔时间是否收到激励广告影响
        val setIntervalOnInit: Boolean = true, //启动时自动设置间隔，即启动的60s内不进行插屏播放
        val interstitialAdIgnoreCount: Int = 0,
        val minIntervalOpenId: Int = 10000, //10s最小播放间隔
        val isValidOpenAdActivity: (Activity) -> Boolean = {
            true
        }
    )

    private const val MAX_RETRY_COUNT = 3
    private val TEST_DEVICE_IDS = arrayOf("1EE13FC64E4080073CE7D50EE5BB0561", "BCA4DE4D6F0C6E9D46162A532370FE91")

    @Volatile
    private var preInited = false

    @Volatile
    private var inited = false
    private lateinit var mApplication: Application
    private lateinit var config: Config

    private val initCompletedListeners = arrayListOf<(IAds) -> Unit>()

    private var bannerRetryCount: Int = 0
    private var rewardRetryCount: Int = 0
    private var interstitialRetryCount: Int = 0
    private var openRetryCount: Int = 0
    private var nativeRetryCount: Int = 0

    @Volatile
    private var bannerIsActive: Boolean = false

    @Volatile
    private var rewardIsActive: Boolean = false

    @Volatile
    private var interstitialIsActive: Boolean = false

    @Volatile
    private var openIsActive: Boolean = false

    @Volatile
    private var nativeIsActive: Boolean = false

    @Volatile
    private var bannerIsLoading: Boolean = false

    @Volatile
    private var rewardIsLoading: Boolean = false

    @Volatile
    private var interstitialIsLoading: Boolean = false

    @Volatile
    private var openIsLoading: Boolean = false

    @Volatile
    private var nativeIsLoading: Boolean = false

    @Volatile
    private var nativeAd: NativeAd? = null

    @Volatile
    private var rewardAd: RewardedAd? = null

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var openAd: AppOpenAd? = null

    @Volatile
    private var isShowingOpenAd: Boolean = false

    @Volatile
    private var isShowingInterstitialAd: Boolean = false

    @Volatile
    private var isShowingRewardAd: Boolean = false

    private var allowOpenAd: Boolean = true

    private var lastShowInterstitialAdTimeStamp: Long? = null
    private var lastShowOpenAdTimeStamp: Long? = null
    private var lastShowRewardAdTimeStamp: Long? = null

    override val rewardAdState = MutableLiveData(false)

    override fun isReadyBannerAd(): Boolean {
        return bannerIsActive
    }

    override fun isReadyNativeAd(): Boolean {
        return nativeIsActive && nativeAd != null
    }

    override fun isReadyInterstitialAd(): Boolean {
        return interstitialIsActive && interstitialAd != null
    }

    private val mMainThreadHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    private val bannerAdView by lazy {
        AdView(mApplication)
    }

    fun preInit(application: Application, config: Config): IAds {
        if (preInited) {
            return this
        }
        preInited = true
        this.mApplication = application
        this.config = config
        return this
    }

    fun init(application: Application, config: Config): IAds {
        preInit(application, config)
        return ready()
    }

    fun ready(): IAds {
        if (!PrivacyManager.isAgree()){
            return this
        }

        if (inited) {
            return this
        }
        inited = true
        if (config.setIntervalOnInit) {
            lastShowInterstitialAdTimeStamp = SystemClock.elapsedRealtime()
        }
        //注册网络连接变化的广播
        Net.doAfterConnectedAlways({
            onNetworkResume()
        })
        //注册前后台切换监听
        App.registerUntilListener {
            val isValid = config.isValidOpenAdActivity(it)
            //尝试显示开屏广告
            if (isValid && allowOpenAd) {
                showOpenAd()
            }
            isValid
        }

        App.registerListener(object : AppLifeCycleListener {
            override fun onFirstActivityResumedSinceEnterApp() {
                tryResumeAdverts()
            }
        })

        val itr = initCompletedListeners.iterator()
        while (itr.hasNext()) {
            val listener = itr.next()
            mMainThreadHandler.post { listener.invoke(this) }
            itr.remove()
        }

        return this
    }

    private fun onNetworkResume() {
        tryResumeAdverts()
    }

    private fun tryResumeAdverts() {
        if (!inited) {
            return
        }
        val topActivity = App.mTopActivity.get() ?: return

        //尝试重新初始化
        bannerRetryCount = 0
        rewardRetryCount = 0
        nativeRetryCount = 0
        openRetryCount = 0
        interstitialRetryCount = 0

        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(TEST_DEVICE_IDS.toList())
            .build()
        MobileAds.setRequestConfiguration(configuration)

//        val metaData = MetaData(topActivity)
//        metaData["gdpr.consent"] = true
//        metaData["privacy.consent"] = true
//        metaData.commit()

        MobileAds.initialize(topActivity) {
            val statusMap: Map<String, AdapterStatus> = it.adapterStatusMap
            for (adapterClass in statusMap.keys) {
                val status = statusMap[adapterClass]
                Log.d(
                    "Ads", String.format(
                        "Adapter name: %s, Description: %s, Latency: %d, Status:%s",
                        adapterClass, status!!.description, status.latency, status.initializationState
                    )
                )
            }

            //无论结果如何 都尝试进行广告初始化
            initBanner()
            initNative()
            initReward()
            initInterstitial()
            initOpen()
        }
    }

    private fun initBanner() {
        val unitId = config.bannerId ?: return
        if (!inited || bannerIsActive || bannerIsLoading || bannerRetryCount >= MAX_RETRY_COUNT) {
            return
        }
        bannerIsLoading = true
        bannerIsActive = false
        bannerAdView.adListener = object : AdListener() {
            override fun onAdFailedToLoad(p0: LoadAdError) {
                Log.e("Ads","load banner failed:${p0.responseInfo}")
                bannerRetryCount++
                bannerIsLoading = false
                bannerIsActive = false
            }

            override fun onAdLoaded() {
                Log.d("Ads","load banner success:${bannerAdView.responseInfo?.mediationAdapterClassName}")
                bannerRetryCount = 0
                bannerIsLoading = false
                bannerIsActive = true
            }
        }
        if (bannerAdView.adSize == null) {
            bannerAdView.setAdSize(getBannerAdSize())
            bannerAdView.adUnitId = unitId
        }
        val request = AdRequest.Builder().build()
        bannerAdView.loadAd(request)
    }

    enum class AdLoadState {
        IDLE, LOADING, FAILED, SUCCEED
    }

    override fun showBannerAdIn(container: FrameLayout, adUnitId : String, adRequest: AdRequest) {
        if (!PrivacyManager.isAgree()){
            return
        }
        container.post {
            val bannerView = AdView(container.context)
            bannerView.setBackgroundColor(Color.TRANSPARENT)
            val width = container.measuredWidth
            val bannerSize = getBannerAdSize(width)
            bannerView.setAdSize(bannerSize)
            bannerView.adUnitId = adUnitId
            bannerView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                private val mainHandler = Handler(Looper.getMainLooper())
                private val adLoadStateRef = Ref(AdLoadState.IDLE)
                private var adListener = object : AdListener() {
                    override fun onAdFailedToLoad(p0: LoadAdError) {
                        Log.e("Ads","load banner failed:${p0.responseInfo}")
                        adLoadStateRef.value = AdLoadState.FAILED
                        //创建恢复策略
                        mainHandler.post { registerPending() }
                    }

                    override fun onAdLoaded() {
                        Log.d("Ads","load banner success:${bannerView.responseInfo?.mediationAdapterClassName}")
                        adLoadStateRef.value = AdLoadState.SUCCEED
                    }
                }
                private var cancelPendingJob: Runnable? = null

                private fun registerPending() {
                    val refTask = Ref<Runnable?>(null)
                    val refListener = Ref<AppLifeCycleListener?>(null)
                    cancelPendingJob = Runnable {
                        refTask.value?.let { Net.removeTaskInOnes(it) }
                        refListener.value?.let { App.unregisterListener(it) }
                    }
                    val task = Runnable {
                        cancelPendingJob?.run()
                        cancelPendingJob = null
                        if (adLoadStateRef.value == AdLoadState.FAILED || adLoadStateRef.value == AdLoadState.IDLE) {
                            adLoadStateRef.value = AdLoadState.LOADING
                            bannerView.loadAd(adRequest)
                        }
                    }
                    refTask.value = task
                    val lifeCycleListener = object : AppLifeCycleListener {
                        override fun onFirstActivityResumedSinceEnterApp() {
                            task.run()
                        }
                    }
                    refListener.value = lifeCycleListener
                    Net.doAfterConnectedOnce(task, false)
                    App.registerListener(lifeCycleListener)
                }

                override fun onViewAttachedToWindow(v: View) {
                    bannerView.adListener = adListener
                    //如果当前没有初始化成功或没有初始化
                    if (adLoadStateRef.value == AdLoadState.FAILED || adLoadStateRef.value == AdLoadState.IDLE) {
                        adLoadStateRef.value = AdLoadState.LOADING
                        //此时加载在监控中有崩溃的情况，所以尝试延迟处理
                        bannerView.postDelayed({
                            bannerView.loadAd(adRequest)
                        },300)
                    }
                    //else 如果加载中等待adListener回调
                }

                override fun onViewDetachedFromWindow(v: View) {
                    mainHandler.removeCallbacksAndMessages(null)
                    cancelPendingJob?.run()
                    cancelPendingJob = null
                }
            })
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                bannerSize.getHeightInPixels(container.context)
            )
            container.addView(bannerView, lp)
        }
    }

    private fun getBannerAdSize(widthInPixels : Int? = null): AdSize {
        // Step 2 - Determine the screen width (less decorations) to use for the ad width.
        val outMetrics: DisplayMetrics = mApplication.resources.displayMetrics
        val widthPixels = widthInPixels?.toFloat() ?: outMetrics.widthPixels.toFloat()
        val density = outMetrics.density
        val adWidth = (widthPixels / density).toInt()
        // Step 3 - Get adaptive ad size and return for setting on the ad view.
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(mApplication, adWidth)
    }

    private fun initNative() {
        val unitId = config.nativeId ?: return
        if (!inited || nativeIsActive || nativeIsLoading || nativeRetryCount >= MAX_RETRY_COUNT) {
            return
        }
        nativeIsLoading = true
        nativeIsActive = false
        AdLoader.Builder(App.mTopActivity.get() ?: mApplication, unitId)
            .forNativeAd {
                this.nativeAd = it
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    Log.e("Ads","load native failed:${p0.responseInfo}")
                    nativeRetryCount++
                    nativeIsLoading = false
                    nativeIsActive = false
                }

                override fun onAdLoaded() {
                    Log.d("Ads","load native success:${nativeAd?.responseInfo?.mediationAdapterClassName}")
                    nativeRetryCount = 0
                    nativeIsLoading = false
                    nativeIsActive = true
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun initReward() {
        val unitId = config.rewardId ?: return
        if (!inited || rewardIsActive || rewardIsLoading || rewardRetryCount >= MAX_RETRY_COUNT) {
            return
        }
        rewardIsLoading = true
        rewardIsActive = false
        RewardedAd.load(
            App.mTopActivity.get() ?: mApplication,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    Log.d("Ads","load reward success:${rewardedAd.responseInfo.mediationAdapterClassName}-->${rewardedAd.adUnitId}")
                    rewardRetryCount = 0
                    this@Ads.rewardAd = rewardedAd
                    rewardIsLoading = false
                    rewardIsActive = true
                    rewardAdState.postValue(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("Ads","load reward failed:${loadAdError.responseInfo}-->${unitId}")
                    rewardRetryCount++
                    rewardIsLoading = false
                    rewardIsActive = false
                }
            })
    }

    private fun initInterstitial() {
        val unitId = config.interstitialId ?: return
        if (!inited || interstitialIsActive || interstitialIsLoading || interstitialRetryCount >= MAX_RETRY_COUNT) {
            return
        }
        interstitialIsLoading = true
        interstitialIsActive = false
        InterstitialAd.load(
            App.mTopActivity.get() ?: mApplication,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d("Ads","load interstitial success:${interstitialAd.responseInfo.mediationAdapterClassName}")
                    interstitialRetryCount = 0
                    this@Ads.interstitialAd = interstitialAd
                    interstitialIsLoading = false
                    interstitialIsActive = true
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("Ads","load interstitial failed:${loadAdError.responseInfo}")
                    interstitialRetryCount++
                    interstitialIsLoading = false
                    interstitialIsActive = false
                }
            })
    }

    private fun initOpen() {
        val unitId = config.openId ?: return
        if (!inited || openIsActive || openIsLoading || openRetryCount >= MAX_RETRY_COUNT) {
            return
        }
        openIsLoading = true
        openIsActive = false
        AppOpenAd.load(
            App.mTopActivity.get() ?: mApplication,
            unitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(p0: AppOpenAd) {
                    Log.d("Ads","load open success:${p0.responseInfo.mediationAdapterClassName}")
                    openRetryCount = 0
                    this@Ads.openAd = p0
                    openIsActive = true
                    openIsLoading = false
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    Log.e("Ads","load open failed:${p0.responseInfo}")
                    openRetryCount++
                    openIsActive = false
                    openIsLoading = false
                }
            })
    }

    override fun getBannerAdHeightInPixel(): Int {
        return getBannerAdSize().getHeightInPixels(mApplication)
    }

    override fun showBannerAd(container: FrameLayout, alwaysPlaceHolder: Boolean): Boolean {
        container.removeAllViews()
        when {
            bannerIsActive || alwaysPlaceHolder -> {
                container.visibility = View.VISIBLE
                val parent = bannerAdView.parent
                if (parent != null) {
                    val vg = parent as ViewGroup
                    vg.removeView(bannerAdView)
                }
                container.addView(
                    bannerAdView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        getBannerAdHeightInPixel()
                    )
                )
                return true
            }
            else -> {
                container.visibility = View.GONE
            }
        }

        //尝试重新加载
        initBanner()
        return false
    }

//    override fun showNativeAd(
//        container: FrameLayout,
//        nativeAdSize: IAds.NativeAdSize,
//        alwaysPlaceHolder: Boolean
//    ): Boolean {
//        container.removeAllViews()
//        val templateView = TemplateView(container.context)
//        val layout = when (nativeAdSize) {
//            IAds.NativeAdSize.Medium -> R.layout.gnt_medium_template_view
//            IAds.NativeAdSize.Small -> R.layout.gnt_small_template_view
//        }
//        templateView.setTemplateType(layout)
//        container.addView(
//            templateView,
//            FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.MATCH_PARENT
//            )
//        )
//        val style = NativeTemplateStyle.Builder()
//            .withMainBackgroundColor(ColorDrawable(Color.WHITE))
//            .build()
//        templateView.setStyles(style)
//
//        val unitId = config.nativeId
//        if (unitId == null){
//            if (alwaysPlaceHolder){
//                container.visibility = View.INVISIBLE
//            } else{
//                container.visibility = View.GONE
//            }
//            return false
//        }
//
//        container.visibility = View.INVISIBLE
//        val templateViewRef = WeakReference(templateView)
//        val containerViewRef = WeakReference(container)
//        AdLoader.Builder(mApplication, unitId)
//            .forNativeAd {ad->
//                mMainThreadHandler.post {
//                    templateViewRef.get()?.let {
//                        it.setNativeAd(ad)
//                    }
//                }
//            }
//            .withAdListener(object : AdListener() {
//                override fun onAdLoaded() {
//                    mMainThreadHandler.post {
//                        containerViewRef.get()?.let {
//                            it.visibility = View.VISIBLE
//                        }
//                    }
//                }
//            })
//            .withNativeAdOptions(NativeAdOptions.Builder().build())
//            .build()
//            .loadAd(AdRequest.Builder().build())
//        return true
//    }

    override fun showNativeAd(
        container: FrameLayout,
        nativeAdSize: IAds.NativeAdSize,
        alwaysPlaceHolder: Boolean
    ): Boolean {
        container.removeAllViews()
        val nativeAd = this.nativeAd
        when {
            nativeIsActive && nativeAd != null -> {
                container.visibility = View.VISIBLE
                val templateView = TemplateView(container.context)
                val layout = when (nativeAdSize) {
                    IAds.NativeAdSize.Medium -> R.layout.gnt_medium_template_view
                    IAds.NativeAdSize.Small -> R.layout.gnt_small_template_view
                }
                templateView.setTemplateType(layout)
                container.addView(
                    templateView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                val style = NativeTemplateStyle.Builder()
                    .withMainBackgroundColor(ColorDrawable(Color.WHITE))
                    .build()
                templateView.setStyles(style)
                templateView.setNativeAd(nativeAd)
                nativeIsActive = false
                initNative()
                return true
            }
            alwaysPlaceHolder -> {
                container.visibility = View.INVISIBLE
            }
            else -> {
                container.visibility = View.GONE
            }
        }

        //尝试重新加载
        initNative()
        return false
    }

    override fun showRewardAd(callback: IAds.RewardAdCallback) {
        if (isWatchingAd()) {
            mMainThreadHandler.post { callback.onShowFailed() }
            return
        }
        val rewardAd = this.rewardAd
        val topActivity = App.mTopActivity.get()
        val gotRef = Array(1) { false }
        if (rewardIsActive && rewardAd != null && topActivity != null) {
            rewardAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    isShowingRewardAd = true
                    mMainThreadHandler.post { callback.onShow() }
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    isShowingRewardAd = false
                    mMainThreadHandler.post { callback.onShowFailed() }
                }

                override fun onAdDismissedFullScreenContent() {
                    isShowingRewardAd = false
                    lastShowRewardAdTimeStamp = SystemClock.elapsedRealtime()
                    mMainThreadHandler.post { callback.onWatchCompleted(gotRef[0]) }
                }
            }
            rewardAd.show(topActivity) {
                gotRef[0] = true
            }
            rewardIsActive = false
        } else {
            mMainThreadHandler.post { callback.onShowFailed() }
        }

        rewardAdState.postValue(false)
        initReward()
    }

    override fun showInterstitialAd(callback: IAds.InterstitialAdCallback) {
        if (isWatchingAd()) {
            mMainThreadHandler.post { callback.onShowFailed() }
            return
        }

        lastShowInterstitialAdTimeStamp?.let {
            val interval = SystemClock.elapsedRealtime() - it
            if (interval < config.minIntervalInterstitialAd) {
                mMainThreadHandler.post { callback.onWatchCompleted() }
                return
            }
        }

        if (config.interstitialWithOpenAd) {
            lastShowOpenAdTimeStamp?.let {
                val interval = SystemClock.elapsedRealtime() - it
                if (interval < config.minIntervalInterstitialAd) {
                    mMainThreadHandler.post { callback.onWatchCompleted() }
                    return
                }
            }
        }

        if (config.interstitialWithRewardAd) {
            lastShowRewardAdTimeStamp?.let {
                val interval = SystemClock.elapsedRealtime() - it
                if (interval < config.minIntervalInterstitialAd) {
                    mMainThreadHandler.post { callback.onWatchCompleted() }
                    return
                }
            }
        }

        if (config.interstitialAdIgnoreCount > 0) {
            val sp = mApplication.getSharedPreferences("a2LcOoh2Hq", Context.MODE_PRIVATE)
            val ignoredCount = sp.getInt("QPpanuUMIb", 0)
            if (ignoredCount < config.interstitialAdIgnoreCount) {
                sp.edit().putInt("QPpanuUMIb", ignoredCount + 1).apply()
                mMainThreadHandler.post { callback.onWatchCompleted() }
                return
            }
        }

        val interstitialAd = this.interstitialAd
        val topActivity = App.mTopActivity.get()
        if (interstitialIsActive && interstitialAd != null && topActivity != null) {
            interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    isShowingInterstitialAd = true
                    mMainThreadHandler.post { callback.onShow() }
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    isShowingInterstitialAd = false
                    mMainThreadHandler.post { callback.onShowFailed() }
                }

                override fun onAdDismissedFullScreenContent() {
                    isShowingInterstitialAd = false
                    lastShowInterstitialAdTimeStamp = SystemClock.elapsedRealtime()
                    mMainThreadHandler.post { callback.onWatchCompleted() }
                }
            }
            interstitialAd.show(topActivity)
            interstitialIsActive = false
        } else {
            mMainThreadHandler.post { callback.onShowFailed() }
        }

        initInterstitial()
    }

    override fun doOnInitCompleted(listener: (IAds) -> Unit) {
        if (inited) {
            mMainThreadHandler.post { listener.invoke(this) }
        } else {
            initCompletedListeners.add(listener)
        }
    }

    override fun enableOpenAd(enable: Boolean) {
        allowOpenAd = enable
    }

    fun showOpenAd() {
        if (isWatchingAd()) {
            return
        }
        lastShowInterstitialAdTimeStamp?.let {
            val interval = SystemClock.elapsedRealtime() - it
            if (interval < config.minIntervalOpenId) {
                return
            }
        }
        lastShowRewardAdTimeStamp?.let {
            val interval = SystemClock.elapsedRealtime() - it
            if (interval < config.minIntervalOpenId) {
                return
            }
        }
        lastShowOpenAdTimeStamp?.let {
            val interval = SystemClock.elapsedRealtime() - it
            if (interval < config.minIntervalOpenId) {
                return
            }
        }
        val openAd = this.openAd
        val topActivity = App.mTopActivity.get()
        if (openIsActive && openAd != null && topActivity != null) {
            openAd.show(topActivity)
            openAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    isShowingOpenAd = true
                }

                override fun onAdDismissedFullScreenContent() {
                    lastShowOpenAdTimeStamp = SystemClock.elapsedRealtime()
                    isShowingOpenAd = false
                }
            }
            openIsActive = false
        }
        initOpen()
    }

    fun isWatchingAd(): Boolean {
        return isShowingOpenAd || isShowingInterstitialAd || isShowingRewardAd
    }
}