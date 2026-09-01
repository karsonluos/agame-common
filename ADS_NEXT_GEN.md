# Google Ad Manager Next-Gen SDK

`iads` provides the provider-neutral ad interface. Both `ads_admob` (legacy) and
`admob_next_gen` (GMA Next-Gen) implement it. The Next-Gen implementation requires
Android `minSdk 24`.

## App setup

```kotlin
import com.robining.games.ads.IAds
import com.robining.games.ads.nextgen.NextGenAdManager

IAds.setProvider(NextGenAdManager)
IAds.preInit(
    application,
    IAds.Config(
        appId = "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy",
        bannerId = "/your/banner",
        interstitialId = "/your/interstitial",
        nativeId = "/your/native",
        openId = "/your/app-open",
        rewardId = "/your/rewarded",
        minIntervalInterstitialAd = 60_000,
        interstitialWithOpenAd = false,
        interstitialWithRewardAd = false,
        setIntervalOnInit = true,
        interstitialAdIgnoreCount = 0,
        minIntervalOpenId = 10_000,
        isValidOpenAdActivity = { activity -> true },
    ),
)

// Call manually after PrivacyManager.onAgree().
IAds.ready()
```

`preInit()` only stores configuration. It does not initialize or load ads.
Call `IAds.ready()` after `PrivacyManager.onAgree()`. Observe
`NextGenAdManager.initializationState` for
`WAITING_FOR_PRIVACY`, `INITIALIZING`, `READY`, or `FAILED`.

The App ID belongs to the business application. Do not put it in the library or
hard-code a production ID in source control.

## Legacy feature compatibility

Both providers support the same `IAds` entry points for cached and inline banners,
native ads, interstitial ads, rewarded ads, and app-open ads. The shared API also
keeps the legacy readiness checks, rewarded-ad state, initialization callback,
full-screen watching state, manual app-open switch/show operations, interval and
ignore-count controls, and custom inline-banner targeting through
`IAds.RequestOptions`.

Failed loads are retried after network recovery or when the app returns to the
foreground. Business callbacks are dispatched on the Android main thread.
