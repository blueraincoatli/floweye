package com.floweye.mvp.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * 覆盖层视图，用于在摄像头预览上绘制检测结果
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        style = Paint.Style.FILL
    }

    private var faceLandmarkerResult: FaceLandmarkerResult? = null
    private var imageWidth = 0
    private var imageHeight = 0

    /**
     * 更新检测结果
     */
    fun updateResults(
        result: FaceLandmarkerResult?,
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.faceLandmarkerResult = result
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate() // 触发重绘
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val result = faceLandmarkerResult ?: return
        if (imageWidth == 0 || imageHeight == 0) return

        // 绘制人脸特征点
        result.faceLandmarks().forEach { landmarks ->
            landmarks.landmarkList().forEach { landmark ->
                val x = landmark.x() * width
                val y = landmark.y() * height
                canvas.drawCircle(x, y, 2f, paint)
            }
        }

        // 绘制眼部区域
        drawEyeRegions(canvas, result)
    }

    private fun drawEyeRegions(
        canvas: Canvas,
        result: FaceLandmarkerResult
    ) {
        result.faceLandmarks().forEach { landmarks ->
            val landmarkList = landmarks.landmarkList()
            
            // 绘制左眼轮廓
            drawEyeContour(canvas, landmarkList, LEFT_EYE_INDICES)
            
            // 绘制右眼轮廓
            drawEyeContour(canvas, landmarkList, RIGHT_EYE_INDICES)
        }
    }

    private fun drawEyeContour(
        canvas: Canvas,
        landmarks: List<com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark>,
        eyeIndices: IntArray
    ) {
        if (landmarks.size <= eyeIndices.maxOrNull() ?: 0) return

        val path = Path()
        eyeIndices.forEachIndexed { index, landmarkIndex ->
            val landmark = landmarks[landmarkIndex]
            val x = landmark.x() * width
            val y = landmark.y() * height
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        
        paint.color = Color.CYAN
        canvas.drawPath(path, paint)
        paint.color = Color.GREEN // 恢复默认颜色
    }

    companion object {
        // MediaPipe Face Mesh 眼部特征点索引
        private val LEFT_EYE_INDICES = intArrayOf(
            33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246
        )
        
        private val RIGHT_EYE_INDICES = intArrayOf(
            362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398
        )
    }
}