package com.robining.games.frame.managers

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.robining.games.frame.BuildConfig
import java.lang.ref.WeakReference

object UmpGdprManager {
    private val handler = Handler(Looper.getMainLooper())

    fun request(activity: Activity, callback: ((agreed: Boolean) -> Unit)? = null) {
        val ref = WeakReference(activity)
        if (!isValidReference(ref)) {
            return
        }

        val debugSettings = ConsentDebugSettings.Builder(activity)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .addTestDeviceHashedId("1EE13FC64E4080073CE7D50EE5BB0561")
            .build()

        val params = ConsentRequestParameters
            .Builder()
            .setConsentDebugSettings(if (BuildConfig.DEBUG) debugSettings else null)
            .setTagForUnderAgeOfConsent(false)
            .build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (!isValidReference(ref)) {
                    return@requestConsentInfoUpdate
                }

                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity
                ) { loadAndShowError ->
                    loadAndShowError?.let {
                        Log.w("UmpGdpr", "show consent form failed:${it.errorCode},${it.message}")
                    }
                    handler.post {
                        if (!isValidReference(ref)) {
                            return@post
                        }

                        callback?.invoke(consentInformation.canRequestAds())
                    }
                }
            },
            { requestConsentError ->
                Log.w("UmpGdpr", "request consent form failed:${requestConsentError.errorCode},${requestConsentError.message}")
                handler.post {
                    if (!isValidReference(ref)) {
                        return@post
                    }

                    callback?.invoke(consentInformation.canRequestAds())
                }
            })
    }

    private fun isValidReference(ref: WeakReference<Activity>): Boolean {
        val activity = ref.get() ?: return false
        return !activity.isFinishing && !activity.isDestroyed
    }
}