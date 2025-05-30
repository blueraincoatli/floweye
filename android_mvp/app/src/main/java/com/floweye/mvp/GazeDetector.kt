package com.floweye.mvp

import android.util.Log
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.*

/**
 * 视线检测器
 * 基于MediaPipe Face Landmarker结果判断用户视线方向
 */
class GazeDetector {

    companion object {
        private const val TAG = "GazeDetector"
        
        // 视线检测参数
        private const val GAZE_THRESHOLD_ANGLE = 25.0f // 视线判断阈值角度（度）
        private const val MIN_CONFIDENCE_THRESHOLD = 0.5f // 最小置信度阈值
        private const val MAX_HORIZONTAL_EYE_ANGLE = 20.0f // 眼球水平最大转动角度
        private const val MAX_VERTICAL_EYE_ANGLE = 15.0f // 眼球垂直最大转动角度
        
        // 屏幕区域定义（相机坐标系）
        private val YES_AREA_CENTER = Vector3D(-0.3f, 0.2f, 1.0f) // 左下区域
        private val NO_AREA_CENTER = Vector3D(0.3f, 0.2f, 1.0f)  // 右下区域
    }

    /**
     * 视线检测结果
     */
    data class GazeResult(
        val isLookingAtYes: Boolean,
        val isLookingAtNo: Boolean,
        val confidence: Float,
        val gazeTarget: GazeTarget,
        val debugInfo: String = ""
    )

    /**
     * 视线目标枚举
     */
    enum class GazeTarget {
        YES_AREA,
        NO_AREA,
        LOOKING_AWAY,
        DETECTION_UNSTABLE
    }

    /**
     * 3D向量类
     */
    data class Vector3D(val x: Float, val y: Float, val z: Float) {
        fun normalize(): Vector3D {
            val length = sqrt(x * x + y * y + z * z)
            return if (length > 0) Vector3D(x / length, y / length, z / length) else this
        }

        fun dot(other: Vector3D): Float = x * other.x + y * other.y + z * other.z

        operator fun minus(other: Vector3D): Vector3D = Vector3D(x - other.x, y - other.y, z - other.z)
    }

    /**
     * 检测视线方向
     */
    fun detectGaze(result: FaceLandmarkerResult): GazeResult {
        try {
            // 检查输入有效性
            if (result.faceLandmarks().isEmpty()) {
                return GazeResult(
                    false, false, 0.0f, GazeTarget.DETECTION_UNSTABLE,
                    "No face detected"
                )
            }

            val faceLandmarks = result.faceLandmarks()[0]
            val transformationMatrix = if (result.facialTransformationMatrixes().isNotEmpty()) {
                result.facialTransformationMatrixes()[0]
            } else null

            val blendshapes = if (result.faceBlendshapes().isNotEmpty()) {
                result.faceBlendshapes()[0]
            } else null

            // 1. 解析头部姿态
            val headPose = parseHeadPose(transformationMatrix)

            // 2. 计算眼球相对旋转（基于Blendshapes）
            val eyeRotation = calculateEyeRotation(blendshapes)

            // 3. 融合得到视线向量
            val gazeVector = combineHeadAndEyeGaze(headPose, eyeRotation)

            // 4. 计算与目标区域的角度
            val angleToYes = calculateAngleToTarget(gazeVector, YES_AREA_CENTER)
            val angleToNo = calculateAngleToTarget(gazeVector, NO_AREA_CENTER)

            // 5. 判断视线目标
            val gazeTarget = determineGazeTarget(angleToYes, angleToNo)

            // 6. 计算置信度
            val confidence = calculateConfidence(angleToYes, angleToNo, blendshapes)

            val debugInfo = "AngleToYes: %.1f°, AngleToNo: %.1f°, Confidence: %.2f".format(
                angleToYes, angleToNo, confidence
            )

            Log.d(TAG, debugInfo)

            return GazeResult(
                isLookingAtYes = gazeTarget == GazeTarget.YES_AREA,
                isLookingAtNo = gazeTarget == GazeTarget.NO_AREA,
                confidence = confidence,
                gazeTarget = gazeTarget,
                debugInfo = debugInfo
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in gaze detection", e)
            return GazeResult(
                false, false, 0.0f, GazeTarget.DETECTION_UNSTABLE,
                "Error: ${e.message}"
            )
        }
    }

    /**
     * 解析头部姿态
     */
    private fun parseHeadPose(transformationMatrix: com.google.mediapipe.framework.formats.Matrix?): Vector3D {
        // 简化实现：假设头部朝向前方
        // 在实际实现中，需要从4x4变换矩阵中提取旋转信息
        return Vector3D(0.0f, 0.0f, 1.0f) // 默认向前
    }

    /**
     * 计算眼球相对旋转
     */
    private fun calculateEyeRotation(blendshapes: com.google.mediapipe.tasks.vision.facelandmarker.FaceBlendshapes?): Vector3D {
        if (blendshapes == null) {
            return Vector3D(0.0f, 0.0f, 1.0f) // 默认向前
        }

        var eyeLookOutLeft = 0.0f
        var eyeLookInLeft = 0.0f
        var eyeLookUpLeft = 0.0f
        var eyeLookDownLeft = 0.0f
        var eyeLookOutRight = 0.0f
        var eyeLookInRight = 0.0f
        var eyeLookUpRight = 0.0f
        var eyeLookDownRight = 0.0f

        // 提取Blendshape值
        blendshapes.blendshapesList().forEach { blendshape ->
            when (blendshape.categoryName()) {
                "eyeLookOutLeft" -> eyeLookOutLeft = blendshape.score()
                "eyeLookInLeft" -> eyeLookInLeft = blendshape.score()
                "eyeLookUpLeft" -> eyeLookUpLeft = blendshape.score()
                "eyeLookDownLeft" -> eyeLookDownLeft = blendshape.score()
                "eyeLookOutRight" -> eyeLookOutRight = blendshape.score()
                "eyeLookInRight" -> eyeLookInRight = blendshape.score()
                "eyeLookUpRight" -> eyeLookUpRight = blendshape.score()
                "eyeLookDownRight" -> eyeLookDownRight = blendshape.score()
            }
        }

        // 计算双眼平均值
        val avgHorizontal = ((eyeLookOutLeft - eyeLookInLeft) + (eyeLookOutRight - eyeLookInRight)) / 2.0f
        val avgVertical = ((eyeLookDownLeft - eyeLookUpLeft) + (eyeLookDownRight - eyeLookUpRight)) / 2.0f

        // 映射到角度
        val horizontalAngle = avgHorizontal * MAX_HORIZONTAL_EYE_ANGLE
        val verticalAngle = avgVertical * MAX_VERTICAL_EYE_ANGLE

        // 转换为3D向量
        val radH = Math.toRadians(horizontalAngle.toDouble()).toFloat()
        val radV = Math.toRadians(verticalAngle.toDouble()).toFloat()

        return Vector3D(
            sin(radH),
            sin(radV),
            cos(radH) * cos(radV)
        ).normalize()
    }

    /**
     * 融合头部姿态和眼球旋转
     */
    private fun combineHeadAndEyeGaze(headPose: Vector3D, eyeRotation: Vector3D): Vector3D {
        // 简化实现：直接使用眼球旋转向量
        // 在完整实现中，需要进行矩阵变换
        return eyeRotation
    }

    /**
     * 计算视线向量与目标的角度
     */
    private fun calculateAngleToTarget(gazeVector: Vector3D, targetCenter: Vector3D): Float {
        val eyePosition = Vector3D(0.0f, 0.0f, 0.0f) // 简化：眼球在原点
        val vectorToTarget = (targetCenter - eyePosition).normalize()
        
        val dotProduct = gazeVector.dot(vectorToTarget)
        val angle = Math.toDegrees(acos(dotProduct.coerceIn(-1.0f, 1.0f)).toDouble()).toFloat()
        
        return angle
    }

    /**
     * 确定视线目标
     */
    private fun determineGazeTarget(angleToYes: Float, angleToNo: Float): GazeTarget {
        val minAngle = minOf(angleToYes, angleToNo)
        
        return when {
            minAngle > GAZE_THRESHOLD_ANGLE -> GazeTarget.LOOKING_AWAY
            angleToYes < angleToNo && angleToYes <= GAZE_THRESHOLD_ANGLE -> GazeTarget.YES_AREA
            angleToNo < angleToYes && angleToNo <= GAZE_THRESHOLD_ANGLE -> GazeTarget.NO_AREA
            else -> GazeTarget.LOOKING_AWAY
        }
    }

    /**
     * 计算置信度
     */
    private fun calculateConfidence(
        angleToYes: Float,
        angleToNo: Float,
        blendshapes: com.google.mediapipe.tasks.vision.facelandmarker.FaceBlendshapes?
    ): Float {
        val minAngle = minOf(angleToYes, angleToNo)
        
        // 基于角度的置信度
        val angleConfidence = if (minAngle <= GAZE_THRESHOLD_ANGLE) {
            1.0f - (minAngle / GAZE_THRESHOLD_ANGLE)
        } else {
            0.0f
        }
        
        // 基于Blendshape质量的置信度
        val blendshapeConfidence = if (blendshapes != null) 0.8f else 0.5f
        
        return (angleConfidence * 0.7f + blendshapeConfidence * 0.3f).coerceIn(0.0f, 1.0f)
    }
}