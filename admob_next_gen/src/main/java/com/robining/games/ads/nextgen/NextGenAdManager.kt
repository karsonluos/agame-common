package com.robining.games.ads.nextgen

import android.app.Application
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.MutableLiveData
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.robining.games.ads.IAds
import com.robining.games.ads.AdsProvider
import com.robining.games.frame.managers.PrivacyManager
import com.robining.games.frame.utils.App
import com.robining.games.frame.utils.AppLifeCycleListener
import com.robining.games.frame.utils.Net
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Google Ad Manager GMA Next-Gen implementation of the common [AdsProvider]. */
object NextGenAdManager : AdsProvider {
    private const val TAG = "Ads"
    private const val MAX_RETRY_COUNT = 3
    private const val OPEN_AD_FOREGROUND_DELAY_MILLIS = 300L
    private const val IGNORE_COUNT_PREFS = "a2LcOoh2Hq"
    private const val IGNORE_COUNT_KEY = "QPpanuUMIb"
    private val TEST_DEVICE_IDS = listOf("1EE13FC64E4080073CE7D50EE5BB0561", "BCA4DE4D6F0C6E9D46162A532370FE91")
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class InlineBannerRequest(
        val unitId: String,
        val options: IAds.RequestOptions,
        val token: Long,
    )

    enum class State { IDLE, WAITING_FOR_PRIVACY, INITIALIZING, READY, FAILED }

    data class Config(
        /** Google Ad Manager app ID, for example ca-app-pub-3940256099942544~3347511713. */
        val appId: String,
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

    val initializationState = MutableLiveData(State.IDLE)
    override val rewardAdState = MutableLiveData(false)
    private val initCompletedListeners = mutableListOf<(AdsProvider) -> Unit>()

    @Volatile
    private var initStarted = false

    @Volatile
    private var initializedAppId: String? = null
    @Volatile
    private var preInited = false
    private lateinit var config: Config
    private lateinit var application: Application
    private var bannerAd: BannerAd? = null
    private var bannerView: AdView? = null
    private var bannerRegistered = false
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var openAd: AppOpenAd? = null
    private var nativeAd: NativeAd? = null
    private var bannerRetryCount = 0
    private var interstitialRetryCount = 0
    private var rewardRetryCount = 0
    private var openRetryCount = 0
    private var nativeRetryCount = 0
    private var bannerLoading = false
    private var interstitialLoading = false
    private var rewardLoading = false
    private var openLoading = false
    private var nativeLoading = false
    private var isShowingInterstitial = false
    private var isShowingReward = false
    private var isShowingOpen = false
    @Volatile
    private var allowOpenAd = true
    private var lastShowInterstitialAt: Long? = null
    private var lastShowRewardAt: Long? = null
    private var lastShowOpenAt: Long? = null
    private val pendingInlineBanners = WeakHashMap<FrameLayout, InlineBannerRequest>()
    private val loadingInlineBannerTokens = WeakHashMap<FrameLayout, Long>()
    private var inlineBannerSequence = 0L

    /** Stores configuration only. Call [ready] manually after privacy consent. */
    override fun preInit(application: Application, config: IAds.Config): AdsProvider {
        return preInit(application, Config(
            appId = requireNotNull(config.appId) { "IAds.Config.appId is required by GMA Next-Gen." },
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
        check(config.appId.isNotBlank()) { "Google Ad Manager appId must not be blank" }
        if (preInited) {
            Log.d(TAG, "[Init] preInit skipped: already pre-inited")
            return this
        }
        preInited = true
        this.application = application
        this.config = config
        Log.d(TAG, "[Init] preInit | banner=${config.bannerId ?: "none"}, native=${config.nativeId ?: "none"}, interstitial=${config.interstitialId ?: "none"}, reward=${config.rewardId ?: "none"}, open=${config.openId ?: "none"}, interstitialInterval=${config.minIntervalInterstitialAd}ms, openInterval=${config.minIntervalOpenId}ms, ignoreCount=${config.interstitialAdIgnoreCount}")
        return this
    }

    /** Convenience API. If privacy has not been agreed, call [ready] again after consent. */
    fun init(application: Application, config: Config): AdsProvider {
        preInit(application, config)
        return ready()
    }

    /** Starts SDK initialization after the business app has obtained privacy consent. */
    override fun ready(): AdsProvider {
        if (!PrivacyManager.isAgree()) {
            initializationState.postValue(State.WAITING_FOR_PRIVACY)
            Log.i(TAG, "privacy not agreed; call NextGenAdManager.ready() after PrivacyManager.onAgree()")
            return this
        }

        synchronized(this) {
            if (initStarted) {
                if (initializedAppId != config.appId) {
                    Log.w(TAG, "already initialized with another app ID; ignoring duplicate initialization")
                }
                return this
            }
            initStarted = true
            initializedAppId = config.appId
        }

        if (config.setIntervalOnInit) lastShowInterstitialAt = SystemClock.elapsedRealtime()
        Net.doAfterConnectedAlways(Runnable { onNetworkOrForegroundResume() })
        App.registerUntilListener { activity ->
            val valid = config.isValidOpenAdActivity(activity)
            if (valid && allowOpenAd) scheduleAutomaticOpenAd(activity)
            valid
        }
        App.registerListener(object : AppLifeCycleListener {
            override fun onFirstActivityResumedSinceEnterApp() = onNetworkOrForegroundResume()
        })

        initializationState.postValue(State.INITIALIZING)
        notifyInitCompleted()
        Thread {
            try {
                MobileAds.initialize(
                    application,
                    InitializationConfig.Builder(config.appId).build(),
                ) { initializationStatus ->
                    onMain {
                        // Next-Gen requires initialize() before every other MobileAds API call.
                        // Apply test-device configuration before starting any ad load.
                        MobileAds.setRequestConfiguration(
                            RequestConfiguration.Builder().setTestDeviceIds(TEST_DEVICE_IDS).build()
                        )
                        initializationState.value = State.READY
                        Log.i(TAG, "GMA Next-Gen initialized: $initializationStatus")
                        loadAll()
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "GMA Next-Gen initialization failed", error)
                synchronized(this) {
                    initStarted = false
                    initializedAppId = null
                }
                initializationState.postValue(State.FAILED)
            }
        }.start()
        return this
    }

    override fun isReadyBannerAd() = bannerAd != null

    override fun isReadyNativeAd() = nativeAd != null

    override fun isReadyInterstitialAd() = interstitialAd != null

    override fun getBannerAdHeightInPixel(): Int {
        if (!this::application.isInitialized) return 0
        return bannerSize().getHeightInPixels(application)
    }

    override fun showBannerAdIn(container: FrameLayout, adUnitId: String, request: IAds.RequestOptions) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            container.post { showBannerAdIn(container, adUnitId, request) }
            return
        }
        if (!canRequestAds("BannerInline")) return
        val pending = InlineBannerRequest(adUnitId, request, ++inlineBannerSequence)
        pendingInlineBanners[container] = pending
        loadInlineBanner(container, pending)
    }

    private fun loadInlineBanner(container: FrameLayout, pending: InlineBannerRequest) {
        if (loadingInlineBannerTokens[container] == pending.token) return
        loadingInlineBannerTokens[container] = pending.token
        val adUnitId = pending.unitId
        val request = pending.options
        Log.d(TAG, "[Banner] inline load start | unitId=$adUnitId")
        val adView = AdView(container.context)
        container.removeAllViews()
        container.addView(adView)
        val builder = BannerAdRequest.Builder(adUnitId, bannerSize(container))
        request.keywords.forEach(builder::addKeyword)
        request.contentUrl?.let(builder::setContentUrl)
        if (request.neighboringContentUrls.isNotEmpty()) builder.setNeighboringContentUrls(request.neighboringContentUrls)
        request.requestAgent?.let(builder::setRequestAgent)
        request.categoryExclusions.forEach(builder::addCategoryExclusion)
        request.customTargeting.forEach(builder::putCustomTargeting)
        adView.loadAd(builder.build(), object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                onMain {
                    if (pendingInlineBanners[container]?.token != pending.token) return@onMain
                    loadingInlineBannerTokens.remove(container)
                    val activity = App.mTopActivity.get() ?: return@onMain
                    pendingInlineBanners.remove(container)
                    ad.adEventCallback = bannerLogger("BannerInline")
                    adView.registerBannerAd(ad, activity)
                    container.visibility = View.VISIBLE
                    Log.d(TAG, "[Banner] inline loaded | response=${ad.getResponseInfo()}")
                }
            }
            override fun onAdFailedToLoad(adError: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) = onMain {
                if (pendingInlineBanners[container]?.token != pending.token) return@onMain
                loadingInlineBannerTokens.remove(container)
                Log.w(TAG, "[Banner] inline load failed | ${adError.message}; retry armed on network-recovered/foreground")
            }
        })
    }

    override fun showBannerAd(container: FrameLayout, alwaysPlaceHolder: Boolean): Boolean {
        requireMainThread()
        container.removeAllViews()
        if (bannerAd == null) {
            loadBanner()
            if (!alwaysPlaceHolder) {
                container.visibility = View.GONE
                return false
            }
            container.visibility = View.VISIBLE
            container.minimumHeight = getBannerAdHeightInPixel()
            bannerView?.let { adView ->
                (adView.parent as? ViewGroup)?.removeView(adView)
                container.addView(adView, bannerLayoutParams(container.context))
            }
            return true
        }
        val adView = bannerView ?: run { loadBanner(); return false }
        (adView.parent as? ViewGroup)?.removeView(adView)
        container.addView(adView, bannerLayoutParams(container.context))
        val activity = App.mTopActivity.get() ?: return false
        registerCachedBannerIfNeeded(activity)
        container.visibility = View.VISIBLE
        return true
    }

    override fun showNativeAd(
        container: FrameLayout,
        nativeAdSize: IAds.NativeAdSize,
        alwaysPlaceHolder: Boolean,
    ): Boolean {
        requireMainThread()
        val ad = nativeAd ?: run {
            container.visibility = if (alwaysPlaceHolder) View.INVISIBLE else View.GONE
            loadNative()
            return false
        }
        container.removeAllViews()
        container.addView(createNativeAdView(container.context, ad, nativeAdSize))
        container.visibility = View.VISIBLE
        nativeAd = null
        Log.d(TAG, "[Native] shown")
        loadNative()
        return true
    }

    override fun showRewardAd(callback: IAds.RewardAdCallback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showRewardAd(callback) }
            return
        }
        if (isWatchingAd()) {
            Log.d(TAG, "[Reward] show blocked: another full-screen ad is showing")
            callback.onShowFailed()
            return
        }
        val ad = rewardedAd ?: run { onMain { callback.onShowFailed() }; loadRewarded(); return }
        val activity = App.mTopActivity.get() ?: run { onMain { callback.onShowFailed() }; return }
        val gotReward = AtomicBoolean(false)
        ad.adEventCallback = rewardedLogger(callback, gotReward)
        Log.d(TAG, "[Reward] show start | response=${ad.getResponseInfo()}")
        ad.show(activity) { gotReward.set(true); Log.d(TAG, "[Reward] reward earned") }
        rewardedAd = null
        rewardAdState.value = false
        loadRewarded()
    }

    override fun showInterstitialAd(callback: IAds.InterstitialAdCallback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showInterstitialAd(callback) }
            return
        }
        if (isWatchingAd()) {
            Log.d(TAG, "[Interstitial] show blocked: another full-screen ad is showing")
            callback.onShowFailed()
            return
        }
        if (shouldSkipInterstitial()) {
            callback.onWatchCompleted()
            return
        }
        val ad = interstitialAd ?: run { onMain { callback.onShowFailed() }; loadInterstitial(); return }
        val activity = App.mTopActivity.get() ?: run { onMain { callback.onShowFailed() }; return }
        ad.adEventCallback = interstitialLogger(callback)
        Log.d(TAG, "[Interstitial] show start | response=${ad.getResponseInfo()}")
        ad.show(activity)
        interstitialAd = null
        loadInterstitial()
    }

    override fun doOnInitCompleted(listener: (AdsProvider) -> Unit) {
        if (initStarted) {
            mainHandler.post { listener(this) }
        } else {
            initCompletedListeners += listener
        }
    }

    override fun enableOpenAd(enable: Boolean) {
        allowOpenAd = enable
        Log.d(TAG, "[Open] enabled=$enable")
    }

    private fun loadAll() { loadBanner(); loadNative(); loadInterstitial(); loadRewarded(); loadOpen() }

    private fun onNetworkOrForegroundResume() = onMain {
        if (initializationState.value != State.READY) return@onMain
        bannerRetryCount = 0
        nativeRetryCount = 0
        interstitialRetryCount = 0
        rewardRetryCount = 0
        openRetryCount = 0
        Log.d(TAG, "[Init] network/foreground resumed; reset retries and reload missing ads")
        loadAll()
        pendingInlineBanners.toMap().forEach { (container, request) ->
            if (container.isAttachedToWindow) loadInlineBanner(container, request)
        }
    }

    private fun bannerSize(container: FrameLayout? = null): AdSize {
        val context = container?.context ?: App.mTopActivity.get() ?: application
        val width = container?.width?.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, (width / context.resources.displayMetrics.density).toInt())
    }

    private fun bannerLayoutParams(context: Context) = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        bannerSize().getHeightInPixels(context),
    )

    private fun registerCachedBannerIfNeeded(activity: Activity) {
        if (bannerRegistered) return
        val ad = bannerAd ?: return
        val view = bannerView ?: return
        ad.adEventCallback = bannerLogger("Banner")
        view.registerBannerAd(ad, activity)
        bannerRegistered = true
    }

    private fun loadBanner() {
        if (initializationState.value != State.READY) return
        if (bannerAd != null || bannerLoading || bannerRetryCount >= MAX_RETRY_COUNT) return
        val activity = App.mTopActivity.get() ?: return
        config.bannerId?.let { id ->
            val view = AdView(activity)
            bannerView = view
            bannerRegistered = false
            bannerLoading = true
            Log.d(TAG, "[Banner] load start | unitId=$id, attempt=${bannerRetryCount + 1}/$MAX_RETRY_COUNT")
            view.loadAd(BannerAdRequest.Builder(id, bannerSize()).build(), object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) { onMain {
                    bannerLoading = false
                    bannerRetryCount = 0
                    bannerAd = ad
                    App.mTopActivity.get()?.let { topActivity ->
                        if (view.parent != null) registerCachedBannerIfNeeded(topActivity)
                    }
                    Log.d(TAG, "[Banner] loaded | response=${ad.getResponseInfo()}")
                } }
                override fun onAdFailedToLoad(adError: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) { onMain { bannerLoading = false; bannerRetryCount++; Log.w(TAG, "[Banner] load failed | ${adError.message}, retry=$bannerRetryCount/$MAX_RETRY_COUNT") } }
            })
        }
    }

    private fun loadNative() {
        if (initializationState.value != State.READY) return
        if (nativeAd != null || nativeLoading || nativeRetryCount >= MAX_RETRY_COUNT) return
        val id = config.nativeId ?: return
        nativeLoading = true
        Log.d(TAG, "[Native] load start | unitId=$id, attempt=${nativeRetryCount + 1}/$MAX_RETRY_COUNT")
        NativeAdLoader.load(
            NativeAdRequest.Builder(id, listOf(NativeAd.NativeAdType.NATIVE)).build(),
            object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) = onMain {
                    nativeLoading = false
                    nativeRetryCount = 0
                    this@NextGenAdManager.nativeAd = nativeAd
                    Log.d(TAG, "[Native] loaded | response=${nativeAd.getResponseInfo()}")
                }
                override fun onAdFailedToLoad(adError: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) = onMain {
                    nativeLoading = false
                    nativeRetryCount++
                    Log.w(TAG, "[Native] load failed | ${adError.message}, retry=$nativeRetryCount/$MAX_RETRY_COUNT")
                }
            },
        )
    }

    private fun loadInterstitial() {
        if (initializationState.value != State.READY) return
        if (interstitialAd != null || interstitialLoading || interstitialRetryCount >= MAX_RETRY_COUNT) return
        val id = config.interstitialId ?: return
        interstitialLoading = true
        Log.d(TAG, "[Interstitial] load start | unitId=$id, attempt=${interstitialRetryCount + 1}/$MAX_RETRY_COUNT")
        InterstitialAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<InterstitialAd> {
            override fun onAdLoaded(ad: InterstitialAd) = onMain { interstitialLoading = false; interstitialRetryCount = 0; interstitialAd = ad; Log.d(TAG, "[Interstitial] loaded | response=${ad.getResponseInfo()}") }
            override fun onAdFailedToLoad(adError: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) = onMain { interstitialLoading = false; interstitialRetryCount++; Log.w(TAG, "[Interstitial] load failed | ${adError.message}, retry=$interstitialRetryCount/$MAX_RETRY_COUNT") }
        })
    }

    private fun loadRewarded() {
        if (initializationState.value != State.READY) return
        if (rewardedAd != null || rewardLoading || rewardRetryCount >= MAX_RETRY_COUNT) return
        val id = config.rewardId ?: return
        rewardLoading = true
        Log.d(TAG, "[Reward] load start | unitId=$id, attempt=${rewardRetryCount + 1}/$MAX_RETRY_COUNT")
        RewardedAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<RewardedAd> {
            override fun onAdLoaded(ad: RewardedAd) = onMain { rewardLoading = false; rewardRetryCount = 0; rewardedAd = ad; rewardAdState.value = true; Log.d(TAG, "[Reward] loaded | response=${ad.getResponseInfo()}") }
            override fun onAdFailedToLoad(adError: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) = onMain { rewardLoading = false; rewardRetryCount++; rewardAdState.value = false; Log.w(TAG, "[Reward] load failed | ${adError.message}, retry=$rewardRetryCount/$MAX_RETRY_COUNT") }
        })
    }

    private fun loadOpen() {
        if (initializationState.value != State.READY) return
        if (openAd != null || openLoading || openRetryCount >= MAX_RETRY_COUNT) return
        val id = config.openId ?: return
        openLoading = true
        Log.d(TAG, "[Open] load start | unitId=$id, attempt=${openRetryCount + 1}/$MAX_RETRY_COUNT")
        AppOpenAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<AppOpenAd> {
            override fun onAdLoaded(ad: AppOpenAd) = onMain { openLoading = false; openRetryCount = 0; openAd = ad; Log.d(TAG, "[Open] loaded | response=${ad.getResponseInfo()}") }
            override fun onAdFailedToLoad(adError: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) = onMain { openLoading = false; openRetryCount++; Log.w(TAG, "[Open] load failed | ${adError.message}, retry=$openRetryCount/$MAX_RETRY_COUNT") }
        })
    }

    override fun showOpenAd() {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { showOpenAd() }; return }
        if (!canRequestAds("Open")) return
        if (isWatchingAd()) { Log.d(TAG, "[Open] show blocked: another full-screen ad is showing"); return }
        val now = SystemClock.elapsedRealtime()
        val recent = listOfNotNull(lastShowInterstitialAt, lastShowRewardAt, lastShowOpenAt).maxOrNull()
        if (recent != null && now - recent < config.minIntervalOpenId) { Log.d(TAG, "[Open] show skipped: interval=${now - recent}ms, min=${config.minIntervalOpenId}ms"); return }
        val ad = openAd ?: run { Log.d(TAG, "[Open] show skipped: ad not ready"); loadOpen(); return }
        val activity = App.mTopActivity.get()
        if (activity == null || !isForegroundActivity(activity)) {
            Log.d(TAG, "[Open] show skipped: no foreground activity")
            return
        }
        ad.adEventCallback = openLogger()
        openAd = null
        Log.d(TAG, "[Open] show start | response=${ad.getResponseInfo()}")
        ad.show(activity)
        loadOpen()
    }
    private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block) }
    private fun requireMainThread() { check(Looper.myLooper() == Looper.getMainLooper()) { "IAds show APIs must be called on the main thread." } }
    private fun scheduleAutomaticOpenAd(activity: Activity) {
        mainHandler.postDelayed({
            if (App.mTopActivity.get() !== activity || !allowOpenAd || !isForegroundActivity(activity)) {
                Log.d(TAG, "[Open] automatic show skipped: activity is no longer foreground")
                return@postDelayed
            }
            showOpenAd()
        }, OPEN_AD_FOREGROUND_DELAY_MILLIS)
    }

    private fun isForegroundActivity(activity: Activity): Boolean =
        !activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus()

    private fun notifyInitCompleted() = onMain {
        initCompletedListeners.toList().forEach { listener -> listener(this) }
        initCompletedListeners.clear()
    }
    private fun canRequestAds(format: String): Boolean {
        if (!PrivacyManager.isAgree()) {
            Log.w(TAG, "[$format] request blocked: privacy not agreed")
            return false
        }
        if (initializationState.value != State.READY) {
            Log.d(TAG, "[$format] request blocked: SDK is not ready | state=${initializationState.value}")
            return false
        }
        return true
    }
    private fun shouldSkipInterstitial(): Boolean {
        val now = SystemClock.elapsedRealtime()
        fun tooRecent(timestamp: Long?) = timestamp != null && now - timestamp < config.minIntervalInterstitialAd
        if (tooRecent(lastShowInterstitialAt) || (config.interstitialWithOpenAd && tooRecent(lastShowOpenAt)) ||
            (config.interstitialWithRewardAd && tooRecent(lastShowRewardAt))) {
            Log.d(TAG, "[Interstitial] show skipped by interval | min=${config.minIntervalInterstitialAd}ms")
            return true
        }
        if (config.interstitialAdIgnoreCount > 0) {
            val prefs = application.getSharedPreferences(IGNORE_COUNT_PREFS, Context.MODE_PRIVATE)
            val ignored = prefs.getInt(IGNORE_COUNT_KEY, 0)
            if (ignored < config.interstitialAdIgnoreCount) {
                prefs.edit().putInt(IGNORE_COUNT_KEY, ignored + 1).apply()
                Log.d(TAG, "[Interstitial] show skipped by ignore count | ${ignored + 1}/${config.interstitialAdIgnoreCount}")
                return true
            }
        }
        return false
    }

    private fun interstitialLogger(callback: IAds.InterstitialAdCallback) = object : InterstitialAdEventCallback {
        override fun onAdShowedFullScreenContent() { onMain { isShowingInterstitial = true; Log.d(TAG, "[Interstitial] showed"); callback.onShow() } }
        override fun onAdDismissedFullScreenContent() { onMain { isShowingInterstitial = false; lastShowInterstitialAt = SystemClock.elapsedRealtime(); Log.d(TAG, "[Interstitial] dismissed"); callback.onWatchCompleted() } }
        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) { onMain { isShowingInterstitial = false; Log.w(TAG, "[Interstitial] show failed | $fullScreenContentError"); callback.onShowFailed() } }
        override fun onAdClicked() { Log.d(TAG, "[Interstitial] clicked") }
    }

    private fun rewardedLogger(callback: IAds.RewardAdCallback, gotReward: AtomicBoolean) = object : RewardedAdEventCallback {
        override fun onAdShowedFullScreenContent() { onMain { isShowingReward = true; Log.d(TAG, "[Reward] showed"); callback.onShow() } }
        override fun onAdDismissedFullScreenContent() { onMain { isShowingReward = false; lastShowRewardAt = SystemClock.elapsedRealtime(); Log.d(TAG, "[Reward] dismissed | gotReward=${gotReward.get()}"); callback.onWatchCompleted(gotReward.get()) } }
        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) { onMain { isShowingReward = false; Log.w(TAG, "[Reward] show failed | $fullScreenContentError"); callback.onShowFailed() } }
        override fun onAdClicked() { Log.d(TAG, "[Reward] clicked") }
    }

    private fun openLogger() = object : AppOpenAdEventCallback {
        override fun onAdShowedFullScreenContent() { onMain { isShowingOpen = true; Log.d(TAG, "[Open] showed") } }
        override fun onAdDismissedFullScreenContent() { onMain { isShowingOpen = false; lastShowOpenAt = SystemClock.elapsedRealtime(); Log.d(TAG, "[Open] dismissed") } }
        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) { onMain { isShowingOpen = false; Log.w(TAG, "[Open] show failed | $fullScreenContentError") } }
        override fun onAdClicked() { Log.d(TAG, "[Open] clicked") }
    }

    private fun bannerLogger(type: String) = object : BannerAdEventCallback {
        override fun onAdImpression() { Log.d(TAG, "[$type] impression") }
        override fun onAdClicked() { Log.d(TAG, "[$type] clicked") }
    }

    override fun isWatchingAd() = isShowingInterstitial || isShowingReward || isShowingOpen

    private fun createNativeAdView(context: Context, ad: NativeAd, size: IAds.NativeAdSize): NativeAdView {
        val root = NativeAdView(context).apply { setBackgroundColor(Color.WHITE) }
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val headline = TextView(context).apply { text = ad.headline; textSize = 17f }
        val media = MediaView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (size == IAds.NativeAdSize.Medium) dp(180) else dp(80)); mediaContent = ad.mediaContent }
        val body = TextView(context).apply { text = ad.body; visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE }
        val icon = ImageView(context).apply { setImageDrawable(ad.icon?.drawable); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)) }
        val cta = Button(context).apply { text = ad.callToAction; visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE }
        content.addView(headline)
        content.addView(media)
        content.addView(body)
        content.addView(icon)
        content.addView(cta)
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.headlineView = headline
        root.bodyView = body
        root.iconView = icon
        root.callToActionView = cta
        ad.adEventCallback = object : com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback {
            override fun onAdImpression() { Log.d(TAG, "[Native] impression") }
            override fun onAdClicked() { Log.d(TAG, "[Native] clicked") }
        }
        root.registerNativeAd(ad, media)
        return root
    }
}
