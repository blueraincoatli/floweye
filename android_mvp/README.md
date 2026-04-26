# FlowEye Android MVP

## 当前状态

Android MVP 已实现主要功能，不再处于“项目初始化”阶段。

当前已具备：

- Camera2 采集
- MediaPipe Face Landmarker 集成
- 视线检测算法
- MQTT 通信
- 校准流程
- Debug APK 构建

当前主要待验证：

- 华为真机联调
- 与 `scanning_coordinator.py` 的真实消息对接

## 技术栈

- Kotlin
- Camera2 API
- MediaPipe Tasks Vision
- MQTT
- 原生 Android View

## 项目结构

```text
android_mvp/
├── app/
│   ├── src/main/
│   │   ├── java/com/gazeinteraction/
│   │   │   ├── MainActivity.kt
│   │   │   ├── camera/CameraManager.kt
│   │   │   ├── mediapipe/FaceLandmarkerHelper.kt
│   │   │   ├── gaze/GazeDetectionAlgorithm.kt
│   │   │   └── mqtt/MqttClient.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## 构建

```bash
cd android_mvp
./gradlew assembleDebug
```

输出：

`app/build/outputs/apk/debug/app-debug.apk`

## 说明

- 当前 Android README 只描述 Android 客户端本身
- 整体项目状态以根目录 [README.md](D:\floweye3\README.md) 和 [project_status.md](D:\floweye3\docs\project_status.md) 为准
