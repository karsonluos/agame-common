package com.robining.games.frame.managers

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Point
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.MutableLiveData
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

object SurfaceSizeManager : Application.ActivityLifecycleCallbacks {
    val availableSize = MutableLiveData<Point>()

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        availableSize.observeForever {
            application.unregisterActivityLifecycleCallbacks(this)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
        activity.addContentView(SurfaceSizeTestView(activity, null), FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }
}

class SurfaceSizeTestView : GLSurfaceView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    init {
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10, config: EGLConfig?) {
                val maxTextureSize = IntArray(1)
                gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
                val maxViewportDims = IntArray(2)
                gl.glGetIntegerv(GL10.GL_MAX_VIEWPORT_DIMS, maxViewportDims, 0)
                val maxWidth = maxTextureSize[0].coerceAtMost(maxViewportDims[0])
                val maxHeight = maxTextureSize[0].coerceAtMost(maxViewportDims[1])
                SurfaceSizeManager.availableSize.postValue(Point(maxWidth, maxHeight))
                post { (parent as ViewGroup).removeView(this@SurfaceSizeTestView) }
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            }

            override fun onDrawFrame(gl: GL10?) {
            }
        })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(1,1,)
    }
}