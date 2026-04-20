package com.gazeinteraction.gaze

import android.content.Context
import android.util.Log
import com.gazeinteraction.mediapipe.FaceLandmarkerHelper
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

/**
 * 视线检测算法 - 基于虹膜关键点的改进版（含一键校准）
 *
 * 核心改进：
 * 1. 使用虹膜关键点（468-477）直接计算瞳孔位置，替代不可靠的 Blendshape
 * 2. 计算眼睑睁开度（EAR），对半睁半闭眼睛进行自适应处理
 * 3. 瞳孔-眼角比例映射视线方向，结合头部姿态做加权融合
 * 4. 时间序列平滑（滑动窗口），减少单帧噪声
 * 5. 一键校准：为每位患者建立个性化的瞳孔位置基准
 */
class GazeDetectionAlgorithm(private val context: Context) {

    companion object {
        private const val TAG = "GazeDetectionAlgorithm"

        // 基础阈值
        private const val CONFIDENCE_THRESHOLD = 0.55f
        private const val PUPIL_POSITION_TO_ANGLE_SCALE = 35.0

        // 虹膜关键点索引（MediaPipe Face Landmarker V2 标准）
        private const val LEFT_PUPIL = 468
        private const val RIGHT_PUPIL = 473

        // 眼部轮廓关键点
        private val LEFT_EYE_CORNERS = intArrayOf(33, 133)
        private val RIGHT_EYE_CORNERS = intArrayOf(362, 263)
        private val LEFT_EYE_TOP_BOTTOM = intArrayOf(159, 145)
        private val RIGHT_EYE_TOP_BOTTOM = intArrayOf(386, 374)

        // 历史帧缓冲（用于时间平滑）
        private const val HISTORY_SIZE = 8
    }

    // ---------- 校准数据 ----------
    private var yesBaseline: Double? = null
    private var noBaseline: Double? = null
    private var isCalibrated = false

    // ---------- 运行时状态 ----------
    private val gazeHistory = ArrayDeque<Pair<String, Float>>(HISTORY_SIZE)
    private var lastGazeTarget = "none"
    private var consecutiveFrames = 0
    private val requiredConsecutiveFrames = 2

    interface GazeListener {
        fun onGazeDetected(target: String, confidence: Float)
        fun onGazeLost()
    }

    private var gazeListener: GazeListener? = null

    fun setGazeListener(listener: GazeListener) {
        gazeListener = listener
    }

    // ==================== 校准接口 ====================

    /** 是否已完成校准 */
    fun isCalibrated(): Boolean = isCalibrated

    /** 设置"是"的瞳孔比例基准（校准后调用） */
    fun setYesBaseline(pupilRatio: Double) {
        yesBaseline = pupilRatio
        checkCalibrationComplete()
        Log.i(TAG, "校准'是'基准: %.3f".format(pupilRatio))
    }

    /** 设置"否"的瞳孔比例基准（校准后调用） */
    fun setNoBaseline(pupilRatio: Double) {
        noBaseline = pupilRatio
        checkCalibrationComplete()
        Log.i(TAG, "校准'否'基准: %.3f".format(pupilRatio))
    }

    /** 重置校准数据 */
    fun resetCalibration() {
        yesBaseline = null
        noBaseline = null
        isCalibrated = false
        Log.i(TAG, "校准数据已重置")
    }

    private fun checkCalibrationComplete() {
        isCalibrated = (yesBaseline != null && noBaseline != null)
    }

    /**
     * 从原始 landmarks 提取校准所需的原始指标。
     * 在校准流程中由 MainActivity 调用，避免触发完整的检测逻辑。
     */
    fun extractCalibrationMetrics(landmarks: List<NormalizedLandmark>): CalibrationMetrics {
        val leftPupilRatio = calculatePupilPositionRatio(landmarks, LEFT_PUPIL, LEFT_EYE_CORNERS)
        val rightPupilRatio = calculatePupilPositionRatio(landmarks, RIGHT_PUPIL, RIGHT_EYE_CORNERS)
        val avgPupilRatio = (leftPupilRatio + rightPupilRatio) / 2.0

        val leftEyeOpenness = calculateEyeOpenness(landmarks, LEFT_EYE_TOP_BOTTOM)
        val rightEyeOpenness = calculateEyeOpenness(landmarks, RIGHT_EYE_TOP_BOTTOM)
        val avgEyeOpenness = (leftEyeOpenness + rightEyeOpenness) / 2.0

        return CalibrationMetrics(avgPupilRatio, avgEyeOpenness)
    }

    data class CalibrationMetrics(val pupilRatio: Double, val eyeOpenness: Double)

    // ==================== 核心检测 ====================

    fun processMediaPipeResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        try {
            val result = resultBundle.results

            if (result.faceLandmarks().isEmpty()) {
                handleNoFaceDetected()
                return
            }

            val faceLandmarks = result.faceLandmarks()[0].landmarkList()
            val gazeResult = detectGazeDirection(faceLandmarks)
            handleGazeResult(gazeResult)

        } catch (e: Exception) {
            Log.e(TAG, "处理 MediaPipe 结果失败", e)
        }
    }

    /**
     * 核心视线检测 - 基于虹膜关键点的改进算法
     */
    private fun detectGazeDirection(landmarks: List<NormalizedLandmark>): GazeResult {
        try {
            // 1. 检查眼睛睁开度（判断眼部数据是否可信）
            val leftEyeOpenness = calculateEyeOpenness(landmarks, LEFT_EYE_TOP_BOTTOM)
            val rightEyeOpenness = calculateEyeOpenness(landmarks, RIGHT_EYE_TOP_BOTTOM)
            val avgEyeOpenness = (leftEyeOpenness + rightEyeOpenness) / 2.0
            val eyeReliable = avgEyeOpenness > 0.15

            // 2. 基于虹膜关键点的瞳孔位置追踪（保留原始比例供校准判断）
            val leftPupilRatio = calculatePupilPositionRatio(landmarks, LEFT_PUPIL, LEFT_EYE_CORNERS)
            val rightPupilRatio = calculatePupilPositionRatio(landmarks, RIGHT_PUPIL, RIGHT_EYE_CORNERS)
            val avgPupilRatio = (leftPupilRatio + rightPupilRatio) / 2.0

            // 3. 瞳孔位置转换为视线角度
            val pupilGazeAngleH = avgPupilRatio * PUPIL_POSITION_TO_ANGLE_SCALE

            // 4. 垂直方向（瞳孔上下位置，用眼睑距离归一化）
            val leftPupilVertical = calculatePupilVerticalPosition(landmarks, LEFT_PUPIL, LEFT_EYE_TOP_BOTTOM)
            val rightPupilVertical = calculatePupilVerticalPosition(landmarks, RIGHT_PUPIL, RIGHT_EYE_TOP_BOTTOM)
            val pupilGazeAngleV = ((leftPupilVertical + rightPupilVertical) / 2.0) * 25.0

            // 5. 头部姿态辅助（从面部关键点估算）
            val headYaw = estimateHeadYaw(landmarks)

            // 6. 加权融合：瞳孔追踪为主（如果眼睛睁开），头部姿态为辅
            val eyeWeight = if (eyeReliable) 0.7 else 0.3
            val headWeight = 1.0 - eyeWeight
            val finalHorizontal = pupilGazeAngleH * eyeWeight + headYaw * headWeight
            val finalVertical = pupilGazeAngleV * eyeWeight

            // 7. 计算置信度
            val confidence = calculateConfidence(
                eyeReliable, avgEyeOpenness, landmarks, leftPupilRatio, rightPupilRatio
            )

            // 8. 判断目标区域（传入原始瞳孔比例，用于个性化校准阈值）
            val target = determineGazeTarget(finalHorizontal, finalVertical, eyeReliable, avgPupilRatio)

            return GazeResult(
                target,
                confidence.toFloat(),
                GazeAngle(finalHorizontal, finalVertical)
            )

        } catch (e: Exception) {
            Log.e(TAG, "视线检测失败", e)
            return GazeResult("none", 0.0f, GazeAngle(0.0, 0.0))
        }
    }

    /**
     * 计算瞳孔在眼宽方向上的相对位置比例。
     *
     * 对于左眼（33 外侧 - 133 内侧）：
     *   - 看向左侧（注视"是"）时，瞳孔偏向内眼角（133），ratio 为负
     *   - 看向右侧（注视"否"）时，瞳孔偏向外眼角（33），ratio 为正
     *
     * 对于右眼（362 外侧 - 263 内侧）：
     *   - 看向左侧时，瞳孔偏向内眼角（263），ratio 为负
     *   - 看向右侧时，瞳孔偏向外眼角（362），ratio 为正
     *
     * 返回：-1.0 ~ +1.0
     */
    private fun calculatePupilPositionRatio(
        landmarks: List<NormalizedLandmark>,
        pupilIndex: Int,
        eyeCorners: IntArray
    ): Double {
        val outerCorner = landmarks[eyeCorners[0]]
        val innerCorner = landmarks[eyeCorners[1]]
        val pupil = landmarks[pupilIndex]

        val eyeWidth = distance2D(outerCorner, innerCorner)
        if (eyeWidth < 0.001) return 0.0

        val pupilToOuter = distance2D(pupil, outerCorner)
        val pupilToInner = distance2D(pupil, innerCorner)

        // 归一化：-1（看向左侧/内眼角） ~ +1（看向右侧/外眼角）
        val ratio = (pupilToOuter - pupilToInner) / eyeWidth
        return ratio.coerceIn(-1.0, 1.0)
    }

    /**
     * 计算瞳孔垂直位置（相对于眼睑）
     */
    private fun calculatePupilVerticalPosition(
        landmarks: List<NormalizedLandmark>,
        pupilIndex: Int,
        eyeLids: IntArray
    ): Double {
        val topLid = landmarks[eyeLids[0]]
        val bottomLid = landmarks[eyeLids[1]]
        val pupil = landmarks[pupilIndex]

        val eyeHeight = abs(topLid.y() - bottomLid.y())
        if (eyeHeight < 0.001) return 0.0

        val pupilRelativeY = (pupil.y() - topLid.y()) / eyeHeight
        return (pupilRelativeY * 2.0 - 1.0).coerceIn(-1.0, 1.0)
    }

    /**
     * 计算眼睑睁开度（简化版 EAR）。
     * 返回值：0.0（完全闭合）~ 1.0+（大睁）
     */
    private fun calculateEyeOpenness(
        landmarks: List<NormalizedLandmark>,
        eyeLids: IntArray
    ): Double {
        val top = landmarks[eyeLids[0]]
        val bottom = landmarks[eyeLids[1]]
        val height = abs(top.y() - bottom.y())
        return (height.coerceIn(0.0, 0.3) / 0.3)
    }

    /**
     * 从面部关键点估算头部 Yaw（简化版，不依赖变换矩阵）。
     * 使用鼻尖到双眼外眼角的距离不对称性。
     */
    private fun estimateHeadYaw(landmarks: List<NormalizedLandmark>): Double {
        val noseTip = landmarks[1]
        val leftEyeOuter = landmarks[33]
        val rightEyeOuter = landmarks[362]

        val leftDist = abs(noseTip.x() - leftEyeOuter.x())
        val rightDist = abs(noseTip.x() - rightEyeOuter.x())
        val totalDist = leftDist + rightDist

        if (totalDist < 0.001) return 0.0

        val asymmetry = (rightDist - leftDist) / totalDist
        return asymmetry * 45.0
    }

    /**
     * 判断视线目标区域。
     *
     * 若已完成校准，使用患者个性化的瞳孔比例基准；
     * 否则使用默认的角度阈值。
     */
    private fun determineGazeTarget(
        horizontal: Double,
        vertical: Double,
        eyeReliable: Boolean,
        currentPupilRatio: Double
    ): String {
        if (abs(vertical) > 25.0) return "none"

        return if (isCalibrated && yesBaseline != null && noBaseline != null) {
            // 个性化阈值：以两基准中点为界，外加 15% 缓冲带避免 jitter
            val midpoint = (yesBaseline!! + noBaseline!!) / 2.0
            val span = abs(noBaseline!! - yesBaseline!!)
            val margin = span * 0.15

            when {
                currentPupilRatio < midpoint - margin -> "yes"
                currentPupilRatio > midpoint + margin -> "no"
                else -> "center"
            }
        } else {
            // 默认阈值（未校准时使用）
            val threshold = if (eyeReliable) 5.0 else 3.0
            when {
                horizontal < -threshold -> "yes"
                horizontal > threshold -> "no"
                else -> "center"
            }
        }
    }

    /**
     * 计算综合置信度
     */
    private fun calculateConfidence(
        eyeReliable: Boolean,
        eyeOpenness: Double,
        landmarks: List<NormalizedLandmark>,
        leftPupilRatio: Double,
        rightPupilRatio: Double
    ): Double {
        var confidence = 0.5

        // 1. 眼睛睁开度（半睁状态也能有一定置信度，但较低）
        confidence += eyeOpenness * 0.3

        // 2. 双眼一致性（左右眼瞳孔位置应该相近）
        val eyeConsistency = 1.0 - abs(leftPupilRatio - rightPupilRatio)
        confidence += eyeConsistency * 0.2

        // 3. 人脸检测质量
        if (landmarks.size >= 468) confidence += 0.1

        // 4. 对于半睁眼睛，降低置信度但不归零
        if (!eyeReliable) confidence *= 0.7

        return confidence.coerceIn(0.0, 1.0)
    }

    /**
     * 处理结果（加入时间序列平滑）
     */
    private fun handleGazeResult(result: GazeResult) {
        gazeHistory.addLast(result.target to result.confidence)
        if (gazeHistory.size > HISTORY_SIZE) gazeHistory.removeFirst()

        // 时间平滑：统计历史帧中最常见的结果
        val voteMap = mutableMapOf<String, Pair<Int, Float>>()
        for ((target, conf) in gazeHistory) {
            val (count, totalConf) = voteMap[target] ?: (0 to 0.0f)
            voteMap[target] = (count + 1) to (totalConf + conf)
        }

        val bestTarget = voteMap.maxByOrNull { it.value.first }?.key ?: "none"
        val bestCount = voteMap[bestTarget]?.first ?: 0
        val avgConfidence =
            (voteMap[bestTarget]?.second ?: 0.0f) / bestCount.coerceAtLeast(1)

        if (bestCount >= HISTORY_SIZE / 2 && avgConfidence >= CONFIDENCE_THRESHOLD) {
            if (bestTarget == lastGazeTarget) {
                consecutiveFrames++
                if (consecutiveFrames >= requiredConsecutiveFrames &&
                    bestTarget != "none" && bestTarget != "center"
                ) {
                    gazeListener?.onGazeDetected(bestTarget, avgConfidence)
                }
            } else {
                lastGazeTarget = bestTarget
                consecutiveFrames = 1
            }
        } else if (bestTarget == "none" || bestTarget == "center") {
            if (lastGazeTarget != "none") {
                lastGazeTarget = "none"
                consecutiveFrames = 0
                gazeListener?.onGazeLost()
            }
        }
    }

    private fun handleNoFaceDetected() {
        gazeHistory.clear()
        if (lastGazeTarget != "none") {
            lastGazeTarget = "none"
            consecutiveFrames = 0
            gazeListener?.onGazeLost()
        }
    }

    // 工具函数
    private fun distance2D(a: NormalizedLandmark, b: NormalizedLandmark): Double {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt(dx * dx + dy * dy)
    }

    // 数据类
    data class GazeResult(
        val target: String,
        val confidence: Float,
        val gazeAngle: GazeAngle
    )

    data class GazeAngle(
        val horizontal: Double,
        val vertical: Double
    )
}
