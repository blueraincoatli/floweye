package com.gazeinteraction

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
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
import androidx.appcompat.app.AlertDialog
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
 * 双屏视线交互主 Activity
 *
 * 每台设备显示一个按钮（"是" 或 "否"），通过前置摄像头判断
 * 用户是否在注视本屏幕。注视时按钮点亮，移开视线时熄灭。
 *
 * 通过右上角按钮校准（正视屏幕时的瞳孔位置）。
 * 通过左上角按钮切换设备角色（是/否）。
 */
class MainActivity : AppCompatActivity(),
    FaceLandmarkerHelper.LandmarkerListener,
    GazeDetectionAlgorithm.GazeListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "gaze_prefs"
        private const val KEY_DEVICE_ROLE = "device_role"
        private const val KEY_DEVICE_ID = "device_id"
        private const val MIN_CALIBRATION_SAMPLES = 10
    }

    // ---------- UI 组件 ----------
    private lateinit var mainButton: TextView
    private lateinit var gazeStatus: TextView
    private lateinit var confidenceText: TextView
    private lateinit var deviceIdText: TextView
    private lateinit var cameraStatus: TextView
    private lateinit var mediapipeStatus: TextView
    private lateinit var mqttStatus: TextView
    private lateinit var calibrateButton: FloatingActionButton
    private lateinit var roleButton: FloatingActionButton

    // ---------- 调试叠加层 ----------
    private var debugPanel: FrameLayout? = null
    private var debugSurfaceView: SurfaceView? = null
    private var faceMeshOverlay: FaceMeshOverlayView? = null
    private var isDebugMode = false

    private val debugSurfaceCallback = object : android.view.SurfaceHolder.Callback {
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
    }

    // ---------- 核心组件 ----------
    private lateinit var cameraManager: CameraManager
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var gazeDetectionAlgorithm: GazeDetectionAlgorithm
    private lateinit var mqttClient: MqttClient

    // ---------- 状态 ----------
    private var deviceRole: String = "yes"  // "yes" 或 "no"
    private var deviceId: String = ""
    private var isLookingAtScreen = false
    private var currentConfidence: Float = 0.0f

    // ---------- MQTT 节流 ----------
    private var lastPublishedLooking = false
    private var lastPublishTimeMs: Long = 0
    private val PUBLISH_MIN_INTERVAL_MS = 500L

    // ---------- 校准 ----------
    private var isCalibrating = false
    private val calibrationSamples = mutableListOf<Double>()

    // 权限
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) initializeComponents()
            else showPermissionDeniedMessage()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadDeviceRole()
        generateDeviceId()
        initializeViews()
        checkAndRequestCameraPermission()
    }

    private fun loadDeviceRole() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        deviceRole = prefs.getString(KEY_DEVICE_ROLE, "yes") ?: "yes"
    }

    private fun saveDeviceRole(role: String) {
        deviceRole = role
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICE_ROLE, role).apply()
    }

    private fun initializeViews() {
        mainButton = findViewById(R.id.mainButton)
        gazeStatus = findViewById(R.id.gazeStatus)
        confidenceText = findViewById(R.id.confidenceText)
        deviceIdText = findViewById(R.id.deviceIdText)
        cameraStatus = findViewById(R.id.cameraStatus)
        mediapipeStatus = findViewById(R.id.mediapipeStatus)
        mqttStatus = findViewById(R.id.mqttStatus)
        calibrateButton = findViewById(R.id.settingsButton)
        roleButton = findViewById(R.id.roleButton)

        updateButtonAppearance()

        // 校准按钮：单击启动校准，长按重置
        calibrateButton.setOnClickListener { startCalibration() }
        calibrateButton.setOnLongClickListener {
            if (::gazeDetectionAlgorithm.isInitialized) {
                gazeDetectionAlgorithm.resetCalibration()
                Toast.makeText(this, "校准数据已重置", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // 角色切换按钮
        roleButton.setOnClickListener { showRoleSwitchDialog() }

        deviceIdText.text = "设备ID: $deviceId"

        // 调试叠加层
        debugPanel = findViewById(R.id.debugPanel)
        debugSurfaceView = findViewById(R.id.debugSurfaceView)
        faceMeshOverlay = findViewById(R.id.faceMeshOverlay)

        deviceIdText.setOnClickListener(object : View.OnClickListener {
            private var lastClickTime = 0L
            override fun onClick(v: View) {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 500) toggleDebugMode()
                lastClickTime = now
            }
        })
    }

    private fun updateButtonAppearance() {
        val label = if (deviceRole == "yes") "是" else "否"
        mainButton.text = label
        if (isLookingAtScreen) {
            mainButton.setBackgroundResource(
                if (deviceRole == "yes") R.drawable.btn_yes_gaze else R.drawable.btn_no_gaze
            )
        } else {
            mainButton.setBackgroundResource(
                if (deviceRole == "yes") R.drawable.btn_yes_normal else R.drawable.btn_no_normal
            )
        }
    }

    private fun showRoleSwitchDialog() {
        val options = arrayOf("是 (YES)", "否 (NO)")
        val currentIdx = if (deviceRole == "yes") 0 else 1
        AlertDialog.Builder(this)
            .setTitle("选择本设备的角色")
            .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                val newRole = if (which == 0) "yes" else "no"
                saveDeviceRole(newRole)
                updateButtonAppearance()
                dialog.dismiss()
                Toast.makeText(this, "设备角色已切换为: ${if (newRole == "yes") "是" else "否"}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleDebugMode() {
        isDebugMode = !isDebugMode
        debugPanel?.visibility = if (isDebugMode) View.VISIBLE else View.GONE
        if (isDebugMode) {
            debugSurfaceView?.holder?.removeCallback(debugSurfaceCallback)
            debugSurfaceView?.holder?.addCallback(debugSurfaceCallback)
        } else {
            debugSurfaceView?.holder?.removeCallback(debugSurfaceCallback)
            if (::cameraManager.isInitialized) cameraManager.setPreviewSurface(null)
            faceMeshOverlay?.clear()
        }
        Toast.makeText(this, if (isDebugMode) "调试模式开" else "调试模式关", Toast.LENGTH_SHORT).show()
    }

    private fun generateDeviceId() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_DEVICE_ID, null)
        if (!savedId.isNullOrBlank()) {
            deviceId = savedId
            return
        }

        deviceId = "GAZE_${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> initializeComponents()
            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "需要摄像头权限", Toast.LENGTH_LONG).show()
        updateStatus(cameraStatus, "权限被拒绝", Color.RED)
    }

    private fun initializeComponents() {
        lifecycleScope.launch {
            try {
                // 1. MediaPipe
                updateStatus(mediapipeStatus, "初始化中...", Color.YELLOW)
                faceLandmarkerHelper = FaceLandmarkerHelper(this@MainActivity, this@MainActivity)
                updateStatus(mediapipeStatus, "就绪", Color.GREEN)

                // 2. 视线算法
                gazeDetectionAlgorithm = GazeDetectionAlgorithm(this@MainActivity)
                gazeDetectionAlgorithm.setGazeListener(this@MainActivity)

                // 3. 摄像头
                updateStatus(cameraStatus, "初始化中...", Color.YELLOW)
                cameraManager = CameraManager(this@MainActivity)
                cameraManager.initialize { bitmap ->
                    faceLandmarkerHelper.detectLiveStream(bitmap, System.currentTimeMillis())
                }
                updateStatus(cameraStatus, "就绪", Color.GREEN)

                // 4. MQTT
                updateStatus(mqttStatus, "连接中...", Color.YELLOW)
                mqttClient = MqttClient(this@MainActivity, deviceId)
                mqttClient.connect()
                updateStatus(mqttStatus, "已连接", Color.GREEN)

            } catch (e: Exception) {
                Log.e(TAG, "初始化失败", e)
                Toast.makeText(this@MainActivity, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== MediaPipe 回调 ====================

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Log.e(TAG, "MediaPipe错误: $error")
            updateStatus(mediapipeStatus, "错误", Color.RED)
        }
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        // 调试叠加层
        if (isDebugMode && faceMeshOverlay != null) {
            try {
                if (resultBundle.results.faceLandmarks().isNotEmpty()) {
                    val landmarks = resultBundle.results.faceLandmarks()[0]
                    runOnUiThread { faceMeshOverlay?.updateLandmarks(landmarks) }
                } else {
                    runOnUiThread { faceMeshOverlay?.clear() }
                }
            } catch (_: Exception) {}
        }

        // 校准模式
        if (isCalibrating) {
            collectCalibrationSample(resultBundle)
            return
        }

        // 正常检测
        gazeDetectionAlgorithm.processMediaPipeResults(resultBundle)
    }

    // ==================== 视线回调 ====================

    override fun onGazeAtScreen(confidence: Float) {
        runOnUiThread {
            isLookingAtScreen = true
            currentConfidence = confidence
            updateUI()
            publishState()
        }
    }

    override fun onGazeAway() {
        runOnUiThread {
            isLookingAtScreen = false
            currentConfidence = 0.0f
            updateUI()
            publishState()
        }
    }

    // ==================== 校准 ====================

    private fun startCalibration() {
        if (!::gazeDetectionAlgorithm.isInitialized) {
            Toast.makeText(this, "系统尚未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        if (isCalibrating) {
            Toast.makeText(this, "校准进行中", Toast.LENGTH_SHORT).show()
            return
        }

        calibrationSamples.clear()
        isCalibrating = true

        lifecycleScope.launch {
            for (i in 3 downTo 1) {
                runOnUiThread { gazeStatus.text = "请正视屏幕... $i" }
                delay(1000)
            }

            isCalibrating = false

            if (calibrationSamples.size >= MIN_CALIBRATION_SAMPLES) {
                val avg = calibrationSamples.average()
                gazeDetectionAlgorithm.setCenterBaseline(avg)
                runOnUiThread {
                    gazeStatus.text = "校准完成"
                    Toast.makeText(this@MainActivity, "校准成功 (样本: ${calibrationSamples.size})", Toast.LENGTH_SHORT).show()
                }
                delay(1500)
                runOnUiThread { updateUI() }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "样本不足(${calibrationSamples.size}/$MIN_CALIBRATION_SAMPLES)，请重试", Toast.LENGTH_SHORT).show()
                    updateUI()
                }
            }
        }
    }

    private fun collectCalibrationSample(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        try {
            if (resultBundle.results.faceLandmarks().isEmpty()) return
            val landmarks = resultBundle.results.faceLandmarks()[0]
            val metrics = gazeDetectionAlgorithm.extractCalibrationMetrics(landmarks)
            if (metrics.eyeOpenness > 0.15) {
                calibrationSamples.add(metrics.pupilRatio)
            }
        } catch (e: Exception) {
            Log.e(TAG, "收集校准样本失败", e)
        }
    }

    // ==================== UI ====================

    private fun updateUI() {
        updateButtonAppearance()
        val roleLabel = if (deviceRole == "yes") "是" else "否"
        if (isLookingAtScreen) {
            gazeStatus.text = "正在注视 -> $roleLabel"
            gazeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
        } else {
            val calibrated = if (::gazeDetectionAlgorithm.isInitialized) gazeDetectionAlgorithm.isCalibrated() else false
            gazeStatus.text = if (calibrated) "未注视（已校准）" else "未注视"
            gazeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
        }
        confidenceText.text = "置信度: ${(currentConfidence * 100).toInt()}%"
    }

    private fun updateStatus(view: TextView, text: String, color: Int) {
        runOnUiThread {
            view.text = text
            view.setTextColor(color)
        }
    }

    // ==================== MQTT ====================

    private fun publishState() {
        val now = System.currentTimeMillis()
        if (isLookingAtScreen != lastPublishedLooking) {
            // 状态变了，立即发布
        } else if (now - lastPublishTimeMs < PUBLISH_MIN_INTERVAL_MS) {
            return
        }

        lastPublishedLooking = isLookingAtScreen
        lastPublishTimeMs = now

        lifecycleScope.launch {
            try {
                val data = mapOf(
                    "deviceId" to deviceId,
                    "timestamp" to System.currentTimeMillis(),
                    "role" to deviceRole,
                    "lookingAtScreen" to isLookingAtScreen,
                    "confidence" to currentConfidence,
                    "calibrated" to (::gazeDetectionAlgorithm.isInitialized && gazeDetectionAlgorithm.isCalibrated())
                )
                mqttClient.publishGazeState(data)
            } catch (e: Exception) {
                Log.e(TAG, "MQTT 发布失败", e)
            }
        }
    }

    // ==================== 生命周期 ====================

    override fun onResume() {
        super.onResume()
        try { if (::cameraManager.isInitialized) cameraManager.startCamera() } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { if (::cameraManager.isInitialized) cameraManager.stopCamera() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::cameraManager.isInitialized) { cameraManager.setPreviewSurface(null); cameraManager.release() }
            if (::faceLandmarkerHelper.isInitialized) faceLandmarkerHelper.clearFaceLandmarker()
            if (::mqttClient.isInitialized) mqttClient.disconnect()
            faceMeshOverlay?.clear()
        } catch (_: Exception) {}
    }
}
