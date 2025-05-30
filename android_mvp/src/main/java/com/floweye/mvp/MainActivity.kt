package com.floweye.mvp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.floweye.mvp.camera.CameraManager
import com.floweye.mvp.gaze.GazeDetector
import com.floweye.mvp.ui.OverlayView
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val MODEL_FILE = "face_landmarker.task"
    }

    // UI组件
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var yesButton: android.widget.Button
    private lateinit var noButton: android.widget.Button
    private lateinit var statusText: android.widget.TextView
    private lateinit var confidenceText: android.widget.TextView
    private lateinit var debugText: android.widget.TextView

    // 核心组件
    private var faceLandmarker: FaceLandmarker? = null
    private var cameraManager: CameraManager? = null
    private val gazeDetector = GazeDetector()

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeCamera()
        } else {
            showError("摄像头权限被拒绝，无法使用视线检测功能")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeMediaPipe()
    }

    /**
     * 初始化视图
     */
    private fun initializeViews() {
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        yesButton = findViewById(R.id.yesButton)
        noButton = findViewById(R.id.noButton)
        statusText = findViewById(R.id.statusText)
        confidenceText = findViewById(R.id.confidenceText)
        debugText = findViewById(R.id.debugText)

        // 设置按钮点击事件（用于测试）
        yesButton.setOnClickListener {
            Toast.makeText(this, "点击了是按钮", Toast.LENGTH_SHORT).show()
        }

        noButton.setOnClickListener {
            Toast.makeText(this, "点击了否按钮", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 初始化MediaPipe
     */
    private fun initializeMediaPipe() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 创建FaceLandmarker选项
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_FILE)
                    .setDelegate(BaseOptions.Delegate.CPU) // 强制使用CPU以确保华为兼容性
                    .build()

                val options = FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumFaces(1) // 只检测一张脸
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinFacePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setOutputFaceBlendshapes(true) // 启用Blendshapes
                    .setOutputFacialTransformationMatrixes(true) // 启用变换矩阵
                    .setResultListener(::onFaceLandmarkerResult)
                    .setErrorListener(::onFaceLandmarkerError)
                    .build()

                faceLandmarker = FaceLandmarker.createFromOptions(this@MainActivity, options)

                withContext(Dispatchers.Main) {
                    checkCameraPermissionAndStart()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MediaPipe", e)
                withContext(Dispatchers.Main) {
                    showError("MediaPipe初始化失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 检查摄像头权限并启动
     */
    private fun checkCameraPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * 初始化摄像头
     */
    private fun initializeCamera() {
        val landmarker = faceLandmarker
        if (landmarker == null) {
            showError("MediaPipe未初始化")
            return
        }

        cameraManager = CameraManager(
            context = this,
            faceLandmarker = landmarker,
            onResults = ::onCameraResults,
            onError = ::onCameraError
        )

        lifecycle.addObserver(cameraManager!!)

        // 启动摄像头
        cameraManager?.openCamera(previewView.surfaceProvider.surface)
    }

    /**
     * FaceLandmarker结果回调
     */
    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, image: MPImage) {
        runOnUiThread {
            // 更新覆盖层
            overlayView.updateResults(result, image.width, image.height)

            // 进行视线检测
            val gazeResult = gazeDetector.detectGaze(result)

            // 更新UI
            updateUI(gazeResult)
        }
    }

    /**
     * FaceLandmarker错误回调
     */
    private fun onFaceLandmarkerError(error: RuntimeException) {
        Log.e(TAG, "FaceLandmarker error", error)
        runOnUiThread {
            showError("人脸检测错误: ${error.message}")
        }
    }

    /**
     * 摄像头结果回调
     */
    private fun onCameraResults(result: FaceLandmarkerResult, image: MPImage) {
        // 这个方法可能不会被调用，因为我们使用的是异步检测
        // 结果会通过onFaceLandmarkerResult返回
    }

    /**
     * 摄像头错误回调
     */
    private fun onCameraError(error: Exception) {
        Log.e(TAG, "Camera error", error)
        runOnUiThread {
            showError("摄像头错误: ${error.message}")
        }
    }

    /**
     * 更新UI
     */
    private fun updateUI(gazeResult: GazeDetector.GazeResult) {
        // 更新状态文本
        val statusResId = when (gazeResult.gazeTarget) {
            GazeDetector.GazeTarget.YES_AREA -> R.string.looking_at_yes
            GazeDetector.GazeTarget.NO_AREA -> R.string.looking_at_no
            GazeDetector.GazeTarget.LOOKING_AWAY -> R.string.looking_away
            GazeDetector.GazeTarget.DETECTION_UNSTABLE -> R.string.detection_unstable
        }
        statusText.setText(statusResId)

        // 更新置信度
        confidenceText.text = getString(R.string.confidence, gazeResult.confidence)

        // 更新调试信息
        debugText.text = gazeResult.debugInfo
        debugText.visibility = android.view.View.VISIBLE

        // 更新按钮状态
        updateButtonStates(gazeResult)
    }

    /**
     * 更新按钮状态
     */
    private fun updateButtonStates(gazeResult: GazeDetector.GazeResult) {
        // 重置按钮颜色
        yesButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.yes_button_normal)
        noButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.no_button_normal)

        // 高亮当前视线目标
        when (gazeResult.gazeTarget) {
            GazeDetector.GazeTarget.YES_AREA -> {
                yesButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.yes_button_active)
            }
            GazeDetector.GazeTarget.NO_AREA -> {
                noButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.no_button_active)
            }
            else -> {
                // 保持默认颜色
            }
        }
    }

    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        faceLandmarker?.close()
    }
}