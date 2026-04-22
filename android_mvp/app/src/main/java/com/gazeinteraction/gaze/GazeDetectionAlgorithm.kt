package com.gazeinteraction.gaze

import android.content.Context
import android.util.Log
import com.gazeinteraction.mediapipe.FaceLandmarkerHelper
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

/**
 * 视线检测算法 - 双屏模式
 *
 * 每台设备只判断：用户是否在注视本屏幕。
 * 判断依据：瞳孔是否在眼眶中央（正视前方）+ 眼睛是否睁开。
 *
 * 不再区分"左/右"，由设备角色（是/否）决定触发哪个选项。
 */
class GazeDetectionAlgorithm(private val context: Context) {

    companion object {
        private const val TAG = "GazeDetectionAlgorithm"
        private const val PREFS_NAME = "gaze_calibration"
        private const val KEY_CENTER_BASELINE = "center_baseline"

        // 虹膜关键点索引（MediaPipe Face Landmarker V2 标准）
        private const val LEFT_PUPIL = 468
        private const val RIGHT_PUPIL = 473

        // 眼部轮廓关键点
        private val LEFT_EYE_CORNERS = intArrayOf(33, 133)
        private val RIGHT_EYE_CORNERS = intArrayOf(362, 263)
        private val LEFT_EYE_TOP_BOTTOM = intArrayOf(159, 145)
        private val RIGHT_EYE_TOP_BOTTOM = intArrayOf(386, 374)

        // 时间平滑窗口
        private const val HISTORY_SIZE = 8

        // 瞳孔居中阈值：|pupilRatio| 低于此值视为"正视"
        private const val PUPIL_CENTER_THRESHOLD = 0.08

        // 眼睛最低睁开度
        private const val EYE_OPEN_MIN = 0.15

        // 置信度阈值
        private const val CONFIDENCE_THRESHOLD = 0.55f
    }

    // ---------- 校准数据（用于个性化瞳孔居中基准） ----------
    private var centerBaseline: Double? = null
    private var isCalibrated = false

    init {
        // 从持久化存储加载校准数据
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getFloat(KEY_CENTER_BASELINE, Float.NaN)
        if (!saved.isNaN()) {
            centerBaseline = saved.toDouble()
            isCalibrated = true
            Log.i(TAG, "加载已保存的校准基准: %.3f".format(centerBaseline!!))
        }
    }

    // ---------- 运行时状态 ----------
    private val gazeHistory = ArrayDeque<Boolean>(HISTORY_SIZE)
    private var lastLookingAtScreen = false
    private var consecutiveFrames = 0
    private val requiredConsecutiveFrames = 2

    interface GazeListener {
        fun onGazeAtScreen(confidence: Float)
        fun onGazeAway()
    }

    private var gazeListener: GazeListener? = null

    fun setGazeListener(listener: GazeListener) {
        gazeListener = listener
    }

    // ==================== 校准接口 ====================

    fun isCalibrated(): Boolean = isCalibrated

    /** 设置正视时的瞳孔比例基准（校准后调用） */
    fun setCenterBaseline(pupilRatio: Double) {
        centerBaseline = pupilRatio
        isCalibrated = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_CENTER_BASELINE, pupilRatio.toFloat()).apply()
        Log.i(TAG, "校准正视基准: %.3f (已保存)".format(pupilRatio))
    }

    fun resetCalibration() {
        centerBaseline = null
        isCalibrated = false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_CENTER_BASELINE).apply()
        Log.i(TAG, "校准数据已重置")
    }

    /**
     * 提取校准指标（正视屏幕时调用）
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

            val faceLandmarks = result.faceLandmarks()[0]
            val (lookingAtScreen, confidence) = detectLookingAtScreen(faceLandmarks)
            handleResult(lookingAtScreen, confidence)

        } catch (e: Exception) {
            Log.e(TAG, "处理 MediaPipe 结果失败", e)
        }
    }

    /**
     * 核心检测：用户是否在注视本屏幕
     *
     * 判断逻辑：瞳孔居中 + 眼睛睁开 = 正视前方 = 在看这块屏幕
     */
    private fun detectLookingAtScreen(landmarks: List<NormalizedLandmark>): Pair<Boolean, Float> {
        // 1. 眼睛睁开度
        val leftEyeOpenness = calculateEyeOpenness(landmarks, LEFT_EYE_TOP_BOTTOM)
        val rightEyeOpenness = calculateEyeOpenness(landmarks, RIGHT_EYE_TOP_BOTTOM)
        val avgEyeOpenness = (leftEyeOpenness + rightEyeOpenness) / 2.0
        val eyesOpen = avgEyeOpenness > EYE_OPEN_MIN

        // 2. 瞳孔水平位置比例
        val leftPupilRatio = calculatePupilPositionRatio(landmarks, LEFT_PUPIL, LEFT_EYE_CORNERS)
        val rightPupilRatio = calculatePupilPositionRatio(landmarks, RIGHT_PUPIL, RIGHT_EYE_CORNERS)
        val avgPupilRatio = (leftPupilRatio + rightPupilRatio) / 2.0

        // 3. 瞳孔垂直偏移
        val leftPupilV = calculatePupilVerticalPosition(landmarks, LEFT_PUPIL, LEFT_EYE_TOP_BOTTOM)
        val rightPupilV = calculatePupilVerticalPosition(landmarks, RIGHT_PUPIL, RIGHT_EYE_TOP_BOTTOM)
        val avgPupilV = (leftPupilV + rightPupilV) / 2.0

        // 4. 判断瞳孔是否居中
        //    有校准时以基准为零点，无校准时以 0 为零点
        val offset = if (isCalibrated && centerBaseline != null) {
            avgPupilRatio - centerBaseline!!
        } else {
            avgPupilRatio
        }

        val horizontallyCentered = abs(offset) < PUPIL_CENTER_THRESHOLD
        val verticallyCentered = abs(avgPupilV) < 0.5

        // 5. 综合判断
        val lookingAtScreen = eyesOpen && horizontallyCentered && verticallyCentered

        // 6. 计算置信度
        var confidence = 0.5
        confidence += avgEyeOpenness * 0.25
        confidence += (1.0 - abs(offset)) * 0.15
        confidence += (1.0 - abs(avgPupilV)) * 0.1
        val eyeConsistency = 1.0 - abs(leftPupilRatio - rightPupilRatio)
        confidence += eyeConsistency * 0.1
        if (!eyesOpen) confidence *= 0.3
        confidence = confidence.coerceIn(0.0, 1.0)

        Log.d(TAG, "注视检测: pupilL=%.3f pupilR=%.3f avg=%.3f offset=%.3f v=%.2f eyeOpen=%.2f eyesOpen=%b hCenter=%b vCenter=%b -> looking=%b conf=%.2f".format(
            leftPupilRatio, rightPupilRatio, avgPupilRatio, offset, avgPupilV, avgEyeOpenness,
            eyesOpen, horizontallyCentered, verticallyCentered, lookingAtScreen, confidence))

        return lookingAtScreen to confidence.toFloat()
    }

    // ==================== 结果平滑 ====================

    private fun handleResult(lookingAtScreen: Boolean, confidence: Float) {
        gazeHistory.addLast(lookingAtScreen)
        if (gazeHistory.size > HISTORY_SIZE) gazeHistory.removeFirst()

        // 投票：多数帧认为在看屏幕才算
        val lookCount = gazeHistory.count { it }
        val looking = lookCount > HISTORY_SIZE / 2

        if (looking) {
            if (lastLookingAtScreen) {
                consecutiveFrames++
                if (consecutiveFrames >= requiredConsecutiveFrames) {
                    gazeListener?.onGazeAtScreen(confidence)
                }
            } else {
                lastLookingAtScreen = true
                consecutiveFrames = 1
            }
        } else {
            if (lastLookingAtScreen) {
                lastLookingAtScreen = false
                consecutiveFrames = 0
                gazeListener?.onGazeAway()
            }
        }
    }

    private fun handleNoFaceDetected() {
        gazeHistory.clear()
        if (lastLookingAtScreen) {
            lastLookingAtScreen = false
            consecutiveFrames = 0
            gazeListener?.onGazeAway()
        }
    }

    // ==================== 辅助计算 ====================

    /**
     * 瞳孔在眼宽方向上的相对位置比例。
     * 返回：约 -0.2 ~ +0.2，0 表示居中
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

        val ratio = (pupilToOuter - pupilToInner) / eyeWidth
        return ratio.coerceIn(-1.0, 1.0)
    }

    /**
     * 瞳孔垂直位置（相对于眼睑）
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
     * 眼睑睁开度（EAR 简化版），归一化到 0.0~1.0+
     */
    private fun calculateEyeOpenness(
        landmarks: List<NormalizedLandmark>,
        eyeLids: IntArray
    ): Double {
        val top = landmarks[eyeLids[0]]
        val bottom = landmarks[eyeLids[1]]
        val eyeHeight = abs(top.y() - bottom.y()).toDouble()

        val corners = if (eyeLids === LEFT_EYE_TOP_BOTTOM) LEFT_EYE_CORNERS else RIGHT_EYE_CORNERS
        val eyeWidth = abs(landmarks[corners[0]].x() - landmarks[corners[1]].x()).toDouble()

        if (eyeWidth < 0.001) return 0.0
        val ear = eyeHeight / eyeWidth
        return ((ear - 0.05) / 0.35).coerceIn(0.0, 1.5)
    }

    private fun distance2D(a: NormalizedLandmark, b: NormalizedLandmark): Double {
        val dx = (a.x() - b.x()).toDouble()
        val dy = (a.y() - b.y()).toDouble()
        return sqrt(dx * dx + dy * dy)
    }
}
