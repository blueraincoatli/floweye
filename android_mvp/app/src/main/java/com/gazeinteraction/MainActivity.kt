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

    // Remote gaze tracking (HOST receives CLIENT gaze via MQTT)
    private data class RemoteGazeState(
        var gazeStartMs: Long = 0L,
        var isLooking: Boolean = false,
        var role: String = "yes",
        var actionConsumed: Boolean = false
    )
    private val remoteGazeStates = HashMap<String, RemoteGazeState>()

    // Local gaze debounce: require look-away before next action
    private var localActionConsumed = false

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
                ttsManager?.onSpeechDone = {
                    coordinatorEngine?.onTtsComplete()
                    // CONFIRM 状态 TTS 播完后，进入等待确认阶段
                    if (coordinatorEngine?.state == CoordinatorEngine.State.CONFIRM && isConfirmAnnouncing) {
                        isConfirmAnnouncing = false
                        runOnUiThread {
                            gazeStartTimeMs = System.currentTimeMillis()
                            localActionConsumed = false
                            updateUIForState()
                        }
                    }
                }
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
        // 同时发布到 MQTT coordination/decision，让其他客户端收到
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected()) {
                val data = mutableMapOf<String, Any>(
                    "type" to type,
                    "timestamp" to System.currentTimeMillis(),
                    "menuDepth" to (coordinatorEngine?.currentDepth ?: 0)
                )
                if (option != null) {
                    data["optionId"] = option.optString("id", "")
                    data["optionLabel"] = option.optString("label", "")
                    data["ttsPrompt"] = option.optString("tts_prompt", "")
                }
                mqttClient.publishCoordinationMessage(data)
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
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        // 主题
        val themeValue = view.findViewById<TextView>(R.id.themeValue)
        themeValue.text = currentTheme.name

        // 角色
        val roleValue = view.findViewById<TextView>(R.id.roleValue)
        roleValue.text = if (deviceRole == "yes") "是" else "否"

        // 操作者模式开关
        val switchOperator = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchOperator)
        switchOperator.isChecked = isOperatorMode

        // 强制主机开关
        val switchForceHost = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchForceHost)
        switchForceHost.isChecked = ::hostManager.isInitialized && hostManager.forceHostMode

        // 连接信息
        val connInfo = view.findViewById<TextView>(R.id.connectionInfo)
        if (::hostManager.isInitialized) {
            connInfo.text = "角色: ${hostManager.role.name} | Broker: ${hostManager.getBrokerHost()}"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(view)
            .setNegativeButton("关闭", null)
            .create()

        // 主题点击
        view.findViewById<View>(R.id.settingTheme).setOnClickListener {
            val themeNames = ThemeConfig.ALL.map { it.name }.toTypedArray()
            val currentIdx = ThemeConfig.ALL.indexOf(currentTheme).coerceAtLeast(0)
            showThemePickerDialog(themeNames, currentIdx)
            themeValue.text = currentTheme.name
        }

        // 角色点击
        view.findViewById<View>(R.id.settingRole).setOnClickListener {
            showRoleSwitchDialog()
            roleValue.text = if (deviceRole == "yes") "是" else "否"
        }

        // 操作者模式
        switchOperator.setOnCheckedChangeListener { _, checked ->
            isOperatorMode = checked
            updateOperatorUI()
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OPERATOR_MODE, isOperatorMode).apply()
        }

        // 强制主机模式
        switchForceHost.setOnCheckedChangeListener { _, checked ->
            if (!::hostManager.isInitialized) return@setOnCheckedChangeListener
            hostManager.forceHostMode = checked
            hostManager.detectRole()
            if (checked) {
                startSelfHosted()
            } else {
                // 关闭强制主机：停止自托管，重新连接
                coordinatorEngine = null
                brokerService?.stop()
                brokerService = null
                hostManager.detectRole()
                // 重新连接 MQTT
                if (::mqttClient.isInitialized) {
                    mqttClient.disconnect()
                    mqttClient.connect(hostManager.getBrokerHost(), hostManager.getBrokerPort())
                }
            }
            connInfo.text = "角色: ${hostManager.role.name} | Broker: ${hostManager.getBrokerHost()}"
            // 同步角色到另一台设备
            publishRoleSync(checked)
        }

        dialog.show()
    }

    private fun publishRoleSync(forceHost: Boolean) {
        if (!::mqttClient.isInitialized || !mqttClient.isConnected()) return
        try {
            val data = mapOf(
                "type" to "role_sync",
                "action" to if (forceHost) "force_host" else "release_host",
                "deviceId" to deviceId,
                "timestamp" to System.currentTimeMillis()
            )
            mqttClient.publishRoleSync(data)
        } catch (_: Exception) {}
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

    /**
     * 选项切换或状态变化后，若用户仍在注视，重新启动光环动画并同步注视计时。
     * 解决连续注视穿过选项边界时光环不收缩的问题。
     */
    private fun restartHaloIfLooking() {
        if (!isLookingAtScreen) return
        gazeStartTimeMs = System.currentTimeMillis()
        perceptionPhaseActive = true
        guidancePhaseActive = false
        Log.w(TAG, "[HALO] restartHalo: restarting gaze progress, screenState=$screenState")
        gazeHaloView.onGazeDetected()
        gazeHaloYes?.onGazeDetected()
        gazeHaloNo?.onGazeDetected()
        startGazeProgress()
    }

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
                    restartHaloIfLooking()
                }
                optionNameText.visibility = View.VISIBLE
                calibrateButton.alpha = 0.3f
                roleButton.alpha = 0.3f
                stopBreathingAnimation()
            }
            ScreenState.CONFIRM -> {
                isAnnouncing = false
                optionNameText.visibility = View.VISIBLE
                applyDepthColor(99)
                calibrateButton.alpha = 0.3f
                roleButton.alpha = 0.3f
                stopBreathingAnimation()
                if (isConfirmAnnouncing) {
                    // TTS 播报中：只显示文字，不显示按钮和光环
                    mainButton.visibility = View.GONE
                    gazeHaloView.visibility = View.GONE
                    arcProgressView.visibility = View.GONE
                } else {
                    // TTS 播完：显示按钮和光环，开始检测视线
                    val label = if (deviceRole == "yes") "是" else "否"
                    mainButton.visibility = View.VISIBLE
                    mainButton.text = label
                    gazeHaloView.visibility = View.VISIBLE
                    gazeHaloView.haloColor = currentTheme.haloColorFor(deviceRole)
                    gazeHaloView.enterConfirmMode()
                    arcProgressView.visibility = View.VISIBLE
                    arcProgressView.arcColor = currentTheme.haloColorFor(deviceRole)
                    arcProgressView.progress = 0f
                    restartHaloIfLooking()
                }
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
                // 持续评估注视动作，随注视时长累积自动触发
                feedCoordinatorGaze(true, currentConfidence)
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
                        if (::hostManager.isInitialized && hostManager.role == HostManager.Role.HOST) {
                            mqttClient.subscribeRemoteGazeStatus()
                        }
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

    // 跟踪上一帧的引擎状态，状态切换时自动重置防抖
    private var lastCoordinatorState: CoordinatorEngine.State? = null
    // CONFIRM 两阶段：TTS 播报中（隐藏按钮/光环）→ TTS 完成后显示按钮开始检测
    private var isConfirmAnnouncing = false

    private fun feedCoordinatorGaze(looking: Boolean, confidence: Float) {
        val engine = coordinatorEngine ?: return
        val interpreter = gazeInterpreter ?: return

        // 引擎状态切换时自动重置防抖（SCAN→CONFIRM等），用户无需移开视线
        if (engine.state != lastCoordinatorState) {
            localActionConsumed = false
            // 离开 CONFIRM 时重置标志
            if (lastCoordinatorState == CoordinatorEngine.State.CONFIRM) {
                isConfirmAnnouncing = false
            }
            lastCoordinatorState = engine.state
            gazeStartTimeMs = System.currentTimeMillis()
        }

        // Debounce: require look-away after each action
        if (!looking) {
            localActionConsumed = false
            engine.tick()
            return
        }
        if (localActionConsumed) {
            engine.tick()
            return
        }

        val elapsed = System.currentTimeMillis() - gazeStartTimeMs

        when (engine.state) {
            CoordinatorEngine.State.IDLE -> {
                if (interpreter.evaluateWake(looking, confidence, gazeStartTimeMs)) {
                    Log.w(TAG, "[FEED] WAKE triggered, elapsed=${elapsed}ms")
                    engine.handleAction("wake")
                    gazeStartTimeMs = System.currentTimeMillis()
                    localActionConsumed = true
                }
            }
            CoordinatorEngine.State.SCAN -> {
                // 播报阶段：阻止注视动作，持续重置注视计时
                if (engine.scanPhase == "announce") {
                    gazeStartTimeMs = System.currentTimeMillis()
                    localActionConsumed = false
                    engine.tick()
                    return
                }
                val action = interpreter.evaluate(deviceRole, looking, confidence, gazeStartTimeMs)
                Log.w(TAG, "[FEED] SCAN role=$deviceRole elapsed=${elapsed}ms conf=$confidence action=$action")
                when (action) {
                    "select", "skip" -> {
                        Log.w(TAG, "[FEED] SCAN action='$action' -> handleAction (consumed)")
                        engine.handleAction(action)
                        gazeStartTimeMs = System.currentTimeMillis()
                        localActionConsumed = true
                    }
                    "hesitate" -> {
                        // 仅重置引擎 dwell 计时器，不消费注视、不重置注视计时
                        engine.handleAction(action)
                    }
                }
            }
            CoordinatorEngine.State.CONFIRM -> {
                // TTS 播报阶段：不检测视线，等 TTS 完成后 isConfirmAnnouncing 变为 false
                if (isConfirmAnnouncing) {
                    engine.tick()
                    return
                }
                if (deviceRole == "yes") {
                    val action = interpreter.evaluate("yes", looking, confidence, gazeStartTimeMs)
                    Log.w(TAG, "[FEED] CONFIRM yes elapsed=${elapsed}ms conf=$confidence action=$action")
                    if (action == "select") {
                        Log.w(TAG, "[FEED] CONFIRM -> handleAction('confirm')")
                        engine.handleAction("confirm")
                        gazeStartTimeMs = System.currentTimeMillis()
                        localActionConsumed = true
                    }
                } else if (deviceRole == "no") {
                    val action = interpreter.evaluate("no", looking, confidence, gazeStartTimeMs)
                    Log.w(TAG, "[FEED] CONFIRM no elapsed=${elapsed}ms conf=$confidence action=$action")
                    if (action == "skip") {
                        Log.w(TAG, "[FEED] CONFIRM -> handleAction('cancel')")
                        engine.handleAction("cancel")
                        gazeStartTimeMs = System.currentTimeMillis()
                        localActionConsumed = true
                    }
                }
            }
            else -> {}
        }
        engine.tick()
    }

    private fun handleRemoteGaze(remoteDeviceId: String, role: String, looking: Boolean, confidence: Float, gazeDurationMs: Long) {
        val engine = coordinatorEngine ?: return
        val interpreter = gazeInterpreter ?: return

        val state = remoteGazeStates.getOrPut(remoteDeviceId) { RemoteGazeState(role = role) }
        state.role = role

        // 引擎状态切换时自动重置远程设备防抖
        if (engine.state != lastCoordinatorState) {
            state.actionConsumed = false
            state.isLooking = false
            lastCoordinatorState = engine.state
        }

        // Debounce: reset on look-away, block until then
        if (!looking) {
            state.isLooking = false
            state.actionConsumed = false
            return
        }
        if (state.actionConsumed) {
            Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) blocked: actionConsumed")
            return
        }

        // 播报阶段：阻止注视动作，持续重置状态
        if (engine.state == CoordinatorEngine.State.SCAN && engine.scanPhase == "announce") {
            state.actionConsumed = false
            state.isLooking = false
            return
        }

        // 用副机消息中携带的注视时长反推起始时间，避免MQTT延迟导致的时长丢失
        val effectiveStartMs = if (gazeDurationMs > 0) {
            System.currentTimeMillis() - gazeDurationMs
        } else {
            // 兼容旧消息：从首次收到looking=true开始计时
            if (!state.isLooking) state.gazeStartMs = System.currentTimeMillis()
            state.gazeStartMs
        }
        state.isLooking = true

        val duration = System.currentTimeMillis() - effectiveStartMs
        when (engine.state) {
            CoordinatorEngine.State.IDLE -> {
                if (interpreter.evaluateWake(looking, confidence, effectiveStartMs)) {
                    Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) WAKE engineState=${engine.state}")
                    engine.handleAction("wake")
                    state.actionConsumed = true
                }
            }
            CoordinatorEngine.State.SCAN -> {
                val action = interpreter.evaluate(role, looking, confidence, effectiveStartMs)
                Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) SCAN engineState=${engine.state} scanPhase=${engine.scanPhase} duration=${duration}ms conf=$confidence action=$action")
                when (action) {
                    "select", "skip" -> {
                        Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) -> handleAction('$action')")
                        engine.handleAction(action)
                        state.actionConsumed = true
                    }
                    "hesitate" -> {
                        engine.handleAction(action)
                    }
                }
            }
            CoordinatorEngine.State.CONFIRM -> {
                // TTS 播报阶段：不检测视线
                if (isConfirmAnnouncing) {
                    Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) CONFIRM blocked: isConfirmAnnouncing")
                    return
                }
                Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) CONFIRM duration=${duration}ms")
                if (role == "yes") {
                    val action = interpreter.evaluate("yes", looking, confidence, effectiveStartMs)
                    if (action == "select") {
                        Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) -> handleAction('confirm')")
                        engine.handleAction("confirm")
                        state.actionConsumed = true
                    }
                } else if (role == "no") {
                    val action = interpreter.evaluate("no", looking, confidence, effectiveStartMs)
                    Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) CONFIRM cancel eval: action=$action")
                    if (action == "skip") {
                        Log.w(TAG, "[REMOTE] $remoteDeviceId ($role) -> handleAction('cancel')")
                        engine.handleAction("cancel")
                        state.actionConsumed = true
                    }
                }
            }
            else -> {}
        }
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

            // Route remote gaze status to coordinator (dual-device mode)
            if (json.has("lookingAtScreen")) {
                val remoteDeviceId = json.optString("deviceId", "")
                if (remoteDeviceId.isNotEmpty() && remoteDeviceId != deviceId) {
                    Log.w(TAG, "[ROUTE] remote gaze from $remoteDeviceId role=${json.optString("role")} looking=${json.optBoolean("lookingAtScreen")} dur=${json.optLong("gazeDurationMs")}")
                    handleRemoteGaze(
                        remoteDeviceId,
                        json.optString("role", "yes"),
                        json.optBoolean("lookingAtScreen", false),
                        json.optDouble("confidence", 0.0).toFloat(),
                        json.optLong("gazeDurationMs", 0L)
                    )
                }
                return
            }

            // 角色同步消息
            if (json.optString("type") == "role_sync") {
                val action = json.optString("action", "")
                val fromDeviceId = json.optString("deviceId", "")
                Log.w(TAG, "[ROLE_SYNC] received $action from $fromDeviceId")
                if (action == "force_host" && fromDeviceId != deviceId) {
                    // 另一台设备开启了强制主机，本机自动退出
                    runOnUiThread {
                        if (::hostManager.isInitialized && hostManager.forceHostMode) {
                            hostManager.forceHostMode = false
                            hostManager.detectRole()
                            coordinatorEngine = null
                            brokerService?.stop()
                            brokerService = null
                            if (::mqttClient.isInitialized) {
                                mqttClient.disconnect()
                                mqttClient.connect(hostManager.getBrokerHost(), hostManager.getBrokerPort())
                            }
                            Toast.makeText(this, "已自动切换为副机", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return
            }

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
                        isConfirmAnnouncing = true  // 先播 TTS，播完才显示按钮
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = "确认: $label"
                        applyDepthColor(99)
                        gazeStatus.text = ""
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
                    "gazeDurationMs" to (if (isLookingAtScreen) System.currentTimeMillis() - gazeStartTimeMs else 0L),
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
