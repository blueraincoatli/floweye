# 安卓原型概念验证技术规格说明书（第二稿）

## 1. 引言与项目概述

本项目旨在开发一个基于安卓设备的视线跟踪原型，利用设备的摄像头和 MediaPipe Face Landmarker 技术检测用户面部特征点，特别是虹膜位置，并计算用户的视线方向，最终判断用户是否注视设备屏幕上的特定区域（例如“是”或“否”按钮区域）。该原型将部署在**无 Google Mobile Services (GMS) 的华为安卓设备**上，通过本地网络（MQTT协议）将视线状态信息发送至中央协调器，以支持视线辅助交互应用的概念验证。

安卓原型验证的范围包括：
1.  在目标设备上实现稳定的摄像头数据采集。
2.  集成 MediaPipe Face Landmarker V5 模型，实时检测人脸特征点、头部姿态和眼球 Blendshape。
3.  基于 MediaPipe 输出数据，实现鲁棒的视线方向判断算法，判断用户是否注视屏幕上的预定义区域，并计算置信度。
4.  开发一个极简用户界面，清晰展示“是”、“否”选项区域，并提供视线检测状态的视觉反馈。
5.  实现 MQTT 客户端功能，将视线状态结果实时发布至本地网络中的 MQTT Broker。
6.  重点解决 MediaPipe 在无 GMS 华为设备上的兼容性和性能问题。

## 2. 系统架构回顾

安卓视觉处理组件在整体系统中扮演 **数据采集与预处理节点** 的角色。其核心任务是捕获用户面部数据，本地执行视线检测算法，并将高层级的视线状态信息（而非原始图像/视频）通过网络发送出去。它与系统的其他部分（中央协调器、其他接收视线信息的设备）通过 MQTT 协议解耦通信。

```svg
<svg width="800" height="300" xmlns="http://www.w3.org/2000/svg">
  <style>
    .node { fill: #e0e0e0; stroke: #333; stroke-width: 2px; }
    .edge { stroke: #666; stroke-width: 1.5px; marker-end: url(#arrow); }
    .text { font-family: sans-serif; font-size: 14px; fill: #333; text-anchor: middle; }
    .component { fill: #a8d8ff; }
    .process { fill: #fff; }
    .data { fill: #a8ffb8; }
    .boundary { stroke: #000; stroke-dasharray: 5,5; fill: none; }
    #arrow { markerUnits: strokeWidth; overflow: visible; }
    #arrow path { fill: #666; stroke: #666; }
  </style>
  <defs>
    <marker id="arrow" viewBox="0 -5 10 10" refX="10" refY="0" markerWidth="10" markerHeight="10" orient="auto">
      <path d="M0,-5L10,0L0,5Z" />
    </marker>
  </defs>

  <!-- Components -->
  <rect x="50" y="100" rx="10" ry="10" width="150" height="80" class="node component"/>
  <text x="125" y="140" class="text">安卓视觉处理组件<tspan x="125" dy="1.2em">(华为无GMS设备)</tspan></text>

  <rect x="600" y="100" rx="10" ry="10" width="150" height="80" class="node component"/>
  <text x="675" y="140" class="text">中央协调器<tspan x="675" dy="1.2em">/ 其他系统组件</tspan></text>

  <!-- MQTT Broker (Optional, could be on Central Coordinator) -->
  <rect x="350" y="20" rx="10" ry="10" width="100" height="60" class="node process"/>
  <text x="400" y="50" class="text">MQTT Broker</text>

  <!-- Internal Android Component Process -->
   <rect x="60" y="110" rx="5" ry="5" width="130" height="30" class="node process"/>
   <text x="125" y="125" class="text">摄像头管理</text>

   <rect x="60" y="145" rx="5" ry="5" width="130" height="30" class="node process"/>
   <text x="125" y="160" class="text">MediaPipe处理</text>

    <rect x="220" y="145" rx="5" ry="5" width="130" height="30" class="node process"/>
   <text x="285" y="160" class="text">视线算法</text>

   <rect x="220" y="110" rx="5" ry="5" width="130" height="30" class="node process"/>
   <text x="285" y="125" class="text">UI展示/反馈</text>

   <rect x="380" y="145" rx="5" ry="5" width="130" height="30" class="node process"/>
   <text x="445" y="160" class="text">MQTT客户端</text>


  <!-- Data/Flows -->
  <line x1="200" y1="150" x2="220" y2="150" class="edge"/>
  <text x="210" y="145" class="data">MP Results</text>

   <line x1="350" y1="150" x2="380" y2="150" class="edge"/>
  <text x="365" y="145" class="data">Gaze State</text>


  <line x1="510" y1="150" x2="380" y2="40" class="edge"/>
   <text x="445" y="80" class="data">Publish</text>

   <line x1="380" y1="40" x2="600" y2="150" class="edge"/>
    <text x="540" y="80" class="data">Subscribe</text>

  <line x1="125" y1="80" x2="400" y2="20" class="edge" marker-end=""/>
   <line x1="675" y1="80" x2="400" y2="20" class="edge" marker-end=""/>
    <text x="400" y="90" class="text">MQTT</text>


</svg>
```

## 3. 目标软硬件环境

*   **目标设备:** 华为安卓手机，特别是无 GMS 的中低端型号（例如搭载麒麟 710/810/970/980 等非最新芯片的设备）。选择这些设备是为了验证在资源相对有限、且缺少 GMS 环境下 MediaPipe 的可行性。
*   **安卓版本:** 支持 Android 9 (API 级别 28) 及以上版本，以兼容 MediaPipe 和 Camera2 API 的常用功能。
*   **MediaPipe 版本:** 使用 MediaPipe Tasks Vision 库的最新稳定版本（例如 `com.google.mediapipe:tasks-vision-android:latest.version`）。
*   **无 GMS 适配策略:**
    *   **优先使用 `tasks-vision-android` 版本依赖**，避免依赖 `tasks-vision-play-services`，从而剥离对 GMS 的硬性依赖。
    *   **摄像头管理：** **强制或优先采用 Camera2 API** 进行摄像头操作。CameraX 在无 GMS 环境下的兼容性问题普遍存在，而 Camera2 API 是 Android 原生框架，更稳定可靠。
    *   **TFLite Delegate：** **首选 TFLite CPU delegate**，并配置多线程。在无 GMS 的华为设备上，NNAPI 和 GPU delegate 的性能表现和兼容性高度依赖于设备驱动，往往不稳定甚至不如优化后的 CPU 性能。动态检测并选择最优 delegate 方案复杂，原型阶段强制使用 CPU delegate 是最稳健的。
    *   **依赖检查：** 仔细检查项目依赖树，确保没有意外引入对 GMS 服务的间接依赖。

## 4. 核心视觉处理功能

### 4.a 摄像头管理

*   **权限获取:** 在 `AndroidManifest.xml` 中声明 `<uses-permission android:name="android.permission.CAMERA"/>` 权限。对于 Android 6.0 (API 23) 及以上版本，必须在运行时动态请求此权限，并处理用户拒绝权限的场景（引导用户到应用设置页面开启）。
*   **摄像头选择与开启:** 使用 Camera2 API 获取可用的前置摄像头 ID。创建 `CameraDevice` 实例并监听其状态回调。
*   **摄像头参数配置:**
    *   **预览尺寸与帧率:** 通过 `CameraCharacteristics` 查询设备支持的尺寸和帧率范围 (`SCALER_STREAM_CONFIGURATION_MAP`, `CONTROL_AE_TARGET_FPS_RANGE`)。选择一个能平衡 MediaPipe 处理性能（通常是固定输入尺寸）和检测精度（需要足够细节）的分辨率（推荐 480p 或 720p）和帧率（推荐 20-30 fps）。
    *   **图像格式:** 配置 `ImageReader` 以接收摄像头输出帧，格式选择 YUV_420_888 是常见且高效的格式，MediaPipe 通常支持直接处理 YUV 数据。
    *   **曝光锁定 (AE Lock):** 在 `CaptureRequest.Builder` 中设置 `CaptureRequest.CONTROL_AE_LOCK = true`。这有助于在检测过程中保持图像亮度稳定，避免因光照变化导致人脸特征点检测漂移。需在 AE 状态收敛后应用锁定。
    *   **对焦锁定 (AF Lock):** 在 `CaptureRequest.Builder` 中设置 `CaptureRequest.CONTROL_AF_MODE` 为合适的模式（如 `CONTINUOUS_PICTURE`），等待对焦稳定后，可以尝试设置 `CaptureRequest.CONTROL_AF_LOCK = true` 或将模式设置为 `CONTROL_AF_MODE_OFF` 以保持当前焦距。对焦锁定确保人脸特征点的清晰度，对虹膜检测尤为重要。

### 4.b MediaPipe 集成 (Face Landmarker)

*   **依赖:** 添加 `com.google.mediapipe:tasks-vision-android:latest.version` 依赖。
*   **模型文件:** 将 `face_landmarker_v2_with_blendshapes.task` 模型文件放置在 `assets` 目录。
*   **初始化流程:**
    1.  创建 `BaseOptions.Builder()`，指定模型文件路径 (`setModelAssetPath`)。
    2.  **配置 Delegate:** 明确设置 `.setDelegate(Delegate.CPU)`，确保在无 GMS 环境下使用稳定的 CPU 推理。
    3.  创建 `FaceLandmarkerOptions.Builder()`，设置 `.setBaseOptions()`。
    4.  配置运行模式：`.setRunningMode(RunningMode.LIVE_STREAM)`。
    5.  设置人脸数量：`.setNumFaces(1)`。
    6.  **开启 Blendshapes 和 Transformation Matrix 输出：** `.setOutputFaceBlendshapes(true)` 和 `.setOutputFacialTransformationMatrixes(true)`，这为视线检测算法提供必需的输入。
    7.  设置置信度阈值：`.setMinFaceDetectionConfidence()`, `.setMinFaceTrackingConfidence()`, `.setMinFacePresenceConfidence()`。根据测试调整，平衡检出率与稳定性。
    8.  设置结果监听器：`.setResultListener(this::onResults)` 和 `.setErrorListener(this::onError)`。
    9.  使用配置好的 `FaceLandmarkerOptions` 调用 `FaceLandmarker.createFromOptions(context, options)` 创建 FaceLandmarker 实例。
*   **数据流获取:**
    *   在 Camera2 `ImageReader.OnImageAvailableListener` 的回调中获取 `android.media.Image` 对象。
    *   将 `Image` 对象转换为 MediaPipe 所需的 `com.google.mediapipe.framework.image.MPImage` 格式。MediaPipe Tasks API 可能提供辅助类或需要手动处理 YUV Planar 数据。
    *   获取当前帧的时间戳（例如 `SystemClock.uptimeMillis()` 或 `Image.getTimestamp()`）。
    *   创建 `ImageProcessingOptions`，设置图像旋转角度 (`setRotation`)，以匹配摄像头传感器方向和设备屏幕方向。
    *   调用 `faceLandmarker.detectAsync(mpImage, frameTime, options)` 将帧数据异步传递给 MediaPipe 进行处理。
    *   在 `onResults` 回调中接收 `FaceLandmarkerResultBundle`，提取 `FaceLandmarkerResult`（通常取第一个，因为 `numFaces=1`），其中包含 `faceLandmarks`, `facialTransformationMatrixes`, `faceBlendshapes` 等。

### 4.c 视线检测算法详解

该算法基于 MediaPipe Face Landmarker V5 的输出数据，结合头部姿态和眼球 Blendshape 来判断视线方向。

**算法输入：**

来源于 `FaceLandmarkerResult` 的以下部分（针对检测到的第一张脸）：
*   `faceLandmarks`: `List<NormalizedLandmarkList>`，包含 478 个 3D 特征点（眼睛、虹膜等）。
*   `facialTransformationMatrixes`: `List<Matrix>`，描述人脸相对于相机坐标系的 4x4 变换矩阵。
*   `faceBlendshapes`: `List<Blendshapes>`，包含 `eyeLook` 等 Blendshape 系数。

**数据结构概览:**

```svg
<svg width="600" height="250" xmlns="http://www.w3.org/2000/svg">
  <style>
    .node { fill: #e0e0e0; stroke: #333; stroke-width: 2px; }
    .edge { stroke: #666; stroke-width: 1.5px; marker-end: url(#arrow); }
    .text { font-family: sans-serif; font-size: 14px; fill: #333; text-anchor: middle; }
    .input { fill: #a8d8ff; }
    .output { fill: #a8ffb8; }
    .process { fill: #fff; }
    #arrow { markerUnits: strokeWidth; overflow: visible; }
    #arrow path { fill: #666; stroke: #666; }
  </style>
  <defs>
    <marker id="arrow" viewBox="0 -5 10 10" refX="10" refY="0" markerWidth="10" markerHeight="10" orient="auto">
      <path d="M0,-5L10,0L0,5Z" />
    </marker>
  </defs>

  <!-- Nodes -->
  <rect x="50" y="20" rx="10" ry="10" width="120" height="60" class="node input"/>
  <text x="110" y="50" class="text">faceLandmarks</text>

  <rect x="50" y="90" rx="10" ry="10" width="120" height="60" class="node input"/>
  <text x="110" y="120" class="text">facialTransformation<tspan x="110" dy="1.2em">Matrixes</tspan></text>

  <rect x="50" y="160" rx="10" ry="10" width="120" height="60" class="node input"/>
  <text x="110" y="190" class="text">faceBlendshapes</text>

  <rect x="250" y="90" rx="10" ry="10" width="120" height="60" class="node process"/>
  <text x="310" y="120" class="text">Gaze Algorithm</text>

  <rect x="450" y="90" rx="10" ry="10" width="120" height="60" class="node output"/>
  <text x="510" y="110" class="text">isLookingAtDevice</text>
   <text x="510" y="130" class="text">gazeConfidence</text>


  <!-- Edges -->
  <line x1="170" y1="50" x2="250" y2="120" class="edge"/>
  <line x1="170" y1="120" x2="250" y2="120" class="edge"/>
  <line x1="170" y1="190" x2="250" y2="120" class="edge"/>
  <line x1="370" y1="120" x2="450" y2="120" class="edge"/>

</svg>
```

**详细计算步骤：**

1.  **数据提取与校验:** 从 MediaPipe 结果中提取左右眼轮廓点、虹膜点 3D 坐标，头部变换矩阵，以及 `eyeLookUp/Down/In/Out` 等 Blendshape 系数。进行基本校验，例如检测关键点是否缺失或坐标是否异常。
2.  **头部姿态解析:** 从 4x4 `facialTransformationMatrix` 中提取 3x3 旋转矩阵 $R_{head}$ 和 3D 平移向量 $(T_x, T_y, T_z)$。$(T_x, T_y, T_z)$ 可作为头部在相机空间的大致位置 $P_{head\_camera}$。旋转矩阵 $R_{head}$ 描述了头部在相机坐标系中的朝向。
3.  **眼球自身旋转计算 (主要依赖 Blendshapes):**
    *   将 `eyeLook` Blendshape 系数映射到眼球相对于头部（或眼睛自身局部坐标系）的水平和垂直旋转角度 ($\theta_{horz}, \theta_{vert}$). 需要预先定义 Blendshape 值与角度的最大映射范围（例如，Blendshape 值 1.0 对应约 15-20 度的转角）。
    *   根据 $\theta_{horz}, \theta_{vert}$ 构建眼球相对旋转矩阵 $R_{eye\_rel}$。
    *   定义眼球在中立状态下的方向向量 $V_{eye\_neutral\_face}$ (在头部坐标系中，通常是 Z 轴方向向量).
    *   计算眼球在头部坐标系中的实际方向向量 $V_{eye\_actual\_face} = R_{eye\_rel} \times V_{eye\_neutral\_face}$.
4.  **融合姿态计算相机空间视线向量:**
    *   将眼球在头部坐标系中的方向向量 $V_{eye\_actual\_face}$ 乘以头部旋转矩阵 $R_{head}$，得到视线在相机坐标系中的方向向量 $V_{gaze\_camera} = R_{head} \times V_{eye\_actual\_face}$. $V_{gaze\_camera}$ 是单位向量。
    *   视线的起点可以近似取头部在相机空间的位置 $P_{eye\_camera} \approx P_{head\_camera}$。
5.  **设备位置参数与屏幕目标区域:**
    *   **简化处理:** 原型阶段可以简化处理设备位置参数。假设屏幕平面法线与相机光轴平行，屏幕中心在相机坐标系前方某个固定距离处。通过预设的屏幕物理尺寸和分辨率，可以定义屏幕上的目标区域（“是”/“否”区域）在相机坐标系中的大致位置或通过校准获取其相对于眼球位置的向量。
    *   **推荐方法:** 定义屏幕目标区域（如“是”区域中心、$P_{target\_yes\_camera}$，“否”区域中心 $P_{target\_no\_camera}$）在相机坐标系中的位置。这可以通过简单的校准过程实现：要求用户看向屏幕中心，记录此时的头部和眼球方向，结合已知屏幕尺寸和相机参数反推出屏幕相对于相机的位置。
6.  **视线与设备方向夹角计算:**
    *   计算从眼球位置 $P_{eye\_camera}$ 到“是”区域目标点 $P_{target\_yes\_camera}$ 的向量 $V_{eye\_to\_target\_yes} = P_{target\_yes\_camera} - P_{eye\_camera}$。
    *   计算视线向量 $V_{gaze\_camera}$ 与 $V_{eye\_to\_target\_yes}$ 之间的夹角 $\alpha_{yes}$。
        ```latex
        $$\alpha_{yes} = \operatorname{arccos}\left( \frac{V_{gaze\_camera} \cdot V_{eye\_to\_target\_yes}}{|V_{gaze\_camera}| |V_{eye\_to\_target\_yes}|} \right)$$
        ```
    *   类似地，计算视线向量与指向“否”区域目标点 $V_{eye\_to\_target\_no}$ 之间的夹角 $\alpha_{no}$.
7.  **注视目标判断:**
    *   定义注视阈值角度 $\theta_{gaze\_threshold}$.
    *   如果 $\alpha_{yes} < \theta_{gaze\_threshold}$ 且 $\alpha_{yes}$ 是所有目标区域夹角中最小的，则判断注视目标为 `"YES_AREA"`.
    *   如果 $\alpha_{no} < \theta_{gaze\_threshold}$ 且 $\alpha_{no}$ 是所有目标区域夹角中最小的，则判断注视目标为 `"NO_AREA"`.
    *   如果某个目标区域的夹角小于阈值 $\theta_{gaze\_threshold}$，但同时有多个区域满足条件，或者视线与屏幕平面相交（`isLookingAtDevice` 为 True）但与所有目标区域夹角均大于阈值，则判断为 `"SCREEN_ACTIVE_NO_TARGET"`.
    *   如果 `isLookingAtDevice` 判断为 False (例如，视线未与屏幕平面相交，或头部姿态偏离过大)，则判断为 `"LOOKING_AWAY"`.
8.  **虹膜点质量评估与整体置信度计算:**
    *   评估 MediaPipe 输出的 `minFace...Confidence` 值。
    *   检查眼部和虹膜关键点的数量、分布和帧间稳定性。如果关键点少、抖动大或位置异常，降低置信度。
    *   检查 `eyeLook` Blendshape 值是否在合理范围。
    *   计算当前帧结果与前一帧结果的差异（头部姿态、视线方向）。如果变化过大，降低置信度（检测不稳定）。可以对结果进行时间平滑。
    *   综合上述因素，计算一个 0.0 到 1.0 之间的 `gazeConfidence` 值。当 `gazeConfidence` 低于某个阈值时，强制将 `gazeTarget` 设为 `"DETECTION_UNSTABLE"`。

```svg
<svg width="600" height="450" xmlns="http://www.w3.org/2000/svg">
  <style>
    .node { fill: #e0e0e0; stroke: #333; stroke-width: 2px; }
    .edge { stroke: #666; stroke-width: 1.5px; marker-end: url(#arrow); }
    .text { font-family: sans-serif; font-size: 14px; fill: #333; text-anchor: middle; }
    .input { fill: #a8d8ff; }
    .output { fill: #a8ffb8; }
    .process { fill: #fff; }
    #arrow { markerUnits: strokeWidth; overflow: visible; }
    #arrow path { fill: #666; stroke: #666; }
    .label { font-family: sans-serif; font-size: 12px; fill: #555; }
  </style>
  <defs>
    <marker id="arrow" viewBox="0 -5 10 10" refX="10" refY="0" markerWidth="10" markerHeight="10" orient="auto">
      <path d="M0,-5L10,0L0,5Z" />
    </marker>
  </defs>

  <!-- Nodes -->
  <rect x="50" y="20" rx="10" ry="10" width="120" height="40" class="node input"/>
  <text x="110" y="45" class="text">Blendshapes</text>

  <rect x="50" y="80" rx="10" ry="10" width="120" height="40" class="node input"/>
  <text x="110" y="105" class="text">Transf. Matrix</text>

  <rect x="250" y="20" rx="10" ry="10" width="140" height="40" class="node process"/>
  <text x="320" y="45" class="text">Map to Eye Rotation (Rel)</text>

   <rect x="250" y="80" rx="10" ry="10" width="140" height="40" class="node process"/>
  <text x="320" y="105" class="text">Parse Head Pose (Abs)</text>

  <rect x="450" y="50" rx="10" ry="10" width="140" height="40" class="node process"/>
  <text x="520" y="75" class="text">Combine Eye & Head Pose</text>

  <rect x="450" y="180" rx="10" ry="10" width="140" height="40" class="node output"/>
  <text x="520" y="205" class="text">Gaze Vector (Camera Space)</text>

  <rect x="50" y="280" rx="10" ry="10" width="120" height="60" class="node input"/>
  <text x="110" y="310" class="text">Device Position<tspan x="110" dy="1.2em">Parameters</tspan></text>

   <rect x="250" y="280" rx="10" ry="10" width="140" height="60" class="node process"/>
  <text x="320" y="310" class="text">Define Screen Plane<tspan x="320" dy="1.2em">in Camera Space</tspan></text>


   <rect x="450" y="280" rx="10" ry="10" width="140" height="60" class="node process"/>
  <text x="520" y="310" class="text">Calculate Ray-Plane<tspan x="520" dy="1.2em">Intersection</tspan></text>

   <rect x="250" y="380" rx="10" ry="10" width="140" height="60" class="node process"/>
  <text x="320" y="410" class="text">Calculate Relative<tspan x="320" dy="1.2em">Angle to Target</tspan></text>

   <rect x="450" y="380" rx="10" ry="10" width="140" height="40" class="node output"/>
  <text x="520" y="405" class="text">isLookingAtDevice</text>


  <!-- Edges -->
  <line x1="170" y1="40" x2="250" y2="40" class="edge"/>
   <line x1="170" y1="100" x2="250" y2="100" class="edge"/>

    <line x1="390" y1="40" x2="450" y2="70" class="edge"/>
    <line x1="390" y1="100" x2="450" y2="70" class="edge"/>

     <line x1="590" y1="70" x2="590" y2="180" class="edge"/>
     <line x1="590" y1="180" x2="590" y2="280" class="edge"/>
      <line x1="520" y1="220" x2="520" y2="280" class="edge"/> <!-- Gaze Vector to Intersection -->

    <line x1="170" y1="310" x2="250" y2="310" class="edge"/>
     <line x1="390" y1="310" x2="450" y2="310" class="edge"/>

     <line x1="520" y1="320" x2="520" y2="380" class="edge"/> <!-- Intersection to Angle -->
      <line x1="590" y1="310" x2="590" y2="380" class="edge"/> <!-- Screen Plane to Angle -->

    <line x1="390" y1="410" x2="450" y2="400" class="edge"/>


  <!-- Labels -->
  <text x="420" y="150" class="label" transform="rotate(90 420,150)">Combined Gaze</text>
  <text x="420" y="250" class="label" transform="rotate(90 420,250)">Screen Target</text>


</svg>
```

**算法输出：**

*   **`isLookingAtDevice`**: Boolean，判断用户是否正看向设备屏幕。
*   **`gazeTarget`**: String 枚举，表示判断的注视目标区域（例如 `"YES_AREA"`, `"NO_AREA"`, `"LOOKING_AWAY"`, `"DETECTION_UNSTABLE"` 等）。
*   **`confidence`**: Float [0.0, 1.0]，表示 `gazeTarget` 判断的可信度。

**移动端性能与精度考量:**

*   **性能:**
    *   主要瓶颈是 MediaPipe 模型推理。在华为中低端设备上，需优化：
        *   强制使用 CPU delegate，确保 TFLite 的 XNNPack 等 CPU 优化库生效，并配置合适的线程数 (例如 2-4 个)。
        *   降低摄像头输入分辨率和帧率（如 480p@25fps），减轻 MediaPipe 输入负载。
        *   使用高效的图像格式转换（例如 Camera2 YUV 直接转 MediaPipe 格式）。
    *   算法计算相对开销小，不是主要瓶颈。
*   **精度:**
    *   依赖 MediaPipe 关键点和 Blendshape 的准确性，受光照、面部角度、遮挡影响。
    *   设备参数（屏幕相对于相机位置）的准确性至关重要，需要测量或通过简单的校准流程获取。
    *   对 Blendshape 到角度的映射需要调优和标定。
    *   采用时间平滑（如低通滤波）处理视线方向和置信度，提高结果稳定性。
    *   精心设计质量评估逻辑，准确反映判断的可信度。

## 5. 用户界面 (UI) 设计

UI 遵循极简风格，核心是清晰呈现“是”和“否”选择区域，并提供实时的视线检测状态反馈。

*   **布局:**
    *   原型采用**双选项模式**，屏幕水平等分为两个主要区域。上半部分代表“是”，下半部分代表“否”。
    *   每个区域内部包含醒目的文本标签（“是”/“否”）。
    *   在屏幕顶部或底部预留一个小型空间，用于显示系统状态指示器。

    ```svg
    <svg width="300" height="550" xmlns="http://www.w3.org/2000/svg">
      <style>
        .rect-yes { fill: #e8f5e9; stroke: #4caf50; stroke-width: 2; }
        .rect-no { fill: #ffebee; stroke: #f44336; stroke-width: 2; }
        .text { font-family: sans-serif; font-size: 48px; fill: #212121; text-anchor: middle; alignment-baseline: middle; }
        .outline { fill: none; stroke: #000; stroke-width: 3; }
      </style>
      <!-- Phone Outline -->
      <rect x="20" y="20" rx="20" ry="20" width="260" height="510" class="outline"/>

      <!-- "Yes" Area -->
      <rect x="30" y="30" rx="10" ry="10" width="240" height="245" class="rect-yes"/>
      <text x="150" y="152.5" class="text">是</text>

      <!-- "No" Area -->
      <rect x="30" y="285" rx="10" ry="10" width="240" height="245" class="rect-no"/>
      <text x="150" y="407.5" class="text">否</text>

      <!-- Divider (optional) -->
      <!-- <line x1="30" y1="275" x2="270" y2="275" stroke="#9e9e9e" stroke-width="2"/> -->

    </svg>
    ```

*   **视线检测状态反馈机制:**
    *   **注视区域高亮:** 当算法判断用户稳定注视“是”区域或“否”区域（`gazeTarget` 为 `"YES_AREA"` 或 `"NO_AREA"`，且 `confidence` 高）时，对应的区域应有明显的视觉变化，例如**边框加粗/颜色变化**或**背景色变亮**。变化应是平滑的动画。高亮强度可与置信度关联。
    *   **状态指示器:** 屏幕顶部或底部的指示器显示系统当前状态：
        *   灰色/黄色：系统处理中，或检测不稳定，或未检测到人脸/眼睛 (`gazeTarget` 为 `"LOOKING_AWAY"`, `"DETECTION_UNSTABLE"`, `"SCREEN_ACTIVE_NO_TARGET"`，或错误状态)。
        *   绿色：用户注视“是”区域 (`gazeTarget` 为 `"YES_AREA"`, `confidence` 高)。
        *   红色：用户注视“否”区域 (`gazeTarget` 为 `"NO_AREA"`, `confidence` 高)。
    *   UI 更新必须在 UI 线程进行，确保与 MediaPipe 异步结果正确同步。

## 6. 网络通信协议 (MQTT)

采用 MQTT 协议进行设备间通信，安卓视觉处理组件作为客户端发布视线状态信息。

*   **消息负载格式:** JSON 格式，包含当前视线状态信息。

    ```json
    {
      "deviceId": "string",
      "timestamp": "string",
      "gazeTarget": "string",
      "confidence": float,
      "headPose": {  // Optional
        "pitch": float,
        "yaw": float,
        "roll": float
      },
      "isLookingAtDevice": boolean  // Optional
    }
    ```
    字段说明如下表：

    | 字段名称            | 数据类型  | 是否必须 | 描述                                                                                                |
    | :------------------ | :-------- | :------- | :-------------------------------------------------------------------------------------------------- |
    | `deviceId`          | String    | 是       | 发送消息的安卓设备唯一标识符。                                                                      |
    | `timestamp`         | String    | 是       | 消息在设备上生成的 UTC 时间戳，ISO 8601 格式。                                                      |
    | `gazeTarget`        | String    | 是       | 用户当前的注视目标状态，必须是预定义的枚举值之一。                                                  |
    | `confidence`        | Float     | 是       | `gazeTarget` 判断的置信度，范围 0.0 到 1.0。                                                        |
    | `headPose`          | Object    | 否       | 用户头部的欧拉角姿态信息（pitch, yaw, roll），单位建议为度。                                          |
    | `isLookingAtDevice` | Boolean   | 否       | 综合判断用户是否正在看向设备屏幕。                                                                  |

*   **MQTT 主题结构:** `gazecontrol/device/{deviceId}/<message_type>`

    ```svg
    <svg width="400" height="200" xmlns="http://www.w3.org/2000/svg">
      <style>
        .node { fill: #e0e0e0; stroke: #333; stroke-width: 2px; }
        .edge { stroke: #666; stroke-width: 1.5px; marker-end: url(#arrow); }
        .text { font-family: sans-serif; font-size: 14px; fill: #333; text-anchor: middle; alignment-baseline: middle; }
        #arrow { markerUnits: strokeWidth; overflow: visible; }
        #arrow path { fill: #666; stroke: #666; }
      </style>
      <defs>
        <marker id="arrow" viewBox="0 -5 10 10" refX="10" refY="0" markerWidth="10" markerHeight="10" orient="auto">
          <path d="M0,-5L10,0L0,5Z" />
        </marker>
      </defs>

      <!-- Nodes -->
      <rect x="150" y="10" rx="10" ry="10" width="100" height="40" class="node"/>
      <text x="200" y="30" class="text">gazecontrol</text>

      <rect x="75" y="70" rx="10" ry="10" width="100" height="40" class="node"/>
      <text x="125" y="90" class="text">device</text>

      <rect x="0" y="130" rx="10" ry="10" width="100" height="40" class="node"/>
      <text x="50" y="150" class="text">{deviceId}</text>

      <rect x="150" y="130" rx="10" ry="10" width="100" height="40" class="node"/>
      <text x="200" y="150" class="text">gaze_status</text>

       <rect x="250" y="130" rx="10" ry="10" width="100" height="40" class="node"/>
      <text x="300" y="150" class="text">command</text>


      <!-- Edges -->
      <line x1="200" y1="50" x2="125" y2="70" class="edge"/>
       <line x1="125" y1="110" x2="50" y2="130" class="edge"/>
      <line x1="125" y1="110" x2="200" y2="130" class="edge"/>
       <line x1="125" y1="110" x2="300" y2="130" class="edge"/>

    </svg>
    ```
    *   状态更新主题：`gazecontrol/device/{deviceId}/gaze_status`
    *   命令主题 (预留)：`gazecontrol/device/{deviceId}/command`

*   **关键状态编码 (`gazeTarget` 枚举):**
    *   `"YES_AREA"`: 注视“是”区域
    *   `"NO_AREA"`: 注视“否”区域
    *   `"SCREEN_ACTIVE_NO_TARGET"`: 注视屏幕但未落在特定目标区域
    *   `"LOOKING_AWAY"`: 未注视设备屏幕
    *   `"DETECTION_UNSTABLE"`: 检测不稳定或置信度低
    *   `"CAMERA_UNAVAILABLE"`: 摄像头不可用
    *   `"MEDIAPIPE_ERROR"`: MediaPipe 处理管线错误

*   **错误/特殊状态传递:** `gazeTarget` 的特殊值用于传递高级别的错误或状态。在这些情况下，`confidence` 应设为 0.0。
*   **服务质量 (QoS):** 建议对 `gaze_status` 消息使用 **QoS 1 (At least once delivery)**，确保消息可靠送达 Broker，尽管可能重复。相比 QoS 0 更可靠，相比 QoS 2 开销更小，适合实时状态更新。

## 7. 安卓原型实现规划概要

基于第一阶段概念验证分析，关键开发步骤包括：

1.  **项目搭建与依赖集成:** 创建 Android 项目，集成 MediaPipe Tasks Vision (`tasks-vision-android` 版本) 和 MQTT 客户端库依赖，放置模型文件。
2.  **权限与摄像头管理:** 实现摄像头权限的动态请求与处理。使用 Camera2 API 实现摄像头开启、预览捕获，配置分辨率、帧率，并实现曝光和对焦锁定。
3.  **MediaPipe 初始化与帧处理:** 初始化 Face Landmarker 实例，配置 CPU delegate 和 LIVE_STREAM 模式。实现摄像头帧（ImageReader 输出）到 MediaPipe 输入格式的转换，并调用 `detectAsync` 进行异步处理。
4.  **视线算法实现:** 在 MediaPipe 的结果回调 (`onResults`) 中，提取头部姿态矩阵、Blendshapes 和关键点。实现详细的视线方向计算逻辑，包括 Blendshape 到角度映射、姿态融合、与屏幕目标区域的相对角度计算、注视目标判断和置信度计算。
5.  **UI 实现:** 开发极简布局，划分“是”/“否”区域。实现基于算法输出的视觉反馈逻辑，如区域高亮和状态指示器更新。确保 UI 更新在主线程进行。
6.  **MQTT 通信实现:** 实现 MQTT 客户端连接 Broker 的逻辑。在视线算法输出结果后，格式化为 JSON 消息，并发布到 `gazecontrol/device/{deviceId}/gaze_status` 主题，QoS 设为 1。
7.  **设备标识符:** 获取或生成设备的唯一标识符作为 `deviceId`。
8.  **测试与调试:** 在目标华为设备上进行功能、性能和兼容性测试。利用日志输出、Android Studio Profiler 等工具进行调试和优化。

## 8. 主要挑战与应对策略

| 主要挑战                                     | 影响                                                         | 应对策略                                                                                                                                                                                             | 参考资料                                                                 |
| :------------------------------------------- | :----------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------- |
| **无 GMS 环境下 MediaPipe TFLite 运行时**    | NNAPI/GPU Delegate 性能不稳定、兼容性差。                   | **强制或优先使用 TFLite CPU Delegate (XNNPack 优化)**。在原型阶段避免使用 NNAPI/GPU，以保证稳定性。                                                                                                     | IPdr1, search TFLite CPU fallback Huawei devices, search MediaPipe performance tuning Huawei no GMS |
| **MediaPipe Tasks Vision 库的潜在间接 GMS 依赖** | 可能导致在无 GMS 设备上运行时出现兼容性或功能问题。               | **优先使用 `tasks-vision-android` 版本**。仔细检查 Gradle 依赖树，排查潜在的 `com.google.android.gms` 依赖。运行时监控 Logcat 查找 GMS 相关错误。                                                      | IPdr1, search GMS indirect dependency detection in MediaPipe |
| **CameraX API 在部分华为设备上的兼容性问题**     | 初始化失败、预览异常、帧丢失、性能不稳定。                      | **强制或优先使用 Camera2 API**。虽然开发复杂度高，但作为原生 API，在无 GMS 环境下通常更稳定可靠，提供更精细的控制（如参数锁定）。                                                                             | IPdr1, search CameraX issues on Huawei non-GMS devices, search MediaPipe with Camera2 API Huawei no GMS |
| **视线检测算法的精度与鲁棒性**               | 受光照、头部姿态、面部特征、设备参数、MediaPipe 原始输出质量影响。 | **精确获取设备参数** (屏幕位置/朝向)。优化 Blendshape 映射到角度的参数。增强算法对不稳定关键点的处理逻辑。采用时间滤波平滑结果。精心设计置信度评估逻辑。考虑简单的用户校准流程。                                 | qsXjA |
| **老旧或中低端设备性能优化**                 | MediaPipe 推理耗时过长，导致帧率低、卡顿。                      | **降低摄像头输入分辨率/帧率**。确保 TFLite CPU Delegate 的多线程配置。使用高效的图像格式转换。精简 UI 绘制逻辑，避免复杂动画。在 MediaPipe 结果回调中减少计算量，仅发送必要数据。                               | qsXjA, search MediaPipe performance tuning Huawei no GMS |
| **摄像头参数（曝光、对焦）控制与锁定**         | 光照/距离变化导致图像质量不稳定，影响检测精度。                   | **使用 Camera2 API 实现精确的 AE Lock 和 AF Lock**。根据场景判断何时应用锁定（例如，在用户面部进入画面并稳定后）。                                                                                     | MO1bh, search MediaPipe with Camera2 API Huawei no GMS |
| **算法输出与 UI 反馈/MQTT 消息的映射与同步**   | UI/中央协调器接收到过时或不一致的状态信息。                     | 确保 MediaPipe 结果处理、算法计算、UI 更新和 MQTT 发布都在合适线程进行，并使用时间戳同步数据。在 UI/MQTT 接收端处理可能的延迟或乱序。                                                                         | g5UVC, iL09B |
| **设备唯一标识符获取**                       | 需要唯一 ID 区分不同的安卓设备。                               | 优先考虑使用 ANDROID_ID，它在设备恢复出厂设置前保持不变。如果需要跨应用或更持久的唯一 ID，可能需要存储 UUID 或考虑其他方案（需符合隐私规定）。避免使用 IMEI 等敏感信息。                                          | 安卓开发规范 |

以上技术规格说明书综合了先前的研究和设计成果，为安卓视觉处理组件的原型概念验证开发提供了详细的技术蓝图。在实际开发过程中，需要根据目标设备的具体情况进行细致的测试、调试和优化。