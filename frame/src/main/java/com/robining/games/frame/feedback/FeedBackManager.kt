package com.robining.games.frame.feedback

import androidx.lifecycle.MutableLiveData
import com.robining.games.frame.common.GameContext
import com.tencent.mmkv.MMKV

object FeedBackManager {
    private const val KEY_BGM_VOLUME = "com.robining.games.frame.feedback.BGMVolume"
    private const val KEY_SOUND_VOLUME = "com.robining.games.frame.feedback.SoundVolume"
    private const val KEY_VIBRATE = "com.robining.games.frame.feedback.Vibrate"
    var defaultVibrateEnable = true

    val bgmVolumeLiveData by lazy {
        val liveData = MutableLiveData<Float>()
        liveData.postValue(bgmVolume)
        liveData
    }
    private val mmkv: MMKV
        get() {
            return GameContext.mmkv
        }
    var bgmVolume: Float
        get() {
            return mmkv.decodeFloat(KEY_BGM_VOLUME, 0.5f)
        }
        set(value) {
            mmkv.encode(KEY_BGM_VOLUME, value)
            bgmVolumeLiveData.postValue(value)
        }
    var soundVolume: Float
        get() {
            return mmkv.decodeFloat(KEY_SOUND_VOLUME, 0.5f)
        }
        set(value) {
            mmkv.encode(KEY_SOUND_VOLUME, value)
        }

    var vibrateEnable: Boolean
        get() {
            return mmkv.decodeBool(KEY_VIBRATE, defaultVibrateEnable)
        }
        set(value) {
            mmkv.encode(KEY_VIBRATE, value)
        }
}