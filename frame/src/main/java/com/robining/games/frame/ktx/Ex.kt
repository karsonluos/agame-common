package com.robining.games.frame.ktx

import android.app.Activity
import android.os.SystemClock
import android.util.TypedValue
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.robining.games.frame.startup.StartUpContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

fun formatTimMS(timeInMillions: Long): String {
    val timeInSeconds = timeInMillions / 1000
    val minutes = timeInSeconds / 60
    val seconds = timeInSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun formatTimMSWithMs(timeInMillions: Long): String {
    val timeInSeconds = timeInMillions / 1000
    val minutes = timeInMillions / 60_000
    val seconds = (timeInMillions % 60_000) / 1000
    val leftMills = (timeInMillions % 60_000) % 1000
    return "%02d:%02d.%03d".format(minutes, seconds, leftMills)
}

fun dp2px(dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        StartUpContext.context.resources.displayMetrics
    ).toInt()
}

fun px2dp(px: Int): Float {
    val scale = StartUpContext.context.resources.displayMetrics.density
    return px / scale + 0.5f
}

suspend fun <T> atLeastTime(duration: Long, block: suspend () -> T): T {
    val start = SystemClock.elapsedRealtime()
    val result = block.invoke()
    val wait = duration - (SystemClock.elapsedRealtime() - start)
    if (wait > 0) {
        delay(wait)
    }
    return result
}

suspend fun requestReview(activity: Activity) {
    withContext(Dispatchers.Main) {
        val activityRef = WeakReference(activity)
        val reviewManager = ReviewManagerFactory.create(activity.applicationContext)
        val reviewInfo = reviewManager.requestReview()
        activityRef.get()?.let {
            reviewManager.launchReview(it, reviewInfo)
        }
    }
}