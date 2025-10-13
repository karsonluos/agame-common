package com.robining.games.frame.feedback

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RawRes
import com.robining.games.frame.startup.StartUpContext

object GameFeedBack {
    private val vibrator: Vibrator by lazy {
        StartUpContext.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun feedBack(@RawRes resId: Int? = null, allowVibrate: Boolean = true, volume: Float? = null) {
        if (allowVibrate && FeedBackManager.vibrateEnable) {
            //振动
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                    vibrator.vibrate(VibrationEffect.createOneShot(40, 10), attributes)
                } else {
                    vibrator.vibrate(40)
                }
            } catch (ignored: Exception) {
                //据日志有报错的现象
            }
        }
        resId?.let { SoundManager.play(it, volume) }
    }
}