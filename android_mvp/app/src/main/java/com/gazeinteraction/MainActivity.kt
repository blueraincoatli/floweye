package com.gazeinteraction

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gazeinteraction.camera.CameraManager
import com.gazeinteraction.debug.FaceMeshOverlayView
import com.gazeinteraction.gaze.GazeDetectionAlgorithm
import com.gazeinteraction.mediapipe.FaceLandmarkerHelper
import com.gazeinteraction.mqtt.MqttClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * 主 Activity - 视线交互界面（含一键校准）
 *
 * 核心功能：
 * 1. 摄像头管理和预览
 * 2. MediaPipe 人脸检测
 * 3. 视线方向算法（支持个性化校准）
 * 4. MQTT 通信
 * 5. UI 状态更新
 *
 * 校准操作：
 * - 单击右上角设置/校准按钮：启动一键校准流程
 * - 长按右上角按钮：重置校准数据
 */
class MainActivity : AppCompatActivity(),
    FaceLandmarkerHelper.LandmarkerListener,
    GazeDetectionAlgorithm.GazeListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val MIN_CALIBRATION_SAMPLES = 10
    }

    // ---------- UI 组件 ----------
    private lateinit var yesButton: TextView
    private lateinit var noButton: TextView
    private lateinit var gazeStatus: TextView
    private lateinit var confidenceText: TextView
    private lateinit var deviceIdText: TextView
    private lateinit var cameraStatus: TextView
    private lateinit var mediapipeStatus: TextView
    private lateinit var mqttStatus: TextView
    private lateinit var calibrateButton: FloatingActionButton

    // ---------- 调试叠加层 ----------
    private var debugPanel: FrameLayout? = null
    private var debugSurfaceView: SurfaceView? = null
    private var faceMeshOverlay: FaceMeshOverlayView? = null
    private var isDebugMode = false

    // ---------- 核心组件 ----------
    private lateinit var cameraManager: CameraManager
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var gazeDetectionAlgorithm: GazeDetectionAlgorithm
    private lateinit var mqttClient: MqttClient

    // ---------- 状态变量 ----------
    private var currentGazeTarget: String = "none"
    private var currentConfidence: Float = 0.0f
    private var deviceId: String = ""

    // ---------- MQTT 发布节流 ----------
    private var lastPublishedTarget: String = "none"
    private var lastPublishedConfidence: Float = 0.0f
    private var lastPublishTimeMs: Long = 0
    private val PUBLISH_MIN_INTERVAL_MS = 500L  // 每秒最多发布 2 次

    // ---------- 校准状态机 ----------
    private enum class CalibrationState { IDLE, CALIBRATING_YES, CALIBRATING_NO, CALIBRATED }
    private var calibrationState = CalibrationState.IDLE
    private val calibrationSamplesYes = mutableListOf<Double>()
    private val calibrationSamplesNo = mutableListOf<Double>()

    // 权限请求启动器
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                initializeComponents()
            } else {
                showPermissionDeniedMessage()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        generateDeviceId()
        initializeViews()
        checkAndRequestCameraPermission()
    }

    private fun initializeViews() {
        yesButton = findViewById(R.id.yesButton)
        noButton = findViewById(R.id.noButton)
        gazeStatus = findViewById(R.id.gazeStatus)
        confidenceText = findViewById(R.id.confidenceText)
        deviceIdText = findViewById(R.id.deviceIdText)
        cameraStatus = findViewById(R.id.cameraStatus)
        mediapipeStatus = findViewById(R.id.mediapipeStatus)
        mqttStatus = findViewById(R.id.mqttStatus)
        calibrateButton = findViewById(R.id.settingsButton)

        // 单击：启动校准
        calibrateButton.setOnClickListener {
            startCalibration()
        }

        // 长按：重置校准
        calibrateButton.setOnLongClickListener {
            if (::gazeDetectionAlgorithm.isInitialized) {
                gazeDetectionAlgorithm.resetCalibration()
                calibrationState = CalibrationState.IDLE
                Toast.makeText(this, "校准数据已重置", Toast.LENGTH_SHORT).show()
            }
            true
        }

        deviceIdText.text = "设备ID: $deviceId"

        // 调试叠加层（双击设备ID切换显示/隐藏）
        debugPanel = findViewById(R.id.debugPanel)
        debugSurfaceView = findViewById(R.id.debugSurfaceView)
        faceMeshOverlay = findViewById(R.id.faceMeshOverlay)

        deviceIdText.setOnClickListener(object : View.OnClickListener {
            private var lastClickTime = 0L
            override fun onClick(v: View) {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 500) {
                    toggleDebugMode()
                }
                lastClickTime = now
            }
        })
    }

    private fun toggleDebugMode() {
        isDebugMode = !isDebugMode
        debugPanel?.visibility = if (isDebugMode) View.VISIBLE else View.GONE
        if (isDebugMode) {
            debugSurfaceView?.holder?.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    if (::cameraManager.isInitialized) {
                        cameraManager.setPreviewSurface(holder.surface)
                    }
                }
                override fun surfaceChanged(holder: android.view.SurfaceHolder, fmt: Int, w: Int, h: Int) {}
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    if (::cameraManager.isInitialized) {
                        cameraManager.setPreviewSurface(null)
                    }
                }
            })
        } else {
            if (::cameraManager.isInitialized) {
                cameraManager.setPreviewSurface(null)
            }
            faceMeshOverlay?.clear()
        }
        Toast.makeText(this, if (isDebugMode) "调试模式已开启" else "调试模式已关闭", Toast.LENGTH_SHORT).show()
    }

    private fun generateDeviceId() {
        deviceId = "GAZE_${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                initializeComponents()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
        updateCameraStatus("权限被拒绝", Color.RED)
    }

    private fun initializeComponents() {
        lifecycleScope.launch {
            try {
                // 1. 初始化 MediaPipe
                updateMediaPipeStatus("正在初始化MediaPipe...", Color.YELLOW)
                faceLandmarkerHelper = FaceLandmarkerHelper(
                    context = this@MainActivity,
                    landmarkerListener = this@MainActivity
                )
                updateMediaPipeStatus("MediaPipe已就绪", Color.GREEN)

                // 2. 初始化视线检测算法
                gazeDetectionAlgorithm = GazeDetectionAlgorithm(this@MainActivity)
                gazeDetectionAlgorithm.setGazeListener(this@MainActivity)

                // 3. 初始化摄像头
                updateCameraStatus("正在初始化摄像头...", Color.YELLOW)
                cameraManager = CameraManager(this@MainActivity)
                cameraManager.initialize { bitmap ->
                    faceLandmarkerHelper.detectLiveStream(bitmap, System.currentTimeMillis())
                }
                updateCameraStatus("摄像头已就绪", Color.GREEN)

                // 4. 初始化 MQTT 客户端
                updateMqttStatus("正在连接MQTT...", Color.YELLOW)
                mqttClient = MqttClient(this@MainActivity, deviceId)
                mqttClient.connect()
                updateMqttStatus("MQTT已连接", Color.GREEN)

            } catch (e: Exception) {
                Log.e(TAG, "初始化组件失败", e)
                Toast.makeText(this@MainActivity, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== MediaPipe 回调 ====================

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Log.e(TAG, "MediaPipe错误: $error (代码: $errorCode)")
            updateMediaPipeStatus("MediaPipe错误", Color.RED)
        }
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        // 调试叠加层：将关键点传递给 FaceMeshOverlayView
        if (isDebugMode && faceMeshOverlay != null) {
            try {
                if (resultBundle.results.faceLandmarks().isNotEmpty()) {
                    val landmarks = resultBundle.results.faceLandmarks()[0].landmarkList()
                    runOnUiThread {
                        faceMeshOverlay?.updateLandmarks(
                            landmarks,
                            resultBundle.inputImageWidth,
                            resultBundle.inputImageHeight
                        )
                    }
                } else {
                    runOnUiThread { faceMeshOverlay?.clear() }
                }
            } catch (e: Exception) {
                Log.d(TAG, "更新调试叠加层失败", e)
            }
        }

        when (calibrationState) {
            CalibrationState.CALIBRATING_YES,
            CalibrationState.CALIBRATING_NO -> {
                collectCalibrationSample(resultBundle)
            }
            else -> {
                gazeDetectionAlgorithm.processMediaPipeResults(resultBundle)
            }
        }
    }

    /**
     * 在校准过程中收集瞳孔比例样本。
     * 只保留眼睛睁开度足够的帧，避免闭眼噪声污染基准。
     */
    private fun collectCalibrationSample(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        try {
            if (resultBundle.results.faceLandmarks().isEmpty()) return
            val landmarks = resultBundle.results.faceLandmarks()[0].landmarkList()
            val metrics = gazeDetectionAlgorithm.extractCalibrationMetrics(landmarks)

            if (metrics.eyeOpenness > 0.1) {
                when (calibrationState) {
                    CalibrationState.CALIBRATING_YES -> calibrationSamplesYes.add(metrics.pupilRatio)
                    CalibrationState.CALIBRATING_NO -> calibrationSamplesNo.add(metrics.pupilRatio)
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "收集校准样本失败", e)
        }
    }

    // ==================== 视线检测回调 ====================

    override fun onGazeDetected(target: String, confidence: Float) {
        runOnUiThread {
            currentGazeTarget = target
            currentConfidence = confidence
            updateUI()
            publishGazeState()
        }
    }

    override fun onGazeLost() {
        runOnUiThread {
            currentGazeTarget = "none"
            currentConfidence = 0.0f
            updateUI()
            publishGazeState()
        }
    }

    // ==================== 校准流程 ====================

    private fun startCalibration() {
        if (!::gazeDetectionAlgorithm.isInitialized) {
            Toast.makeText(this, "系统尚未就绪，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        if (calibrationState == CalibrationState.CALIBRATING_YES ||
            calibrationState == CalibrationState.CALIBRATING_NO
        ) {
            Toast.makeText(this, "校准正在进行中", Toast.LENGTH_SHORT).show()
            return
        }

        calibrationSamplesYes.clear()
        calibrationSamplesNo.clear()

        lifecycleScope.launch {
            try {
                // ----- 阶段一：校准"是" -----
                calibrationState = CalibrationState.CALIBRATING_YES
                for (i in 3 downTo 1) {
                    updateGazeStatus("请注视\"是\"按钮... $i", isCalibrating = true)
                    highlightYesButton(true)
                    delay(1000)
                }

                if (calibrationSamplesYes.size >= MIN_CALIBRATION_SAMPLES) {
                    val avgYes = calibrationSamplesYes.average()
                    gazeDetectionAlgorithm.setYesBaseline(avgYes)
                    Log.i(TAG, "'是'校准完成，样本数: ${calibrationSamplesYes.size}, 均值: $avgYes")
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "\"是\"校准样本不足(${calibrationSamplesYes.size} < $MIN_CALIBRATION_SAMPLES)，请重试",
                        Toast.LENGTH_SHORT
                    ).show()
                    resetCalibrationUI()
                    return@launch
                }

                // 阶段间隔
                highlightYesButton(false)
                updateGazeStatus("很好，准备下一步...", isCalibrating = false)
                delay(800)

                // ----- 阶段二：校准"否" -----
                calibrationState = CalibrationState.CALIBRATING_NO
                for (i in 3 downTo 1) {
                    updateGazeStatus("请注视\"否\"按钮... $i", isCalibrating = true)
                    highlightNoButton(true)
                    delay(1000)
                }

                if (calibrationSamplesNo.size >= MIN_CALIBRATION_SAMPLES) {
                    val avgNo = calibrationSamplesNo.average()
                    gazeDetectionAlgorithm.setNoBaseline(avgNo)
                    Log.i(TAG, "'否'校准完成，样本数: ${calibrationSamplesNo.size}, 均值: $avgNo")
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "\"否\"校准样本不足(${calibrationSamplesNo.size} < $MIN_CALIBRATION_SAMPLES)，请重试",
                        Toast.LENGTH_SHORT
                    ).show()
                    resetCalibrationUI()
                    return@launch
                }

                // ----- 完成 -----
                highlightNoButton(false)
                calibrationState = CalibrationState.CALIBRATED
                updateGazeStatus("校准完成！", isCalibrating = false)
                Toast.makeText(this@MainActivity, "个性化校准已完成", Toast.LENGTH_SHORT).show()
                delay(1500)
                updateUI()  // 恢复常规状态显示

            } catch (e: Exception) {
                Log.e(TAG, "校准过程失败", e)
                resetCalibrationUI()
                Toast.makeText(this@MainActivity, "校准失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== UI 辅助方法 ====================

    private fun updateUI() {
        when (currentGazeTarget) {
            "yes" -> {
                yesButton.setBackgroundResource(R.drawable.btn_yes_gaze)
                noButton.setBackgroundResource(R.drawable.btn_no_normal)
                gazeStatus.text = "检测到注视: 是"
                gazeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
            }
            "no" -> {
                yesButton.setBackgroundResource(R.drawable.btn_yes_normal)
                noButton.setBackgroundResource(R.drawable.btn_no_gaze)
                gazeStatus.text = "检测到注视: 否"
                gazeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
            }
            else -> {
                yesButton.setBackgroundResource(R.drawable.btn_yes_normal)
                noButton.setBackgroundResource(R.drawable.btn_no_normal)
                val isCalibrated = if (::gazeDetectionAlgorithm.isInitialized) {
                    gazeDetectionAlgorithm.isCalibrated()
                } else false
                gazeStatus.text = if (isCalibrated) {
                    "未检测到注视（已校准）"
                } else {
                    getString(R.string.no_gaze_detected)
                }
                gazeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
            }
        }
        confidenceText.text = "置信度: ${(currentConfidence * 100).toInt()}%"
    }

    private fun updateGazeStatus(message: String, isCalibrating: Boolean) {
        runOnUiThread {
            gazeStatus.text = message
            gazeStatus.setTextColor(if (isCalibrating) Color.YELLOW else ContextCompat.getColor(this, R.color.text_on_primary))
        }
    }

    private fun highlightYesButton(active: Boolean) {
        runOnUiThread {
            yesButton.setBackgroundResource(
                if (active) R.drawable.btn_yes_gaze else R.drawable.btn_yes_normal
            )
        }
    }

    private fun highlightNoButton(active: Boolean) {
        runOnUiThread {
            noButton.setBackgroundResource(
                if (active) R.drawable.btn_no_gaze else R.drawable.btn_no_normal
            )
        }
    }

    private fun resetCalibrationUI() {
        calibrationState = CalibrationState.IDLE
        runOnUiThread {
            yesButton.setBackgroundResource(R.drawable.btn_yes_normal)
            noButton.setBackgroundResource(R.drawable.btn_no_normal)
            gazeStatus.text = getString(R.string.no_gaze_detected)
            gazeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
        }
    }

    // ==================== MQTT ====================

    private fun publishGazeState() {
        // 节流：仅在 target 变化或 confidence 显著变化且满足最小间隔时发布
        val now = System.currentTimeMillis()
        val targetChanged = currentGazeTarget != lastPublishedTarget
        val confidenceChanged = Math.abs(currentConfidence - lastPublishedConfidence) > 0.1f
        val intervalOk = now - lastPublishTimeMs >= PUBLISH_MIN_INTERVAL_MS

        if (!targetChanged && !confidenceChanged) return
        if (!intervalOk && !targetChanged) return

        lastPublishedTarget = currentGazeTarget
        lastPublishedConfidence = currentConfidence
        lastPublishTimeMs = now

        lifecycleScope.launch {
            try {
                val gazeData = mapOf(
                    "deviceId" to deviceId,
                    "timestamp" to System.currentTimeMillis(),
                    "gazeTarget" to currentGazeTarget,
                    "confidence" to currentConfidence,
                    "calibrated" to gazeDetectionAlgorithm.isCalibrated(),
                    "displayedContent" to mapOf(
                        "yes" to getString(R.string.yes_button),
                        "no" to getString(R.string.no_button)
                    ),
                    "isLookingAtThisDevice" to (currentGazeTarget != "none")
                )
                mqttClient.publishGazeState(gazeData)
            } catch (e: Exception) {
                Log.e(TAG, "发布 MQTT 消息失败", e)
            }
        }
    }

    private fun updateCameraStatus(status: String, color: Int) {
        runOnUiThread {
            cameraStatus.text = status
            cameraStatus.setTextColor(color)
        }
    }

    private fun updateMediaPipeStatus(status: String, color: Int) {
        runOnUiThread {
            mediapipeStatus.text = status
            mediapipeStatus.setTextColor(color)
        }
    }

    private fun updateMqttStatus(status: String, color: Int) {
        runOnUiThread {
            mqttStatus.text = status
            mqttStatus.setTextColor(color)
        }
    }

    // ==================== 生命周期 ====================

    override fun onResume() {
        super.onResume()
        try {
            if (::cameraManager.isInitialized) {
                cameraManager.startCamera()
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复摄像头失败", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (::cameraManager.isInitialized) {
                cameraManager.stopCamera()
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停摄像头失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::cameraManager.isInitialized) {
                cameraManager.setPreviewSurface(null)
                cameraManager.release()
            }
            if (::faceLandmarkerHelper.isInitialized) {
                faceLandmarkerHelper.clearFaceLandmarker()
            }
            if (::mqttClient.isInitialized) {
                mqttClient.disconnect()
            }
            faceMeshOverlay?.clear()
        } catch (e: Exception) {
            Log.e(TAG, "清理资源失败", e)
        }
    }
}
