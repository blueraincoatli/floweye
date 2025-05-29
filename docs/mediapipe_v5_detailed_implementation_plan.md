# MediaPipe V5 详细实施规划

基于 `docs/mediapipe_v5_integration_plan.md` 的进一步细化。

---

### **第一阶段：Android 概念验证 (预计2周)**

这个阶段的目标是快速验证核心技术在 Android 平台上的可行性。

1.  **环境搭建与项目创建 (0.5 天)**
    *   确保 Android Studio 是最新版本。
    *   创建一个新的 Android 项目 (Kotlin 或 Java，推荐 Kotlin)。
    *   查阅 MediaPipe 官方文档，了解 Android 平台的集成方式和依赖项。
    *   在项目的 `build.gradle` 文件中添加 MediaPipe Face Landmarker 的依赖。
        *   例如：`implementation 'com.google.mediapipe:tasks-vision-face-landmarker:0.10.0'` (请以官方最新版本为准)
    *   同步项目，确保依赖下载成功。

2.  **摄像头基础功能实现 (1 天)**
    *   实现基本的摄像头预览功能。可以使用 `CameraX` API，它相对简单易用。
    *   获取摄像头权限 (`<uses-permission android:name="android.permission.CAMERA" />`)。
    *   **关键点**：确保应用使用摄像头的自动曝光设置。观察并初步记录在目标场景下（患者在病床上，光线相对稳定但可能有自然光变化）自动曝光的稳定性和图像质量。
    *   鉴于前置摄像头通常具有大景深特性，暂时不投入精力于复杂的对焦控制。

3.  **MediaPipe Face Landmarker 初始化与运行 (2 天)**
    *   参考 MediaPipe官方示例代码 (Android)，学习如何初始化 `FaceLandmarker`。
    *   配置 `FaceLandmarker.FaceLandmarkerOptions`：
        *   `numFaces`: 设置为 1 (通常我们只关注一个用户)。
        *   `outputFaceBlendshapes`: 根据需要开启或关闭 (初期可以关闭以优化性能)。
        *   `outputFacialTransformationMatrixes`: 开启，这对于后续计算头部姿态很重要。
        *   `runningMode`: 选择 `LIVE_STREAM` 模式，以便处理摄像头实时数据。
        *   设置 `resultListener` 来异步接收检测结果。
    *   将摄像头预览的每一帧图像 (`ImageProxy`) 转换为 MediaPipe 需要的 `MPImage` 格式。
    *   调用 `faceLandmarker.detectAsync()` 方法进行人脸特征点检测。

4.  **虹膜与眼球方向检测 (3 天)**
    *   在 `resultListener` 中获取 `FaceLandmarkerResult`。
    *   从结果中提取面部特征点 ( `faceLandmarks` )，特别是与眼睛相关的点。
    *   **关键点**：MediaPipe 的 Face Landmarker 已经包含了虹膜关键点 (通常是468个面部关键点之后的额外5个点，分别代表瞳孔中心和虹膜边缘)。
        *   找到这些虹膜关键点的索引 (需要仔细查阅官方文档或示例，确认是478个点中的哪几个，还是独立的虹膜输出)。
        *   文档中提到"每只眼睛5个关键点（中心+4边缘点）"，验证这些点是否直接可用。
    *   提取 MediaPipe 直接提供的眼球运动方向参数：`eyeLookOutLeft`, `eyeLookInLeft`, `eyeLookUpLeft`, `eyeLookDownLeft` (以及右眼对应参数)。
    *   **初步算法验证**：根据"核心算法思路"中的伪代码，尝试实现一个简化版的视线方向判断逻辑。
        *   计算虹膜相对于眼眶中心的偏移。
        *   结合 MediaPipe 提供的 `eyeLook` 参数。

5.  **结果可视化与初步分析 (2.5 天)**
    *   在摄像头预览界面上叠加绘制检测到的人脸特征点、特别是眼睛和虹膜的关键点。
    *   将计算出的虹膜偏移向量、`eyeLook` 参数的值实时显示在屏幕上。
    *   创建一个简单的指示器 (例如一个箭头或一个色块)，根据初步的视线方向判断逻辑，指示用户大概看向的方向 (例如：左、右、中间)。
    *   **测试**：在目标场景下，观察自动曝光时，MediaPipe关键点检测的稳定性和眼动参数的准确性。特别关注在一天中不同时间段（自然光变化时）自动曝光的适应性及其对检测结果的影响。

6.  **本地网络通信模块 (MQTT客户端) (1 天)**
    *   选择一个轻量级的 Android MQTT 客户端库，例如 `org.eclipse.paho.android.service`。
    *   实现连接到本地 MQTT Broker 的基本功能 (Broker可以暂时用PC上的Mosquitto等)。
    *   定义一个简单的状态信号格式 (例如 JSON)，包含设备ID、检测到的状态 (如 "看向设备" / "未看向设备")、时间戳、置信度 (初期置信度可以先用固定值或基于某些简单规则)。
    *   将检测结果打包成状态信号，通过 MQTT 发送出去。
    *   **测试**：用一个 MQTT 客户端工具 (如 MQTT Explorer) 订阅相应主题，查看是否能正确接收到信号。

---

### **第二阶段：iOS 实现 (预计2周)**

这个阶段主要是将 Android PoC 的成功经验移植到 iOS 平台。

1.  **环境搭建与项目创建 (0.5 天)**
    *   确保 Xcode 是最新版本。
    *   创建一个新的 iOS 项目 (Swift 或 Objective-C，推荐 Swift)。
    *   查阅 MediaPipe 官方文档，了解 iOS 平台的集成方式，通常使用 CocoaPods。
    *   在 `Podfile` 中添加 MediaPipe Face Landmarker 的依赖。
        *   例如：`pod 'GoogleMediaPipeTasksVision'` (请以官方最新说明为准)
    *   运行 `pod install`，打开 `.xcworkspace` 文件。

2.  **摄像头基础功能实现 (1.5 天)**
    *   使用 `AVFoundation`框架实现摄像头预览。
    *   获取摄像头权限 (`Info.plist` 中添加 `NSCameraUsageDescription`)。
    *   **关键点**：确保应用使用摄像头的自动曝光设置。观察并初步记录在目标场景下自动曝光的稳定性和图像质量。
    *   同样，鉴于前置摄像头特性，不投入精力于复杂对焦控制。

3.  **MediaPipe Face Landmarker 初始化与运行 (2.5 天)**
    *   参考 MediaPipe 官方 iOS 示例代码。
    *   配置 `FaceLandmarkerOptions` (与 Android 类似，但 API 调用方式会有所不同)。
    *   实现 `FaceLandmarkerLiveStreamDelegate` 协议来接收实时检测结果。
    *   将摄像头捕获的 `CMSampleBuffer` 转换为 MediaPipe 需要的 `MPImage`。
    *   调用 `faceLandmarker.detectAsync()`。

4.  **虹膜与眼球方向检测逻辑移植 (2 天)**
    *   将 Android 版本中经过验证的虹膜关键点提取逻辑和眼球方向参数使用方式，用 Swift (或 Objective-C) 重新实现。
    *   核心算法逻辑应该是一致的。

5.  **结果可视化与分析 (2.5 天)**
    *   使用 `CoreGraphics` 或 `SpriteKit`/`SceneKit` (如果需要3D叠加) 在摄像头预览上绘制关键点和调试信息。
    *   移植 Android 上的可视化指示器。
    *   **测试**：在目标 iPhone 设备上，观察自动曝光时MediaPipe检测的稳定性和准确性，尤其关注自然光变化时的表现。

6.  **本地网络通信模块 (MQTT客户端) (1 天)**
    *   选择一个适用于 iOS 的 MQTT 客户端库，例如 `CocoaMQTT`。
    *   实现与 Android 版本兼容的 MQTT 通信逻辑。

---

### **第三阶段：协调器应用 (预计2周)**

这个阶段将升级现有的 Flutter 协调器应用，使其能够与新的原生客户端配合工作。

1.  **回顾与理解现有协调器 (1 天)**
    *   如果已有协调器应用，仔细阅读其代码，理解其状态机逻辑、设备管理和 UI 更新机制。
    *   确定需要修改和扩展的部分。

2.  **更新通信协议与数据结构 (2 天)**
    *   根据原生客户端发送的状态信号格式 (在第一、二阶段定义和优化的)，更新协调器应用中用于接收和解析这些信号的数据结构。
        *   确保字段（设备ID、状态、时间戳、置信度、可能的眼动详细参数）能被正确解析。
    *   如果使用了 `status_signal.dart`，检查是否需要更新。

3.  **适配新的眼球方向数据 (3 天)**
    *   **关键点**：协调器的核心挑战在于如何有效地利用来自 MediaPipe 的更丰富的眼球方向数据 (虹膜偏移、`eyeLook` 参数)。
    *   修改状态机逻辑：
        *   之前可能只处理简单的 "看向设备" / "未看向设备" 的布尔信号。
        *   现在需要考虑如何融合来自多个设备的、带有置信度和更细致方向信息的信号。
        *   可能需要引入更复杂的规则来判断用户的最终意图，例如：
            *   哪个设备的置信度最高？
            *   用户是否持续看向某个设备一段时间？
            *   多个设备是否同时检测到用户看向它们（这可能表示用户在看中间或需要校准）？
    *   可能需要根据头部姿态信息（如果客户端也发送了面部转换矩阵的衍生数据）进行补偿。

4.  **实现与原生客户端的 MQTT 通信 (2 天)**
    *   确保 Flutter 协调器中的 MQTT 客户端 (例如 `mqtt_client` 包) 配置正确，能够订阅原生客户端发布的主题。
    *   处理连接、断开连接、消息接收等逻辑。

5.  **多设备演示与测试场景构建 (3 天)**
    *   设置一个包含至少2-3台设备（Android + iOS）的测试环境。
    *   在协调器应用中创建能够清晰展示多设备状态和用户选择结果的界面。
    *   设计一些典型的交互场景进行测试：
        *   简单选择："是"/"否" （两台设备）。
        *   条目选择：从列表中选择一项（多台设备，每台代表一个选项或一组选项）。

6.  **配置界面升级 (3 天)**
    *   根据"配置参数"部分，在协调器应用中添加或修改配置界面，以支持新的参数调整：
        *   **视线判断阈值**：例如，虹膜偏离眼眶中心的角度或像素阈值。
        *   **信号持续时间**：用户需要看向设备多久才算一次有效输入。
        *   **头部/眼球权重**：如果同时考虑头部姿态和眼球内部运动，如何分配它们的权重。
        *   **虹膜检测置信度阈值**：MediaPipe 可能会提供特征点的置信度，用于过滤不可靠的检测。
        *   设备角色、设备位置参数 (用于视线方向判断中的几何关系计算)。

---

### **第四阶段：整合与性能优化 (预计2周)**

这是打磨产品的阶段，确保系统稳定、准确、高效。

1.  **端到端系统测试 (3 天)**
    *   在真实的目标设备 (旧款 Android/华为设备、目标 iPhone 型号) 上部署所有组件 (原生客户端、Flutter 协调器)。
    *   进行全面的交互测试，覆盖所有设计的功能和交互模式。
    *   模拟不同的使用场景和环境条件 (不同光照、用户姿态变化等)。
    *   记录遇到的问题、bug、性能瓶颈。

2.  **虹膜检测与视线方向算法优化 (4 天)**
    *   **核心攻坚**：基于"核心算法思路"中的伪代码，细化并迭代实现 `判断视线方向` 函数。
        *   **眼球中心计算**：精确定义如何从眼眶相关的面部特征点计算"眼球中心"。
        *   **虹膜偏移向量**：标准化该向量，可能需要考虑头部到屏幕的距离。
        *   **`eyeLook` 参数融合**：研究这些参数的取值范围和敏感度，如何与虹膜偏移向量加权融合。
        *   **头部姿态融合**：从 `outputFacialTransformationMatrixes` (面部转换矩阵) 中提取头部的欧拉角 (pitch, yaw, roll)。设计算法，将头部朝向与眼球的相对朝向结合，得到在世界坐标系（或相对于屏幕的坐标系）中的真实视线方向。
        *   **设备位置参数**：这非常关键。协调器或客户端需要知道每个设备在物理空间中的相对位置和屏幕朝向，才能准确判断用户是否看向"这个"设备。这可能需要一个校准程序。
        *   **置信度计算**：设计一个合理的置信度评分机制。可以基于：虹膜关键点的检测质量 (MediaPipe 可能提供?)、视线与屏幕法线的夹角大小、信号的稳定性等。
    *   **迭代与调试**：在客户端应用中添加详细的日志和可视化工具，方便调试视线判断算法的每一步。例如，实时显示计算出的头部欧拉角、眼球偏移向量、最终视线向量等。

3.  **摄像头与环境适应性观察 (1 天)**
    *   在客户端，持续观察并记录在不同光线条件（尤其是自然光变化时）下，自动曝光的适应情况及其对MediaPipe检测结果（特征点稳定性、`eyeLook`参数）的影响。
    *   如果发现自动曝光在某些特定情况下导致检测显著不稳定，则考虑是否需要实现一个简单的备选方案（例如，提供几种预设的曝光补偿调整）。但主要策略仍是依赖自动曝光。

4.  **性能与电池优化 (2 天)**
    *   **客户端**：
        *   分析 MediaPipe 的运行耗时。
        *   优化图像转换和预处理步骤。
        *   检查是否有不必要的计算或内存占用。
        *   确保在主线程之外执行耗时操作。
    *   **协调器**：
        *   优化状态机逻辑和 UI 更新，避免不必要的重绘。
    *   使用 Android Studio Profiler 和 Xcode Instruments 分析CPU、内存和电池使用情况。

5.  **错误处理与鲁棒性增强 (1 天)**
    *   **客户端**：
        *   处理摄像头权限未授予、MediaPipe 初始化失败、检测无结果等情况。
        *   在网络连接断开或不稳定时，有合理的重连和提示机制。
    *   **协调器**：
        *   处理客户端意外断开、接收到格式错误的消息等情况。
        *   确保系统在部分设备故障时仍能尽可能运行 (优雅降级)。

6.  **无GMS环境适配 (针对华为设备，贯穿始终，额外预留时间)**
    *   如果在华为设备上遇到 MediaPipe 依赖 GMS 的问题 (虽然 MediaPipe Tasks API 通常设计为可在无GMS环境下工作，但需验证)，需要寻找解决方案。
    *   这可能涉及到使用 MediaPipe 的不同版本或编译选项，或者寻找替代的 ML 运行库。

---

### **核心算法思路细化：`判断视线方向`**

```pseudocode
// 在原生客户端 (Android/iOS) 中实现
function 判断视线方向(
    faceLandmarks: List<Point3D>, // MediaPipe输出的478个3D面部特征点
    irisLandmarksLeft: List<Point2D>, // 左眼5个虹膜点 (2D, 屏幕坐标)
    irisLandmarksRight: List<Point2D>, // 右眼5个虹膜点 (2D, 屏幕坐标)
    facialTransformMatrix: Matrix4x4, // 面部转换矩阵
    eyeLookParams: { // MediaPipe直接输出的眼动参数
        left: {out, in, up, down},
        right: {out, in, up, down}
    },
    cameraParams: { // 相机内参，如焦距、主点
        fx, fy, cx, cy
    },
    deviceScreenNormalVectorInWorld: Vector3D, // 当前设备屏幕法向量 (在世界坐标系或某个共享参考系中)
    deviceScreenCenterInWorld: Point3D      // 当前设备屏幕中心点 (同上)
): (isLookingAtDevice: Bool, confidence: Float)

    // === 1. 头部姿态估计 ===
    // 从 facialTransformMatrix 提取头部旋转 (欧拉角: pitch, yaw, roll) 和平移向量。
    // 这代表了头部在摄像头坐标系中的姿态。
    headRotation: EulerAngles = extractRotation(facialTransformMatrix)
    headTranslation: Vector3D = extractTranslation(facialTransformMatrix)

    // === 2. 眼球内部旋转估计 (基于虹膜偏移) ===
    // 2.1 获取眼眶中心点 (3D, 摄像头坐标系)
    //     这些索引需要根据MediaPipe文档或可视化来精确确定
    leftEyeCenter3D = average(faceLandmarks[索引_左眼眶点1], ..., faceLandmarks[索引_左眼眶点N])
    rightEyeCenter3D = average(faceLandmarks[索引_右眼眶点1], ..., faceLandmarks[索引_右眼眶点N])

    // 2.2 获取虹膜中心点 (3D, 摄像头坐标系)
    //     虹膜关键点通常是2D的，需要反投影到3D。
    //     一个简化的方法是：假设虹膜在眼球表面，其深度约等于眼眶中心深度。
    //     或者，更精确的方法是利用多个虹膜边缘点和模型化的眼球几何进行估计。
    //     MediaPipe新版FaceLandmarker可能直接输出3D虹膜点，需要确认。
    //     假设我们已经得到了3D的虹膜中心点：
    leftIrisCenter3D: Point3D = estimateIrisCenter3D(irisLandmarksLeft, leftEyeCenter3D, cameraParams)
    rightIrisCenter3D: Point3D = estimateIrisCenter3D(irisLandmarksRight, rightEyeCenter3D, cameraParams)

    // 2.3 计算从眼球中心指向虹膜中心的向量 (眼球坐标系下的视线方向近似)
    leftEyeGazeVectorLocal = normalize(leftIrisCenter3D - leftEyeCenter3D)
    rightEyeGazeVectorLocal = normalize(rightIrisCenter3D - rightEyeCenter3D)

    // === 3. 融合 MediaPipe 的 eyeLook 参数 ===
    // eyeLook参数表示眼球在眼眶内的相对转动，可以用来进一步细化 left/rightEyeGazeVectorLocal
    // 或者作为独立的特征。
    // 例如，可以根据 eyeLookParams.left.out - eyeLookParams.left.in 来调整水平方向的 gaze vector。
    // 这一步的融合策略需要实验。

    // === 4. 计算世界坐标系下的视线向量 ===
    // 4.1 将眼球坐标系下的视线向量转换到摄像头坐标系
    //     这需要知道眼球坐标系相对于头部坐标系的姿态，可以近似认为与头部姿态一致，
    //     或者根据标准人脸模型进行更精确的标定。
    //     一个简化做法：直接使用头部旋转来转换眼球的局部视线向量。
    leftEyeGazeVectorCam = applyRotation(leftEyeGazeVectorLocal, headRotation)
    rightEyeGazeVectorCam = applyRotation(rightEyeGazeVectorLocal, headRotation)

    // 4.2 (可选) 平均双眼视线向量得到单一视线向量 (在摄像头坐标系)
    combinedGazeVectorCam = normalize(average(leftEyeGazeVectorCam, rightEyeGazeVectorCam))

    // 4.3 将摄像头坐标系的视线向量原点 (可认为是头部某点或双眼中心点) 和方向向量转换到世界坐标系。
    //     这需要知道摄像头在世界坐标系中的位置和姿态 (cameraExtrinsics)。
    //     cameraExtrinsics 可能需要通过一个校准过程或者固定的设备摆放来获得。
    //     如果所有设备都只判断"是否看向自己"，则可以将每个设备的摄像头坐标系作为局部参考，
    //     然后将"屏幕位置"转换到这个摄像头坐标系下。

    // === 5. 判断是否看向当前设备屏幕 ===
    //     假设我们已经将视线起点(gazeOriginWorld)和方向(gazeDirectionWorld)以及
    //     设备屏幕中心(deviceScreenCenterInWorld)和法向量(deviceScreenNormalVectorInWorld)
    //     都转换到了同一个世界坐标系。

    // 5.1 计算从视线起点到屏幕中心的向量
    vectorToScreenCenter = deviceScreenCenterInWorld - gazeOriginWorld

    // 5.2 计算视线向量与指向屏幕中心向量的夹角
    angleToScreenCenter = angleBetween(gazeDirectionWorld, vectorToScreenCenter)

    // 5.3 计算视线向量与屏幕法线的夹角 (越小说明越正对屏幕)
    angleToScreenNormal = angleBetween(gazeDirectionWorld, deviceScreenNormalVectorInWorld)

    // 5.4 判断逻辑 (示例，需要大量调整和测试)
    isLookingAtDevice = false
    confidence = 0.0

    thresholdAngleCenter = 15.0 // 视线与屏幕中心连线的最大允许夹角 (度)
    thresholdAngleNormal = 25.0 // 视线与屏幕法线的最大允许夹角 (度)

    if angleToScreenCenter < thresholdAngleCenter AND angleToScreenNormal < thresholdAngleNormal:
        isLookingAtDevice = true
        // 置信度可以基于角度大小、虹膜检测质量 (MediaPipe是否提供?) 等
        confidence = (1.0 - angleToScreenNormal / thresholdAngleNormal) * (1.0 - angleToScreenCenter / thresholdAngleCenter)
        confidence = max(0, min(1, confidence)) //确保在0-1之间

    return isLookingAtDevice, confidence

end function
``` 