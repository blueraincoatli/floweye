package com.gazeinteraction.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class GazeHaloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var haloColor: Int = 0xFF6BA87A.toInt()
        set(v) { field = v; updatePaints(); invalidate() }
    var textColor: Int = 0xFFF5F0E8.toInt()
        set(v) { field = v; invalidate() }
    var labelText: String = ""
        set(v) { field = v; invalidate() }
    var arcProgress: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    var gazePhase: Int = 0
        private set

    var onPerceptionStart: (() -> Unit)? = null
    var onGuidanceStart: (() -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private var currentRadius = 0f
    private var currentAlpha = 0.3f
    private var initialRadius = 0f
    private var isGazing = false
    private var phase2Triggered = false

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private fun updatePaints() {
        ringPaint.color = haloColor
        arcPaint.color = haloColor
    }

    private val handler = Handler(Looper.getMainLooper())
    private var perceptionAnim: ValueAnimator? = null
    private var guidanceAnim: ValueAnimator? = null
    private var reverseAnim: ValueAnimator? = null

    init {
        updatePaints()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initialRadius = minOf(w, h) * 0.38f
        if (currentRadius == 0f) {
            currentRadius = initialRadius
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        // 1. Background halo glow (RadialGradient)
        haloPaint.shader = RadialGradient(
            cx, cy, currentRadius * 1.5f,
            intArrayOf(
                adjustAlpha(haloColor, currentAlpha),
                adjustAlpha(haloColor, currentAlpha * 0.4f),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, currentRadius * 1.5f, haloPaint)

        // 2. Inner ring
        val ringRadius = currentRadius * 0.82f
        ringPaint.alpha = (currentAlpha * 160).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)

        // 3. Arc progress
        if (arcProgress > 0f && arcProgress < 1f) {
            arcPaint.alpha = (currentAlpha * 255).toInt().coerceIn(0, 255)
            val arcRect = RectF(
                cx - ringRadius, cy - ringRadius,
                cx + ringRadius, cy + ringRadius
            )
            canvas.drawArc(arcRect, -90f, arcProgress * 360f, false, arcPaint)
        }

        // 4. Completion pulse
        if (arcProgress >= 1f && isGazing) {
            arcPaint.alpha = 255
            val pulseExtra = 12f * density
            val arcRect = RectF(
                cx - ringRadius - pulseExtra, cy - ringRadius - pulseExtra,
                cx + ringRadius + pulseExtra, cy + ringRadius + pulseExtra
            )
            canvas.drawArc(arcRect, -90f, 360f, false, arcPaint)
        }

        // 5. Center label text
        if (labelText.isNotEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                textSize = minOf(width, height) * 0.1f
            }
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(labelText, cx, textY, textPaint)
        }
    }

    fun onGazeDetected() {
        if (isGazing) return
        isGazing = true
        phase2Triggered = false
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)

        gazePhase = 1
        onPerceptionStart?.invoke()

        // Phase 1: Perception (0-500ms) — brighten only, no contraction
        perceptionAnim = ValueAnimator.ofFloat(currentAlpha, 0.55f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        // After 500ms, enter Phase 2
        handler.postDelayed({
            if (isGazing && !phase2Triggered) {
                phase2Triggered = true
                gazePhase = 2
                onGuidanceStart?.invoke()
                startGuidanceAnimation()
            }
        }, 500)
    }

    private fun startGuidanceAnimation() {
        val contractTarget = minOf(width, height) * 0.10f
        guidanceAnim = ValueAnimator.ofFloat(currentRadius, contractTarget).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val frac = it.animatedFraction
                currentRadius = it.animatedValue as Float
                currentAlpha = 0.55f + frac * 0.45f
                invalidate()
            }
            start()
        }
    }

    fun onGazeLost() {
        isGazing = false
        phase2Triggered = false
        gazePhase = 0
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)

        // Smooth expand back (0.8s)
        reverseAnim = ValueAnimator.ofFloat(currentRadius, initialRadius).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentRadius = it.animatedValue as Float
                currentAlpha = 0.15f + (1f - it.animatedFraction) * 0.25f
                invalidate()
            }
            start()
        }
    }

    fun resetToIdle() {
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)
        isGazing = false
        phase2Triggered = false
        gazePhase = 0
        arcProgress = 0f
        currentRadius = initialRadius
        currentAlpha = 0.3f
        invalidate()
    }

    fun enterScanMode() {
        arcProgress = 0f
        currentAlpha = 0.3f
        currentRadius = initialRadius
        isGazing = false
        phase2Triggered = false
        gazePhase = 0
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)
        invalidate()
    }

    fun enterConfirmMode() {
        enterScanMode()
        currentAlpha = 0.4f
    }

    private fun cancelAnimations() {
        perceptionAnim?.cancel()
        guidanceAnim?.cancel()
        reverseAnim?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private fun adjustAlpha(color: Int, alpha: Float): Int {
            val a = (Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt()
            return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
