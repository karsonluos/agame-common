package com.robining.games.frame.ktx

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.TranslateAnimation
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import com.robining.games.frame.R
import com.robining.games.frame.utils.AppUtil

fun View.shakeTranslate(x: Float = 5f, y: Float = 0f) {
    val animation = TranslateAnimation(x, -x, y, -y)
    animation.interpolator = OvershootInterpolator()
    animation.duration = 100
    animation.repeatCount = 5
    animation.repeatMode = Animation.REVERSE
    startAnimation(animation)
}

fun View.shakeRotate(degree: Float = 15f) {
    val animation = RotateAnimation(-degree, degree, width / 2f, height / 2f)
    animation.interpolator = OvershootInterpolator()
    animation.duration = 50
    animation.repeatCount = 5
    animation.repeatMode = Animation.REVERSE
    startAnimation(animation)
}

@MainThread
fun View.startAnimator(animator: Animator, completeBefore: Boolean = true) {
    val oldAnimator = getTag(R.id.tag_view_animator) as Animator?
    if (oldAnimator != null) {
        if (completeBefore) {
            oldAnimator.end()
        } else {
            oldAnimator.cancel()
        }
    }
    setTag(R.id.tag_view_animator, animator)
    animator.setTarget(this)
    animator.start()
    val callback: (Animator) -> Unit = { it ->
        val temp = getTag(R.id.tag_view_animator) as Animator?
        if (temp == it) {
            setTag(R.id.tag_view_animator, null)
        }
    }
    animator.doOnEnd(callback)
    animator.doOnCancel(callback)
}

@MainThread
fun View.clearAnimator(completeBefore: Boolean = true) {
    val oldAnimator = getTag(R.id.tag_view_animator) as Animator?
    if (oldAnimator != null) {
        if (completeBefore) {
            oldAnimator.end()
        } else {
            oldAnimator.cancel()
        }
        setTag(R.id.tag_view_animator, null)
    }
}

fun TextView.animationChangeValue(newValue: String, durationMillis: Long = 300L) {
    val nowValue = text.toString()
    if (nowValue == newValue) {
        return
    }

    val animator = ValueAnimator.ofFloat(0f, 1f, 2f)
    animator.duration = durationMillis
    var called = false
    animator.addUpdateListener {
        val value = it.animatedValue as Float
        if (value <= 1f) {
            //向上
            translationY = -height.toFloat() * value
            alpha = 1f - value
        } else {
            translationY = height.toFloat() * (2f - value)
            alpha = value - 1f
        }
        if (value >= 1f && !called) {
            text = newValue
            called = true
        }
    }
    startAnimator(animator)
}

fun <T : View> T.fitSystemWindowPadding() {
    setPadding(
        paddingStart, paddingTop + AppUtil.getStatusBarHeight(), paddingEnd, paddingBottom
    )
}