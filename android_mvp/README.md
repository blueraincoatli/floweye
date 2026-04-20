# 失能患者视线检测交互应用 - Android MVP

## 项目简介
这是一个为失能患者设计的视线检测交互应用，通过摄像头检测用户是否正在注视设备屏幕，帮助患者通过眼神与外界进行简单的"是/否"交互。

## 技术栈
- **语言**: Kotlin
- **摄像头**: Camera2 API（华为无GMS设备兼容）
- **视觉处理**: MediaPipe Face Landmarker V5
- **通信**: MQTT协议
- **UI**: 原生Android视图

## 设备要求
- Android 9.0 (API 28) 及以上
- 前置摄像头
- 网络连接（用于MQTT通信）

## 特别适配
- 针对华为无GMS设备进行了专门优化
- 使用MediaPipe Tasks Vision Android版本（无GMS依赖）
- 强制使用CPU Delegate确保稳定性
- Camera2 API替代CameraX避免兼容性问题

## 项目结构
```
android_mvp/
├── app/
│   ├── src/main/
│   │   ├── java/com/gazeinteraction/
│   │   │   ├── MainActivity.kt
│   │   │   ├── camera/
│   │   │   │   └── CameraManager.kt
│   │   │   ├── mediapipe/
│   │   │   │   └── FaceLandmarkerHelper.kt
│   │   │   ├── gaze/
│   │   │   │   └── GazeDetectionAlgorithm.kt
│   │   │   ├── mqtt/
│   │   │   │   └── MqttClient.kt
│   │   │   └── ui/
│   │   │       └── GazeInteractionView.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## 开发进度
- [x] 项目架构设计
- [ ] 基础Android项目创建
- [ ] MediaPipe集成
- [ ] Camera2管理实现
- [ ] 视线检测算法
- [ ] UI界面
- [ ] MQTT通信
- [ ] 华为设备测试
