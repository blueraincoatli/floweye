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
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

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
    fun updateLandmarks(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.landmarks = landmarks
        this.imageWidth = imageWidth.coerceAtLeast(1)
        this.imageHeight = imageHeight.coerceAtLeast(1)
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

        // 计算边界框
        var minX = 1f
        var maxX = 0f
        var minY = 1f
        var maxY = 0f

        // 先映射所有点并找到边界
        val points = FloatArray(landmarks.size * 2)
        for (i in landmarks.indices) {
            val lm = landmarks[i]
            // 前置摄像头镜像翻转
            val nx = 1.0f - lm.x()
            val ny = lm.y()
            points[i * 2] = nx * viewW
            points[i * 2 + 1] = ny * viewH

            if (nx < minX) minX = nx
            if (nx > maxX) maxX = nx
            if (ny < minY) minY = ny
            if (ny > maxY) maxY = ny
        }

        // 绘制边界框
        canvas.drawRect(
            minX * viewW, minY * viewH,
            maxX * viewW, maxY * viewH,
            boxPaint
        )

        // 按区域绘制关键点
        for (i in landmarks.indices) {
            val x = points[i * 2]
            val y = points[i * 2 + 1]

            val (paint, radius) = getPaintForIndex(i)
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    /**
     * 根据关键点索引返回对应的画笔和半径。
     * 索引范围参照 MediaPipe Face Landmarker 标准定义。
     */
    private fun getPaintForIndex(index: Int): Pair<Paint, Float> {
        return when {
            // 虹膜/瞳孔（核心关键点，最醒目）
            index in 468..477 -> irisPaint to 4f

            // 右眼区域
            index in 33..42 || index in 133..154 || index in 159..168 -> eyePaint to 2.5f

            // 左眼区域
            index in 263..272 || index in 362..381 || index in 385..394 -> eyePaint to 2.5f

            // 鼻子区域
            index in 1..32 || index in 97..132 -> nosePaint to 2f

            // 嘴唇区域
            index in 61..96 -> lipPaint to 2f

            // 眉毛区域
            index in 296..334 || index in 336..346 -> eyebrowPaint to 2f

            // 其他点
            else -> defaultPaint to 1f
        }
    }
}
