package com.robining.games.frame.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.TextureView

open class GameTextureView : TextureView, TextureView.SurfaceTextureListener {
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

    var fps = 30
    private var destoried = false
    private var mPendingRender = false
    var renderMode: RenderMode = RenderMode.RENDERMODE_CONTINUOUSLY
        set(value) {
            field = value
            requestRenderImmediate()
        }
    private val mFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destoried) {
                return
            }

            try {
                val canvas = lockCanvas() ?: return
                try {
                    drawFrame(canvas)
                } catch (ex: Exception) {
                    Log.d("GameSurfaceView", null, ex)
                } finally {
                    unlockCanvasAndPost(canvas)
                }

                if (renderMode == RenderMode.RENDERMODE_CONTINUOUSLY) {
                    Choreographer.getInstance().postFrameCallback(this)
                }else if (mPendingRender){
                    mPendingRender = false
                    Choreographer.getInstance().removeFrameCallback(this)
                }
            } catch (ex: Exception) {
                Log.d("GameSurfaceView", "surface maybe released", ex)
            }
        }
    }

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    protected open fun drawFrame(canvas: Canvas) {

    }

    protected fun requestRenderImmediate() {
        if (destoried){
            return
        }
        if (renderMode == RenderMode.RENDERMODE_WHEN_DIRTY) {
            mPendingRender = true
            Choreographer.getInstance().postFrameCallback(mFrameCallback)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("GameSurfaceView", "onSurfaceTextureAvailable")
        destoried = false
        //先调用一帧 避免出现黑屏
        mFrameCallback.doFrame(0)
        Choreographer.getInstance().postFrameCallback(mFrameCallback)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surface.setDefaultBufferSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d("GameSurfaceView", "onSurfaceTextureDestroyed")
        destoried = true
        Choreographer.getInstance().removeFrameCallback(mFrameCallback)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }
}