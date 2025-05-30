package com.floweye.mvp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.floweye.mvp.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    
    // MediaPipe相关
    private var faceLandmarker: FaceLandmarker? = null
    private var isMediaPipeInitialized = false
    
    // 视线检测器
    private val gazeDetector = GazeDetector()
    
    // 协程作用域
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "FloweEyeMVP"
        private const val FACE_LANDMARKER_MODEL_FILE = "face_landmarker.task"
    }
    
    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "摄像头权限已授予")
            setupCamera()
        } else {
            Log.e(TAG, "摄像头权限被拒绝")
            showToast(getString(R.string.camera_permission_required))
            updateStatus("权限被拒绝")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化执行器
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // 设置UI
        setupUI()
        
        // 检查并请求权限
        checkCameraPermission()
        
        // 初始化MediaPipe
        initializeMediaPipe()
    }
    
    private fun setupUI() {
        updateStatus("初始化中...")
        updateDetectionResult("等待检测...")
        
        // 设置按钮点击事件（用于测试）
        binding.yesButton.setOnClickListener {
            showToast("点击了：是")
        }
        
        binding.noButton.setOnClickListener {
            showToast("点击了：否")
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "摄像头权限已存在")
                setupCamera()
            }
            else -> {
                Log.d(TAG, "请求摄像头权限")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun initializeMediaPipe() {
        mainScope.launch {
            try {
                updateStatus("初始化MediaPipe...")
                
                withContext(Dispatchers.IO) {
                    // 创建MediaPipe选项 - 使用CPU delegate确保华为设备兼容性
                    val baseOptions = BaseOptions.builder()
                        .setModelAssetPath(FACE_LANDMARKER_MODEL_FILE)
                        .setDelegate(BaseOptions.Delegate.CPU) // 强制使用CPU以避免GMS依赖
                        .build()
                    
                    val options = FaceLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumFaces(1)
                        .setMinFaceDetectionConfidence(0.5f)
                        .setMinFacePresenceConfidence(0.5f)
                        .setMinTrackingConfidence(0.5f)
                        .setOutputFaceBlendshapes(true)
                        .setOutputFacialTransformationMatrixes(true)
                        .setResultListener { result, inputImage ->
                            handleFaceLandmarkerResult(result, inputImage)
                        }
                        .setErrorListener { error ->
                            Log.e(TAG, "MediaPipe错误: ${error.message}")
                            runOnUiThread {
                                updateStatus("MediaPipe错误: ${error.message}")
                            }
                        }
                        .build()
                    
                    faceLandmarker = FaceLandmarker.createFromOptions(this@MainActivity, options)
                }
                
                isMediaPipeInitialized = true
                updateStatus("MediaPipe初始化成功")
                Log.d(TAG, "MediaPipe初始化成功")
                
            } catch (e: Exception) {
                Log.e(TAG, "MediaPipe初始化失败", e)
                updateStatus("MediaPipe初始化失败: ${e.message}")
                showToast(getString(R.string.mediapipe_init_failed))
            }
        }
    }
    
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                updateStatus("摄像头已启动")
            } catch (e: Exception) {
                Log.e(TAG, "摄像头初始化失败", e)
                updateStatus("摄像头初始化失败")
                showToast(getString(R.string.camera_init_failed))
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        // 预览用例
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
        
        // 图像分析用例
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, FaceAnalyzer())
            }
        
        // 选择前置摄像头
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        
        try {
            // 解绑所有用例
            cameraProvider.unbindAll()
            
            // 绑定用例到生命周期
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "绑定摄像头用例失败", e)
            updateStatus("绑定摄像头失败")
        }
    }
    
    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (!isMediaPipeInitialized || faceLandmarker == null) {
                imageProxy.close()
                return
            }
            
            try {
                // 转换ImageProxy为MPImage
                val bitmap = imageProxy.toBitmap()
                val mpImage = BitmapImageBuilder(bitmap).build()
                
                // 获取时间戳
                val frameTime = System.currentTimeMillis()
                
                // 异步检测
                faceLandmarker?.detectAsync(mpImage, frameTime)
                
            } catch (e: Exception) {
                Log.e(TAG, "图像分析错误", e)
            } finally {
                imageProxy.close()
            }
        }
    }
    
    private fun handleFaceLandmarkerResult(
        result: FaceLandmarkerResult,
        inputImage: MPImage
    ) {
        runOnUiThread {
            if (result.faceLandmarks().isNotEmpty()) {
                updateDetectionResult("检测到人脸")
                
                // 进行视线检测
                val gazeResult = gazeDetector.detectGaze(result)
                
                // 更新UI状态
                updateGazeResult(gazeResult)
                
                // 更新按钮状态
                updateButtonStates(gazeResult)
                
            } else {
                updateDetectionResult("未检测到人脸")
                updateStatus("等待检测人脸...")
                resetButtonStates()
            }
        }
    }
    
    private fun updateStatus(status: String) {
        binding.statusText.text = status
        Log.d(TAG, "状态更新: $status")
    }
    
    private fun updateDetectionResult(result: String) {
        binding.detectionText.text = result
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun updateGazeResult(gazeResult: GazeDetector.GazeResult) {
        val statusText = when (gazeResult.gazeTarget) {
            GazeDetector.GazeTarget.YES_AREA -> getString(R.string.looking_at_yes)
            GazeDetector.GazeTarget.NO_AREA -> getString(R.string.looking_at_no)
            GazeDetector.GazeTarget.LOOKING_AWAY -> getString(R.string.looking_away)
            GazeDetector.GazeTarget.DETECTION_UNSTABLE -> "检测不稳定"
        }
        
        updateStatus("$statusText (置信度: ${String.format("%.2f", gazeResult.confidence)})")
        updateDetectionResult(gazeResult.debugInfo)
    }
    
    private fun updateButtonStates(gazeResult: GazeDetector.GazeResult) {
        // 重置按钮状态
        binding.yesButton.alpha = 1.0f
        binding.noButton.alpha = 1.0f
        
        // 根据视线检测结果高亮按钮
        when (gazeResult.gazeTarget) {
            GazeDetector.GazeTarget.YES_AREA -> {
                if (gazeResult.confidence > 0.6f) {
                    binding.yesButton.alpha = 1.0f
                    binding.noButton.alpha = 0.5f
                }
            }
            GazeDetector.GazeTarget.NO_AREA -> {
                if (gazeResult.confidence > 0.6f) {
                    binding.yesButton.alpha = 0.5f
                    binding.noButton.alpha = 1.0f
                }
            }
            else -> {
                // 保持默认状态
            }
        }
    }
    
    private fun resetButtonStates() {
        binding.yesButton.alpha = 1.0f
        binding.noButton.alpha = 1.0f
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceLandmarker?.close()
        mainScope.cancel()
    }
}

// 扩展函数：将ImageProxy转换为Bitmap
private fun ImageProxy.toBitmap(): android.graphics.Bitmap {
    val yBuffer = planes[0].buffer // Y
    val vuBuffer = planes[2].buffer // VU
    
    val ySize = yBuffer.remaining()
    val vuSize = vuBuffer.remaining()
    
    val nv21 = ByteArray(ySize + vuSize)
    
    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)
    
    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, this.width, this.height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}