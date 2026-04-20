package com.gazeinteraction.debug

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * 人脸网格可视化叠加层（移植自 Flutter POC 的 FaceOverlayPainter）
 *
 * 在调试模式下绘制 MediaPipe 返回的 478 个人脸关键点，
 * 按面部区域分色显示，帮助直观判断检测准确性。
 *
 * 颜色分区：
 * - 眼睛区域: 黄色
 * - 虹膜/瞳孔: 青色（核心关键点 468-477，最大最醒目）
 * - 鼻子区域: 蓝色
 * - 嘴唇区域: 粉色
 * - 眉毛区域: 橙色
 * - 其他点: 灰色小圆点
 */
class FaceMeshOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var landmarks: List<NormalizedLandmark> = emptyList()

    // 缓存坐标数组，避免 onDraw 每帧分配
    private var cachedPoints = FloatArray(0)

    // 缓存边界框（在 updateLandmarks 时预计算）
    private var boundsMinX = 0f
    private var boundsMaxX = 0f
    private var boundsMinY = 0f
    private var boundsMaxY = 0f

    // 画笔定义
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }
    private val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4488FF")
        style = Paint.Style.FILL
    }
    private val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF69B4")
        style = Paint.Style.FILL
    }
    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA500")
        style = Paint.Style.FILL
    }
    private val defaultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        style = Paint.Style.FILL
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FF44")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    /**
     * 更新人脸关键点数据并触发重绘。
     * 在 UI 线程调用。
     */
    fun updateLandmarks(landmarks: List<NormalizedLandmark>) {
        this.landmarks = landmarks

        // 预分配/复用坐标缓存
        val needed = landmarks.size * 2
        if (cachedPoints.size < needed) {
            cachedPoints = FloatArray(needed)
        }

        // 预计算坐标映射和边界框
        boundsMinX = 1f
        boundsMaxX = 0f
        boundsMinY = 1f
        boundsMaxY = 0f
        for (i in landmarks.indices) {
            val lm = landmarks[i]
            val nx = 1.0f - lm.x()
            val ny = lm.y()
            cachedPoints[i * 2] = nx
            cachedPoints[i * 2 + 1] = ny
            if (nx < boundsMinX) boundsMinX = nx
            if (nx > boundsMaxX) boundsMaxX = nx
            if (ny < boundsMinY) boundsMinY = ny
            if (ny > boundsMaxY) boundsMaxY = ny
        }

        invalidate()
    }

    /** 清除画面 */
    fun clear() {
        landmarks = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (landmarks.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        // 绘制边界框（使用预计算的 bounds）
        canvas.drawRect(
            boundsMinX * viewW, boundsMinY * viewH,
            boundsMaxX * viewW, boundsMaxY * viewH,
            boxPaint
        )

        // 按区域绘制关键点
        for (i in landmarks.indices) {
            val x = cachedPoints[i * 2] * viewW
            val y = cachedPoints[i * 2 + 1] * viewH
            drawLandmark(canvas, i, x, y)
        }
    }

    /**
     * 根据关键点索引直接绘制到 canvas，避免创建 Pair 对象。
     */
    private fun drawLandmark(canvas: Canvas, index: Int, x: Float, y: Float) {
        when {
            index in 468..477 -> canvas.drawCircle(x, y, 4f, irisPaint)

            index in 33..42 || index in 133..154 || index in 159..168
                -> canvas.drawCircle(x, y, 2.5f, eyePaint)

            index in 263..272 || index in 362..381 || index in 385..394
                -> canvas.drawCircle(x, y, 2.5f, eyePaint)

            index in 1..32 || index in 97..132
                -> canvas.drawCircle(x, y, 2f, nosePaint)

            index in 61..96
                -> canvas.drawCircle(x, y, 2f, lipPaint)

            index in 296..334 || index in 336..346
                -> canvas.drawCircle(x, y, 2f, eyebrowPaint)

            else -> canvas.drawCircle(x, y, 1f, defaultPaint)
        }
    }
}
