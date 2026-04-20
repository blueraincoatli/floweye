package com.gazeinteraction.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface

/**
 * Camera2 API管理类
 * 
 * 专门针对华为无GMS设备进行优化：
 * 1. 使用Camera2 API而非CameraX
 * 2. 强制曝光和对焦锁定
 * 3. 优化分辨率和帧率配置
 * 4. 处理厂商兼容性问题
 */
class CameraManager(context: Context) {

    private val appContext = context.applicationContext
    
    companion object {
        private const val TAG = "CameraManager"
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
        private const val TARGET_FPS = 30
    }
    
    // Camera2 组件
    private var cameraManager: android.hardware.camera2.CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    // 摄像头参数
    private var cameraId: String = ""
    private var sensorOrientation: Int = 0
    private var previewSize: Size = Size(TARGET_WIDTH, TARGET_HEIGHT)
    
    // 回调
    private var frameCallback: ((Bitmap) -> Unit)? = null

    // 调试预览 Surface（可选）
    private var previewSurface: Surface? = null

    // 状态
    private var isInitialized = false
    private var isCapturing = false

    /**
     * 设置调试预览 Surface。
     * 在 createCaptureSession 时会同时输出到该 Surface。
     */
    fun setPreviewSurface(surface: Surface?) {
        previewSurface = surface
    }

    /**
     * 初始化摄像头管理器
     */
    fun initialize(onFrameAvailable: (Bitmap) -> Unit) {
        frameCallback = onFrameAvailable
        
        try {
            startBackgroundThread()
            setupCamera()
            isInitialized = true
            Log.i(TAG, "摄像头管理器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "摄像头管理器初始化失败", e)
            throw e
        }
    }
    
    /**
     * 启动后台线程
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper ?: throw IllegalStateException("后台线程启动失败"))
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
            Log.e(TAG, "停止后台线程时发生异常", e)
        }
    }
    
    /**
     * 设置摄像头参数
     */
    @SuppressLint("MissingPermission")
    private fun setupCamera() {
        cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        
        try {
            // 查找前置摄像头
            for (id in cameraManager?.cameraIdList ?: emptyArray()) {
                val characteristics = cameraManager?.getCameraCharacteristics(id)
                val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
                
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id
                    
                    // 获取传感器方向
                    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                    
                    // 选择最优预览尺寸
                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    previewSize = chooseOptimalSize(map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray())
                    
                    Log.i(TAG, "选择前置摄像头: $cameraId, 分辨率: $previewSize, 传感器方向: $sensorOrientation")
                    break
                }
            }
            
            if (cameraId.isEmpty()) {
                throw RuntimeException("未找到前置摄像头")
            }
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "访问摄像头失败", e)
            throw e
        }
    }
    
    /**
     * 选择最优预览尺寸
     */
    private fun chooseOptimalSize(sizes: Array<Size>): Size {
        val preferredRatio = TARGET_WIDTH.toDouble() / TARGET_HEIGHT
        var bestSize = sizes.firstOrNull() ?: Size(TARGET_WIDTH, TARGET_HEIGHT)
        var bestRatioDiff = Double.MAX_VALUE
        
        for (size in sizes) {
            // 限制最大尺寸以确保性能
            if (size.width > 1280 || size.height > 720) continue
            
            val ratio = size.width.toDouble() / size.height
            val ratioDiff = Math.abs(ratio - preferredRatio)
            
            if (ratioDiff < bestRatioDiff) {
                bestRatioDiff = ratioDiff
                bestSize = size
            }
        }
        
        Log.i(TAG, "选择的预览尺寸: ${bestSize.width}x${bestSize.height}")
        return bestSize
    }
    
    /**
     * 启动摄像头
     */
    @SuppressLint("MissingPermission")
    fun startCamera() {
        if (!isInitialized || isCapturing) return
        
        try {
            cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                    Log.i(TAG, "摄像头已打开")
                }
                
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    Log.w(TAG, "摄像头连接断开")
                }
                
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e(TAG, "摄像头错误: $error")
                }
            }, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "打开摄像头失败", e)
        }
    }
    
    /**
     * 创建捕获会话
     */
    private fun createCaptureSession() {
        try {
            // 创建ImageReader
            imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.YUV_420_888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader?.acquireLatestImage()
                    image?.let { processImage(it) }
                }, backgroundHandler)
            }
            
            val surfaces = mutableListOf(imageReader?.surface)
            previewSurface?.let { surfaces.add(it) }
            
            cameraDevice?.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startPreview()
                        Log.i(TAG, "捕获会话已配置")
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "捕获会话配置失败")
                    }
                },
                backgroundHandler
            )
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "创建捕获会话失败", e)
        }
    }
    
    /**
     * 开始预览
     */
    private fun startPreview() {
        try {
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder?.addTarget(imageReader?.surface ?: return)
            
            // 针对华为设备的关键配置
            configureForHuaweiDevice(requestBuilder)
            
            val captureRequest = requestBuilder.build()
            captureSession?.setRepeatingRequest(captureRequest, null, backgroundHandler)
            isCapturing = true
            
            Log.i(TAG, "开始预览捕获")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "开始预览失败", e)
        }
    }
    
    /**
     * 华为设备专用配置
     */
    private fun configureForHuaweiDevice(requestBuilder: CaptureRequest.Builder) {
        try {
            // 1. 设置自动对焦模式
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

            // 2. 设置自动曝光模式
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

            // 3. 锁定白平衡（减少颜色变化）
            requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

            // 4. 设置帧率范围（自适应设备支持的范围）
            val fpsRange = selectBestFpsRange()
            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)

            // 5. 启用防抖（仅在支持时设置）
            trySetOisIfSupported(requestBuilder)

            Log.i(TAG, "华为设备专用配置已应用, 帧率: $fpsRange")

        } catch (e: Exception) {
            Log.w(TAG, "部分华为设备配置可能不受支持", e)
        }
    }

    /**
     * 查询设备支持的帧率范围，选择最接近目标帧率的范围
     */
    private fun selectBestFpsRange(): android.util.Range<Int> {
        val targetRange = android.util.Range(TARGET_FPS, TARGET_FPS)
        try {
            val characteristics = cameraManager?.getCameraCharacteristics(cameraId) ?: return targetRange
            val availableRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            if (availableRanges != null && availableRanges.isNotEmpty()) {
                // 优先找固定帧率范围，其次找包含目标帧率的范围
                val exactMatch = availableRanges.firstOrNull { it == targetRange }
                if (exactMatch != null) return exactMatch

                val containingMatch = availableRanges.firstOrNull {
                    it.lower <= TARGET_FPS && it.upper >= TARGET_FPS
                }
                if (containingMatch != null) return containingMatch

                // 回退：选择上限最接近目标的范围
                return availableRanges.minByOrNull { Math.abs(it.upper - TARGET_FPS) } ?: targetRange
            }
        } catch (e: Exception) {
            Log.w(TAG, "查询帧率范围失败，使用默认值", e)
        }
        return targetRange
    }

    /**
     * 检测设备是否支持 OIS，仅在支持时才设置
     */
    private fun trySetOisIfSupported(requestBuilder: CaptureRequest.Builder) {
        try {
            val characteristics = cameraManager?.getCameraCharacteristics(cameraId) ?: return
            val availableModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            if (availableModes?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) {
                requestBuilder.set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "OIS 检测跳过", e)
        }
    }
    
    /**
     * 处理图像数据
     */
    private fun processImage(image: android.media.Image) {
        try {
            val bitmap = imageToBitmap(image)
            image.close()

            // 在后台线程直接回调，FaceLandmarkerHelper.detectAsync 本身线程安全
            frameCallback?.invoke(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "处理图像失败", e)
            image.close()
        }
    }
    
    /**
     * 将Image转换为Bitmap
     */
    /**
     * 将 YUV_420_888 Image 直接转换为 Bitmap（不经过 JPEG 编解码）
     * 通过 RenderScript 完成转换，大幅减少 GC 压力
     */
    private var renderScript: android.renderscript.RenderScript? = null
    private var yuvToRgb: android.renderscript.ScriptIntrinsicYuvToRGB? = null

    @Suppress("DEPRECATION")
    private fun imageToBitmap(image: android.media.Image): Bitmap {
        // 使用 RenderScript 进行 YUV -> ARGB 转换
        if (renderScript == null) {
            renderScript = android.renderscript.RenderScript.create(appContext)
            yuvToRgb = android.renderscript.ScriptIntrinsicYuvToRGB.Builder(renderScript)
                .setElement(android.renderscript.Element.U8_4(renderScript))
                .build()
        }

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val allocation = android.renderscript.Allocation.createFromSized(
            renderScript,
            android.renderscript.Element.U8(renderScript),
            nv21.size
        )
        allocation.copyFrom(nv21)

        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val bitmapAllocation = android.renderscript.Allocation.createFromBitmap(renderScript, bitmap)

        yuvToRgb?.setInput(allocation)
        yuvToRgb?.forEach(bitmapAllocation)
        bitmapAllocation.copyTo(bitmap)

        allocation.destroy()
        bitmapAllocation.destroy()

        return bitmap
    }
    
    /**
     * 停止摄像头
     */
    fun stopCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            isCapturing = false
            Log.i(TAG, "摄像头已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止摄像头失败", e)
        }
    }
    
    /**
     * 释放所有资源
     */
    fun release() {
        stopCamera()
        stopBackgroundThread()
        isInitialized = false
        Log.i(TAG, "摄像头管理器已释放")
    }
    
    /**
     * 检查是否正在捕获
     */
    fun isCapturing(): Boolean = isCapturing
    
    /**
     * 获取预览尺寸
     */
    fun getPreviewSize(): Size = previewSize
}
