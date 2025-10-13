package com.robining.games.frame.utils

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*

class TimeCounter(
    private val lifecycle: Lifecycle,
    private val intervalInMillionSeconds: Long = 1000,
    initCounterInMillionSeconds: Long = 0,
    autoStart: Boolean = false,
    private val elapseCallback: (elapsedTimeInMillionSeconds: Long) -> Unit
) :
    LifecycleEventObserver {
    private var elapsedTimeInMillionSeconds: Long = initCounterInMillionSeconds
    private var scope = CoroutineScope(Dispatchers.Unconfined)
    private var countDownJob: Job? = null

    init {
        elapseCallback.invoke(initCounterInMillionSeconds)
        if (autoStart){
            start()
        }
    }

    fun start(): TimeCounter {
        lifecycle.addObserver(this)
        return this
    }

    fun stop() {
        stopCountDown()
        lifecycle.removeObserver(this)
    }

    private fun stopCountDown() {
        countDownJob?.cancel()
        countDownJob = null
    }

    private fun startCountDown() {
        if (countDownJob?.isActive == true) {
            return
        }

        countDownJob = scope.launch {
            var startTime = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(intervalInMillionSeconds)
                val currentTime = SystemClock.elapsedRealtime()
                val diff = currentTime - startTime
                elapsedTimeInMillionSeconds += diff
                startTime = currentTime
                withContext(Dispatchers.Main) {
                    elapseCallback.invoke(elapsedTimeInMillionSeconds)
                }
            }
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event.targetState) {
            //走了onStart或onPause
            Lifecycle.State.STARTED -> {
                //尝试关闭一次计时器
                stopCountDown()
            }
            //走了onResume
            Lifecycle.State.RESUMED -> {
                startCountDown()
            }
            //走了onDestroy
            Lifecycle.State.DESTROYED -> {
                //自动停止
                stop()
            }
            else -> {
                //do nothing
            }
        }
    }

}