package com.robining.minigames.base

import com.robining.minigames.base.interfaces.ILayoutContext
import com.robining.minigames.base.interfaces.ILevelBoard
import com.robining.minigames.base.interfaces.ISavable
import org.json.JSONObject

abstract class SimpleLevelBoard<L : ISavable, LAYOUT_CONTEXT : ILayoutContext> : ILevelBoard<L> {
    lateinit var level: L
    protected var inited = false
    var layoutContext: LAYOUT_CONTEXT? = null
        private set
    override var levelStatusCallback: ILevelBoard.LevelStatusCallback? = null
    private var layouted = false

    override fun init(level: L) {
        assert(!inited)
        this.inited = true
        this.layouted = false
        this.level = level
    }

    override fun layout(canvasWidth: Float, canvasHeight: Float) {
        assert(inited)
        val currentLayoutContext = this.layoutContext
        if (currentLayoutContext != null && currentLayoutContext.canvasWidth == canvasWidth && currentLayoutContext.canvasHeight == canvasHeight) {
            return
        }
        val layoutContext = onLayout(canvasWidth, canvasHeight)
        this.layoutContext = layoutContext
        layoutWithContext(layoutContext)
        this.layouted = true
    }

    override fun loadSnapshot(snapshot: JSONObject) {

    }

    override fun snapshot(snapshot: JSONObject) {
    }

    abstract fun layoutWithContext(layoutContext: LAYOUT_CONTEXT)

    abstract fun onLayout(canvasWidth: Float, canvasHeight: Float): LAYOUT_CONTEXT

    override fun isReady(): Boolean {
        return inited && layouted
    }
}