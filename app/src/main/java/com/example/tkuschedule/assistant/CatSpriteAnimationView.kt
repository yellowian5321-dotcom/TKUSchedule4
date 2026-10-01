package com.example.tkuschedule.assistant

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import com.example.tkuschedule.R

class CatSpriteAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(
    context,
    attrs,
    defStyleAttr
) {
    private val framePaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG or
                    Paint.DITHER_FLAG
        )

    private val sourceRect =
        Rect()

    private val destinationRect =
        RectF()

    private val bitmapCache =
        mutableMapOf<Int, Bitmap>()

    private var spriteSheet:
            Bitmap? = null

    private var spriteAnimator:
            ValueAnimator? = null

    private var currentDrawableRes =
        0

    private var currentFrame =
        0

    private var frameBounds:
            List<Rect> = emptyList()

    private var maximumContentWidth =
        1

    private var maximumContentHeight =
        1

    private var animationToken =
        0

    fun playIdle(
        restart: Boolean = false
    ) {
        playSpriteSheet(
            drawableRes =
                R.drawable
                    .cat_anim_idle_sheet,

            durationMillis =
                1_600L,

            loop = true,

            restart = restart
        )
    }

    fun playWave(
        onFinished: (() -> Unit)? = null
    ) {
        playSpriteSheet(
            drawableRes =
                R.drawable
                    .cat_anim_wave_sheet,

            durationMillis =
                2_000L,

            loop = false,

            restart = true,

            onFinished = onFinished
        )
    }

    fun playLieDown(
        onFinished: (() -> Unit)? = null
    ) {
        playSpriteSheet(
            drawableRes =
                R.drawable
                    .cat_anim_lie_down_sheet,

            durationMillis =
                1_300L,

            loop = false,

            restart = true,

            onFinished = onFinished
        )
    }

    fun playSleep() {
        playSpriteSheet(
            drawableRes =
                R.drawable
                    .cat_anim_sleep_sheet,

            durationMillis =
                2_000L,

            loop = true,

            restart = false
        )
    }

    fun isPlaying(
        @DrawableRes drawableRes: Int
    ): Boolean {
        return currentDrawableRes ==
                drawableRes &&
                spriteAnimator?.isRunning ==
                true
    }

    fun playSpriteSheet(
        @DrawableRes drawableRes: Int,
        durationMillis: Long,
        loop: Boolean,
        restart: Boolean = true,
        columns: Int = 4,
        rows: Int = 4,
        frameCount: Int =
            columns * rows,
        onFinished: (() -> Unit)? = null
    ) {
        /*
         * 相同動畫還在播放時，
         * 不要反覆建立 Animator。
         */
        if (
            !restart &&
            currentDrawableRes ==
            drawableRes &&
            spriteAnimator?.isRunning ==
            true
        ) {
            return
        }

        stopSpriteAnimation(
            clearSheet = false
        )

        val bitmap =
            bitmapCache[drawableRes]
                ?: BitmapFactory
                    .decodeResource(
                        resources,
                        drawableRes
                    )
                    ?.also {
                            decodedBitmap ->

                        bitmapCache[
                            drawableRes
                        ] = decodedBitmap
                    }
                ?: return

        val safeColumns =
            columns.coerceAtLeast(1)

        val safeRows =
            rows.coerceAtLeast(1)

        val safeFrameCount =
            frameCount.coerceIn(
                1,
                safeColumns *
                        safeRows
            )

        spriteSheet = bitmap

        currentDrawableRes =
            drawableRes

        frameBounds =
            getPrecalculatedBounds(
                drawableRes =
                    drawableRes
            )

        /*
         * 如果圖片不是已知的四張小貓動畫，
         * 就使用固定的 4×4 格子。
         */
        if (
            frameBounds.size !=
            safeFrameCount
        ) {
            frameBounds =
                buildCellBounds(
                    bitmap = bitmap,
                    columns =
                        safeColumns,
                    rows =
                        safeRows,
                    frameCount =
                        safeFrameCount
                )
        }

        maximumContentWidth =
            frameBounds
                .maxOfOrNull {
                    it.width()
                }
                ?: 1

        maximumContentHeight =
            frameBounds
                .maxOfOrNull {
                    it.height()
                }
                ?: 1

        currentFrame = 0

        invalidate()

        if (
            safeFrameCount <= 1 ||
            durationMillis <= 0L
        ) {
            onFinished?.invoke()
            return
        }

        val token =
            ++animationToken

        var cancelled =
            false

        spriteAnimator =
            ValueAnimator.ofInt(
                0,
                safeFrameCount - 1
            ).apply {
                duration =
                    durationMillis

                interpolator =
                    LinearInterpolator()

                repeatCount =
                    if (loop) {
                        ValueAnimator.INFINITE
                    } else {
                        0
                    }

                addUpdateListener {
                        animator ->

                    val nextFrame =
                        animator.animatedValue
                                as Int

                    if (
                        nextFrame !=
                        currentFrame
                    ) {
                        currentFrame =
                            nextFrame

                        postInvalidateOnAnimation()
                    }
                }

                addListener(
                    object :
                        AnimatorListenerAdapter() {

                        override fun onAnimationCancel(
                            animation: Animator
                        ) {
                            cancelled = true
                        }

                        override fun onAnimationEnd(
                            animation: Animator
                        ) {
                            if (
                                !cancelled &&
                                token ==
                                animationToken &&
                                !loop
                            ) {
                                currentFrame =
                                    safeFrameCount - 1

                                invalidate()

                                onFinished
                                    ?.invoke()
                            }
                        }
                    }
                )

                start()
            }
    }

    fun stopSpriteAnimation(
        clearSheet: Boolean = false
    ) {
        animationToken++

        spriteAnimator?.cancel()
        spriteAnimator = null

        if (clearSheet) {
            spriteSheet = null
            currentDrawableRes = 0
            currentFrame = 0
            frameBounds = emptyList()

            invalidate()
        }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        val bitmap =
            spriteSheet
                ?: return

        val bounds =
            frameBounds.getOrNull(
                currentFrame
            ) ?: return

        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        sourceRect.set(
            bounds
        )

        val availableWidth =
            (
                    width -
                            paddingLeft -
                            paddingRight
                    )
                .coerceAtLeast(1)
                .toFloat()

        val availableHeight =
            (
                    height -
                            paddingTop -
                            paddingBottom
                    )
                .coerceAtLeast(1)
                .toFloat()

        /*
         * 所有影格使用同一個縮放基準。
         */
        val scale =
            minOf(
                availableWidth /
                        maximumContentWidth
                            .toFloat(),

                availableHeight /
                        maximumContentHeight
                            .toFloat()
            )

        val displayedWidth =
            bounds.width() *
                    scale

        val displayedHeight =
            bounds.height() *
                    scale

        /*
         * 固定水平中央，避免撞牆。
         */
        val left =
            paddingLeft +
                    (
                            availableWidth -
                                    displayedWidth
                            ) / 2f

        /*
         * 固定腳底位置，避免上下跳動。
         */
        val bottom =
            height -
                    paddingBottom
                        .toFloat()

        destinationRect.set(
            left,
            bottom -
                    displayedHeight,
            left +
                    displayedWidth,
            bottom
        )

        canvas.drawBitmap(
            bitmap,
            sourceRect,
            destinationRect,
            framePaint
        )
    }

    /*
     * 直接使用預先計算好的影格範圍。
     *
     * App 執行時不再掃描幾百萬個像素，
     * 所以不會拖慢系所下拉選單。
     */
    private fun getPrecalculatedBounds(
        @DrawableRes drawableRes: Int
    ): List<Rect> {
        return when (drawableRes) {
            R.drawable
                .cat_anim_idle_sheet -> {
                listOf(
                    Rect(36, 12, 167, 186),
                    Rect(215, 12, 341, 186),
                    Rect(396, 11, 524, 186),
                    Rect(582, 12, 707, 186),
                    Rect(40, 202, 163, 378),
                    Rect(215, 202, 341, 378),
                    Rect(397, 202, 524, 378),
                    Rect(584, 202, 705, 378),
                    Rect(41, 394, 163, 569),
                    Rect(218, 394, 341, 569),
                    Rect(399, 394, 521, 569),
                    Rect(582, 394, 704, 569),
                    Rect(35, 585, 168, 761),
                    Rect(215, 585, 341, 761),
                    Rect(395, 585, 524, 761),
                    Rect(581, 585, 707, 761)
                )
            }

            R.drawable
                .cat_anim_wave_sheet -> {
                listOf(
                    Rect(27, 4, 165, 188),
                    Rect(220, 5, 356, 188),
                    Rect(413, 5, 547, 188),
                    Rect(603, 4, 741, 188),
                    Rect(28, 196, 163, 380),
                    Rect(221, 196, 355, 380),
                    Rect(413, 196, 546, 380),
                    Rect(605, 197, 738, 380),
                    Rect(30, 389, 161, 572),
                    Rect(221, 389, 354, 572),
                    Rect(413, 393, 547, 572),
                    Rect(605, 395, 739, 572),
                    Rect(29, 586, 163, 764),
                    Rect(221, 590, 354, 764),
                    Rect(413, 590, 547, 764),
                    Rect(605, 590, 739, 764)
                )
            }

            R.drawable
                .cat_anim_lie_down_sheet -> {
                listOf(
                    Rect(24, 4, 167, 188),
                    Rect(207, 11, 368, 188),
                    Rect(399, 33, 560, 188),
                    Rect(593, 50, 750, 188),
                    Rect(10, 240, 182, 380),
                    Rect(200, 245, 376, 380),
                    Rect(394, 247, 565, 380),
                    Rect(589, 260, 754, 380),
                    Rect(7, 435, 185, 572),
                    Rect(201, 437, 375, 572),
                    Rect(398, 442, 562, 572),
                    Rect(592, 444, 751, 572),
                    Rect(15, 631, 176, 764),
                    Rect(208, 634, 368, 764),
                    Rect(396, 633, 564, 764),
                    Rect(589, 626, 755, 764)
                )
            }

            R.drawable
                .cat_anim_sleep_sheet -> {
                listOf(
                    Rect(9, 13, 192, 170),
                    Rect(201, 18, 384, 170),
                    Rect(393, 18, 576, 171),
                    Rect(585, 17, 764, 171),
                    Rect(9, 205, 192, 363),
                    Rect(201, 199, 384, 363),
                    Rect(393, 210, 576, 363),
                    Rect(584, 207, 768, 362),
                    Rect(9, 398, 192, 554),
                    Rect(201, 398, 384, 554),
                    Rect(393, 401, 576, 554),
                    Rect(584, 400, 768, 555),
                    Rect(9, 589, 192, 746),
                    Rect(201, 593, 384, 747),
                    Rect(393, 593, 576, 747),
                    Rect(584, 591, 768, 745)
                )
            }

            else -> emptyList()
        }
    }

    private fun buildCellBounds(
        bitmap: Bitmap,
        columns: Int,
        rows: Int,
        frameCount: Int
    ): List<Rect> {
        val frameWidth =
            bitmap.width /
                    columns

        val frameHeight =
            bitmap.height /
                    rows

        return List(
            frameCount
        ) {
                frameIndex ->

            val column =
                frameIndex %
                        columns

            val row =
                frameIndex /
                        columns

            val left =
                column *
                        frameWidth

            val top =
                row *
                        frameHeight

            val right =
                if (
                    column ==
                    columns - 1
                ) {
                    bitmap.width
                } else {
                    left +
                            frameWidth
                }

            val bottom =
                if (
                    row ==
                    rows - 1
                ) {
                    bitmap.height
                } else {
                    top +
                            frameHeight
                }

            Rect(
                left,
                top,
                right,
                bottom
            )
        }
    }

    override fun onDetachedFromWindow() {
        stopSpriteAnimation(
            clearSheet = true
        )

        bitmapCache.values.forEach {
                bitmap ->

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        bitmapCache.clear()

        super.onDetachedFromWindow()
    }
}