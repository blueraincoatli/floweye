# 最优技术方案建议：多设备协同视线交互系统MVP

**作者**: MiniMax Agent
**日期**: 2025-06-23

## 1. 核心目标

本方案旨在为“多设备协同视线交互系统”的最小可行产品（MVP）提供一套最优的技术实现路径。方案的核心是**在确保功能稳定可靠的前提下，以最高效的方式完成开发**，并为后续的迭代和扩展打下坚实的基础。此方案是基于对现有规划和最新技术发展的全面研究所得出的结论。

## 2. 推荐技术栈与架构

**结论：完全采纳并执行现有技术文档中最终确定的技术栈与架构。** 该架构在性能、稳定性、开发效率和未来扩展性之间取得了最佳平衡。

### 2.1 客户端 (Android / iOS 感知节点)

*   **开发语言**: **Kotlin (Android)** / **Swift (iOS)**。
*   **核心技术**: **原生开发**。
    *   **理由**: 放弃跨平台方案是完全正确的。原生开发是唯一能够满足本项目对摄像头精细控制和高频、低延迟视觉处理性能要求的途径。

### 2.2 核心算法库

*   **库**: **MediaPipe Tasks Vision (Android / iOS)**。
*   **模型**: `face_landmarker_v2_with_blendshapes.task`。
    *   **理由**: MediaPipe是当前最成熟的、端到端的移动端视觉处理框架，提供了本项目算法所需的全部核心数据（关键点、变换矩阵、Blendshapes），并且经过了大规模的商业化验证，鲁棒性强。

### 2.3 协调器 (中央决策节点)

*   **框架**: **Flutter**。
    *   **理由**: 协调器对实时性能要求不高，UI和业务逻辑相对简单。Flutter能实现一次开发、多端（Windows, macOS, Linux, Mobile）部署，极大提升了协调器应用的开发和分发效率。

### 2.4 通信协议

*   **协议**: **MQTT**。
    *   **理由**: MQTT轻量、稳定，其发布/订阅模式与本项目的“多对一”通信需求完美契合，且在本地网络中延迟足够低，实现简单，是最高效、最可靠的选择。

## 3. 针对华为无GMS设备的最优实现路径

对于在无GMS的华为设备上运行的安卓客户端，必须遵循以下经过验证的最佳实践，以确保应用的稳定性和性能。

*   **依赖库**: 明确使用`com.google.mediapipe:tasks-vision-android`依赖，此版本不包含对GMS的硬性依赖。

*   **摄像头管理**: **必须使用原生Camera2 API**。
    *   **放弃CameraX**: CameraX在无GMS设备上存在已知的兼容性问题。
    *   **核心操作**: 利用Camera2 API实现以下关键功能：
        1.  **精确的预览尺寸和帧率配置**: 根据设备能力选择480p或720p分辨率，目标20-30fps。
        2.  **曝光锁定 (AE Lock)**: 在自动曝光稳定后锁定曝光，避免光线变化影响检测。
        3.  **对焦锁定 (AF Lock)**: 在对焦稳定后锁定焦点，确保人脸和虹膜的清晰度。

*   **模型推理**: **必须强制使用TFLite CPU Delegate**。
    *   **放弃GPU/NNAPI**: 在初始化`FaceLandmarker`时，通过`BaseOptions.Builder().setDelegate(Delegate.CPU)`明确指定使用CPU进行推理。
    *   **理由**: 这是在华为设备上获得**稳定、可预测性能**的最可靠方法。GPU和NNAPI的兼容性问题会导致应用在部分设备上运行缓慢甚至崩溃。

## 4. MVP核心算法实现建议

建议完全遵循`IrisDetectionAndGazeDirectionAlgorithmDesign.md`和`android gaze tracking prototype technical specification.md`文档中最终确定的算法路径。

1.  **输入**: 从`FaceLandmarkerResult`中获取`facialTransformationMatrixes`和`faceBlendshapes`。
2.  **头部姿态**: 从`facialTransformationMatrixes`中解析出3x3旋转矩阵$R_{head}$。
3.  **眼球旋转**: **主要依赖`eyeLook` Blendshape系数**，将其线性映射为眼球的水平和垂直旋转角度，并构建相对旋转矩阵$R_{eye\_rel}$。
4.  **视线向量**: 融合计算出最终的相机空间视线向量$V_{gaze\_camera} = R_{head} \times (R_{eye\_rel} \times V_{eye\_neutral\_face})$。
5.  **注视判断**: 计算$V_{gaze\_camera}$与指向屏幕目标区域（如“是”/“否”按钮中心）的向量之间的夹角，若小于阈值则判断为注视。
6.  **置信度**: 综合MediaPipe原始置信度、关键点稳定性、帧间一致性等因素，生成一个0到1的`gazeConfidence`值，用于过滤不可靠的判断。

## 5. 总结

本技术方案建议的核心思想是：**信任并执行现有经过充分研究和规划的设计**。调研结果表明，当前的技术选型和架构设计是健壮、先进且高度可行的。MVP阶段应避免不必要的技术探索，聚焦于高质量地完成上述方案的工程实现，这将是项目成功的关键。
