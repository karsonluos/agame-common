package com.robining.minigames.base.grids

import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.view.ViewConfiguration
import androidx.core.math.MathUtils
import com.robining.games.frame.startup.StartUpContext
import com.robining.minigames.base.Direction
import com.robining.minigames.base.GridLayout
import com.robining.minigames.base.MoveDirection
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


abstract class AbstractSlideGridBoard<T : ISlideGridItem, L : ISlideGridLevel<T>> :
    AbstractGridBoard<L>() {
    private val blockMap = mutableMapOf<Int, T>()
    private val tempPositions = mutableMapOf<Int, RectF>()
    private var draggingStartBlock: T? = null
    private var touchingBeginPosition: PointF? = null
    private var draggingDirection: MoveDirection? = null
    val draggingContext = mutableMapOf<T, MoveLimitContext>()

    override fun init(level: L) {
        super.init(level)
        level.blocks.forEach {
            blockMap[it.id] = it
            fillGrid(it, true)
        }
    }

    override fun layoutWithContext(layoutContext: GridLayout.LayoutContext) {
        //更新每个滑块的位置
        level.blocks.forEach {
            updateBlockPosition(layoutContext, it)
        }
    }

    open fun getBlockById(id: Int): T {
        return blockMap[id]!!
    }


    fun getBlock(colIndex: Int, rowIndex: Int): T {
        val id = bordGrid[rowIndex][colIndex]
        return blockMap[id]!!
    }

    fun getPosition(block: T): RectF {
        return tempPositions[block.id]!!
    }

    fun fillGrid(block: T, fill: Boolean = true) {
        for (c in block.colIndex until (block.colIndex + block.cols)) {
            for (r in block.rowIndex until (block.rowIndex + block.rows)) {
                bordGrid[r][c] = if (fill) {
                    block.id
                } else {
                    -1
                }
            }
        }

        if (fill) {
            val layoutContext = this.layoutContext ?: return
            updateBlockPosition(layoutContext, block)
        }
    }

    private fun updateBlockPosition(layoutContext: GridLayout.LayoutContext, block: T) {
        tempPositions[block.id] = GridLayout.getPosition(
            layoutContext, block.colIndex, block.rowIndex, block.cols, block.rows
        )
    }

    fun move(
        block: T, targetRowIndex: Int, targetColIndex: Int
    ) {
        fillGrid(block, false)
        block.colIndex = targetColIndex
        block.rowIndex = targetRowIndex
        fillGrid(block, true)
    }

    fun getStepCount(diff: Float): Int {
        return (diff.absoluteValue / (layoutContext!!.gridSize + layoutContext!!.spacing)).roundToInt()
    }

    private fun getMoveAbleMultiStepCount(
        blocks: Set<Int>,
        direction: Direction,
        moveContexts: MutableMap<Int, Int>,
        allowMoveMultiBlock: Boolean = true,
    ): Int {
        if (blocks.isEmpty()) {
            return 0
        }
        val stepCount = blocks.map { id ->
            val block = blockMap[id]!!
            val stepCount = getMoveAbleSingleStepCount(block, direction)
            if (stepCount == 0 && allowMoveMultiBlock) {
                val nextBlocks = mutableSetOf<Int>()
                when (direction) {
                    Direction.LEFT, Direction.RIGHT -> {
                        val targetColIndex = if (direction == Direction.RIGHT) {
                            block.colIndex + block.cols
                        } else {
                            block.colIndex - 1
                        }
                        if (targetColIndex in 0 until level.boardCols) {
                            for (row in block.rowIndex until block.rowIndex + block.rows) {
                                val tid = bordGrid[row][targetColIndex]
                                if (tid != -1 && !nextBlocks.contains(tid)) {
                                    nextBlocks.add(tid)
                                }
                            }
                        }
                    }
                    Direction.TOP, Direction.BOTTOM -> {
                        val targetRowIndex = if (direction == Direction.BOTTOM) {
                            block.rowIndex + block.rows
                        } else {
                            block.rowIndex - 1
                        }
                        if (targetRowIndex in 0 until level.boardRows) {
                            for (col in block.colIndex until block.colIndex + block.cols) {
                                val tid = bordGrid[targetRowIndex][col]
                                if (tid != -1 && !nextBlocks.contains(tid)) {
                                    nextBlocks.add(tid)
                                }
                            }
                        }
                    }
                }

                try {
                    if (nextBlocks.isEmpty()) {
                        0
                    } else {
                        getMoveAbleMultiStepCount(
                            nextBlocks, direction, moveContexts, allowMoveMultiBlock
                        )
                    }
                } catch (ex: Throwable) {
                    throw IllegalStateException(
                        "move exception,direction:${direction.ordinal},${direction},${
                            blocks.joinToString(
                                "-"
                            )
                        }", ex
                    )
                }
            } else {
                stepCount
            }
        }.minOrNull() ?: 0
        blocks.forEach {
            val block = blockMap[it]!!
            moveContexts[block.id] = stepCount
        }
        return stepCount
    }

    private fun getMoveAbleSingleStepCount(block: T, direction: Direction): Int {
        val coordinate = Point(block.colIndex, block.rowIndex)
        var stepCount = 0
        when (direction) {
            Direction.LEFT -> {
                for (col in coordinate.x - 1 downTo 0) {
                    var allow = true
                    for (row in coordinate.y until (coordinate.y + block.rows)) {
                        val id = bordGrid[row][col]
                        if (id != -1 && id != block.id) {
                            allow = false
                            break
                        }
                    }

                    if (allow) {
                        stepCount++
                    } else {
                        break
                    }
                }
            }
            Direction.RIGHT -> {
                for (col in coordinate.x + block.cols until level.boardCols) {
                    var allow = true
                    for (row in coordinate.y until (coordinate.y + block.rows)) {
                        val id = bordGrid[row][col]
                        if (id != -1 && id != block.id) {
                            allow = false
                            break
                        }
                    }

                    if (allow) {
                        stepCount++
                    } else {
                        break
                    }
                }
            }

            Direction.TOP -> {
                for (row in coordinate.y - 1 downTo 0) {
                    var allow = true
                    for (col in coordinate.x until (coordinate.x + block.cols)) {
                        val id = bordGrid[row][col]
                        if (id != -1 && id != block.id) {
                            allow = false
                            break
                        }
                    }

                    if (allow) {
                        stepCount++
                    } else {
                        break
                    }
                }
            }

            Direction.BOTTOM -> {
                for (row in coordinate.y + block.rows until level.boardRows) {
                    var allow = true
                    for (col in coordinate.x until (coordinate.x + block.cols)) {
                        val id = bordGrid[row][col]
                        if (id != -1 && id != block.id) {
                            allow = false
                            break
                        }
                    }

                    if (allow) {
                        stepCount++
                    } else {
                        break
                    }
                }
            }
        }

        return stepCount
    }

    fun moveLimit(
        block: T,
        moveLimitContexts: MutableMap<T, MoveLimitContext>,
        moveDirection: MoveDirection,
        allowMoveMultiBlock: Boolean = true
    ) {
        moveLimitContexts.clear()
        val moveContexts = mutableMapOf<Int, Int>()
        if (moveDirection == MoveDirection.HORIZONTAL) {
            getMoveAbleMultiStepCount(
                setOf(block.id), Direction.LEFT, moveContexts, allowMoveMultiBlock
            )
            moveLimit(
                moveContexts, moveLimitContexts
            ) { context, offset -> context.moveLimitMinX -= offset }
            moveContexts.clear()
            getMoveAbleMultiStepCount(
                setOf(block.id), Direction.RIGHT, moveContexts, allowMoveMultiBlock
            )
            moveLimit(
                moveContexts, moveLimitContexts
            ) { context, offset -> context.moveLimitMaxX += offset }
            moveContexts.clear()
        } else {
            getMoveAbleMultiStepCount(
                setOf(block.id), Direction.TOP, moveContexts, allowMoveMultiBlock
            )
            moveLimit(
                moveContexts, moveLimitContexts
            ) { context, offset -> context.moveLimitMinY -= offset }
            moveContexts.clear()
            getMoveAbleMultiStepCount(
                setOf(block.id), Direction.BOTTOM, moveContexts, allowMoveMultiBlock
            )
            moveLimit(
                moveContexts, moveLimitContexts
            ) { context, offset -> context.moveLimitMaxY += offset }
        }
    }

    private fun moveLimit(
        moveContexts: MutableMap<Int, Int>,
        moveLimitContexts: MutableMap<T, MoveLimitContext>,
        op: (context: MoveLimitContext, offset: Float) -> Unit
    ) {
        moveContexts.forEach {
            if (it.value != 0) {
                val targetBlock = blockMap[it.key]!!
                var context = moveLimitContexts[targetBlock]
                if (context == null) {
                    val position = getPosition(targetBlock)
                    context = MoveLimitContext(RectF(position), 0f, 0f, 0f, 0f)
                }
                op.invoke(
                    context,
                    it.value * layoutContext!!.gridSize + it.value * layoutContext!!.spacing
                )
                moveLimitContexts[targetBlock] = context
            }
        }
    }

    override fun onTouchDown(x: Float, y: Float): Boolean {
        level.blocks.forEach { block ->
            val rectF = getPosition(block)
            if (rectF.contains(x, y)) {
                this.draggingStartBlock = block
                this.touchingBeginPosition = PointF(x, y)
                this.draggingDirection = null
                return true
            }
        }
        this.touchingBeginPosition = null
        return false
    }

    override fun onTouchMove(x: Float, y: Float): Boolean {
        val touchingBeginPosition = this.touchingBeginPosition
        val draggingStartBlock = this.draggingStartBlock
        if (draggingStartBlock != null && touchingBeginPosition != null) {
            val diffX = x - touchingBeginPosition.x
            val diffY = y - touchingBeginPosition.y
            //只滑动一个方向
            if (draggingDirection == null) {
                draggingDirection = ensureMoveDirection(draggingStartBlock, diffX, diffY)
                if (draggingDirection != null) {
                    moveLimit(
                        draggingStartBlock,
                        draggingContext,
                        draggingDirection!!,
                        allowMoveMultiBlock = allowMultiMove()
                    )
                } else {
                    return true
                }
            }

            draggingContext.forEach {
                val item = it.key
                val moveLimitContext = it.value
                val draggingStartPosition = getPosition(item)
                val itemDiffX = MathUtils.clamp(
                    diffX, moveLimitContext.moveLimitMinX, moveLimitContext.moveLimitMaxX
                )
                val itemDiffY = MathUtils.clamp(
                    diffY, moveLimitContext.moveLimitMinY, moveLimitContext.moveLimitMaxY
                )
                moveLimitContext.movingPosition.set(draggingStartPosition)
                moveLimitContext.movingPosition.offset(itemDiffX, itemDiffY)
            }
        }
        return true
    }

    abstract fun allowMultiMove(): Boolean

    override fun onTouchUpOrCancel(x: Float, y: Float): Boolean {
        var moved = false
        draggingContext.forEach {
            fillGrid(it.key, false)
        }

        draggingContext.forEach {
            val item = it.key
            val draggingPosition = it.value.movingPosition
            val draggingStartPosition = getPosition(item)
            val diffX = draggingPosition.left - draggingStartPosition.left
            val diffY = draggingPosition.top - draggingStartPosition.top
            val directionX = if (diffX < 0) -1 else 1
            val directionY = if (diffY < 0) -1 else 1
            val fromRowIndex = item.rowIndex
            val fromColIndex = item.colIndex
            val targetRowIndex = item.rowIndex + directionY * getStepCount(diffY)
            val targetColIndex = item.colIndex + directionX * getStepCount(diffX)
            val itemMoved =
                targetRowIndex != fromRowIndex || targetColIndex != fromColIndex
//                    board.move(item, targetRowIndex, targetColIndex) //多块移动的时候不能这样移动，因为清除的时候可能那个块还有值
            item.rowIndex = targetRowIndex
            item.colIndex = targetColIndex

            if (itemMoved) {
                moved = true
            }

            onBlockMoved(item, itemMoved)
        }

        draggingContext.forEach {
            fillGrid(it.key, true)
        }

        if (moved) {
            onMoved()
        }

        this.draggingStartBlock = null
        this.draggingContext.clear()
        this.touchingBeginPosition = null
        this.draggingDirection = null
        return true
    }

    fun onMoved() {
        levelStatusCallback?.onMoved()
        if (isPassLevel()) {
            onPassLevel()
            levelStatusCallback?.onGameOver(true)
        }
    }

    /**
     * @param realMoved true表示发生了有效的移动, false表示发生了拖拽行为，但是还没实现有效移动
     */
    protected open fun onBlockMoved(block: T, realMoved: Boolean) {}

    protected open fun onPassLevel() {}

    protected abstract fun isPassLevel(): Boolean

    private fun ensureMoveDirection(
        draggingStartBlock: T, diffX: Float, diffY: Float
    ): MoveDirection? {
        if (diffX.absoluteValue > diffY.absoluteValue) {
            if (diffX.absoluteValue < ViewConfiguration.get(StartUpContext.context).scaledTouchSlop) {
                return null
            }
            return MoveDirection.HORIZONTAL
        } else {
            if (diffY.absoluteValue < ViewConfiguration.get(StartUpContext.context).scaledTouchSlop) {
                return null
            }
            return MoveDirection.VERTICAL
        }
    }

    data class MoveLimitContext(
        var movingPosition: RectF,
        var moveLimitMinX: Float,
        var moveLimitMinY: Float,
        var moveLimitMaxX: Float,
        var moveLimitMaxY: Float
    )
}