### 安卓原型第一阶段技术规划：无GMS华为设备上的MediaPipe Face Landmarker集成

本文档旨在为在无Google Mobile Services (GMS) 的华为安卓设备上开发基于MediaPipe Face Landmarker的人脸特征点检测原型提供技术规划。规划将涵盖摄像头管理、MediaPipe集成、针对华为设备的特殊考虑以及关键代码实现思路。

---

#### 1. 摄像头权限管理

确保应用拥有使用摄像头的权限是首要步骤。遵循标准的Android权限请求流程。

*   **权限声明:** 在 `AndroidManifest.xml` 中声明摄像头权限：
    ```xml
    <uses-permission android:name="android.permission.CAMERA"/>
    ```
    如果应用需要在后台或锁屏状态下使用摄像头（尽管原型阶段可能不需要），可能还需要其他权限和特殊处理。
*   **运行时权限请求:** 对于Android 6.0 (API level 23) 及以上版本，需要在运行时动态请求权限。推荐使用 `ActivityResultLauncher` 处理权限请求结果，以简化回调逻辑。
    ```kotlin
    // Kotlin
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 权限已授予，可以初始化并打开摄像头
                setupCamera()
            } else {
                // 权限被拒绝
                handlePermissionDenied()
            }
        }

    fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予
            setupCamera()
        } else {
            // 请求权限
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    ```
*   **处理用户拒绝:**
    *   **首次拒绝:** 用户首次拒绝后，再次请求权限时，系统会显示一个标准的权限请求对话框。
    *   **勾选“不再提示”后拒绝:** 如果用户勾选了“不再提示”并拒绝，后续的 `requestPermissionLauncher.launch()` 调用会直接回调 `isGranted = false`，而不再弹出对话框。此时应向用户解释为何需要此权限，并引导用户手动前往应用设置页面开启。
    *   提供友好的提示界面（如 `AlertDialog` 或一个独立的权限说明页面），解释权限用途，并提供跳转到应用设置的按钮。可以使用 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` Intent 跳转。

---

#### 2. 摄像头参数配置

稳定的摄像头预览是进行准确人脸特征点和视线检测的基础。需查询设备支持参数并进行针对性配置。

*   **查询支持的参数:**
    *   使用Camera2 API，通过 `CameraManager` 获取 `CameraCharacteristics` 对象，可以查询设备支持的所有摄像头参数，包括：
        *   支持的分辨率/尺寸 (`SCALER_STREAM_CONFIGURATION_MAP`)
        *   支持的帧率范围 (`CONTROL_AE_TARGET_FPS_RANGE`)
        *   对焦模式 (`CONTROL_AF_AVAILABLE_MODES`)
        *   曝光模式 (`CONTROL_AE_AVAILABLE_MODES`)
        *   曝光补偿范围 (`CONTROL_AE_COMPENSATION_RANGE`) 等。
    *   CameraX 也提供类似的功能，但底层仍依赖 Camera2。
*   **锁定曝光 (AE Lock) 和对焦 (AF Lock):**
    *   视线检测对图像质量和稳定性要求较高。光照变化和焦距漂移会严重影响检测结果。锁定曝光和对焦是提升稳定性的关键。
    *   **使用 Camera2 API 实现:**
        *   **AE Lock:** 在 `CaptureRequest.Builder` 中设置 `CaptureRequest.CONTROL_AE_LOCK = true`。需要在创建 CaptureSession 时包含此设置。
        *   **AF Lock:** 首先设置自动对焦模式，例如 `CaptureRequest.CONTROL_AF_MODE = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE` 或 `_CONTINUOUS_VIDEO`。当需要锁定对焦时，触发一次自动对焦扫描，并在扫描完成后设置 `CaptureRequest.CONTROL_AF_TRIGGER = CaptureRequest.CONTROL_AF_TRIGGER_START`。在AF状态变为 `CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED` 或 `_NOT_FOCUSED_LOCKED` 后，设置 `CaptureRequest.CONTROL_AF_MODE = CaptureRequest.CONTROL_AF_MODE_MANUAL` 或保留连续模式但将 `CONTROL_AF_TRIGGER` 设为 `CONTROL_AF_TRIGGER_IDLE`。更简单的方式是在连续AF模式下等待对焦稳定，然后设置 `CaptureRequest.CONTROL_AF_LOCK = true` (如果设备支持)。
        *   在创建 `CaptureRequest` 或更新 Repeating Request 时应用这些设置。
    *   **CameraX 中的等效控制:** CameraX 提供了 `MeteringPointFactory` 和 `FocusMeteringAction` 来控制对焦和测光区域，以及 `ExposureState` 和 `ExposureControl` 来调整曝光补偿。虽然没有直接的“Lock”方法，但可以通过持续设置固定的对焦区域和曝光补偿值，或在对焦/曝光稳定后停止更新来达到类似“锁定”的效果。鉴于CameraX在无GMS华为设备上的潜在兼容性问题（见第4节），**推荐优先考虑 Camera2 API 进行精细控制。**
*   **推荐参数配置 (中端华为设备):**
    *   **预览尺寸:** 选择一个能平衡检测精度和处理性能的尺寸。MediaPipe Face Landmarker模型通常会处理成固定大小的输入图像。过高的分辨率会增加摄像头捕获和图像预处理的开销，过低则影响精度。对于中端设备，建议从 **480p (640x480) 或 720p (1280x720)** 开始测试。选择摄像头支持的与MediaPipe期望输入宽高比相似的尺寸。
    *   **帧率:** 目标是实时处理，通常希望达到 **20-30 fps**。检查设备支持的帧率范围 (`CONTROL_AE_TARGET_FPS_RANGE`)，选择一个固定或范围内的最高帧率，并在MediaPipe初始化时配置为 `RUNNING_MODE_LIVE_STREAM`。实际处理帧率取决于MediaPipe的推理速度。
    *   **传感器帧时长 (Sensor Frame Duration):** (`SENSOR_FRAME_DURATION`) 可以通过 Camera2 查询，影响最大帧率。通常无需手动配置，由选择的帧率决定。

---

#### 3. MediaPipe Face Landmarker 初始化

正确初始化 MediaPipe Face Landmarker 实例是使用其功能的关键。

*   **引入必要的依赖:**
    在项目的 `build.gradle (Module :app)` 文件中添加 MediaPipe Tasks Vision 库依赖。确保使用最新稳定版本。
    ```gradle
    dependencies {
        // ... other dependencies
        implementation 'com.google.mediapipe:tasks-vision-play-services:latest.version' // 如果要使用 play-services 版本 (不推荐在无GMS设备上)
        // OR
        implementation 'com.google.mediapipe:tasks-vision-android:latest.version' // 推荐在无GMS设备上使用此版本
    }
    ```
    **注意:** 根据任务IPdr1和search结果，`tasks-vision-android` 版本更适合无GMS环境，因为它不直接依赖GMS。需要查阅最新的MediaPipe官方文档确认具体的Maven坐标和版本号。
*   **模型文件加载:**
    将 `face_landmarker.task` 文件（或包含Blendshapes的 `face_landmarker_v2_with_blendshapes.task`）放置在 Android 项目的 `src/main/assets` 目录下。初始化时通过 `BaseOptions.Builder().setModelAssetPath("face_landmarker_v2_with_blendshapes.task")` 指定加载。
*   **代理 (Delegate) 选择:**
    根据任务IPdr1和search结果，在无GMS华为设备上，TFLite的硬件加速（NNAPI和GPU）表现不稳定且依赖于设备具体的驱动实现。
    *   **策略:** **首选 TFLite CPU delegate，并开启多线程。** 这是最稳健的方案，且在许多华为设备上，优化后的CPU性能（如使用XNNPack）可能优于不稳定的硬件加速。
    *   **备选/动态选择:** 可以尝试在应用首次启动或设置页面进行简单的性能基准测试，比较CPU、GPU（如果兼容）和NNAPI（如果可用且性能优于CPU）代理的速度，动态选择最优代理。但在原型阶段，为保证稳定性，**建议优先使用 CPU delegate**。
    *   **TFLite CPU 后备方案的重要性:** 这是确保在硬件加速不可用或性能不佳时，模型仍然可以正常运行的关键。使用CPU delegate本身就是一种后备方案。需要确保MediaPipe编译的TFLite版本包含了优化的CPU路径（如XNNPack）。
    *   **配置:** 在 `BaseOptions` 中配置代理。
      ```kotlin
      // Kotlin
      import com.google.mediapipe.tasks.vision.core.Delegate
      import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerOptions

      fun buildFaceLandmarkerOptions(): FaceLandmarkerOptions {
          val baseOptionsBuilder = BaseOptions.builder()
              .setModelAssetPath("face_landmarker_v2_with_blendshapes.task")
              // 配置Delegate
              .setDelegate(Delegate.CPU) // 明确指定使用CPU delegate
              // 可以尝试 NNAPI 或 GPU (需谨慎)
              // .setDelegate(Delegate.NNAPI)
              // .setDelegate(Delegate.GPU)

          // 如果使用CPU，可以考虑设置线程数 (Tasks API通常会自动管理，但某些低层TFLite InterpreterOptions允许设置)
          // Tasks API BaseOptions目前没有直接设置线程数的方法，线程管理可能在Tasks API内部或通过低层TFLite接口。
          // 如需更精细控制TFLite线程，可能需要绕过Tasks API直接使用TFLite Interpreter。
          // 但对于原型，先依赖Tasks API默认行为。

          return FaceLandmarkerOptions.builder()
              .setBaseOptions(baseOptionsBuilder.build())
              .setRunningMode(RunningMode.LIVE_STREAM) // 用于实时处理摄像头帧
              .setNumFaces(1) // 通常关注单个用户
              .setOutputFaceBlendshapes(true) // 根据需求决定，视线检测可能需要
              .setOutputFacialTransformationMatrixes(true) // 对头部姿态判断重要
              .setMinFaceDetectionConfidence(0.7f) // 可调
              .setMinFaceTrackingConfidence(0.7f) // 可调
              .setMinFacePresenceConfidence(0.7f) // 可调
              // 设置结果监听器
              .setResultListener(this::onResults) // 替换为实际的结果处理函数
              .setErrorListener(this::onError) // 替换为实际的错误处理函数
              .build()
      }
      ```
*   **FaceLandmarker 选项配置:**
    *   `runningMode`: 必须设置为 `RunningMode.LIVE_STREAM` 以处理连续的视频帧。
    *   `numFaces`: 设为 `1`，因为通常只检测主要用户的视线。
    *   `outputFaceBlendshapes`: 根据是否需要表情信息来决定。视线检测理论上不需要 blendshapes，但模型可能包含相关内部特征，开启可能无妨，但会增加处理开销。原型阶段可以开启以备后续需求。
    *   `outputFacialTransformationMatrixes`: **必须开启**。这些矩阵描述了头部姿态，对于将2D/3D特征点转换到头部局部坐标系或进行视线方向的3D几何计算至关重要。
    *   `minFaceDetectionConfidence`, `minFaceTrackingConfidence`, `minFacePresenceConfidence`: 这些阈值影响检测的灵敏度和稳定性。可以根据实际测试效果进行调整，提高阈值可以减少误检，降低则提高检出率。建议从0.5-0.8范围内进行测试。
*   **初始化实例:** 使用配置好的选项创建 `FaceLandmarker` 实例。
    ```kotlin
    // Kotlin
    private var faceLandmarker: FaceLandmarker? = null

    fun setupFaceLandmarker() {
        try {
            val options = buildFaceLandmarkerOptions()
            faceLandmarker = FaceLandmarker.createFromOptions(this, options) // Context需要传入
        } catch (e: Exception) {
            // 处理初始化错误，例如模型文件未找到、设备不支持等
            onError(e)
        }
    }

    private fun onResults(resultBundle: FaceLandmarkerResultBundle) {
        // 在这里处理检测结果
        // 结果包含 timestampMs, results (List<FaceLandmarkerResult>)
        val results = resultBundle.results()
        if (results.isNotEmpty()) {
            val faceLandmarkerResult = results[0] // 只处理检测到的第一张脸
            // 提取 faceLandmarks 和 facialTransformationMatrixes 进行后续计算
            val landmarks = faceLandmarkerResult.faceLandmarks()
            val transformationMatrixes = faceLandmarkerResult.facialTransformationMatrixes()
            // 调用视线检测和虹膜数据处理逻辑
            processDetectionResults(landmarks, transformationMatrixes)
        }
    }

    private fun onError(exception: Exception) {
        // 处理MediaPipe检测过程中的错误
        Log.e("FaceLandmarker", "Error: ${exception.message}")
        // 可能需要通知用户或尝试重新初始化
    }
    ```

---

#### 4. 华为无GMS设备特定注意事项

在无GMS的华为设备上开发需要特别关注兼容性和性能问题。

*   **关键挑战总结 (源自任务IPdr1及search结果):**
    *   **TFLite运行时兼容性与性能:** NNAPI和GPU代理在部分Kirin芯片上表现不稳定，可能需要强制使用CPU。
    *   **CameraX 在部分华为设备上的问题:** 存在初始化失败、预览异常、帧丢失等兼容性风险，可能依赖部分GMS组件或与底层HAL不兼容。
    *   **潜在的GMS间接依赖:** MediaPipe Tasks Vision库本身不强依赖GMS，但其底层依赖库（如某些AndroidX组件、TFLite的特定构建、硬件加速驱动）可能存在对GMS环境的隐含期望或调用。
    *   **硬件加速 (GPU/NNAPI) 的有效性:** 性能高度依赖于设备型号、Android/HarmonyOS版本以及厂商提供的驱动质量。
*   **具体的实施策略或备选方案:**
    *   **摄像头API选择:** **强烈建议优先使用 Camera2 API** 进行摄像头数据采集。Camera2 API 是 Android 原生 API，不依赖 GMS，提供了更底层、更稳定的控制能力，虽然开发复杂度高于 CameraX，但在兼容性方面更有保障。
    *   **TFLite CPU 回退机制:** 在 MediaPipe 初始化时，明确配置使用 `Delegate.CPU`。在选择其他代理时，务必进行运行时测试并准备好回退到CPU的逻辑。如果直接使用低层TFLite Interpreter，可以更精细地配置Delegate列表，让TFLite自行选择或回退。
    *   **依赖检查与排除:**
        *   在项目构建时，仔细检查 Gradle 依赖树 (`./gradlew :app:dependencies`)，查找任何 `com.google.android.gms` 或 `com.google.firebase` 开头的依赖。
        *   如果发现非预期的GMS依赖，尝试升级到不包含GMS的版本，或寻找替代库。
        *   考虑从MediaPipe源码自行编译 Tasks Vision 库，可以更精确地控制依赖和构建选项，排除潜在的GMS相关代码，但这会显著增加开发和维护成本。对于原型阶段，先使用官方 `tasks-vision-android` 版本，并关注运行时错误。
    *   **详尽的兼容性和性能测试方法论:**
        *   **设备覆盖:** 在不同型号、不同Android/HarmonyOS版本的典型无GMS华为设备上进行测试。重点关注中端机型，因为它们更可能出现性能瓶颈。
        *   **场景测试:** 在不同光照条件、不同用户面部角度和距离下测试人脸检测和特征点跟踪的稳定性、准确性。
        *   **性能分析:** 使用 Android Studio Profiler 监控 CPU、内存和 GPU 使用率。记录摄像头帧率、MediaPipe推理延迟。对比不同 Delegate 设置下的性能差异。
        *   **日志分析:** 开启详细日志，特别是与摄像头、MediaPipe、TFLite相关的日志，排查潜在的运行时错误或警告。
        *   **长时间运行测试:** 测试应用长时间运行时的稳定性和资源消耗，检查是否存在内存泄漏或性能随时间下降的问题。

---

#### 5. 关键代码片段示例（概念性）

以下提供一些关键操作的实现思路代码片段。

*   **请求和检查摄像头权限 (Kotlin):** 参见上面第1节的代码示例。

*   **使用 Camera2 API 配置摄像头，并锁定曝光和对焦 (概念性 Kotlin):**
    ```kotlin
    // 假设 cameraManager, cameraId, activity, handler 已初始化
    // 假设 previewSurface 已创建 (例如来自 SurfaceView 或 TextureView)

    fun configureCamera2(cameraManager: CameraManager, cameraId: String, activity: Activity, handler: Handler) {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
                // CameraDevice 已打开，创建 CaptureSession
                val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder.addTarget(previewSurface)
                // 可以为 MediaPipe 添加 ImageReader Surface 作为处理输入
                // captureRequestBuilder.addTarget(imageReader.surface)

                // 配置对焦和曝光模式
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) // 或其他自动曝光模式

                // 创建 CaptureSession
                cameraDevice.createCaptureSession(listOf(previewSurface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        // Session 已配置，开始重复请求预览帧
                        session.setRepeatingRequest(captureRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                // 每一帧完成时回调，可以在这里检查 AF/AE 状态以决定是否锁定
                                val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)

                                if (afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN) {
                                     // AF 扫描中
                                } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                     // AF 锁定状态
                                     // 如果需要，可以在这里设置 CONTROL_AF_LOCK = true (如果设备支持)
                                }

                                if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                    // AE 已收敛，可以尝试锁定曝光
                                    // captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                                    // session.setRepeatingRequest(captureRequestBuilder.build(), this, handler) // 应用更新
                                }
                            }
                        }, handler)
                    }
                    // ... other session callbacks
                }, handler)
            }
            // ... other cameraDevice callbacks (onError, onDisconnected)
        }, handler)
    }
    ```
    *更简化的锁定思路是在对焦/曝光稳定后，直接更新RepeatingRequest设置 `CONTROL_AF_MODE = CONTROL_AF_MODE_OFF` (或保持连续模式但不再触发) 和 `CONTROL_AE_LOCK = true`。*

*   **初始化 MediaPipe FaceLandmarker 实例 (Kotlin):** 参见上面第3节的代码示例 `setupFaceLandmarker()`。

*   **将摄像头帧数据传递给 FaceLandmarker (概念性 Kotlin):**
    如果使用 Camera2 的 `ImageReader`，在 `onImageAvailable` 回调中获取 `Image` 对象，并将其转换为 MediaPipe Tasks Vision 所需的格式（通常是 `com.google.mediapipe.framework.image.MPImage`）。
    ```kotlin
    import com.google.mediapipe.framework.image.MPImage
    import com.google.mediapipe.tasks.vision.core.RunningMode
    import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
    import android.media.Image // 来自 ImageReader

    // ImageReader.OnImageAvailableListener 的回调
    fun onImageAvailable(reader: ImageReader) {
        val image: Image? = reader.acquireLatestImage()
        if (image != null) {
            val frameTime = SystemClock.uptimeMillis() // 或使用 Image 的 timestamp
            // 将 Android Image 转换为 MediaPipe MPImage
            // 这通常需要将 YUV 或其他格式转换为 RGB 或 MediaPipe内部优化格式
            // MediaPipe Tasks API 可能提供辅助类进行转换，或者需要手动处理 Plane 数据
            // 假设存在一个转换函数 convertAndroidImageToMPImage()
            val mpImage: MPImage = convertAndroidImageToMPImage(image)

            // 创建 ImageProcessingOptions (设置旋转等)
            val options = ImageProcessingOptions.builder()
                .setRotation(getRotationDegrees()) // 根据设备方向和传感器方向计算
                .build()

            // 将帧传递给 FaceLandmarker 进行异步处理
            if (faceLandmarker != null && faceLandmarker?.runningMode == RunningMode.LIVE_STREAM) {
                faceLandmarker?.detectAsync(mpImage, frameTime, options)
            }

            image.close() // 释放 Image 资源
        }
    }

    // 转换函数示例 (概念性)
    fun convertAndroidImageToMPImage(image: Image): MPImage {
        // 实际转换逻辑依赖于 Image 格式 (YUV_420_888 是常见格式)
        // 需要访问 image.planes 并可能进行颜色空间转换和内存拷贝
        // 查找 MediaPipe 官方示例或文档中关于 Image 转换的部分
        // MPImage.createFromXyz(...)
        throw NotImplementedError("Conversion not implemented")
    }
    ```
    如果使用 CameraX 的 `ImageAnalysis`，在 `analyze(imageProxy: ImageProxy)` 回调中获取 `ImageProxy` 对象，并将其转换为 `MPImage`。
    ```kotlin
    import androidx.camera.core.ImageProxy
    import com.google.mediapipe.framework.image.MPImage
    import com.google.mediapipe.tasks.vision.core.RunningMode
    import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions

    // ImageAnalysis.Analyzer 的 analyze 方法
    fun analyze(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis() // 或 imageProxy.imageInfo.timestamp
        // 将 ImageProxy 转换为 MediaPipe MPImage
        // MediaPipe Tasks Android API 提供了一个 ImageProxyUtil 工具类
        val mpImage = ImageProxyUtil.createMpImageFromImageProxy(imageProxy) // 需要检查这个工具类是否存在并兼容

        // 创建 ImageProcessingOptions
        val options = ImageProcessingOptions.builder()
            .setRotation(imageProxy.imageInfo.rotationDegrees) // CameraX 提供旋转信息
            .build()

        // 将帧传递给 FaceLandmarker 进行异步处理
        if (faceLandmarker != null && faceLandmarker?.runningMode == RunningMode.LIVE_STREAM) {
            faceLandmarker?.detectAsync(mpImage, frameTime, options)
        }

        imageProxy.close() // 释放 ImageProxy 资源
    }
    ```

*   **处理 FaceLandmarker 的检测结果回调 (Kotlin):** 参见上面第3节的代码示例 `onResults()`。在这个回调中，可以提取 `faceLandmarks` 和 `facialTransformationMatrixes`，并进行后续的视线方向计算和数据发布（例如通过MQTT）。

---

本文档提供了原型开发第一阶段的技术规划，重点考虑了无GMS华为设备上的技术选型和潜在挑战。实际开发过程中，需要根据具体设备进行详细的兼容性测试和性能调优。