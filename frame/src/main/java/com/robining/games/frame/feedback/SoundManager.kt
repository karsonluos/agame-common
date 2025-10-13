package com.robining.games.frame.feedback

import android.app.Activity
import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import androidx.annotation.RawRes
import com.robining.games.frame.startup.StartUpContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


object SoundManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val soundMap = mutableMapOf<Int, Int>()
    private val loadedId = mutableSetOf<Int>()
    private val mutex = Mutex()
    private val soundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(20)
            .build()
    }

    val mediaPlayer by lazy {
        val player = MediaPlayer()
        player.isLooping = true
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_GAME)
                .build()
        )
        player.setScreenOnWhilePlaying(false)
        val volume = FeedBackManager.bgmVolume
        player.setVolume(volume, volume)
        player
    }
    private var keepBgmClazz = emptyList<Class<*>>()

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        private var activePageCount = 0
        override fun onActivityCreated(p0: Activity, p1: Bundle?) {
        }

        override fun onActivityStarted(p0: Activity) {
            activePageCount++
        }

        override fun onActivityResumed(p0: Activity) {
            if (keepBgmClazz.isNotEmpty()) {
                try {
                    if (keepBgmClazz.contains(p0.javaClass)) {
                        mediaPlayer.start()
                    } else {
                        mediaPlayer.pause()
                    }
                } catch (e: Exception) {
                }
            }
        }

        override fun onActivityPaused(p0: Activity) {
        }

        override fun onActivityStopped(p0: Activity) {
            activePageCount--
            if (activePageCount <= 0) {
                try {
                    mediaPlayer.pause()
                } catch (e: Exception) {
                }
            }
        }

        override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
        }

        override fun onActivityDestroyed(p0: Activity) {
        }
    }
    private val mPendingSounds = mutableListOf<SoundItem>()

    @Synchronized
    fun init(sounds: Array<Int>) {
        soundPool.setOnLoadCompleteListener { _, sampleId, _ ->
            loadedId.add(sampleId)
            scope.launch {
                mutex.withLock {
                    val iterator = mPendingSounds.iterator()
                    while (iterator.hasNext()){
                        val item = iterator.next()
                        if (loadedId.contains(item.soundId)){
                            iterator.remove()
                            launch {
                                playBySoundId(item)
                            }
                        }else{
                            //如果资源没有加载完成 继续等待，保持顺序
                            break
                        }
                    }
                }
            }
        }

        sounds.forEach {
            soundMap[it] = soundPool.load(StartUpContext.context, it, 1)
        }

        FeedBackManager.bgmVolumeLiveData.observeForever {
            mediaPlayer.setVolume(it, it)
        }
    }

    fun setUpBgmKeep(vararg clazz: Class<*>) {
        this.keepBgmClazz = clazz.toList()
        (StartUpContext.context as Application).registerActivityLifecycleCallbacks(
            activityLifecycleCallbacks
        )
    }

    private fun playBySoundId(sound: SoundItem) {
        val volume = FeedBackManager.soundVolume
        soundPool.play(sound.soundId, sound.volume ?: volume, sound.volume ?: volume, 1, 0, 1.0f)
    }

    fun playMusic(@RawRes resId: Int, packageName: String = StartUpContext.context.packageName) {
        mediaPlayer.stop()
        val mediaPath: Uri = Uri.parse("android.resource://$packageName/$resId")
        mediaPlayer.setDataSource(StartUpContext.context, mediaPath)
        mediaPlayer.prepare()
        mediaPlayer.start()
    }

    @Synchronized
    fun play(@RawRes resId: Int, volume: Float? = null) {
        scope.launch {
            mutex.withLock {
                if (!soundMap.containsKey(resId)) {
                    soundMap[resId] = soundPool.load(StartUpContext.context, resId, 1)
                    //等待加载完成
                    mPendingSounds.add(SoundItem(soundMap[resId]!!, volume))
                } else if (!loadedId.contains(soundMap[resId]!!)) {
                    //等待加载完成
                    mPendingSounds.add(SoundItem(soundMap[resId]!!, volume))
                } else {
                    playBySoundId(SoundItem(soundMap[resId]!!, volume))
                }
            }
        }
    }

    data class SoundItem(val soundId: Int, val volume: Float? = null)
}