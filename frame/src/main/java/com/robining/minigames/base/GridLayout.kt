package com.robining.minigames.base

import android.graphics.RectF

object GridLayout {
    class LayoutContext(
        canvasWidth: Float,
        canvasHeight: Float,
        val paddingLeft: Float,
        val paddingRight: Float,
        val paddingTop: Float,
        val paddingBottom: Float,
        val spacing: Float,
        val width: Float = 0f,
        val height: Float = 0f,
        val gridSize: Float = 0f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val boardCanvasBounds: RectF = RectF(),
        val gridPositions: Array<Array<RectF>>,
        val boardCols: Int,
        val boardRows: Int,
    ) : SimpleLayoutContext(canvasWidth, canvasHeight)

    fun layout(
        canvasWidth: Float,
        canvasHeight: Float,
        boardCols: Int,
        boardRows: Int,
        padding: Float = 0f,
        spacing: Float = 0f,
        spacingMode: SpacingMode = SpacingMode.SPACING_MODE_FIXED,
        spacingMax: Float = Float.POSITIVE_INFINITY,
        spacingMin: Float = 0f,
        paddingLeft: Float = padding,
        paddingRight: Float = padding,
        paddingTop: Float = padding,
        paddingBottom: Float = padding,
        maxGridSize: Float = Float.POSITIVE_INFINITY
    ): LayoutContext {
        val freeWidth = canvasWidth - paddingLeft - paddingRight
        val freeHeight = canvasHeight - paddingTop - paddingBottom

        var gridSize = 0f
        var realSpacing = 0f

        when (spacingMode) {
            SpacingMode.SPACING_MODE_FIXED -> {
                realSpacing = spacing.coerceAtMost(spacingMax).coerceAtLeast(spacingMin)
                val maxGridWidth = (freeWidth - (boardCols - 1) * realSpacing) / boardCols
                val maxGridHeight = (freeHeight - (boardRows - 1) * realSpacing) / boardRows
                gridSize = maxGridWidth.coerceAtMost(maxGridHeight).coerceAtMost(maxGridSize)

            }
            SpacingMode.SPACING_MODE_RELATIVE_GRID -> {
                //先计算出正常spacing
                val partW = freeWidth / (boardCols + (boardCols - 1) * spacing)
                val partH = freeHeight / (boardRows + (boardRows - 1) * spacing)
                val part = partW.coerceAtMost(partH)
                //限制spacing的范围
                realSpacing = spacing * part
                realSpacing = realSpacing.coerceAtMost(spacingMax).coerceAtLeast(spacingMin)
                //根据新的spacing计算最大的gridSize
                val maxGridWidth = (freeWidth - (boardCols - 1) * realSpacing) / boardCols
                val maxGridHeight = (freeHeight - (boardRows - 1) * realSpacing) / boardRows
                gridSize = maxGridWidth.coerceAtMost(maxGridHeight)
                //限制gridSize的范围
                gridSize = gridSize.coerceAtMost(maxGridSize)
                //重新计算spacing 只会更小（所以空间是足够的）
                realSpacing = spacing * gridSize
                realSpacing = realSpacing.coerceAtMost(spacingMax).coerceAtLeast(spacingMin)
            }

            SpacingMode.SPACING_MODE_RELATIVE_PARENT -> {
                realSpacing = (canvasWidth * spacing).coerceAtMost(canvasHeight * spacing)
                realSpacing = realSpacing.coerceAtMost(spacingMax).coerceAtLeast(spacingMin)
                val maxGridWidth = (freeWidth - (boardCols - 1) * realSpacing) / boardCols
                val maxGridHeight = (freeHeight - (boardRows - 1) * realSpacing) / boardRows
                gridSize = maxGridWidth.coerceAtMost(maxGridHeight).coerceAtMost(maxGridSize)
            }
        }

        val boardCanvasWidth = boardCols * gridSize + (boardCols - 1) * realSpacing
        val boardCanvasHeight = boardRows * gridSize + (boardRows - 1) * realSpacing
        val offsetX = (freeWidth - boardCanvasWidth) / 2f + paddingLeft
        val offsetY = (freeHeight - boardCanvasHeight) / 2f + paddingTop
        val boardCanvasBounds =
            RectF(offsetX, offsetY, offsetX + boardCanvasWidth, offsetY + boardCanvasHeight)


        val gridPositions = Array(boardRows) { Array(boardCols) { RectF() } }
        var startY = offsetY
        for (row in gridPositions) {
            var startX = offsetX
            for (rect in row) {
                rect.set(
                    startX,
                    startY,
                    startX + gridSize,
                    startY + gridSize
                )
                startX += gridSize + spacing
            }
            startY += gridSize + spacing
        }

        return LayoutContext(
            canvasWidth,
            canvasHeight,
            paddingLeft,
            paddingRight,
            paddingTop,
            paddingBottom,
            realSpacing,
            boardCanvasWidth,
            boardCanvasHeight,
            gridSize,
            offsetX,
            offsetY,
            boardCanvasBounds,
            gridPositions,
            boardCols,
            boardRows
        )
    }

    fun getPosition(
        layoutContext: LayoutContext,
        colIndex: Int,
        rowIndex: Int,
        cols: Int,
        rows: Int
    ): RectF {
        val x =
            layoutContext.offsetX + colIndex * layoutContext.gridSize + colIndex * layoutContext.spacing
        val y =
            layoutContext.offsetY + rowIndex * layoutContext.gridSize + rowIndex * layoutContext.spacing
        val width = layoutContext.gridSize * cols + (cols - 1) * layoutContext.spacing
        val height = layoutContext.gridSize * rows + (rows - 1) * layoutContext.spacing
        return RectF(x, y, x + width, y + height)
    }
}