package com.robining.minigames.base

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.lifecycle.MutableLiveData
import com.robining.games.frame.views.GameTextureViewV3
import com.robining.minigames.base.interfaces.ILevelBoard
import com.robining.minigames.base.interfaces.ILevelDrawer

open class MiniGameSurfaceView : GameTextureViewV3 {
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

    private var init = false
    private lateinit var board: ILevelBoard<*>
    private lateinit var drawer: ILevelDrawer<Canvas, ILevelBoard<*>>
    @Volatile
    private var fistFrameExecuted = false
    @Volatile
    private var firstFrameLiveDataPosted = false
    val firstFrameLiveData = MutableLiveData<Boolean>()
    var onInterceptTouchBegin: (() -> Boolean)? = null

    fun <BOARD : ILevelBoard<*>> init(board: BOARD, drawer: ILevelDrawer<Canvas, BOARD>) {
        this.board = board
        this.drawer = drawer as ILevelDrawer<Canvas, ILevelBoard<*>>
        this.init = true
    }

    override fun drawFrame(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (!init) {
            return
        }
        board.layout(canvas.width.toFloat(), canvas.height.toFloat())

        if (board.isReady() && drawer.draw(canvas, board)){
            if (!fistFrameExecuted) {
                fistFrameExecuted = true
            } else if (!firstFrameLiveDataPosted) {
                firstFrameLiveDataPosted = true
                //实际是第二帧才开始回调，保证已经绘制过一次
                firstFrameLiveData.postValue(true)
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!init) {
            return false
        }
        if (onInterceptTouchBegin?.invoke() == false) {
            return false
        }
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> board.onTouchDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> board.onTouchMove(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> board.onTouchUpOrCancel(
                event.x,
                event.y
            )
            else -> {
                super.dispatchTouchEvent(event)
            }
        }
    }
}