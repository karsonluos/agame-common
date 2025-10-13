package com.robining.games.frame.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView

open class GameTextureViewV2 : TextureView, TextureView.SurfaceTextureListener {
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

    enum class RenderMode {
        RENDERMODE_WHEN_DIRTY,
        RENDERMODE_CONTINUOUSLY
    }

    var fps = 60
    var renderMode: RenderMode = RenderMode.RENDERMODE_CONTINUOUSLY
        set(value) {
            field = value
            requestRenderImmediate()
        }
    private var renderThread : RenderThread? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    private fun doFrame(){
        val canvas = lockCanvas() ?: return
        try {
            drawFrame(canvas)
        } catch (ex: Exception) {
            Log.d("GameSurfaceView", null, ex)
        } finally {
            unlockCanvasAndPost(canvas)
        }
    }

    protected open fun drawFrame(canvas: Canvas) {

    }

    protected fun requestRenderImmediate() {
        if (renderThread == null){
            return
        }
        if (renderMode == RenderMode.RENDERMODE_WHEN_DIRTY) {
            renderThread?.interrupt()
            renderThread = null
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("GameSurfaceView", "onSurfaceTextureAvailable")
        renderThread?.stopRenderSafety()
        renderThread = RenderThread()
        renderThread?.start()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surface.setDefaultBufferSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d("GameSurfaceView", "onSurfaceTextureDestroyed")
        renderThread?.stopRenderSafety()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    private inner class RenderThread : Thread() {
        private var destoried = false
        override fun run() {
            while (isAlive && !destoried){
                val start = SystemClock.elapsedRealtime()
                doFrame()
                val cost = SystemClock.elapsedRealtime() - start
                val targetInterval = 1000L / fps
                val sleepTime = 0L.coerceAtLeast(targetInterval - cost)
                if (sleepTime > 0){
                    try {
                        sleep(sleepTime)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        fun stopRenderSafety(){
            destoried = true
            interrupt()
        }
    }
}