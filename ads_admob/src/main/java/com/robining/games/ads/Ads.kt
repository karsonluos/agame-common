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
import com.google.android.gms.ads.admanager.AdManagerAdRequest
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

object Ads : AdsProvider {
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
    private const val TAG = "Ads"
    private val TEST_DEVICE_IDS = arrayOf("1EE13FC64E4080073CE7D50EE5BB0561", "BCA4DE4D6F0C6E9D46162A532370FE91")

    private fun log(type: String, message: String) {
        Log.d(TAG, "[$type] $message")
    }

    private fun logLoadStart(type: String, unitId: String?, extra: String = "") {
        val suffix = if (extra.isNotEmpty()) ", $extra" else ""
        Log.d(TAG, "[$type] load start | unitId=${unitId ?: "none"}$suffix")
    }

    private fun logLoadSuccess(type: String, responseInfo: ResponseInfo?) {
        Log.d(TAG, "[$type] loaded | responseId=${responseInfo?.responseId}, adapter=${responseInfo?.mediationAdapterClassName}")
    }

    private fun logLoadFailure(type: String, error: LoadAdError, extra: String = "") {
        val suffix = if (extra.isNotEmpty()) ", $extra" else ""
        Log.e(TAG, "[$type] load failed | code=${error.code}, msg=${error.message}, cause=${error.cause?.message ?: "none"}$suffix, response=${error.responseInfo}")
    }

    @Volatile
    private var preInited = false

    @Volatile
    private var inited = false
    private lateinit var mApplication: Application
    private lateinit var config: Config

    private val initCompletedListeners = arrayListOf<(AdsProvider) -> Unit>()

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

    override fun preInit(application: Application, config: IAds.Config): AdsProvider {
        return preInit(application, Config(
            bannerId = config.bannerId,
            interstitialId = config.interstitialId,
            nativeId = config.nativeId,
            openId = config.openId,
            rewardId = config.rewardId,
            minIntervalInterstitialAd = config.minIntervalInterstitialAd,
            interstitialWithOpenAd = config.interstitialWithOpenAd,
            interstitialWithRewardAd = config.interstitialWithRewardAd,
            setIntervalOnInit = config.setIntervalOnInit,
            interstitialAdIgnoreCount = config.interstitialAdIgnoreCount,
            minIntervalOpenId = config.minIntervalOpenId,
            isValidOpenAdActivity = config.isValidOpenAdActivity,
        ))
    }

    fun preInit(application: Application, config: Config): AdsProvider {
        if (preInited) {
            log("Init", "preInit skipped: already pre-inited")
            return this
        }
        preInited = true
        this.mApplication = application
        this.config = config
        log(
            "Init", "preInit | bannerId=${config.bannerId ?: "none"}, interstitialId=${config.interstitialId ?: "none"}, " +
                    "nativeId=${config.nativeId ?: "none"}, openId=${config.openId ?: "none"}, rewardId=${config.rewardId ?: "none"}, " +
                    "minIntervalInterstitial=${config.minIntervalInterstitialAd}ms, minIntervalOpen=${config.minIntervalOpenId}ms, " +
                    "interstitialAdIgnoreCount=${config.interstitialAdIgnoreCount}"
        )
        return this
    }

    fun init(application: Application, config: Config): AdsProvider {
        preInit(application, config)
        return ready()
    }

    override fun ready(): AdsProvider {
        if (!PrivacyManager.isAgree()){
            log("Init", "ready blocked: privacy not agreed; call Ads.ready() after PrivacyManager.onAgree()")
            return this
        }

        if (inited) {
            log("Init", "ready skipped: already inited")
            return this
        }
        inited = true
        log("Init", "ready | setIntervalOnInit=${config.setIntervalOnInit}")
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

        log("Init", "notify ${initCompletedListeners.size} pending init-completed listener(s)")
        val itr = initCompletedListeners.iterator()
        while (itr.hasNext()) {
            val listener = itr.next()
            mMainThreadHandler.post { listener.invoke(this) }
            itr.remove()
        }

        return this
    }

    private fun onNetworkResume() {
        log("Net", "network connected, try resume adverts")
        tryResumeAdverts()
    }

    private fun tryResumeAdverts() {
        if (!inited) {
            log("Init", "resume adverts skipped: not inited")
            return
        }
        val topActivity = App.mTopActivity.get() ?: run {
            log("Init", "resume adverts skipped: no top activity")
            return
        }

        //尝试重新初始化
        log("Init", "resume adverts: reset retry counters")
        bannerRetryCount = 0
        rewardRetryCount = 0
        nativeRetryCount = 0
        openRetryCount = 0
        interstitialRetryCount = 0

        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(TEST_DEVICE_IDS.toList())
            .build()
        MobileAds.setRequestConfiguration(configuration)
        log("Init", "request configuration set | testDeviceIds=$TEST_DEVICE_IDS")

//        val metaData = MetaData(topActivity)
//        metaData["gdpr.consent"] = true
//        metaData["privacy.consent"] = true
//        metaData.commit()

        MobileAds.initialize(topActivity) {
            val statusMap: Map<String, AdapterStatus> = it.adapterStatusMap
            for (adapterClass in statusMap.keys) {
                val status = statusMap[adapterClass]
                Log.d(
                    TAG, "[Init] SDK adapter | name=$adapterClass, state=${status!!.initializationState}, latency=${status.latency}ms, desc=${status.description}"
                )
            }
            log("Init", "SDK initialize completed, start loading all ads")

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
            log("Banner", "load skipped | inited=$inited, active=$bannerIsActive, loading=$bannerIsLoading, retry=$bannerRetryCount/$MAX_RETRY_COUNT")
            return
        }
        bannerIsLoading = true
        bannerIsActive = false
        logLoadStart("Banner", unitId, "attempt=${bannerRetryCount + 1}/$MAX_RETRY_COUNT")
        bannerAdView.adListener = object : AdListener() {
            override fun onAdFailedToLoad(p0: LoadAdError) {
                bannerRetryCount++
                bannerIsLoading = false
                bannerIsActive = false
                logLoadFailure("Banner", p0, "retry=$bannerRetryCount/$MAX_RETRY_COUNT")
            }

            override fun onAdLoaded() {
                bannerRetryCount = 0
                bannerIsLoading = false
                bannerIsActive = true
                logLoadSuccess("Banner", bannerAdView.responseInfo)
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

    override fun showBannerAdIn(container: FrameLayout, adUnitId: String, request: IAds.RequestOptions) {
        val builder = AdManagerAdRequest.Builder()
        request.keywords.forEach(builder::addKeyword)
        request.contentUrl?.let(builder::setContentUrl)
        if (request.neighboringContentUrls.isNotEmpty()) builder.setNeighboringContentUrls(request.neighboringContentUrls.toList())
        request.requestAgent?.let(builder::setRequestAgent)
        request.categoryExclusions.forEach(builder::addCategoryExclusion)
        request.customTargeting.forEach(builder::addCustomTargeting)
        showBannerAdIn(container, adUnitId, builder.build())
    }

    /** Legacy AdMob-specific overload for callers that need a custom [AdRequest]. */
    fun showBannerAdIn(container: FrameLayout, adUnitId: String, adRequest: AdRequest) {
        if (!PrivacyManager.isAgree()){
            log("Banner", "showBannerAdIn blocked: privacy not agreed")
            return
        }
        log("Banner", "showBannerAdIn | unitId=$adUnitId")
        container.post {
            val bannerView = AdView(container.context)
            bannerView.setBackgroundColor(Color.TRANSPARENT)
            val width = container.measuredWidth
            val bannerSize = getBannerAdSize(width)
            bannerView.setAdSize(bannerSize)
            bannerView.adUnitId = adUnitId
            log("Banner", "inline banner created | unitId=$adUnitId, width=${width}px, size=$bannerSize")
            bannerView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                private val mainHandler = Handler(Looper.getMainLooper())
                private val adLoadStateRef = Ref(AdLoadState.IDLE)
                private var adListener = object : AdListener() {
                    override fun onAdFailedToLoad(p0: LoadAdError) {
                        logLoadFailure("Banner", p0, "mode=inline")
                        adLoadStateRef.value = AdLoadState.FAILED
                        //创建恢复策略
                        mainHandler.post { registerPending() }
                    }

                    override fun onAdLoaded() {
                        adLoadStateRef.value = AdLoadState.SUCCEED
                        logLoadSuccess("Banner", bannerView.responseInfo)
                    }
                }
                private var cancelPendingJob: Runnable? = null

                private fun registerPending() {
                    log("Banner", "inline banner retry armed on network-recovered/foreground")
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
                        log("Banner", "inline banner attached, load | unitId=$adUnitId, state=${adLoadStateRef.value}")
                        //此时加载在监控中有崩溃的情况，所以尝试延迟处理
                        bannerView.postDelayed({
                            bannerView.loadAd(adRequest)
                        },300)
                    }
                    //else 如果加载中等待adListener回调
                }

                override fun onViewDetachedFromWindow(v: View) {
                    log("Banner", "inline banner detached, cancel pending tasks")
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
        return AdSize.getLargeAnchoredAdaptiveBannerAdSize(mApplication, adWidth)
    }

    private fun initNative() {
        val unitId = config.nativeId ?: return
        if (!inited || nativeIsActive || nativeIsLoading || nativeRetryCount >= MAX_RETRY_COUNT) {
            log("Native", "load skipped | inited=$inited, active=$nativeIsActive, loading=$nativeIsLoading, retry=$nativeRetryCount/$MAX_RETRY_COUNT")
            return
        }
        nativeIsLoading = true
        nativeIsActive = false
        logLoadStart("Native", unitId, "attempt=${nativeRetryCount + 1}/$MAX_RETRY_COUNT")
        AdLoader.Builder(App.mTopActivity.get() ?: mApplication, unitId)
            .forNativeAd {
                this.nativeAd = it
                logLoadSuccess("Native", it.responseInfo)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    nativeRetryCount++
                    nativeIsLoading = false
                    nativeIsActive = false
                    logLoadFailure("Native", p0, "retry=$nativeRetryCount/$MAX_RETRY_COUNT")
                }

                override fun onAdLoaded() {
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
            log("Reward", "load skipped | inited=$inited, active=$rewardIsActive, loading=$rewardIsLoading, retry=$rewardRetryCount/$MAX_RETRY_COUNT")
            return
        }
        rewardIsLoading = true
        rewardIsActive = false
        logLoadStart("Reward", unitId, "attempt=${rewardRetryCount + 1}/$MAX_RETRY_COUNT")
        RewardedAd.load(
            App.mTopActivity.get() ?: mApplication,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    rewardRetryCount = 0
                    this@Ads.rewardAd = rewardedAd
                    rewardIsLoading = false
                    rewardIsActive = true
                    rewardAdState.postValue(true)
                    logLoadSuccess("Reward", rewardedAd.responseInfo)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardRetryCount++
                    rewardIsLoading = false
                    rewardIsActive = false
                    logLoadFailure("Reward", loadAdError, "unitId=$unitId, retry=$rewardRetryCount/$MAX_RETRY_COUNT")
                }
            })
    }

    private fun initInterstitial() {
        val unitId = config.interstitialId ?: return
        if (!inited || interstitialIsActive || interstitialIsLoading || interstitialRetryCount >= MAX_RETRY_COUNT) {
            log("Interstitial", "load skipped | inited=$inited, active=$interstitialIsActive, loading=$interstitialIsLoading, retry=$interstitialRetryCount/$MAX_RETRY_COUNT")
            return
        }
        interstitialIsLoading = true
        interstitialIsActive = false
        logLoadStart("Interstitial", unitId, "attempt=${interstitialRetryCount + 1}/$MAX_RETRY_COUNT")
        InterstitialAd.load(
            App.mTopActivity.get() ?: mApplication,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    interstitialRetryCount = 0
                    this@Ads.interstitialAd = interstitialAd
                    interstitialIsLoading = false
                    interstitialIsActive = true
                    logLoadSuccess("Interstitial", interstitialAd.responseInfo)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialRetryCount++
                    interstitialIsLoading = false
                    interstitialIsActive = false
                    logLoadFailure("Interstitial", loadAdError, "retry=$interstitialRetryCount/$MAX_RETRY_COUNT")
                }
            })
    }

    private fun initOpen() {
        val unitId = config.openId ?: return
        if (!inited || openIsActive || openIsLoading || openRetryCount >= MAX_RETRY_COUNT) {
            log("Open", "load skipped | inited=$inited, active=$openIsActive, loading=$openIsLoading, retry=$openRetryCount/$MAX_RETRY_COUNT")
            return
        }
        openIsLoading = true
        openIsActive = false
        logLoadStart("Open", unitId, "attempt=${openRetryCount + 1}/$MAX_RETRY_COUNT")
        AppOpenAd.load(
            App.mTopActivity.get() ?: mApplication,
            unitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(p0: AppOpenAd) {
                    openRetryCount = 0
                    this@Ads.openAd = p0
                    openIsActive = true
                    openIsLoading = false
                    logLoadSuccess("Open", p0.responseInfo)
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    openRetryCount++
                    openIsActive = false
                    openIsLoading = false
                    logLoadFailure("Open", p0, "retry=$openRetryCount/$MAX_RETRY_COUNT")
                }
            })
    }

    override fun getBannerAdHeightInPixel(): Int {
        return getBannerAdSize().getHeightInPixels(mApplication)
    }

    override fun showBannerAd(container: FrameLayout, alwaysPlaceHolder: Boolean): Boolean {
        log("Banner", "showBannerAd | active=$bannerIsActive, alwaysPlaceHolder=$alwaysPlaceHolder")
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
                log("Banner", "banner attached to container | filledByCache=$bannerIsActive")
                return true
            }
            else -> {
                log("Banner", "banner not ready, hide container and try reload")
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
        log("Native", "showNativeAd | active=$nativeIsActive, size=$nativeAdSize, alwaysPlaceHolder=$alwaysPlaceHolder")
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
                log("Native", "native ad shown | responseId=${nativeAd.responseInfo?.responseId}, adapter=${nativeAd.responseInfo?.mediationAdapterClassName}, reload next")
                nativeIsActive = false
                initNative()
                return true
            }
            alwaysPlaceHolder -> {
                log("Native", "show skipped: no ready native ad, show placeholder")
                container.visibility = View.INVISIBLE
            }
            else -> {
                log("Native", "show skipped: no ready native ad, hide container")
                container.visibility = View.GONE
            }
        }

        //尝试重新加载
        initNative()
        return false
    }

    override fun showRewardAd(callback: IAds.RewardAdCallback) {
        if (isWatchingAd()) {
            log("Reward", "show blocked: another ad showing")
            mMainThreadHandler.post { callback.onShowFailed() }
            return
        }
        val rewardAd = this.rewardAd
        val topActivity = App.mTopActivity.get()
        val gotRef = Array(1) { false }
        if (rewardIsActive && rewardAd != null && topActivity != null) {
            log("Reward", "show start | responseId=${rewardAd.responseInfo.responseId}, adapter=${rewardAd.responseInfo.mediationAdapterClassName}")
            rewardAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    isShowingRewardAd = true
                    log("Reward", "showed")
                    mMainThreadHandler.post { callback.onShow() }
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    isShowingRewardAd = false
                    Log.e(TAG, "[Reward] show failed | code=${p0.code}, msg=${p0.message}, cause=${p0.cause?.message ?: "none"}")
                    mMainThreadHandler.post { callback.onShowFailed() }
                }

                override fun onAdDismissedFullScreenContent() {
                    isShowingRewardAd = false
                    lastShowRewardAdTimeStamp = SystemClock.elapsedRealtime()
                    log("Reward", "dismissed | gotReward=${gotRef[0]}")
                    mMainThreadHandler.post { callback.onWatchCompleted(gotRef[0]) }
                }
            }
            rewardAd.show(topActivity) {
                gotRef[0] = true
                log("Reward", "reward earned")
            }
            rewardIsActive = false
        } else {
            log("Reward", "show failed: ad not ready | active=$rewardIsActive, ad=${rewardAd != null}, topActivity=${topActivity != null}")
            mMainThreadHandler.post { callback.onShowFailed() }
        }

        rewardAdState.postValue(false)
        initReward()
    }

    override fun showInterstitialAd(callback: IAds.InterstitialAdCallback) {
        if (isWatchingAd()) {
            log("Interstitial", "show blocked: another ad showing")
            mMainThreadHandler.post { callback.onShowFailed() }
            return
        }

        lastShowInterstitialAdTimeStamp?.let {
            val interval = SystemClock.elapsedRealtime() - it
            if (interval < config.minIntervalInterstitialAd) {
                log("Interstitial", "show skipped: within interstitial interval | elapsed=${interval}ms, min=${config.minIntervalInterstitialAd}ms")
                mMainThreadHandler.post { callback.onWatchCompleted() }
                return
            }
        }

        if (config.interstitialWithOpenAd) {
            lastShowOpenAdTimeStamp?.let {
                val interval = SystemClock.elapsedRealtime() - it
                if (interval < config.minIntervalInterstitialAd) {
                    log("Interstitial", "show skipped: within open-ad-linked interval | elapsed=${interval}ms, min=${config.minIntervalInterstitialAd}ms")
                    mMainThreadHandler.post { callback.onWatchCompleted() }
                    return
                }
            }
        }

        if (config.interstitialWithRewardAd) {
            lastShowRewardAdTimeStamp?.let {
                val interval = SystemClock.elapsedRealtime() - it
                if (interval < config.minIntervalInterstitialAd) {
                    log("Interstitial", "show skipped: within reward-ad-linked interval | elapsed=${interval}ms, min=${config.minIntervalInterstitialAd}ms")
                    mMainThreadHandler.post { callback.onWatchCompleted() }
                    return
                }
            }
        }

        if (config.interstitialAdIgnoreCount > 0) {
            val sp = mApplication.getSharedPreferences("a2LcOoh2Hq", Context.MODE_PRIVATE)
            val ignoredCount = sp.getInt("QPpanuUMIb", 0)
            if (ignoredCount < config.interstitialAdIgnoreCount) {
                log("Interstitial", "show skipped: ignore count | ${ignoredCount + 1}/${config.interstitialAdIgnoreCount}")
                sp.edit().putInt("QPpanuUMIb", ignoredCount + 1).apply()
                mMainThreadHandler.post { callback.onWatchCompleted() }
                return
            }
        }

        val interstitialAd = this.interstitialAd
        val topActivity = App.mTopActivity.get()
        if (interstitialIsActive && interstitialAd != null && topActivity != null) {
            log("Interstitial", "show start | responseId=${interstitialAd.responseInfo.responseId}, adapter=${interstitialAd.responseInfo.mediationAdapterClassName}")
            interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    isShowingInterstitialAd = true
                    log("Interstitial", "showed")
                    mMainThreadHandler.post { callback.onShow() }
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    isShowingInterstitialAd = false
                    Log.e(TAG, "[Interstitial] show failed | code=${p0.code}, msg=${p0.message}, cause=${p0.cause?.message ?: "none"}")
                    mMainThreadHandler.post { callback.onShowFailed() }
                }

                override fun onAdDismissedFullScreenContent() {
                    isShowingInterstitialAd = false
                    lastShowInterstitialAdTimeStamp = SystemClock.elapsedRealtime()
                    log("Interstitial", "dismissed")
                    mMainThreadHandler.post { callback.onWatchCompleted() }
                }
            }
            interstitialAd.show(topActivity)
            interstitialIsActive = false
        } else {
            log("Interstitial", "show failed: ad not ready | active=$interstitialIsActive, ad=${interstitialAd != null}, topActivity=${topActivity != null}")
            mMainThreadHandler.post { callback.onShowFailed() }
        }

        initInterstitial()
    }

    override fun doOnInitCompleted(listener: (AdsProvider) -> Unit) {
        if (inited) {
            log("Init", "doOnInitCompleted: already inited, invoke immediately")
            mMainThreadHandler.post { listener.invoke(this) }
        } else {
            log("Init", "doOnInitCompleted: listener queued")
            initCompletedListeners.add(listener)
        }
    }

    override fun enableOpenAd(enable: Boolean) {
        log("Open", "enableOpenAd: $enable")
        allowOpenAd = enable
    }

    override fun showOpenAd() {
        if (isWatchingAd()) {
            log("Open", "show blocked: another ad showing")
            return
        }
        lastShowInterstitialAdTimeStamp?.let {
            val interval = SystemClock.elapsedRealtime() - it
            if (interval < config.minIntervalOpenId) {
                log("Open", "show skipped: within interstitial interval | elapsed=${interval}ms, min=${config.minIntervalOpenId}ms")
                return
            }
        }
        lastShowRewardAdTimeStamp?.let {
            val interval = SystemClock.elapsedRealtime() - it
            if (interval < config.minIntervalOpenId) {
                log("Open", "show skipped: within reward interval | elapsed=${interval}ms, min=${config.minIntervalOpenId}ms")
                return
            }
        }
        lastShowOpenAdTimeStamp?.let {
            val interval = SystemClock.elapsedRealtime() - it
            if (interval < config.minIntervalOpenId) {
                log("Open", "show skipped: within open interval | elapsed=${interval}ms, min=${config.minIntervalOpenId}ms")
                return
            }
        }
        val openAd = this.openAd
        val topActivity = App.mTopActivity.get()
        if (openIsActive && openAd != null && topActivity != null) {
            log("Open", "show start | responseId=${openAd.responseInfo.responseId}, adapter=${openAd.responseInfo.mediationAdapterClassName}")
            openAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    isShowingOpenAd = true
                    log("Open", "showed")
                }

                override fun onAdDismissedFullScreenContent() {
                    lastShowOpenAdTimeStamp = SystemClock.elapsedRealtime()
                    isShowingOpenAd = false
                    log("Open", "dismissed")
                }
            }
            openAd.show(topActivity)
            openIsActive = false
        } else {
            log("Open", "show skipped: ad not ready | active=$openIsActive, ad=${openAd != null}, topActivity=${topActivity != null}")
        }
        initOpen()
    }

    override fun isWatchingAd(): Boolean {
        return isShowingOpenAd || isShowingInterstitialAd || isShowingRewardAd
    }
}
