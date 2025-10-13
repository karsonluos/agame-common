package com.robining.games.frame.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView

abstract class GameSurfaceView : SurfaceView, SurfaceHolder.Callback{
    enum class RenderMode {
        RENDERMODE_WHEN_DIRTY,
        RENDERMODE_CONTINUOUSLY
    }

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
                val start = SystemClock.elapsedRealtime()
                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                try {
                    drawFrame(canvas)
                } catch (ex: Exception) {
                    Log.w("GameSurfaceView", null, ex)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }

                if (renderMode == RenderMode.RENDERMODE_CONTINUOUSLY) {
                    Choreographer.getInstance().postFrameCallback(this)
                }else if (mPendingRender){
                    mPendingRender = false
                    Choreographer.getInstance().removeFrameCallback(this)
                }
            } catch (ex: Exception) {
                Log.e("GameSurfaceView", "surface maybe released", ex)
            }
        }
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        destoried = false
//        setZOrderOnTop(true)
        //先调用一帧 避免出现黑屏
        mFrameCallback.doFrame(0)
        Choreographer.getInstance().postFrameCallback(mFrameCallback)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        destoried = true
        Choreographer.getInstance().removeFrameCallback(mFrameCallback)
    }

    protected open fun drawFrame(canvas: Canvas) {

    }

    protected fun requestRenderImmediate() {
        if (renderMode == RenderMode.RENDERMODE_WHEN_DIRTY) {
            mPendingRender = true
            Choreographer.getInstance().postFrameCallback(mFrameCallback)
        }
    }
}