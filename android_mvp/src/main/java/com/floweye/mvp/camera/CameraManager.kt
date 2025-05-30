package com.floweye.mvp.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 摄像头管理器
 * 负责Camera2 API的封装和MediaPipe集成
 */
class CameraManager(
    private val context: Context,
    private val faceLandmarker: FaceLandmarker,
    private val onResults: (FaceLandmarkerResult, MPImage) -> Unit,
    private val onError: (Exception) -> Unit
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "CameraManager"
        private const val MAX_PREVIEW_WIDTH = 1280
        private const val MAX_PREVIEW_HEIGHT = 720
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val cameraOpenCloseLock = Semaphore(1)

    private var previewSize: Size? = null
    private var cameraId: String? = null

    /**
     * 检查摄像头权限
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 开启摄像头
     */
    fun openCamera(previewSurface: Surface) {
        if (!hasCameraPermission()) {
            onError(SecurityException("Camera permission not granted"))
            return
        }

        startBackgroundThread()
        setupCamera()
        openCameraDevice(previewSurface)
    }

    /**
     * 关闭摄像头
     */
    fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
            stopBackgroundThread()
        }
    }

    /**
     * 设置摄像头
     */
    private fun setupCamera() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                
                // 只使用前置摄像头
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // 选择合适的预览尺寸
                previewSize = chooseOptimalSize(
                    map.getOutputSizes(ImageFormat.YUV_420_888),
                    MAX_PREVIEW_WIDTH,
                    MAX_PREVIEW_HEIGHT
                )

                cameraId = id
                break
            }
        } catch (e: CameraAccessException) {
            onError(e)
        }
    }

    /**
     * 选择最优预览尺寸
     */
    private fun chooseOptimalSize(choices: Array<Size>, maxWidth: Int, maxHeight: Int): Size {
        val bigEnough = mutableListOf<Size>()
        val notBigEnough = mutableListOf<Size>()

        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight) {
                if (option.width >= 640 && option.height >= 480) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        return when {
            bigEnough.isNotEmpty() -> bigEnough.minByOrNull { it.width * it.height } ?: choices[0]
            notBigEnough.isNotEmpty() -> notBigEnough.maxByOrNull { it.width * it.height } ?: choices[0]
            else -> choices[0]
        }
    }

    /**
     * 打开摄像头设备
     */
    private fun openCameraDevice(previewSurface: Surface) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cameraId ?: return

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            // 创建ImageReader用于MediaPipe处理
            val size = previewSize ?: return
            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)

            manager.openCamera(id, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            onError(e)
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * 摄像头状态回调
     */
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
            onError(RuntimeException("Camera error: $error"))
        }
    }

    /**
     * 创建预览会话
     */
    private fun createCameraPreviewSession() {
        try {
            val camera = cameraDevice ?: return
            val reader = imageReader ?: return

            // 创建预览请求
            val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(reader.surface)

            // 设置自动对焦和曝光
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )

            // 创建捕获会话
            camera.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return

                        captureSession = session
                        try {
                            session.setRepeatingRequest(
                                previewRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            onError(e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError(RuntimeException("Failed to configure camera"))
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            onError(e)
        }
    }

    /**
     * 图像可用监听器
     */
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        
        try {
            // 转换为MPImage
            val mpImage = convertToMPImage(image)
            
            // 创建处理选项
            val options = ImageProcessingOptions.builder()
                .setRotationDegrees(0) // 根据需要调整
                .build()

            // 异步处理
            val timestamp = System.currentTimeMillis()
            faceLandmarker.detectAsync(mpImage, timestamp, options)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            onError(e)
        } finally {
            image.close()
        }
    }

    /**
     * 转换Android Image为MPImage
     */
    private fun convertToMPImage(image: Image): MPImage {
        // 简化实现：这里需要实际的YUV到RGB转换
        // 在实际项目中，需要处理YUV_420_888格式的转换
        
        // 临时实现：创建一个空的bitmap
        val bitmap = android.graphics.Bitmap.createBitmap(
            image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888
        )
        
        return BitmapImageBuilder(bitmap).build()
    }

    /**
     * 启动后台线程
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    /**
     * 停止后台线程
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    // Lifecycle callbacks
    override fun onDestroy(owner: LifecycleOwner) {
        closeCamera()
    }
}