package com.robining.games.frame.utils.theme

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.StyleRes

object ThemeManager : Application.ActivityLifecycleCallbacks {
    @StyleRes
    var theme: Int? = null
        set(value) {
            val needUpdate = value != field
            field = value
            if (needUpdate) {
                for (activity in mActivities) {
                    activity.recreate()
                }
            }
        }
    private val mActivities = mutableListOf<Activity>()

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        mActivities.add(activity)
        theme?.let { activity.setTheme(it) }
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        mActivities.remove(activity)
    }
}