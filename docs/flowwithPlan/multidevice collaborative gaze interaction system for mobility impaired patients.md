# 多设备协同视线交互系统项目最终总结报告

## 1. 系统总体架构与组件功能说明

本项目旨在构建一个面向行动受限患者的多设备协同视线交互系统。该系统采用分布式架构，由多个运行在安卓或 iOS 设备上的**客户端（分布式定向感知节点）**和一个运行在桌面平台（如 Windows, macOS, Linux）上的**中央协调器**组成，通过本地网络的 **MQTT Broker** 进行实时状态通信。

系统核心功能是通过客户端设备的前置摄像头检测用户的视线方向，判断用户是否注视该设备屏幕，并将此状态上报至中央协调器。中央协调器综合分析多个客户端的状态，做出最终的用户选择判断（例如，用户选择了屏幕显示“是”的设备，或显示“否”的设备）。

系统的总体架构如下所示：

```svg
<svg width="900" height="400" xmlns="http://www.w3.org/2000/svg">
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
  <rect x="50" y="150" rx="10" ry="10" width="150" height="80" class="node component"/>
  <text x="125" y="190" class="text">安卓客户端 A<tspan x="125" dy="1.2em">(感知节点)</tspan></text>

   <rect x="250" y="150" rx="10" ry="10" width="150" height="80" class="node component"/>
  <text x="325" y="190" class="text">安卓客户端 B<tspan x="325" dy="1.2em">(感知节点)</tspan></text>

   <rect x="450" y="150" rx="10" ry="10" width="150" height="80" class="node component"/>
  <text x="525" y="190" class="text">iOS 客户端 C<tspan x="525" dy="1.2em">(感知节点)</tspan></text>


  <rect x="700" y="150" rx="10" ry="10" width="150" height="80" class="node component"/>
  <text x="775" y="190" class="text">中央协调器<tspan x="775" dy="1.2em">(决策与展示)</tspan></text>

  <!-- MQTT Broker (Optional, could be on Central Coordinator) -->
  <rect x="400" y="20" rx="10" ry="10" width="100" height="60" class="node process"/>
  <text x="450" y="50" class="text">MQTT Broker</text>


  <!-- Data/Flows -->
  <line x1="125" y1="80" x2="450" y2="20" class="edge" marker-end=""/>
  <line x1="325" y1="80" x2="450" y2="20" class="edge" marker-end=""/>
  <line x1="525" y1="80" x2="450" y2="20" class="edge" marker-end=""/>
  <line x1="775" y1="80" x2="450" y2="20" class="edge" marker-end=""/>
   <text x="450" y="90" class="text">MQTT</text>


  <line x1="125" y1="150" x2="125" y2="90" class="edge"/>
  <line x1="325" y1="150" x2="325" y2="90" class="edge"/>
  <line x1="525" y1="150" x2="525" y2="90" class="edge"/>

  <line x1="125" y1="90" x2="400" y2="50" class="edge"/>
  <line x1="325" y1="90" x2="400" y2="50" class="edge"/>
  <line x1="525" y1="90" x2="400" y2="50" class="edge"/>
  <text x="300" y="120" class="data">Publish Gaze State</text>

  <line x1="400" y1="50" x2="775" y2="90" class="edge"/>
   <line x1="775" y1="90" x2="775" y1="150" class="edge"/>
   <text x="600" y="120" class="data">Subscribe Gaze States</text>

   <text x="775" y="270" class="text">综合判断用户意图</text>
   <text x="775" y="300" class="text">(是/否/无选择)</text>

</svg>
```

### 1.1 安卓客户端

*   **核心功能:**
    *   摄像头管理：通过 Camera2 API 捕获前置摄像头视频流，实现曝光和对焦锁定。
    *   MediaPipe 集成：集成 MediaPipe Tasks Vision (specifically Face Landmarker V5)，实时处理视频帧，获取人脸关键点、头部姿态矩阵和眼球 Blendshape。
    *   **本机视线判断：** 基于 MediaPipe 数据，运行本地视线判断算法，确定用户是否正看向**当前设备屏幕所在的物理方向**，并输出置信度。
    *   MQTT 发布：将本机检测到的用户注视状态（包括设备 ID, 时间戳, 注视目标状态 `gazeTarget`, 置信度 `confidence`, 本机显示内容 `displayedContent`, `isLookingAtThisDevice`）实时发布到 MQTT Broker 的特定主题 (`gazecontrol/device/{deviceId}/gaze_status`)。
    *   极简 UI：显示本机代表的选项（“是”或“否”，几乎撑满屏幕），并根据本机注视状态提供视觉反馈（如边框高亮）。
*   **技术实现:** 主要使用 Kotlin/Java，Camera2 API，MediaPipe Tasks Vision Android Library，以及 Android MQTT 客户端库 (e.g., Eclipse Paho)。
*   **相互协作:** 作为感知节点，独立运行，仅通过 MQTT 向协调器发送本机状态，不直接与其他客户端通信。

### 1.2 iOS 客户端

*   **核心功能:** 与安卓客户端功能对等，作为分布式定向感知节点。
    *   摄像头管理：使用 AVFoundation 捕获前置摄像头视频流。
    *   MediaPipe 集成：集成 MediaPipe Tasks Vision (iOS Library)，实时处理视频帧，获取人脸关键点、头部姿态和眼球 Blendshape。
    *   **本机视线判断：** 基于 MediaPipe 数据，运行本地视线判断算法，确定用户是否正看向**当前 iOS 设备屏幕所在的物理方向**，并输出置信度。
    *   MQTT 发布：将本机检测到的用户注视状态 (与安卓客户端相同的 JSON 格式) 实时发布到 MQTT Broker。
    *   极简 UI：显示本机代表的选项（“是”或“否”），并根据本机注视状态提供视觉反馈。
*   **技术实现:** 主要使用 Swift，AVFoundation 框架，MediaPipe Tasks Vision iOS Library，以及 iOS MQTT 客户端库 (e.g., CocoaMQTT, Moscapsule)。
*   **相互协作:** 与安卓客户端类似，独立运行，仅通过 MQTT 向协调器发送本机状态。

### 1.3 中央协调器

*   **核心功能:**
    *   MQTT 订阅：连接 MQTT Broker，订阅来自所有客户端设备的状态主题 (`gazecontrol/device/+/gaze_status`)。
    *   消息接收与解析：接收并解析来自客户端的 JSON 消息，校验数据有效性。
    *   客户端状态管理：实时维护和更新所有连接客户端的最新状态信息，包括注视状态、置信度、显示内容及最后更新时间。管理设备在线/离线状态。
    *   **综合决策逻辑：** 基于所有活跃客户端报告的注视状态 (`isLookingAtThisDevice`) 和置信度 (`confidence`)，特别是用户明确注视某个设备的信号，运用预设的决策算法判断用户的最终意图是选择“是”、“否”还是“无明确选择”。
    *   结果展示 UI：提供用户界面，实时显示各客户端的状态列表以及协调器做出的最终决策结果。可能包含 MQTT 连接状态指示。
*   **技术实现:** 选用 Flutter 框架，使用 `mqtt_client` 库进行 MQTT 通信，选用 Riverpod 或 Provider 进行状态管理，构建响应式 UI。
*   **相互协作:** 作为系统中心，接收并处理所有客户端上报的状态信息，但不向客户端下发实时控制指令（原型阶段客户端配置为静态）。

## 2. 核心算法实现细节

本项目包含两个核心算法：客户端的**本机视线方向判断算法**和中央协调器的**多设备协同决策算法**。

### 2.1 客户端：本机视线方向判断算法

该算法运行在每个客户端设备上，基于 MediaPipe Face Landmarker V5 输出，判断用户是否正看向**当前设备屏幕所在的物理方向**。

*   **算法输入：** MediaPipe Face Landmarker V5 检测结果中的头部姿态变换矩阵 (`facialTransformationMatrixes`) 和眼球相关的 Blendshape 系数 (`faceBlendshapes`)。
*   **主要逻辑：**
    1.  **解析头部姿态：** 从 4x4 头部变换矩阵中提取头部在相机坐标系中的旋转矩阵 $R_{head}$ 和大致位置 $P_{head\_camera}$。
    2.  **计算眼球相对旋转：** 根据 `eyeLookUp/Down/In/Out` Blendshape 系数，映射到眼球相对于头部坐标系的水平和垂直旋转角度，构建眼球相对旋转矩阵 $R_{eye\_rel}$。
    3.  **融合姿态得到相机空间视线向量：** 将眼球在中立状态下的头部坐标系方向向量（例如 $(0,0,1)$ 或 $(0,0,-1)$，取决于 MediaPipe 坐标系约定）左乘 $R_{eye\_rel}$ 得到眼球在头部坐标系中的方向 $V_{eye\_actual\_face}$。然后将 $V_{eye\_actual\_face}$ 左乘 $R_{head}$ 得到视线在相机坐标系中的方向向量 $V_{gaze\_camera}$。视线起点近似取为 $P_{eye\_camera} \approx P_{head\_camera}$。
    4.  **定义本机屏幕方向：** 在相机坐标系中定义当前设备屏幕的**近似物理方向**。这可以通过定义屏幕中心的大致位置 $P_{this\_device\_screen\_center\_camera}$。此位置可在应用启动时通过预设配置或简单校准确定。
    5.  **计算视线与设备方向夹角：** 计算视线向量 $V_{gaze\_camera}$ 与从眼球位置 $P_{eye\_camera}$ 指向本机屏幕中心 $P_{this\_device\_screen\_center\_camera}$ 的向量 $V_{eye\_to\_this\_device}$ 之间的夹角 $\alpha_{this\_device}$。
        ```latex
        $$\alpha_{this\_device} = \operatorname{arccos}\left( \frac{V_{gaze\_camera} \cdot V_{eye\_to\_this\_device}}{|V_{gaze\_camera}| |V_{eye\_to\_this\_device}|} \right)$$
        ```
    6.  **判断是否看向本机：** 设定一个注视阈值角度 $\theta_{gaze\_threshold}$。如果 $\alpha_{this\_device} < \theta_{gaze\_threshold}$，则判断 `isLookingAtThisDevice = true`，否则为 `false`。
    7.  **计算置信度：** 综合 MediaPipe 原始置信度、关键点稳定性、Blendshape 值合理性、帧间结果一致性等因素，计算一个 0.0 到 1.0 的综合置信度 `confidence`。
*   **算法输出：** `isLookingAtThisDevice` (Boolean), `confidence` (Float), `gazeTarget` (String 枚举)。

该算法流程图如下：

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
  <text x="110" y="310" class="text">This Device's<tspan x="110" dy="1.2em">Physical Direction</tspan></text>

   <rect x="250" y="380" rx="10" ry="10" width="140" height="60" class="node process"/>
  <text x="320" y="410" class="text">Calculate Angle<tspan x="320" dy="1.2em">to This Device</tspan></text>

   <rect x="450" y="380" rx="10" ry="10" width="140" height="40" class="node output"/>
  <text x="520" y="405" class="text">isLookingAtThisDevice</text>


  <!-- Edges -->
  <line x1="170" y1="40" x2="250" y2="40" class="edge"/>
   <line x1="170" y1="100" x2="250" y2="100" class="edge"/>

    <line x1="390" y1="40" x2="450" y2="70" class="edge"/>
    <line x1="390" y1="100" x2="450" y2="70" class="edge"/>

     <line x1="590" y1="70" x2="590" y2="180" class="edge"/>
      <line x1="520" y1="220" x2="520" y2="380" class="edge"/> <!-- Gaze Vector to Angle -->

    <line x1="170" y1="310" x2="320" y2="380" class="edge"/> <!-- Device Direction to Angle -->

     <line x1="390" y1="410" x2="450" y2="400" class="edge"/>


  <!-- Labels -->
  <text x="420" y="150" class="label" transform="rotate(90 420,150)">Combined Gaze</text>
  <text x="420" y="300" class="label" transform="rotate(90 420,300)">Device Position</text>


</svg>
```

### 2.2 中央协调器：多设备协同决策逻辑

该算法运行在中央协调器上，综合来自多个客户端的视线状态报告，判断用户的最终选择。

*   **算法输入：** 所有当前活跃客户端设备（安卓和 iOS）的最新状态列表，每条状态包含 `deviceId`, `gazeTarget`, `confidence`, `displayedContent`, `isLookingAtThisDevice` 等。
*   **主要逻辑：**
    1.  **设备状态过滤与准备：** 过滤掉长时间未更新（例如最后 5 秒内无消息）的设备，将其视为离线。从活跃设备列表中，过滤掉 `gazeTarget` 为 `"DETECTION_UNSTABLE"` 或 `confidence` 低于预设阈值（例如 0.5）的不可靠信号。
    2.  **识别“看向本机”信号：** 从过滤后的列表中，找出所有 `isLookingAtThisDevice` 为 `true` 的设备，按 `confidence` 降序排列。
    3.  **识别“未看向本机”信号：** 从过滤后的列表中，找出所有 `isLookingAtThisDevice` 为 `false` 的设备，按 `confidence` 降序排列。
    4.  **执行决策判断：**
        *   **高置信度“看向”判断：** 如果排名最高的 `isLookingAtThisDevice: true` 设备，其 `confidence` 高于更高阈值（例如 0.7），且没有其他设备报告 `isLookingAtThisDevice: true` 且置信度与其接近（例如差值小于 0.1），则最终决策为该设备的 `displayedContent`（“是”或“否”）。
        *   **多高置信度“看向”冲突：** 如果有多个设备同时报告高置信度的 `isLookingAtThisDevice: true`，或置信度差异微小，则判断为“无明确选择”或“正在犹豫”。
        *   **无高置信度“看向”：** 如果没有设备报告高置信度的 `isLookingAtThisDevice: true`：
            *   若存在高置信度 `isLookingAtThisDevice: false` 的设备，则判断为“无明确选择”或“未注视任何选项”。
            *   若所有信号均不稳定或低置信度，则判断为“检测不稳定”。
        *   **单设备情况：** 如果只有一个活跃设备，决策直接取决于该设备的 `isLookingAtThisDevice` 和 `confidence`。
    5.  **决策输出：** 输出最终决策结果（例如，枚举值 `Decision.YES`, `Decision.NO`, `Decision.NO_CHOICE`, `Decision.UNSTABLE`）。
*   **数据处理流程：** MQTT 消息接收 -> JSON 解析 -> 更新设备状态管理器 -> 状态管理器通知决策逻辑模块 -> 决策逻辑计算结果 -> 更新协调器 UI。
*   **决策更新频率：** 决策应在接收到新的高置信度客户端状态消息时触发，可设置最小更新间隔避免抖动。目标端到端响应时间（视线变化到协调器决策更新）小于 500ms。

决策逻辑简化流程图如下：

```svg
<svg width="700" height="450" xmlns="http://www.w3.org/2000/svg">
  <style>
    .node { fill: #e0e0e0; stroke: #333; stroke-width: 2px; }
    .edge { stroke: #666; stroke-width: 1.5px; marker-end: url(#arrow); }
    .text { font-family: sans-serif; font-size: 14px; fill: #333; text-anchor: middle; }
    .input { fill: #a8d8ff; }
    .output { fill: #a8ffb8; }
    .process { fill: #fff; }
    .decision { fill: #ffcc99; }
    .condition { fill: #ffff99; }
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
  <rect x="250" y="20" rx="10" ry="10" width="200" height="40" class="node input"/>
  <text x="350" y="45" class="text">所有活跃设备状态 (List&lt;DeviceState&gt;)</text>

  <rect x="250" y="80" rx="10" ry="10" width="200" height="40" class="node process"/>
  <text x="350" y="105" class="text">过滤低置信度/不稳定信号</text>

  <rect x="150" y="150" rx="10" ry="10" width="200" height="40" class="node condition"/>
  <text x="250" y="175" class="text">存在高置信度 "看向本机" 的设备?</text>

  <rect x="400" y="150" rx="10" ry="10" width="200" height="40" class="node condition"/>
  <text x="500" y="175" class="text">存在高置信度 "未看向本机" 的设备?</text>


  <rect x="50" y="250" rx="10" ry="10" width="200" height="40" class="node decision"/>
  <text x="150" y="275" class="text">最终决策: 该设备显示的选项</text>

  <rect x="250" y="300" rx="10" ry="10" width="200" height="40" class="node decision"/>
  <text x="350" y="325" class="text">最终决策: 无明确选择 / 正在犹豫</text>

  <rect x="450" y="250" rx="10" ry="10" width="200" height="40" class="node decision"/>
  <text x="550" y="275" class="text">最终决策: 无明确选择 / 未注视任何选项</text>

  <rect x="250" y="400" rx="10" ry="10" width="200" height="40" class="node output"/>
  <text x="350" y="425" class="text">决策结果 (到 UI)</text>


  <!-- Edges -->
  <line x1="350" y1="60" x2="350" y2="80" class="edge"/>
  <line x1="350" y1="120" x2="250" y2="150" class="edge"/>
   <line x1="350" y1="120" x2="450" y2="150" class="edge"/>

  <line x1="250" y1="190" x2="150" y2="250" class="edge"/>
   <text x="200" y="220" class="label">Yes</text>

  <line x1="250" y1="190" x2="350" y2="300" class="edge"/>
   <text x="300" y="220" class="label">Conflict / Low Diff</text>

   <line x1="400" y1="190" x2="550" y2="250" class="edge"/>
    <text x="470" y="220" class="label">Yes (No High "Looking At")</text>

   <line x1="400" y1="190" x2="350" y2="300" class="edge"/>
   <text x="400" y="320" class="label">No / Unstable</text>

   <line x1="150" y1="290" x2="350" y2="400" class="edge"/>
    <line x1="350" y1="340" x2="350" y2="400" class="edge"/>
     <line x1="550" y1="290" x2="350" y2="400" class="edge"/>


</svg>
```

## 3. 预期技术挑战与解决方案

基于项目规划和研究，预期可能遇到的主要技术挑战及解决方案如下：

| 挑战领域                  | 具体挑战                                                                                                                              | 预期影响                                                           | 规划/建议解决方案                                                                                                                                                                                                                                                           | 来源                                                                                                                                                              |
| :------------------------ | :------------------------------------------------------------------------------------------------------------------------------------ | :----------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MediaPipe 在特定设备适配** | MediaPipe TFLite 运行时在无 GMS 华为设备上的 GPU/NNAPI Delegate 性能不稳定或兼容性差。                                                    | 推理速度慢，帧率低，影响实时性；运行时崩溃或异常。                                | **强制或优先使用 TFLite CPU Delegate (特别是支持 XNNPack 优化的版本)**。在原型阶段避免使用 NNAPI/GPU，以保证稳定性。通过动态检测 NNAPI/GPU 可用性并自动回退到 CPU。                                                                                                          | IPdr1, search TFLite CPU fallback Huawei devices, search MediaPipe performance tuning Huawei no GMS, search TFLite GPU and NNAPI optimization on Huawei without GMS |
| **MediaPipe GMS 依赖**   | MediaPipe Tasks Vision 库是否存在潜在的、未明确声明的间接 GMS 依赖，导致在无 GMS 环境下运行时异常。                                          | 应用无法启动或部分功能受限。                                                         | **优先使用 `tasks-vision-android` 版本**。仔细审查 Gradle/Pod 依赖树，排查潜在的 `com.google.android.gms` 依赖。运行时监控 Logcat 查找 GMS 相关错误。必要时考虑从源码构建并剔除依赖。                                                                                            | IPdr1, search GMS indirect dependency detection in MediaPipe, search MediaPipe Tasks Vision library GMS dependencies check, search MediaPipe vision tasks without Google Mobile Services |
| **Camera API 兼容性**    | CameraX 在无 GMS 华为设备上兼容性问题（如初始化失败、预览异常、帧丢失）。                                                                 | 无法稳定获取摄像头输入，导致视觉处理中断。                                             | **安卓端强制或优先使用 Camera2 API**。虽然开发复杂度高，但作为原生 API 更稳定可靠，提供更精细控制。iOS 端使用 AVFoundation。                                                                                                                                                    | IPdr1, search CameraX issues on Huawei non-GMS devices, search MediaPipe with Camera2 API Huawei no GMS, search CameraX API compatibility on Huawei devices without GMS |
| **跨平台开发一致性**      | 安卓和 iOS 客户端在 MediaPipe 模型推理结果、视线算法实现细节（如 Blendshape 映射参数、角度阈值）和 MQTT 消息发布频率/内容上的差异。                     | 可能导致不同设备报告的状态不完全一致，影响协调器决策准确性。                                 | 确保核心算法逻辑（基于头部姿态和 Blendshapes 计算视线向量，以及判断是否看向本机）在两平台实现上高度一致。 Blendshape 到角度的映射参数需统一或通过标定获得。MQTT 消息格式、主题和 QoS 严格遵守协议规范。                                                                                    | IPdr1, iOS Client Plan, Gaze Algorithm Design, MQTT Spec                                                                                                          |
| **本地网络通信稳定性与低延迟** | MQTT 消息在本地网络传输过程中可能出现延迟、丢包或连接中断。                                                              | 客户端状态更新不及时，协调器获取的状态滞后或不完整，影响决策实时性和准确性。                        | MQTT 客户端和协调器端实现自动重连机制。对于状态消息使用 **QoS 1 (At least once)** 保证消息可靠送达。协调器端实现设备状态超时判断，将长时间未更新的设备标记为离线。在网络不稳定时，客户端可适当降低消息发布频率，但仍需发送心跳。                                                | MQTT Spec, Coordinator Plan, System Integration and Testing Plan                                                                                                |
| **多设备协同决策准确性与鲁棒性** | 在用户视线在不同设备间快速切换、视线介于两个设备之间、或部分客户端信号不稳定/缺失时，协调器难以做出准确判断。                                   | 误判、决策频繁抖动、或长时间无决策输出。                                               | 优化决策算法：引入置信度阈值过滤低质量信号；处理多个高置信度“看向”信号时的冲突（例如，引入时间窗口，连续稳定信号才确认为选择）。实现设备离线处理逻辑。UI 上清晰显示决策过程和依赖的设备状态，必要时允许人工介入。                                                                   | Coordinator Plan, System Integration and Testing Plan                                                                                                           |
| **视线判断算法鲁棒性**     | 在不同光照、用户头部姿态、距离、面部遮挡或特征点抖动等复杂场景下，客户端本地视线判断算法的准确性和稳定性受影响。                     | 客户端报告错误的注视状态或置信度过低。                                                 | 优化摄像头配置（曝光对焦锁定）。对 MediaPipe 原始输出（关键点、Blendshape）进行有效性检查和滤波。精心设计 Blendshape 到角度的映射。对最终的 `isLookingAtThisDevice` 状态和 `confidence` 进行时间平滑处理。加强置信度评估逻辑，准确反映当前判断可靠性。                              | Gaze Algorithm Design, IPdr1, System Integration and Testing Plan                                                                                             |
| **中低端设备性能**         | 客户端设备资源有限，MediaPipe 推理和图像处理可能消耗过多 CPU/内存，导致设备发热、卡顿，影响用户体验。                                    | 应用运行不流畅，甚至崩溃。                                                             | 降低摄像头输入 MediaPipe 的分辨率和帧率。确保 TFLite CPU Delegate 使用多线程优化。优化图像预处理和转换效率。精简 UI 绘制。持续进行性能 Profiling，定位瓶颈。考虑对模型进行量化。                                                                                                      | Gaze Algorithm Design, IPdr1, search MediaPipe performance tuning Huawei no GMS, search MediaPipe model optimization Huawei devices |

## 4. 系统性能评估框架与指标

根据系统集成与测试计划，系统性能评估将采用分层级、多维度的框架。由于尚未有实际测试数据，本部分重点阐述评估方法和关键指标的预期标准。

**评估框架：**

1.  **客户端性能测试：** 关注单个客户端在设备上的运行表现，主要通过设备自带或开发者工具提供的性能分析器（如 Android Studio Profiler, Xcode Instruments）进行。
2.  **通信性能测试：** 关注 MQTT 消息的传输效率和可靠性，通过 MQTT 客户端工具（如 MQTT Explorer）或在应用内部记录时间戳进行。
3.  **系统集成性能测试：** 关注从用户行为到协调器最终决策的全链路响应时间，通过在客户端和协调器日志中记录时间戳并计算差值，或使用外部计时工具进行。
4.  **鲁棒性测试：** 在不同环境条件（光照、距离、姿态）和异常情况（网络波动、客户端下线）下，观察系统功能的稳定性和决策的准确性。

**关键性能指标 (KPIs)：**

| 指标类别       | 关键指标                            | 测量方法                                                                                                                                                                                             | 预期标准 (或目标)                                                                                                                                    | 对用户体验的重要性                                                                                                                                                             |
| :------------- | :---------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **客户端性能**   | **客户端应用的 CPU/内存资源消耗**       | 使用设备性能分析工具监控应用长时间运行时的 CPU 使用率、内存占用峰值和稳定性。                                                                                                                          | CPU 使用率应在合理范围内 (例如持续运行时不超过 30-50%)，不导致设备明显发热。内存占用应稳定，无明显泄漏。                                                               | 影响设备续航能力、设备温度以及是否影响设备上同时运行的其他应用。资源消耗过高会导致设备卡顿或崩溃。                                                                               |
| **客户端性能**   | **视线判断算法处理帧率 (FPS)**         | 在 MediaPipe 回调中记录每帧处理完成所需时间，计算平均帧率。                                                                                                                                              | 理想情况下达到 20-30 FPS。最低应保证在 10-15 FPS 以上以提供基本的实时感知能力。                                                                                    | 直接影响视线判断的实时性和流畅性。帧率过低会导致用户视线变化无法被及时捕获，反馈滞后。                                                                                             |
| **客户端性能**   | **单帧处理延迟 (MediaPipe + 算法)** | 记录从摄像头捕获帧到视线算法输出结果的时间间隔。                                                                                                                                                       | 理想情况下小于 50-100 ms。                                                                                                                             | 影响客户端本地 UI 反馈的及时性。                                                                                                                                               |
| **算法精度**     | **视线判断准确率 (Accuracy)**          | 在不同用户、不同环境下收集大量标注数据，与算法判断结果（isLookingAtThisDevice）进行对比。计算 (正确判断样本数) / (总样本数)。                                                                                    | 核心判断 (看向本机 vs 未看向本机) 的准确率应尽可能高，目标 > 85-90%。                                                                                                 | 直接影响系统能否正确理解用户的注视意图。准确率低会导致误判，用户体验差。                                                                                                           |
| **算法精度**     | **视线判断召回率 (Recall)**            | 在标注数据中，计算 (正确判断看向本机的样本数) / (实际看向本机的样本数)。                                                                                                                                    | 召回率应尽可能高，避免漏判用户的有效注视。                                                                                                                           | 影响系统对用户意图的响应灵敏度。召回率低意味着用户注视了设备但系统未检测到，用户会觉得系统“不灵敏”。                                                                              |
| **通信性能**     | **MQTT 消息的平均延迟**              | 在客户端发布消息前和协调器接收消息后记录时间戳，计算平均传输延迟。                                                                                                                                       | 本地网络环境下，平均延迟应小于 50-100 ms。                                                                                                                             | 影响客户端状态同步到协调器的速度，是端到端响应时间的关键组成部分。                                                                                                               |
| **通信性能**     | **MQTT 消息的丢包率**                | 在客户端和协调器记录已发布和已接收消息数量。对于 QoS 1，理论丢包率为 0（Broker 层面）。但需关注客户端到 Broker 的实际送达率或协调器处理消息的完整性。                                                               | QoS 1 保证消息至少送达一次，实际应用层无需担心物理层丢包。关注协调器能否持续接收到来自活跃客户端的消息。                                                                       | 消息丢失会导致协调器状态信息不完整，可能基于过时状态做出决策。                                                                                                                 |
| **系统集成性能** | **端到端响应时间**                   | 测量从用户视线变化（客户端检测到并判断看向本机/未看向本机状态）到中央协调器更新最终决策结果并显示在 UI 上的时间间隔。                                                                                              | **关键指标，目标小于 500 ms。** 理想更低 (如 300 ms)。                                                                                                                   | **核心用户体验指标。** 响应时间越短，系统越“跟手”，交互越流畅自然。延迟过高会使用户感到系统迟钝。                                                                             |
| **系统集成性能** | **多客户端高负载下的协调器性能**       | 模拟多个客户端同时以高频率发布状态消息，监控协调器应用的 CPU/内存使用率和决策计算耗时。                                                                                                                | 协调器应能稳定处理多客户端并发消息，CPU/内存使用率在合理范围，决策计算耗时不成为瓶颈。                                                                                           | 决定系统能够支持的设备数量以及在多设备场景下的稳定性。                                                                                                                         |

## 5. 用户测试考量

进行用户测试对于验证系统的实际可用性、易用性以及在真实使用场景下的表现至关重要。考虑到本系统的特殊目标用户群体（行动受限患者），用户测试的设计和执行需要更加审慎和周全。

**用户测试的重要性：**

*   **验证核心功能可用性：** 确认系统在实际用户（而非开发者）操作和自然行为模式下，视线检测和最终决策是否准确可靠。
*   **评估易用性与舒适度：** 对于目标用户而言，系统的界面是否清晰易懂，交互是否直观流畅，长时间使用是否舒适（例如，对眼睛或头部的要求是否过高）。
*   **收集真实反馈：** 获取目标用户对系统优缺点、潜在问题以及改进方向的直接反馈。
*   **评估环境适应性：** 在典型的用户使用环境（如病房、居家客厅）下测试系统对不同光照、背景、设备摆放方式的适应性。

**建议的用户测试方案 (如果尚未进行)：**

1.  **目标用户群体：**
    *   优先邀请行动受限但认知能力正常的潜在用户进行测试。
    *   可根据患者的具体行动受限程度进行分组，例如轻度、中度、重度。
    *   也可包含照护人员作为观察者或辅助者（在需要时）。
    *   招募少量具备视线控制经验或使用过类似辅助技术的用户（如果可能）。
2.  **测试场景：**
    *   **基础功能测试：** 用户按指示明确看向显示“是”或“否”的设备，验证系统能否快速准确识别。
    *   **自然交互测试：** 用户在模拟真实使用场景下（例如，选择一个简单的选项、浏览菜单）自然地移动视线，观察系统的响应。
    *   **不同设备布局测试：** 测试设备以不同角度和距离摆放时，系统的表现。
    *   **环境因素测试：** 在不同的光照条件（白天、夜晚、室内灯光）下进行测试。
    *   **长时间使用模拟：** 让用户使用一段时间，评估疲劳度和稳定性。
    *   **异常情况模拟：** 例如，故意部分遮挡面部、快速移动头部，观察系统的鲁棒性表现和错误反馈。
3.  **需要收集的关键反馈点：**
    *   **视线判断准确性：** 用户是否觉得系统准确捕捉了他们的意图？是否有误判或漏判？
    *   **响应速度：** 系统从用户注视到给出反馈/决策需要多长时间？是否感到延迟？
    *   **易用性：** 系统是否容易理解和使用？界面是否清晰？是否有困惑的地方？
    *   **舒适度：** 长时间使用是否感到眼部或头部疲劳？对姿态是否有不适要求？
    *   **不同光照和距离下的表现：** 系统在不同环境下的稳定性和准确性如何？
    *   **UI 清晰度：** 客户端屏幕上显示的“是”/“否”文字是否足够大和清晰？协调器界面上的状态显示是否易读？
    *   **期望的功能或改进：** 用户认为系统还可以增加哪些功能或在哪些方面进行改进？
4.  **测试方法：**
    *   **观察法：** 测试人员在旁观察用户的操作过程和系统的响应。
    *   **访谈法：** 在测试后或测试过程中与用户进行交流，收集定性反馈。
    *   **问卷法：** 设计结构化问卷，收集用户对各项指标的评分和意见。
    *   **日志记录：** 应用内部记录关键事件日志（如每次注视判断结果、置信度、时间戳），辅助分析。
    *   **视频记录：** 记录用户测试过程的视频，用于回放分析用户行为和系统表现。
5.  **测试注意事项：**
    *   测试环境应舒适且安全。
    *   测试流程应清晰简洁，给用户充分的指导。
    *   保持耐心，给用户充足的时间进行操作和表达。
    *   确保测试人员对系统的技术原理有基本了解，能初步判断问题原因。
    *   对于行动受限的用户，可能需要调整测试任务的复杂度或由照护人员辅助完成部分操作（如设备摆放）。

## 6. 未来扩展与改进建议

基于当前系统设计和技术潜力，未来可以从以下方向进行扩展和改进：

**未来扩展方向：**

1.  **支持更多类型的交互：** 除了简单的“是/否”选择，可以扩展支持其他基于视线或简单面部动作的交互，例如：
    *   **眨眼识别：** 利用 MediaPipe Blendshape 数据检测用户眨眼，作为确认选择或触发其他操作的信号。
    *   **特定面部表情识别：** 检测微笑、皱眉等简单表情，用于表达情绪或进行简单指令。
    *   **更精细的屏幕注视区域判断：** 如果对精度要求提高，可以判断用户注视屏幕上的具体区域（如用于菜单选择或虚拟键盘输入），但这需要更复杂的算法和校准。
2.  **集成更多智能辅助功能：**
    *   **文字输入辅助：** 开发基于视线的虚拟键盘。
    *   **环境控制集成：** 通过视线控制智能家居设备（如灯光、窗帘）。
    *   **信息浏览优化：** 支持通过视线进行网页滚动、文本放大等。
3.  **适配更多类型的设备或操作系统：**
    *   **桌面端应用：** 开发 Windows/macOS 客户端，支持使用普通摄像头进行视线跟踪。
    *   **专用硬件集成：** 与专用的高精度眼球跟踪硬件集成，提供更准确的视线数据。
    *   **智能眼镜/头戴设备支持：** 适配未来可能出现的视线跟踪智能穿戴设备。
4.  **云端部署选项：**
    *   将中央协调器迁移到云端，实现更灵活的部署和管理，支持跨地域或更复杂场景的应用。
    *   考虑将部分计算量大但对实时性要求稍低的任务（如用户行为分析、个性化模型训练）迁移到云端。

**改进建议：**

1.  **算法精度和鲁棒性优化：**
    *   **数据驱动的 Blendshape 映射：** 通过收集更多用户数据，训练更精确的 Blendshape 值到实际眼球角度的映射模型，减少对固定经验参数的依赖。
    *   **多模态融合：** 探索融合基于 Blendshape 的眼球旋转和基于关键点的几何方法，提高视线判断的稳定性。
    *   **设备参数自适应：** 开发更鲁棒的设备位置/朝向校准流程，甚至尝试无感知的自动标定。
    *   **复杂环境适应性：** 利用深度学习技术提升算法在低光照、阴影、部分遮挡等复杂环境下的表现。
2.  **降低系统资源消耗：**
    *   **模型量化与裁剪：** 对 MediaPipe Face Landmarker 模型进行更深度的量化或结构优化，减少计算和内存占用。
    *   **动态资源分配：** 根据设备负载和用户行为，动态调整摄像头帧率、MediaPipe 推理频率等参数。
    *   **高效的跨平台实现：** 持续优化安卓和 iOS 客户端的代码，减少不必要的计算和内存拷贝。
3.  **增强用户个性化配置选项：**
    *   允许用户根据自己的舒适度和习惯调整视线判断的灵敏度、注视阈值角度等参数。
    *   支持针对特定用户的模型微调或参数学习。
4.  **提升复杂环境下的适应性：**
    *   研究并实现算法对头部姿态大幅度变化、用户靠近/远离设备的鲁棒性。
    *   考虑增加对多个人脸的处理，区分主要用户和背景中的其他人。

## 7. 结论

多设备协同视线交互系统项目基于 MediaPipe、MQTT 和跨平台技术，为行动受限患者提供了一种潜在的新的交互方式。通过安卓和 iOS 客户端的本地感知与中央协调器的全局决策相结合，系统能够有效地将用户的视线意图转化为明确的选择判断。尽管在 MediaPipe 在特定设备上的兼容性、本地网络稳定性、多设备决策鲁棒性等方面存在技术挑战，但已规划了相应的解决方案，为项目的进一步开发和优化奠定了基础。未来的扩展方向和改进建议为系统的功能增强、用户体验提升和更广泛应用描绘了蓝图。