# Floweye - 失能患者视线交互系统

为失能患者开发的多设备协同视线交互系统。通过检测用户的视线方向，让患者能够通过眼神进行简单的"是/否"选择。

## 系统架构

```
[华为手机A (是)] --+
[华为手机B (否)] --+--> [MQTT Broker] --> [中央协调器] --> [最终决策]
[iPhone (是)]    --+
```

## 技术栈

| 组件 | 技术 |
|------|------|
| Android 应用 | Kotlin + Camera2 API + MediaPipe Tasks Vision |
| 视线检测 | 虹膜关键点(468-477) + 瞳孔位置比例 + 头部姿态融合 |
| 通信协议 | MQTT (Eclipse Paho) |
| 中央协调器 | Python 3.7+ (paho-mqtt) |
| 目标设备 | 华为手机(无GMS)、iPhone |

## 快速开始

### 1. 部署 MQTT Broker

```bash
# macOS
brew install mosquitto && brew services start mosquitto

# Linux
sudo apt install mosquitto && sudo systemctl start mosquitto

# Windows: 下载安装 https://mosquitto.org/download/
```

### 2. 启动中央协调器

```bash
cd coordinator_app
pip install -r requirements.txt
python simple_coordinator.py <broker_ip> 1883
```

### 3. 构建 Android 应用

1. 下载 MediaPipe 模型文件到 `android_mvp/app/src/main/assets/`:
   - 模型: `face_landmarker_v2_with_blendshapes.task`
   - 下载地址: https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker_v2_with_blendshapes/float16/1/face_landmarker_v2_with_blendshapes.task

2. 使用 Android Studio 打开 `android_mvp/` 目录，构建并安装到设备

### 4. 使用

1. 启动 MQTT Broker
2. 启动中央协调器
3. 在手机上打开应用，授予摄像头权限
4. 单击右上角按钮启动校准（注视"是"3秒 -> 注视"否"3秒）
5. 校准完成后即可使用视线进行交互

## 项目结构

```
floweye3/
  android_mvp/          # Android 应用
    app/src/main/java/com/gazeinteraction/
      MainActivity.kt           # 主界面
      camera/CameraManager.kt   # Camera2 封装（华为设备适配）
      gaze/GazeDetectionAlgorithm.kt  # 视线检测算法（虹膜关键点）
      mediapipe/FaceLandmarkerHelper.kt  # MediaPipe 封装
      mqtt/MqttClient.kt        # MQTT 客户端
  coordinator_app/      # Python 中央协调器
    simple_coordinator.py       # 多设备数据融合和决策
  docs/
    archive/            # 历史文档（技术调研、开发计划等）
```

## 关键参数

```kotlin
// GazeDetectionAlgorithm.kt
CONFIDENCE_THRESHOLD = 0.55f   // 最低置信度
HISTORY_SIZE = 8               // 时间平滑窗口
requiredConsecutiveFrames = 2  // 连续确认帧数

// CameraManager.kt
TARGET_WIDTH = 640, TARGET_HEIGHT = 480  // 摄像头分辨率
TARGET_FPS = 30                           // 目标帧率
```

## 硬件要求

- **Android**: 9.0+ (API 28+), 前置摄像头
- **网络**: 所有设备在同一局域网
- **使用距离**: 40-60cm
- **光照**: 避免逆光/强光直射

## 文档

- [CLAUDE.md](CLAUDE.md) - 项目技术文档（架构、命令、配置）
- [docs/archive/](docs/archive/) - 历史文档（调研报告、开发计划、部署指南等）

## 免责声明

本项目为研究和概念验证目的开发，请在实际医疗环境中使用前进行充分测试和验证。
