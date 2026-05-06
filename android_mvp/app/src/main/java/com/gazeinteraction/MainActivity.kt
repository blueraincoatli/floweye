package com.gazeinteraction

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
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
import com.gazeinteraction.ui.ArcProgressView
import com.gazeinteraction.ui.GazeHaloView
import com.gazeinteraction.ui.ThemeConfig
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.widget.AppCompatImageButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

import com.gazeinteraction.coordinator.BrokerService
import com.gazeinteraction.coordinator.CoordinatorEngine
import com.gazeinteraction.coordinator.HostManager
import com.gazeinteraction.coordinator.AndroidTTSManager
import com.gazeinteraction.coordinator.GazeInterpreter

class MainActivity : AppCompatActivity(),
    FaceLandmarkerHelper.LandmarkerListener,
    GazeDetectionAlgorithm.GazeListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "gaze_prefs"
        private const val KEY_DEVICE_ROLE = "device_role"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_THEME = "theme_name"
        private const val KEY_OPERATOR_MODE = "operator_mode"
        private const val MIN_CALIBRATION_SAMPLES = 10
        private const val PERCEPTION_PHASE_MS = 500L
        private const val GAZE_SELECT_THRESHOLD_MS = 1500L
    }

    private enum class ScreenState { IDLE, TRANSITION, SCAN, CONFIRM, FEEDBACK }

    // UI
    private lateinit var mainButton: TextView
    private lateinit var gazeHaloView: GazeHaloView
    private lateinit var arcProgressView: ArcProgressView
    private lateinit var gazeStatus: TextView
    private lateinit var confidenceText: TextView
    private lateinit var optionNameText: TextView
    private lateinit var calibrateButton: AppCompatImageButton
    private lateinit var hostManager: HostManager
    private var coordinatorEngine: CoordinatorEngine? = null
    private var brokerService: BrokerService? = null
    private var ttsManager: AndroidTTSManager? = null
    private var gazeInterpreter: GazeInterpreter? = null
    private lateinit var settingsButton: AppCompatImageButton
    private lateinit var roleButton: AppCompatImageButton
    private lateinit var connectionDot: View
    private lateinit var topStatusBar: View
    private lateinit var bottomInfo: View
    private lateinit var centerTextContainer: View

    // Landscape dual buttons (nullable)
    private var yesButton: TextView? = null
    private var noButton: TextView? = null
    private var gazeHaloYes: GazeHaloView? = null
    private var gazeHaloNo: GazeHaloView? = null

    // Sound
    private var soundPool: SoundPool? = null
    private var soundDingDong = 0
    private var soundWhoosh = 0

    // Theme
    private var currentTheme: ThemeConfig = ThemeConfig.WARM_HEALING
    private var isOperatorMode = false

    // Debug
    private var debugPanel: FrameLayout? = null
    private var debugSurfaceView: SurfaceView? = null
    private var faceMeshOverlay: FaceMeshOverlayView? = null
    private var isDebugMode = false

    private val debugSurfaceCallback = object : android.view.SurfaceHolder.Callback {
        override fun surfaceCreated(holder: android.view.SurfaceHolder) {
            if (::cameraManager.isInitialized) cameraManager.setPreviewSurface(holder.surface)
        }
        override fun surfaceChanged(holder: android.view.SurfaceHolder, fmt: Int, w: Int, h: Int) {}
        override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
            if (::cameraManager.isInitialized) cameraManager.setPreviewSurface(null)
        }
    }

    // Core
    private lateinit var cameraManager: CameraManager
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var gazeDetectionAlgorithm: GazeDetectionAlgorithm
    private lateinit var mqttClient: MqttClient

    // State
    private var deviceRole: String = "yes"
    private var deviceId: String = ""
    private var isLookingAtScreen = false
    private var currentConfidence: Float = 0.0f
    private var screenState = ScreenState.IDLE
    private var isAnnouncing = false
    private var currentMenuDepth = 0

    // Two-phase gaze feedback
    private var gazeStartTimeMs = 0L
    private var perceptionPhaseActive = false
    private var guidancePhaseActive = false

    // MQTT throttle
    private var lastPublishTimeMs: Long = 0
    private val PUBLISH_MIN_INTERVAL_MS = 500L

    // Calibration
    private var isCalibrating = false
    private val calibrationSamples = mutableListOf<Double>()

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) initializeComponents()
            else showPermissionDeniedMessage()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadPreferences()
        generateDeviceId()
        initializeViews()
        checkAndRequestCameraPermission()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        deviceRole = prefs.getString(KEY_DEVICE_ROLE, "yes") ?: "yes"
        currentTheme = ThemeConfig.byName(prefs.getString(KEY_THEME, "WARM_HEALING") ?: "WARM_HEALING")
        isOperatorMode = prefs.getBoolean(KEY_OPERATOR_MODE, false)
    }

    private fun initializeViews() {
        gazeHaloView = findViewById(R.id.gazeHaloView)
        mainButton = findViewById(R.id.mainButton)
        arcProgressView = findViewById(R.id.arcProgressView)
        optionNameText = findViewById(R.id.optionNameText)
        gazeStatus = findViewById(R.id.gazeStatus)
        confidenceText = findViewById(R.id.confidenceText)
        calibrateButton = findViewById(R.id.calibrateButton)
        settingsButton = findViewById(R.id.settingsButton)
        roleButton = findViewById(R.id.roleButton)
        connectionDot = findViewById(R.id.connectionDot)
        topStatusBar = findViewById(R.id.topStatusBar)
        bottomInfo = findViewById(R.id.bottomInfo)
        centerTextContainer = findViewById(R.id.centerTextContainer)

        yesButton = findViewById<TextView?>(R.id.yesButton)
        noButton = findViewById<TextView?>(R.id.noButton)
        gazeHaloYes = findViewById<GazeHaloView?>(R.id.gazeHaloYes)
        gazeHaloNo = findViewById<GazeHaloView?>(R.id.gazeHaloNo)

        applyTheme(currentTheme)
        updateUIForState()
        updateOperatorUI()

        calibrateButton.setOnClickListener { startCalibration() }
        calibrateButton.setOnLongClickListener {
            if (::gazeDetectionAlgorithm.isInitialized) {
                gazeDetectionAlgorithm.resetCalibration()
                Toast.makeText(this, "校准数据已重置", Toast.LENGTH_SHORT).show()
            }
            true
        }

        settingsButton.setOnClickListener { showSettingsDialog() }
        roleButton.setOnClickListener { showRoleSwitchDialog() }

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttrs).build()
        soundDingDong = soundPool?.load(this, R.raw.ding_dong, 1) ?: 0
        soundWhoosh = soundPool?.load(this, R.raw.whoosh, 1) ?: 0

        debugPanel = findViewById(R.id.debugPanel)
        debugSurfaceView = findViewById(R.id.debugSurfaceView)
        faceMeshOverlay = findViewById(R.id.faceMeshOverlay)

        connectionDot.setOnClickListener(object : View.OnClickListener {
            private var lastClick = 0L
            override fun onClick(v: View) {
                val now = System.currentTimeMillis()
                if (now - lastClick < 500) toggleDebugMode()
                lastClick = now
            }
        })

        connectionDot.setOnLongClickListener {
            isOperatorMode = !isOperatorMode
            updateOperatorUI()
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OPERATOR_MODE, isOperatorMode).apply()
            Toast.makeText(this, if (isOperatorMode) "操作者模式" else "患者模式", Toast.LENGTH_SHORT).show()
            true
        }

        gazeHaloView.onPerceptionStart = { perceptionPhaseActive = true }
        gazeHaloView.onGuidanceStart = { guidancePhaseActive = true }

        // Self-hosted mode: detect role and setup coordinator
        setupSelfHosted()
    }

    // ==================== Self-Hosted Coordinator ====================

    private fun setupSelfHosted() {
        hostManager = HostManager(this)
        val role = hostManager.detectRole()
        Log.i(TAG, "HostManager role: $role")

        if (role == HostManager.Role.HOST) {
            startSelfHosted()
        } else {
            gazeInterpreter = GazeInterpreter()
            Toast.makeText(this, "角色: ${role.name} (长按圆点→设置→强制主机)", Toast.LENGTH_LONG).show()
        }
    }

    private fun startSelfHosted() {
        // Stop existing broker if running
        brokerService?.stop()

        brokerService = BrokerService()
        brokerService?.start(this, 1883)
        val running = brokerService?.isRunning ?: false
        val err = brokerService?.lastError ?: ""
        if (!running) {
            Toast.makeText(this, "Broker启动失败: $err", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val menuJson = assets.open("menu_config.json").bufferedReader().readText()
            val patientJson = assets.open("patient_config.json").bufferedReader().readText()
            coordinatorEngine = CoordinatorEngine(menuJson, patientJson)
            coordinatorEngine?.onDecision = { type, option ->
                publishCoordinatorDecision(type, option)
            }
            coordinatorEngine?.onTtsRequest = { text ->
                ttsManager?.speak(text)
            }

            if (ttsManager == null) {
                ttsManager = AndroidTTSManager(this)
                ttsManager?.initialize { }
            }

            gazeInterpreter = GazeInterpreter()

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (::mqttClient.isInitialized) {
                    mqttClient.disconnect()
                    mqttClient.connect("127.0.0.1", 1883)
                }
            }, 1000)
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun publishCoordinatorDecision(type: String, option: org.json.JSONObject?) {
        // 直接调用 handleCoordinationMessage 驱动 UI，不走 MQTT 回路
        val msg = org.json.JSONObject().apply {
            put("type", type)
            put("timestamp", System.currentTimeMillis())
            put("menuDepth", coordinatorEngine?.currentDepth ?: 0)
            if (option != null) {
                put("optionId", option.optString("id", ""))
                put("optionLabel", option.optString("label", ""))
                put("ttsPrompt", option.optString("tts_prompt", ""))
            }
        }
        runOnUiThread {
            handleCoordinationMessage("", msg.toString())
        }
        // 同时发布到 MQTT，让其他客户端收到
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected()) {
                mqttClient.publishGazeState(mapOf("type" to type, "payload" to msg.toString()))
            }
        } catch (_: Exception) {}
    }

    @Suppress("UNUSED_PARAMETER")
    private fun processGazeForCoordinator(deviceId: String, looking: Boolean, confidence: Float) {
        val engine = coordinatorEngine ?: return
        @Suppress("UNUSED_VARIABLE") val interpreter = gazeInterpreter ?: return

        if (engine.state == CoordinatorEngine.State.IDLE) {
            // Check for wake
            // Note: wake evaluation happens externally via gaze duration
            engine.tick()
        } else if (engine.state == CoordinatorEngine.State.SCAN || engine.state == CoordinatorEngine.State.CONFIRM) {
            engine.tick()
        }
    }

    // ==================== Theme ====================

    private fun applyTheme(theme: ThemeConfig) {
        currentTheme = theme
        val rootLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootLayout)
        rootLayout.setBackgroundColor(theme.bgColor)
        mainButton.setTextColor(theme.textPrimary)
        optionNameText.setTextColor(theme.textPrimary)
        gazeStatus.setTextColor(theme.textPrimary)
        confidenceText.setTextColor(theme.textPrimary)
        gazeHaloView.textColor = theme.textPrimary
        gazeHaloView.haloColor = theme.haloColorFor(deviceRole)
        gazeHaloYes?.haloColor = theme.haloYes
        gazeHaloNo?.haloColor = theme.haloNo
        // 按钮图标色调
        val iconTint = android.content.res.ColorStateList.valueOf(theme.textPrimary)
        settingsButton.imageTintList = iconTint
        roleButton.imageTintList = iconTint
        calibrateButton.imageTintList = iconTint
        // 按钮背景：浅色主题用半透明黑，深色主题用半透明白
        val bgArgb = if (theme == ThemeConfig.MODERN_MINIMAL) 0x18000000.toInt() else 0x1AFFFFFF.toInt()
        updateButtonBackground(settingsButton, bgArgb)
        updateButtonBackground(roleButton, bgArgb)
        updateButtonBackground(calibrateButton, bgArgb)
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.name).apply()
    }

    private fun updateButtonBackground(button: AppCompatImageButton, argb: Int) {
        (button.background as? GradientDrawable)?.setColor(argb)
    }

    private fun showSettingsDialog() {
        val themeNames = ThemeConfig.ALL.map { it.name }.toTypedArray()
        val currentIdx = ThemeConfig.ALL.indexOf(currentTheme).coerceAtLeast(0)
        val hostLabel = if (::hostManager.isInitialized && hostManager.forceHostMode) "开" else "关"
        val options = arrayOf(
            "切换主题",
            "切换角色 (当前: ${if (deviceRole == "yes") "是" else "否"})",
            "操作者模式: ${if (isOperatorMode) "开" else "关"}",
            "强制主机模式: $hostLabel"
        )
        AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemePickerDialog(themeNames, currentIdx)
                    1 -> showRoleSwitchDialog()
                    2 -> {
                        isOperatorMode = !isOperatorMode
                        updateOperatorUI()
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean(KEY_OPERATOR_MODE, isOperatorMode).apply()
                    }
                    3 -> {
                        if (::hostManager.isInitialized) {
                            hostManager.forceHostMode = !hostManager.forceHostMode
                            hostManager.detectRole()
                            if (hostManager.role == HostManager.Role.HOST) {
                                startSelfHosted()
                            }
                            Toast.makeText(this,
                                "主机模式: ${if (hostManager.forceHostMode) "开" else "关"}",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showThemePickerDialog(themeNames: Array<String>, currentIdx: Int) {
        AlertDialog.Builder(this)
            .setTitle("选择主题")
            .setSingleChoiceItems(themeNames, currentIdx) { dialog, which ->
                applyTheme(ThemeConfig.ALL[which])
                updateUIForState()
                dialog.dismiss()
                Toast.makeText(this, "主题: ${ThemeConfig.ALL[which].name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateOperatorUI() {
        // 设置按钮始终可见
        settingsButton.visibility = View.VISIBLE
        // 角色切换和置信度只在操作者模式下显示
        val operatorVisibility = if (isOperatorMode) View.VISIBLE else View.GONE
        roleButton.visibility = operatorVisibility
        confidenceText.visibility = operatorVisibility
    }

    // ==================== State Engine ====================

    private fun updateUIForState() {
        val rootLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootLayout)
        when (screenState) {
            ScreenState.IDLE -> {
                isAnnouncing = false
                gazeHaloView.resetToIdle()
                mainButton.visibility = View.VISIBLE
                mainButton.text = "你好"
                mainButton.alpha = 1f
                arcProgressView.visibility = View.GONE
                optionNameText.visibility = View.GONE
                calibrateButton.alpha = 1f
                roleButton.alpha = 1f
                rootLayout.setBackgroundColor(currentTheme.bgColor)
                startBreathingAnimation()
                gazeStatus.text = "注视屏幕开始"
            }
            ScreenState.TRANSITION -> {
                isAnnouncing = false
                mainButton.visibility = View.VISIBLE
                mainButton.text = ""
                optionNameText.visibility = View.GONE
                arcProgressView.visibility = View.GONE
                calibrateButton.alpha = 0.3f
                roleButton.alpha = 0.3f
                stopBreathingAnimation()
            }
            ScreenState.SCAN -> {
                if (isAnnouncing) {
                    mainButton.visibility = View.GONE
                    arcProgressView.visibility = View.GONE
                    stopBreathingAnimation()
                    gazeHaloView.visibility = View.GONE
                } else {
                    val label = if (deviceRole == "yes") "是" else "否"
                    mainButton.visibility = View.VISIBLE
                    mainButton.text = label
                    gazeHaloView.visibility = View.VISIBLE
                    gazeHaloView.haloColor = currentTheme.haloColorFor(deviceRole)
                    gazeHaloView.enterScanMode()
                    arcProgressView.visibility = View.VISIBLE
                    arcProgressView.arcColor = currentTheme.haloColorFor(deviceRole)
                    arcProgressView.progress = 0f
                    applyDepthColor(currentMenuDepth)
                }
                optionNameText.visibility = View.VISIBLE
                calibrateButton.alpha = 0.3f
                roleButton.alpha = 0.3f
                stopBreathingAnimation()
            }
            ScreenState.CONFIRM -> {
                isAnnouncing = false
                val label = if (deviceRole == "yes") "是" else "否"
                mainButton.visibility = View.VISIBLE
                mainButton.text = label
                gazeHaloView.visibility = View.VISIBLE
                gazeHaloView.haloColor = currentTheme.haloColorFor(deviceRole)
                gazeHaloView.enterConfirmMode()
                arcProgressView.visibility = View.VISIBLE
                arcProgressView.arcColor = currentTheme.haloColorFor(deviceRole)
                arcProgressView.progress = 0f
                optionNameText.visibility = View.VISIBLE
                applyDepthColor(99)
                calibrateButton.alpha = 0.3f
                roleButton.alpha = 0.3f
                stopBreathingAnimation()
            }
            ScreenState.FEEDBACK -> {
                isAnnouncing = false
                mainButton.visibility = View.VISIBLE
                mainButton.text = ""
                mainButton.alpha = 1f
                gazeHaloView.resetToIdle()
                arcProgressView.visibility = View.GONE
                optionNameText.visibility = View.VISIBLE
                calibrateButton.alpha = 1f
                roleButton.alpha = 1f
                rootLayout.setBackgroundColor(currentTheme.bgColor)
                stopBreathingAnimation()
            }
        }
    }

    private fun applyDepthColor(depth: Int) {
        val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
        when {
            depth == 0 -> rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_level_0))
            depth == 1 -> rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_level_1))
            depth >= 2 -> rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_level_2))
            depth == 99 -> rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.confirm_bg))
        }
    }

    // ==================== Two-Phase Gaze Feedback ====================

    private var gazeProgressJob: Job? = null

    private fun onGazeDetectedStart() {
        perceptionPhaseActive = true
        guidancePhaseActive = false
        gazeHaloView.onGazeDetected()
        gazeHaloYes?.onGazeDetected()
        gazeHaloNo?.onGazeDetected()
        startGazeProgress()
    }

    private fun onGazeDetectedEnd() {
        perceptionPhaseActive = false
        guidancePhaseActive = false
        gazeHaloView.onGazeLost()
        gazeHaloYes?.onGazeLost()
        gazeHaloNo?.onGazeLost()
        gazeProgressJob?.cancel()
        // 重置弧线进度，避免残留的半圆弧
        gazeHaloView.arcProgress = 0f
        gazeHaloYes?.arcProgress = 0f
        gazeHaloNo?.arcProgress = 0f
        arcProgressView.progress = 0f
    }

    private fun startGazeProgress() {
        gazeProgressJob?.cancel()
        gazeStartTimeMs = System.currentTimeMillis()
        gazeProgressJob = lifecycleScope.launch {
            val interval = 50L
            while (isActive && isLookingAtScreen) {
                val elapsed = System.currentTimeMillis() - gazeStartTimeMs
                val progress = (elapsed.toFloat() / GAZE_SELECT_THRESHOLD_MS).coerceAtMost(1f)
                gazeHaloView.arcProgress = progress
                gazeHaloYes?.arcProgress = progress
                gazeHaloNo?.arcProgress = progress
                arcProgressView.progress = progress
                if (perceptionPhaseActive && elapsed >= PERCEPTION_PHASE_MS) {
                    perceptionPhaseActive = false
                    guidancePhaseActive = true
                }
                delay(interval)
            }
        }
    }

    // ==================== Breathing Animation ====================

    private var breathingJob: Job? = null

    private fun startBreathingAnimation() {
        breathingJob?.cancel()
        breathingJob = lifecycleScope.launch {
            val period = currentTheme.pulsePeriodMs
            val halfPeriod = period / 2
            while (isActive) {
                mainButton.animate().alpha(0.6f).setDuration(halfPeriod).start()
                delay(halfPeriod)
                mainButton.animate().alpha(1.0f).setDuration(halfPeriod).start()
                delay(halfPeriod)
            }
        }
    }

    private fun stopBreathingAnimation() {
        breathingJob?.cancel()
        mainButton.alpha = 1f
    }

    // ==================== Role Switch ====================

    private fun showRoleSwitchDialog() {
        val options = arrayOf("是 (YES)", "否 (NO)")
        val currentIdx = if (deviceRole == "yes") 0 else 1
        AlertDialog.Builder(this)
            .setTitle("选择本设备角色")
            .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                val newRole = if (which == 0) "yes" else "no"
                deviceRole = newRole
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_DEVICE_ROLE, newRole).apply()
                gazeHaloView.haloColor = currentTheme.haloColorFor(newRole)
                updateUIForState()
                dialog.dismiss()
                Toast.makeText(this, "角色: ${if (newRole == "yes") "是" else "否"}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== Debug ====================

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
        Toast.makeText(this, if (isDebugMode) "调试开" else "调试关", Toast.LENGTH_SHORT).show()
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

    // ==================== Permissions & Init ====================

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> initializeComponents()
            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "需要摄像头权限", Toast.LENGTH_LONG).show()
    }

    private fun initializeComponents() {
        lifecycleScope.launch {
            runOnUiThread { updateUIForState() }

            try {
                faceLandmarkerHelper = FaceLandmarkerHelper(this@MainActivity, this@MainActivity)
                withContext(Dispatchers.IO) { faceLandmarkerHelper.initialize() }
            } catch (e: Exception) {
                Log.e(TAG, "MediaPipe init failed", e)
            }

            try {
                gazeDetectionAlgorithm = GazeDetectionAlgorithm(this@MainActivity)
                gazeDetectionAlgorithm.setGazeListener(this@MainActivity)
            } catch (e: Exception) {
                Log.e(TAG, "Gaze algo init failed", e)
            }

            try {
                cameraManager = CameraManager(this@MainActivity)
                cameraManager.initialize { bitmap ->
                    faceLandmarkerHelper.detectLiveStream(bitmap, System.currentTimeMillis())
                }
                cameraManager.startCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Camera init failed", e)
            }

            try {
                mqttClient = MqttClient(this@MainActivity, deviceId)
                mqttClient.connectionListener = object : MqttClient.ConnectionListener {
                    override fun onConnected() {
                        setConnectionDotStatus("connected")
                        startPeriodicPublish()
                    }
                    override fun onDisconnected() {
                        setConnectionDotStatus("disconnected")
                    }
                    override fun onConnectionFailed(error: String) {
                        setConnectionDotStatus("disconnected")
                    }
                    override fun onMessageReceived(topic: String, message: String) {
                        handleCoordinationMessage(topic, message)
                    }
                }
                setConnectionDotStatus("connecting")
                Toast.makeText(this@MainActivity, "正在连接MQTT...", Toast.LENGTH_SHORT).show()
                // connect() uses dynamic address from HostManager
                if (!mqttClient.isConnected()) {
                    mqttClient.connect(hostManager.getBrokerHost(), hostManager.getBrokerPort())
                }
            } catch (e: Exception) {
                Log.e(TAG, "MQTT init failed", e)
                Toast.makeText(this@MainActivity, "MQTT初始化异常: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setConnectionDotStatus(status: String) {
        runOnUiThread {
            val drawable = when (status) {
                "connected" -> R.drawable.dot_status_connected
                "disconnected" -> R.drawable.dot_status_disconnected
                else -> R.drawable.dot_status_idle
            }
            connectionDot.setBackgroundResource(drawable)
        }
    }

    // ==================== MediaPipe ====================

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "MediaPipe error: $error")
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
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

        if (isCalibrating) {
            collectCalibrationSample(resultBundle)
            return
        }

        gazeDetectionAlgorithm.processMediaPipeResults(resultBundle)
    }

    // ==================== Gaze Callbacks ====================

    override fun onGazeAtScreen(confidence: Float) {
        runOnUiThread {
            val justStarted = !isLookingAtScreen
            isLookingAtScreen = true
            currentConfidence = confidence
            if (justStarted) {
                gazeStartTimeMs = System.currentTimeMillis()
                onGazeDetectedStart()
            }
            feedCoordinatorGaze(true, confidence)
            updateScanUI()
            publishState()
            startPeriodicPublish()
        }
    }

    override fun onGazeAway() {
        runOnUiThread {
            isLookingAtScreen = false
            currentConfidence = 0.0f
            onGazeDetectedEnd()
            feedCoordinatorGaze(false, 0f)
            updateScanUI()
            publishState()
            stopPeriodicPublish()
        }
    }

    private fun feedCoordinatorGaze(looking: Boolean, confidence: Float) {
        val engine = coordinatorEngine ?: return
        val interpreter = gazeInterpreter ?: return

        when (engine.state) {
            CoordinatorEngine.State.IDLE -> {
                if (interpreter.evaluateWake(looking, confidence, gazeStartTimeMs)) {
                    engine.handleAction("wake")
                }
            }
            CoordinatorEngine.State.SCAN -> {
                val action = interpreter.evaluate(deviceRole, looking, confidence, gazeStartTimeMs)
                if (action != "none") {
                    engine.handleAction(action)
                }
            }
            CoordinatorEngine.State.CONFIRM -> {
                if (deviceRole == "yes") {
                    val action = interpreter.evaluate("yes", looking, confidence, gazeStartTimeMs)
                    if (action == "select") engine.handleAction("confirm")
                } else if (deviceRole == "no") {
                    if (interpreter.evaluate("no", looking, confidence, gazeStartTimeMs) == "skip") {
                        engine.handleAction("cancel")
                    }
                }
            }
            else -> {}
        }
        engine.tick()
    }

    private fun updateScanUI() {
        if (screenState != ScreenState.SCAN && screenState != ScreenState.CONFIRM) {
            if (isLookingAtScreen && screenState == ScreenState.IDLE) {
                gazeStatus.text = "注视以唤醒"
            } else if (screenState == ScreenState.IDLE) {
                gazeStatus.text = "注视屏幕开始"
            }
        }
        if (isOperatorMode) {
            confidenceText.text = "置信度: ${(currentConfidence * 100).toInt()}%"
        }
    }

    // ==================== Periodic Publish ====================

    private var periodicPublishJob: Job? = null

    private fun startPeriodicPublish() {
        if (periodicPublishJob?.isActive == true) return
        periodicPublishJob = lifecycleScope.launch {
            while (isActive) {
                delay(PUBLISH_MIN_INTERVAL_MS)
                publishState()
            }
        }
    }

    private fun stopPeriodicPublish() {
        periodicPublishJob?.cancel()
    }

    // ==================== Calibration ====================

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
                    Toast.makeText(this@MainActivity, "校准成功", Toast.LENGTH_SHORT).show()
                }
                delay(1500)
                runOnUiThread { updateUIForState() }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "样本不足，请重试", Toast.LENGTH_SHORT).show()
                    updateUIForState()
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
            Log.e(TAG, "collect sample failed", e)
        }
    }

    // ==================== Coordinator Messages ====================

    private var delayedUiJob: Job? = null

    @Suppress("UNUSED_PARAMETER")
    private fun handleCoordinationMessage(topic: String, message: String) {
        try {
            val json = org.json.JSONObject(message)
            val type = json.optString("type", "")
            val depth = json.optInt("menuDepth", 0)
            runOnUiThread {
                currentMenuDepth = depth
                when (type) {
                    "idle" -> {
                        screenState = ScreenState.IDLE
                        currentMenuDepth = 0
                        delayedUiJob?.cancel()
                        updateUIForState()
                    }
                    "transition" -> {
                        screenState = ScreenState.TRANSITION
                        delayedUiJob?.cancel()
                        updateUIForState()
                        gazeStatus.text = "即将播放选项..."
                    }
                    "announce" -> {
                        screenState = ScreenState.SCAN
                        isAnnouncing = true
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = label
                        applyDepthColor(depth)
                        gazeStatus.text = ""
                        updateUIForState()
                    }
                    "scan_progress" -> {
                        screenState = ScreenState.SCAN
                        isAnnouncing = false
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = label
                        applyDepthColor(depth)
                        gazeStatus.text = "注视[${if (deviceRole == "yes") "是" else "否"}]${if (deviceRole == "yes") "选择" else "跳过"}此项"
                        updateUIForState()
                        arcProgressView.visibility = View.VISIBLE
                    }
                    "confirm" -> {
                        screenState = ScreenState.CONFIRM
                        isAnnouncing = false
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = "确认: $label"
                        applyDepthColor(99)
                        gazeStatus.text = "注视[是]确认选择"
                        updateUIForState()
                    }
                    "action_feedback" -> {
                        val action = json.optString("action", "")
                        when (action) {
                            "select", "confirm" -> soundPool?.play(soundDingDong, 1f, 1f, 1, 0, 1f)
                            "skip", "cancel" -> soundPool?.play(soundWhoosh, 1f, 1f, 1, 0, 1f)
                        }
                    }
                    "skip_feedback" -> {
                        if (deviceRole == "no") {
                            gazeStatus.text = "已跳过"
                            delayedUiJob?.cancel()
                            delayedUiJob = lifecycleScope.launch {
                                delay(800)
                                gazeStatus.text = ""
                                updateUIForState()
                            }
                        }
                    }
                    "selection" -> {
                        val label = json.optString("optionLabel", "")
                        gazeStatus.text = "已选择: $label"
                    }
                    "executed" -> {
                        screenState = ScreenState.FEEDBACK
                        delayedUiJob?.cancel()
                        updateUIForState()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = "已通知\n$label"
                        gazeStatus.text = ""
                        lifecycleScope.launch {
                            delay(2000)
                            screenState = ScreenState.IDLE
                            currentMenuDepth = 0
                            updateUIForState()
                        }
                    }
                    "emergency" -> {
                        // Patient-side: no special emphasis, same as regular scan
                        screenState = ScreenState.SCAN
                        isAnnouncing = false
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "紧急")
                        optionNameText.text = label
                        applyDepthColor(depth)
                        gazeStatus.text = "注视[${if (deviceRole == "yes") "是" else "否"}]${if (deviceRole == "yes") "选择" else "跳过"}此项"
                        updateUIForState()
                        arcProgressView.visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse coordination msg failed", e)
        }
    }

    // ==================== MQTT ====================

    private fun publishState() {
        val now = System.currentTimeMillis()
        if (now - lastPublishTimeMs < PUBLISH_MIN_INTERVAL_MS) return
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
                Log.e(TAG, "MQTT publish failed", e)
            }
        }
    }

    // ==================== Lifecycle ====================

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
            periodicPublishJob?.cancel()
            breathingJob?.cancel()
            gazeProgressJob?.cancel()
            if (::cameraManager.isInitialized) { cameraManager.setPreviewSurface(null); cameraManager.release() }
            if (::faceLandmarkerHelper.isInitialized) faceLandmarkerHelper.clearFaceLandmarker()
            if (::mqttClient.isInitialized) mqttClient.disconnect()
            faceMeshOverlay?.clear()
        } catch (_: Exception) {}
    }
}
