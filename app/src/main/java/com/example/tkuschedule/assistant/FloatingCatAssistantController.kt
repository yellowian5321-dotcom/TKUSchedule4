package com.example.tkuschedule.assistant

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

class FloatingCatAssistantController(
    private val activity: Activity,
    private val overlayContainer: FrameLayout,
    private val onOpenAssistant: () -> Unit
) {

    companion object {

        // 提醒對話框固定顯示 10 秒
        private const val MESSAGE_DURATION_MS = 10_000L

        // 放著不操作多久後趴下
        private const val LIE_DOWN_DELAY_MS = 8_000L

        // 趴下後多久睡覺
        private const val SLEEP_DELAY_MS = 5_000L

        // 靠邊動畫時間
        private const val SNAP_DURATION_MS = 220L

        private const val PREFS_NAME = "floating_cat_preferences"
        private const val PREF_SIDE = "cat_side"
        private const val PREF_Y_FRACTION = "cat_y_fraction"

        private const val SIDE_LEFT = "left"
        private const val SIDE_RIGHT = "right"
    }

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val preferences =
        activity.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val touchSlop =
        ViewConfiguration
            .get(activity)
            .scaledTouchSlop

    private val catSizePx = dp(56)
    private val edgeMarginPx = dp(8)
    private val bubbleMarginPx = dp(8)
    private val bubbleMaximumWidthPx = dp(230)

    private val catView =
        CatSpriteAnimationView(activity)

    private val messageView =
        TextView(activity)

    private var downRawX = 0f
    private var downRawY = 0f

    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    private var dragging = false
    private var destroyed = false
    private var paused = false
    private var firstPositionCompleted = false

    private val hideMessageRunnable =
        Runnable {
            hideReminder()
        }

    private val lieDownRunnable =
        Runnable {
            if (!destroyed && !paused && !dragging) {
                catView.playLieDown()
                scheduleSleep()
            }
        }

    private val sleepRunnable =
        Runnable {
            if (!destroyed && !paused && !dragging) {
                catView.playSleep()
            }
        }

    private val returnToIdleRunnable = Runnable {
        if (!destroyed && !paused && !dragging) {
            catView.playIdle()
            scheduleIdleActions()
        }
    }

    init {
        createMessageView()
        createCatView()
        placeCatAfterLayout()
    }

    private fun createMessageView() {
        messageView.apply {
            textSize = 14f
            setTextColor(Color.rgb(66, 47, 37))
            setTypeface(typeface, Typeface.BOLD)

            setPadding(
                dp(14),
                dp(10),
                dp(14),
                dp(10)
            )

            maxWidth = bubbleMaximumWidthPx
            gravity = Gravity.CENTER_VERTICAL

            elevation = dpFloat(8f)

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpFloat(18f)
                setColor(Color.rgb(255, 250, 235))
                setStroke(
                    dp(2),
                    Color.rgb(255, 179, 71)
                )
            }

            visibility = View.GONE
            alpha = 0f

            // 不攔截觸控，避免擋住底下課表
            isClickable = false
            isFocusable = false
        }

        val layoutParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

        overlayContainer.addView(
            messageView,
            layoutParams
        )
    }

    private fun createCatView() {
        catView.apply {
            isClickable = true
            isFocusable = true

            elevation = dpFloat(12f)

            playIdle()

            setOnTouchListener { _, event ->
                handleCatTouch(event)
            }
        }

        val layoutParams =
            FrameLayout.LayoutParams(
                catSizePx,
                catSizePx
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

        overlayContainer.addView(
            catView,
            layoutParams
        )
    }

    private fun placeCatAfterLayout() {
        overlayContainer.post {
            if (destroyed) {
                return@post
            }

            if (
                overlayContainer.width <= 0 ||
                overlayContainer.height <= 0
            ) {
                overlayContainer.post {
                    placeCatAfterLayout()
                }
                return@post
            }

            val savedSide =
                preferences.getString(
                    PREF_SIDE,
                    SIDE_RIGHT
                ) ?: SIDE_RIGHT

            val savedYFraction =
                preferences.getFloat(
                    PREF_Y_FRACTION,
                    0.55f
                ).coerceIn(0f, 1f)

            val maximumX = maximumCatX()
            val maximumY = maximumCatY()

            catView.x =
                if (savedSide == SIDE_LEFT) {
                    edgeMarginPx.toFloat()
                } else {
                    maximumX
                }

            catView.y =
                (maximumY * savedYFraction)
                    .coerceIn(
                        edgeMarginPx.toFloat(),
                        maximumY
                    )

            firstPositionCompleted = true

            updateMessagePosition()
            scheduleIdleActions()
        }
    }

    private fun handleCatTouch(
        event: MotionEvent
    ): Boolean {
        if (destroyed) {
            return false
        }

        val rootLocation = IntArray(2)
        overlayContainer.getLocationOnScreen(rootLocation)

        val localRawX =
            event.rawX - rootLocation[0]

        val localRawY =
            event.rawY - rootLocation[1]

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPositionAnimation()
                cancelIdleActions()

                dragging = false

                downRawX = event.rawX
                downRawY = event.rawY

                touchOffsetX =
                    localRawX - catView.x

                touchOffsetY =
                    localRawY - catView.y

                catView.parent
                    ?.requestDisallowInterceptTouchEvent(true)

                catView.playIdle()

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val movedX =
                    abs(event.rawX - downRawX)

                val movedY =
                    abs(event.rawY - downRawY)

                if (
                    movedX > touchSlop ||
                    movedY > touchSlop
                ) {
                    dragging = true
                }

                if (dragging) {
                    val wantedX =
                        localRawX - touchOffsetX

                    val wantedY =
                        localRawY - touchOffsetY

                    catView.x =
                        wantedX.coerceIn(
                            edgeMarginPx.toFloat(),
                            maximumCatX()
                        )

                    catView.y =
                        wantedY.coerceIn(
                            edgeMarginPx.toFloat(),
                            maximumCatY()
                        )

                    // 只移動原生 View，不更新 Compose 狀態
                    updateMessagePosition()
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                catView.parent
                    ?.requestDisallowInterceptTouchEvent(false)

                if (dragging) {
                    snapCatToNearestSide()
                } else {
                    handleCatClick()
                }

                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                catView.parent
                    ?.requestDisallowInterceptTouchEvent(false)

                if (dragging) {
                    snapCatToNearestSide()
                } else {
                    scheduleIdleActions()
                }

                dragging = false
                return true
            }
        }

        return false
    }

    private fun handleCatClick() {
        cancelIdleActions()

        catView.playWave()

        mainHandler.removeCallbacks(returnToIdleRunnable)
        mainHandler.postDelayed(returnToIdleRunnable, 1_600L)

        // 開啟 AI 助理
        onOpenAssistant()
    }

    private fun snapCatToNearestSide() {
        if (!firstPositionCompleted) {
            return
        }

        cancelPositionAnimation()

        val containerMiddle =
            overlayContainer.width / 2f

        val catMiddle =
            catView.x + catView.width / 2f

        val targetSide =
            if (catMiddle < containerMiddle) {
                SIDE_LEFT
            } else {
                SIDE_RIGHT
            }

        val targetX =
            if (targetSide == SIDE_LEFT) {
                edgeMarginPx.toFloat()
            } else {
                maximumCatX()
            }

        catView
            .animate()
            .x(targetX)
            .setDuration(SNAP_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener {
                // 原生動畫由系統 VSync 驅動，
                // 120Hz 手機會以裝置能提供的頻率更新
                updateMessagePosition()
            }
            .setListener(
                object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(
                        animation: Animator
                    ) {
                        catView.animate()
                            .setUpdateListener(null)
                            .setListener(null)

                        updateMessagePosition()
                        saveCatPosition(targetSide)
                        scheduleIdleActions()
                    }

                    override fun onAnimationCancel(
                        animation: Animator
                    ) {
                        catView.animate()
                            .setUpdateListener(null)
                            .setListener(null)
                    }
                }
            )
            .start()
    }

    private fun saveCatPosition(
        side: String
    ) {
        val maximumY = maximumCatY()

        val yFraction =
            if (maximumY > 0f) {
                (catView.y / maximumY)
                    .coerceIn(0f, 1f)
            } else {
                0.55f
            }

        preferences
            .edit()
            .putString(PREF_SIDE, side)
            .putFloat(PREF_Y_FRACTION, yFraction)
            .apply()
    }

    /**
     * 顯示貓咪提醒。
     *
     * 每次呼叫後固定顯示 10 秒，
     * 接著自動淡出。
     */
    fun showReminder(
        message: String
    ) {
        if (
            destroyed || paused ||
            message.isBlank()
        ) {
            return
        }

        mainHandler.removeCallbacks(
            hideMessageRunnable
        )

        messageView.animate().cancel()

        messageView.text = message.trim()
        messageView.visibility = View.VISIBLE
        messageView.alpha = 0f
        messageView.scaleX = 0.92f
        messageView.scaleY = 0.92f

        messageView.post {
            if (destroyed) {
                return@post
            }

            updateMessagePosition()

            messageView
                .animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .setListener(null)
                .start()
        }

        cancelIdleActions()
        catView.playWave()

        mainHandler.removeCallbacks(returnToIdleRunnable)
        mainHandler.postDelayed(returnToIdleRunnable, 1_600L)

        // 10 秒後自動消失
        mainHandler.postDelayed(
            hideMessageRunnable,
            MESSAGE_DURATION_MS
        )
    }

    fun hideReminder() {
        if (
            destroyed ||
            messageView.visibility != View.VISIBLE
        ) {
            return
        }

        mainHandler.removeCallbacks(
            hideMessageRunnable
        )

        messageView
            .animate()
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(180L)
            .setListener(
                object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(
                        animation: Animator
                    ) {
                        messageView.visibility = View.GONE
                        messageView.scaleX = 1f
                        messageView.scaleY = 1f

                        messageView.animate()
                            .setListener(null)
                    }
                }
            )
            .start()
    }

    private fun updateMessagePosition() {
        if (
            messageView.visibility != View.VISIBLE ||
            overlayContainer.width <= 0 ||
            overlayContainer.height <= 0
        ) {
            return
        }

        if (
            messageView.width <= 0 ||
            messageView.height <= 0
        ) {
            messageView.post {
                updateMessagePosition()
            }
            return
        }

        val catCenterX =
            catView.x + catView.width / 2f

        val showBubbleOnRight =
            catCenterX <
                    overlayContainer.width / 2f

        val wantedX =
            if (showBubbleOnRight) {
                catView.x +
                        catView.width +
                        bubbleMarginPx
            } else {
                catView.x -
                        messageView.width -
                        bubbleMarginPx
            }

        val maximumMessageX =
            max(
                edgeMarginPx.toFloat(),
                (
                        overlayContainer.width -
                                messageView.width -
                                edgeMarginPx
                        ).toFloat()
            )

        messageView.x =
            wantedX.coerceIn(
                edgeMarginPx.toFloat(),
                maximumMessageX
            )

        val wantedY =
            catView.y +
                    catView.height / 2f -
                    messageView.height / 2f

        val maximumMessageY =
            max(
                edgeMarginPx.toFloat(),
                (
                        overlayContainer.height -
                                messageView.height -
                                edgeMarginPx
                        ).toFloat()
            )

        messageView.y =
            wantedY.coerceIn(
                edgeMarginPx.toFloat(),
                maximumMessageY
            )
    }

    private fun scheduleIdleActions() {
        cancelIdleActions()

        if (
            destroyed || paused ||
            dragging
        ) {
            return
        }

        catView.playIdle()

        mainHandler.postDelayed(
            lieDownRunnable,
            LIE_DOWN_DELAY_MS
        )
    }

    private fun scheduleSleep() {
        mainHandler.removeCallbacks(
            sleepRunnable
        )

        mainHandler.postDelayed(
            sleepRunnable,
            SLEEP_DELAY_MS
        )
    }

    private fun cancelIdleActions() {
        mainHandler.removeCallbacks(
            lieDownRunnable
        )

        mainHandler.removeCallbacks(
            sleepRunnable
        )
    }

    private fun cancelPositionAnimation() {
        catView.animate().cancel()

        catView.animate()
            .setUpdateListener(null)
            .setListener(null)
    }

    private fun maximumCatX(): Float {
        return max(
            edgeMarginPx.toFloat(),
            (
                    overlayContainer.width -
                            catView.width -
                            edgeMarginPx
                    ).toFloat()
        )
    }

    private fun maximumCatY(): Float {
        return max(
            edgeMarginPx.toFloat(),
            (
                    overlayContainer.height -
                            catView.height -
                            edgeMarginPx
                    ).toFloat()
        )
    }

    fun pause() {
        if (destroyed || paused) return
        paused = true
        mainHandler.removeCallbacksAndMessages(null)
        cancelPositionAnimation()
        messageView.animate().cancel()
        messageView.visibility = View.GONE
        catView.stopSpriteAnimation()
    }

    fun resume() {
        if (destroyed) return
        paused = false
        if (firstPositionCompleted) scheduleIdleActions()
    }

    /**
     * 必須在 MainActivity.onDestroy() 呼叫。
     */
    fun destroy() {
        if (destroyed) {
            return
        }

        destroyed = true

        mainHandler.removeCallbacksAndMessages(null)

        catView.animate().cancel()
        messageView.animate().cancel()

        catView.setOnTouchListener(null)

        overlayContainer.removeView(messageView)
        overlayContainer.removeView(catView)
    }

    private fun dp(
        value: Int
    ): Int {
        return (
                value *
                        activity.resources
                            .displayMetrics
                            .density
                ).toInt()
    }

    private fun dpFloat(
        value: Float
    ): Float {
        return value *
                activity.resources
                    .displayMetrics
                    .density
    }
}