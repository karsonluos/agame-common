package com.robining.games.frame.utils

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.annotation.MainThread
import java.lang.ref.WeakReference

object App : Application.ActivityLifecycleCallbacks {
    private var activeActivityCount = 0
    private var calledFirstActivityResumeCallback = false
    var mTopActivity: WeakReference<Activity> = WeakReference(null)
        private set
    private var listeners = mutableSetOf<AppLifeCycleListener>()
    private var untilResumeListeners = mutableMapOf<(Activity) -> Boolean, Boolean>()

    //    private var activities = mutableListOf<WeakReference<Activity>>()
    private var lastPauseComponentName: ComponentName? = null

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    private fun <T> Set<T>.safeForEach(action: (T) -> Unit) {
        val snapshot = mutableSetOf<T>()
        snapshot.addAll(this)
        snapshot.forEach {
            action.invoke(it)
        }
    }

    @MainThread
    fun registerListener(listener: AppLifeCycleListener) {
        listeners.add(listener)
    }

    @MainThread
    fun unregisterListener(listener: AppLifeCycleListener) {
        listeners.remove(listener)
    }

    @MainThread
    fun registerUntilListener(listener: (Activity) -> Boolean) {
        untilResumeListeners[listener] = false
    }

    @MainThread
    fun unregisterUntilListener(listener: (Activity) -> Boolean) {
        untilResumeListeners.remove(listener)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
//        activities.add(WeakReference(activity))
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is LastPauseLifecycleCallback){
            activity.onStartWith(lastPauseComponentName)
        }
        if (activeActivityCount == 0) {
            calledFirstActivityResumeCallback = false
            untilResumeListeners.forEach {
                untilResumeListeners[it.key] = false
            }
            onEnterApp()
        }
        activeActivityCount++
    }

    override fun onActivityResumed(activity: Activity) {
        mTopActivity = WeakReference(activity)
        if (activity is LastPauseLifecycleCallback) {
//            var activityCount = 0
//            var activeActivityCount = 0
//            val it = activities.listIterator()
//            while (it.hasNext()) {
//                val act = it.next().get()
//                if (act == null || act.isDestroyed) {
//                    it.remove()
//                    continue
//                }
//
//                activityCount++
//                if (!act.isFinishing) {
//                    activeActivityCount++
//                }
//            }
//
//            activity.onResume(activityCount, activeActivityCount, lastPauseComponentName)
            activity.onResumeWith(lastPauseComponentName)
        }

        if (!calledFirstActivityResumeCallback) {
            calledFirstActivityResumeCallback = true
            onFirstActivityResumedSinceEnterApp()
        }

        untilResumeListeners.forEach {
            if (!it.value) {
                if (it.key.invoke(activity)) {
                    untilResumeListeners[it.key] = true
                }
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        lastPauseComponentName = activity.componentName
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivityCount--
        if (activeActivityCount == 0) {
            onLeaveApp()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
//        val it = activities.listIterator()
//        while (it.hasNext()) {
//            val act = it.next().get()
//            if (act == null || act.isDestroyed || act == activity) {
//                it.remove()
//            }
//        }
    }

    @MainThread
    private fun onEnterApp() {
        listeners.safeForEach {
            it.onEnterApp()
        }
    }

    @MainThread
    private fun onLeaveApp() {
        listeners.safeForEach {
            it.onLeaveApp()
        }
    }

    @MainThread
    private fun onFirstActivityResumedSinceEnterApp() {
        listeners.safeForEach {
            it.onFirstActivityResumedSinceEnterApp()
        }
    }
}

interface AppLifeCycleListener {
    fun onEnterApp() {}
    fun onLeaveApp() {}
    fun onFirstActivityResumedSinceEnterApp() {}
}

interface LastPauseLifecycleCallback {
    fun onStartWith(lastPauseComponentName: ComponentName?) {}
    fun onResumeWith(lastPauseComponentName: ComponentName?) {}
}