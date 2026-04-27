package com.gazeinteraction.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * MediaPipe Face Landmarker 封装类
 *
 * 针对华为无GMS设备优化：
 * 1. 使用 tasks-vision-android 版本（无GMS依赖）
 * 2. 强制使用CPU Delegate确保稳定性
 * 3. 关闭未使用的 Blendshapes/变换矩阵输出以节省 CPU
 * 4. 虹膜关键点(468-477)由模型固有输出，无需额外开关
 * 5. 实时流模式处理
 */
class FaceLandmarkerHelper(
    context: Context,
    private val landmarkerListener: LandmarkerListener
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "FaceLandmarkerHelper"
        private const val MODEL_ASSET_PATH = "face_landmarker_v2_with_blendshapes.task"
        private const val MAX_NUM_FACES = 1 // 单人脸检测
        private const val MIN_FACE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_FACE_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
    }
    
    // MediaPipe组件
    private var faceLandmarker: FaceLandmarker? = null
    private var isInitialized = false
    
    /**
     * 结果监听器接口
     */
    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = 0)
        fun onResults(resultBundle: ResultBundle)
    }
    
    /**
     * 结果数据包装类
     */
    data class ResultBundle(
        val results: FaceLandmarkerResult,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inputImageRotation: Int = 0
    )
    
    /**
     * 初始化Face Landmarker
     */
    fun initialize() {
        try {
            // 创建BaseOptions - 华为设备专用配置
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .setDelegate(Delegate.CPU) // 强制使用CPU，确保华为设备兼容性
                .build()
            
            // 创建FaceLandmarkerOptions
            val options = FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(MAX_NUM_FACES)
                .setMinFaceDetectionConfidence(MIN_FACE_DETECTION_CONFIDENCE)
                .setMinFacePresenceConfidence(MIN_FACE_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setOutputFaceBlendshapes(false) // 未使用，关闭以节省CPU
                .setOutputFacialTransformationMatrixes(false) // 未使用，关闭以节省CPU
                .setResultListener { result, inputImage ->
                    // 异步处理结果
                    handleLandmarkerResult(result, inputImage)
                }
                .setErrorListener { error ->
                    // 错误处理
                    handleLandmarkerError(error)
                }
                .build()
            
            // 创建FaceLandmarker实例
            faceLandmarker = FaceLandmarker.createFromOptions(appContext, options)
            isInitialized = true
            
            Log.i(TAG, "MediaPipe Face Landmarker 初始化成功")
            
        } catch (e: Exception) {
            val errorMsg = "MediaPipe Face Landmarker 初始化失败: ${e.message}"
            Log.e(TAG, errorMsg, e)
            landmarkerListener.onError(errorMsg, -1)
        }
    }
    
    /**
     * 处理实时视频流
     */
    fun detectLiveStream(bitmap: Bitmap, frameTimeMs: Long) {
        if (!isInitialized || faceLandmarker == null) {
            landmarkerListener.onError("Face Landmarker 未初始化")
            return
        }
        
        try {
            // 创建MPImage
            val mpImage = BitmapImageBuilder(bitmap).build()

            Log.d(TAG, "detectAsync: bitmap=${bitmap.width}x${bitmap.height}, ts=$frameTimeMs")
            // 异步检测
            faceLandmarker?.detectAsync(mpImage, frameTimeMs)
            
        } catch (e: Exception) {
            val errorMsg = "检测失败: ${e.message}"
            Log.e(TAG, errorMsg, e)
            landmarkerListener.onError(errorMsg)
        }
    }
    
    /**
     * 处理检测结果
     */
    private fun handleLandmarkerResult(result: FaceLandmarkerResult, inputImage: MPImage) {
        try {
            val resultBundle = ResultBundle(
                results = result,
                inputImageHeight = inputImage.height,
                inputImageWidth = inputImage.width,
                inputImageRotation = 0
            )
            
            // 在主线程回调结果
            mainHandler.post {
                landmarkerListener.onResults(resultBundle)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理检测结果失败", e)
            landmarkerListener.onError("处理检测结果失败: ${e.message}")
        }
    }
    
    /**
     * 处理检测错误
     */
    private fun handleLandmarkerError(error: RuntimeException) {
        val errorMsg = "MediaPipe 检测错误: ${error.message}"
        Log.e(TAG, errorMsg, error)
        
        mainHandler.post {
            landmarkerListener.onError(errorMsg, -2)
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * 获取支持的最大人脸数量
     */
    fun getMaxNumFaces(): Int = MAX_NUM_FACES
    
    /**
     * 清理Face Landmarker
     */
    fun clearFaceLandmarker() {
        try {
            faceLandmarker?.close()
            faceLandmarker = null
            isInitialized = false
            Log.i(TAG, "Face Landmarker 已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理Face Landmarker失败", e)
        }
    }
    
    /**
     * 重新初始化（用于错误恢复）
     */
    fun reinitialize() {
        clearFaceLandmarker()
        initialize()
    }
    
    /**
     * 获取模型信息
     */
    fun getModelInfo(): Map<String, Any> {
        return mapOf(
            "modelPath" to MODEL_ASSET_PATH,
            "delegate" to "CPU",
            "maxFaces" to MAX_NUM_FACES,
            "faceDetectionConfidence" to MIN_FACE_DETECTION_CONFIDENCE,
            "facePresenceConfidence" to MIN_FACE_PRESENCE_CONFIDENCE,
            "trackingConfidence" to MIN_TRACKING_CONFIDENCE,
            "blendshapes" to false,
            "transformationMatrix" to false
        )
    }
}
