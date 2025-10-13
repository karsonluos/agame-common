package com.robining.games.frame.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import kotlinx.coroutines.*
import java.util.concurrent.Executors

open class GameTextureViewV3 : TextureView, TextureView.SurfaceTextureListener {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    private var scope: CoroutineScope? = null
    var paused: Boolean = false
    var fps = 60

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    private suspend fun doFrame() {
        try {
            if (paused) {
                return
            }
            val canvas: Canvas?
            withContext(Dispatchers.Main) {
                canvas = lockCanvas()
            }
            if (canvas == null || canvas.width == 0 || canvas.height == 0) {
                return
            }
            try {
                drawFrame(canvas)
            } catch (ex: Throwable) {
                Log.d("GameSurfaceView", null, ex)
            } finally {
                withContext(Dispatchers.Main) {
                    if (isActive) {
                        unlockCanvasAndPost(canvas)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d("GameSurfaceView", null, e)
        }
    }

    protected fun requestRenderImmediate() {
    }

    protected open fun drawFrame(canvas: Canvas) {

    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("GameSurfaceView", "onSurfaceTextureAvailable")
        scope = CoroutineScope(Dispatchers.IO)
        scope?.launch {
            while (isActive) {
                val start = SystemClock.elapsedRealtime()
                doFrame()
                val cost = SystemClock.elapsedRealtime() - start
                val targetInterval = 1000L / fps
                val sleepTime = 0L.coerceAtLeast(targetInterval - cost)
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("GameSurfaceView", "onSurfaceTextureSizeChanged")
        surface.setDefaultBufferSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d("GameSurfaceView", "onSurfaceTextureDestroyed")
        scope?.cancel("SurfaceTexture Destroyed")
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }
}