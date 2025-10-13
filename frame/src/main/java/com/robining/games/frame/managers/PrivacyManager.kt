package com.robining.games.frame.managers

import androidx.annotation.MainThread
import com.robining.games.frame.common.GameContext

object PrivacyManager {
    private const val KEY_FLAG = "com.robining.games.frame.PrivacyFlag"
    private var mPendingTask: Runnable? = null
    private var allowWithOutTask: Boolean = true

    @MainThread
    fun doAfterAgree(runnable: Runnable?): Boolean {
        return if (isAgree()) {
            runnable?.run()
            true
        } else {
            mPendingTask = runnable
            allowWithOutTask = runnable == null
            false
        }
    }

    fun isAgree() : Boolean{
        return GameContext.mmkv.decodeBool(KEY_FLAG, false)
    }

    @MainThread
    fun onAgree() {
        GameContext.mmkv.encode(KEY_FLAG, true)
        val pendingTask = this.mPendingTask
        if (pendingTask != null) {
            pendingTask.run()
            mPendingTask = null
            allowWithOutTask = true
        } else {
            if (!allowWithOutTask) {
                throw RuntimeException("loss task after agree of privacy policy")
            }
        }
    }
}