package com.gazeinteraction.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ArcProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var arcColor: Int = 0xFF6BA87A.toInt()
        set(v) { field = v; arcPaint.color = v; invalidate() }
    var progress: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var strokeWidthDp: Float = 5f
        set(v) {
            field = v
            arcPaint.strokeWidth = v * context.resources.displayMetrics.density
            invalidate()
        }
    var arcAlpha: Float = 1f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * context.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress <= 0f) return

        val cx = width / 2f
        val cy = height / 2f
        val padding = arcPaint.strokeWidth / 2 + 2f
        val radius = minOf(width, height) / 2f - padding

        arcPaint.alpha = (arcAlpha * 255).toInt().coerceIn(0, 255)

        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, -90f, progress * 360f, false, arcPaint)

        if (progress >= 1f) {
            val glowPaint = Paint(arcPaint).apply {
                alpha = (arcAlpha * 100).toInt().coerceIn(0, 255)
                strokeWidth = arcPaint.strokeWidth * 2.5f
            }
            canvas.drawArc(oval, -90f, 360f, false, glowPaint)
            canvas.drawArc(oval, -90f, 360f, false, arcPaint)
        }
    }
}
