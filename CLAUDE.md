# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个为失能患者开发的多设备协同视线交互系统，通过检测用户的视线方向，让患者能够通过眼神进行简单的"是/否"选择。系统采用分布式架构，多设备协同工作，提高交互的可靠性和准确性。

**技术栈**: Kotlin + MediaPipe + MQTT + Python
**目标设备**: 华为手机（无GMS）、iPhone、协调器

## 常用开发命令

### 构建和部署

#### Android应用构建
```bash
# 进入Android项目目录
cd android_mvp

# 下载MediaPipe模型文件
curl -L -o "app/src/main/assets/face_landmarker_v2_with_blendshapes.task" \
  https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker_v2_with_blendshapes/float16/1/face_landmarker_v2_with_blendshapes.task

# 使用Android Studio构建（推荐）
# 或者使用命令行:
./gradlew assembleDebug

# 生成的APK路径
# app/build/outputs/apk/debug/app-debug.apk
```

#### 中央协调器启动
```bash
# 进入协调器目录
cd coordinator_app

# 安装Python依赖
pip install -r requirements.txt

# 启动协调器（默认连接本机1883端口）
python simple_coordinator.py

# 指定MQTT Broker地址
python simple_coordinator.py 192.168.1.100 1883
```

#### 快速构建
```bash
# 使用项目提供的快速构建脚本
./quick_build.bat
```

### 调试和监控

#### Android应用日志
```bash
# 过滤应用相关日志
adb logcat | grep -E "(GazeInteraction|MediaPipe|MQTT)"
```

#### 协调器日志
```bash
# 协调器输出到文件
python simple_coordinator.py 2>&1 | tee coordinator.log
```

## 系统架构

### 分布式设计
```
📱 华为手机A (是) ──┐
📱 华为手机B (否) ──┼─→ 🌐 MQTT Broker ──→ 💻 中央协调器 ──→ ✅ 最终决策
📱 iPhone (是)     ──┘
```

### 核心组件

#### Android应用 (`android_mvp/`)
- **MainActivity.kt**: 主界面，协调所有组件
- **CameraManager.kt**: Camera2 API管理（华为设备兼容）
- **FaceLandmarkerHelper.kt**: MediaPipe封装，使用Face Landmarker V2
- **GazeDetectionAlgorithm.kt**: 视线检测算法
- **MqttClient.kt**: MQTT通信客户端

#### 中央协调器 (`coordinator_app/`)
- **simple_coordinator.py**: 主协调器，接收多设备数据并做出决策
- **requirements.txt**: Python依赖（paho-mqtt等）

### 数据流设计
1. **视觉处理**: 摄像头 → Camera2 API → MediaPipe → 视线检测算法
2. **状态发布**: 本地结果 → MQTT → 中央协调器
3. **决策制定**: 多设备输入 → 置信度分析 → 最终选择

## 技术栈详情

### Android应用
- **语言**: Kotlin
- **Android版本**: API 28+ (Android 9.0+)
- **摄像头**: Camera2 API（兼容华为无GMS设备）
- **视觉处理**: MediaPipe Tasks Vision (v0.10.8)
- **推理引擎**: CPU Delegate（确保华为设备稳定性）
- **通信**: Eclipse Paho MQTT (v1.2.5)

### 中央协调器
- **语言**: Python 3.7+
- **MQTT**: paho-mqtt (v1.6.1)
- **跨平台**: Windows/macOS/Linux

## 开发注意事项

### 华为设备特殊配置
- 使用Camera2 API替代CameraX避免兼容性问题
- 强制使用CPU Delegate确保稳定性
- 分辨率640x480，帧率自适应设备支持范围（目标30fps）
- 使用RenderScript直接YUV->ARGB转换，避免JPEG编解码开销
- 关闭未使用的Blendshapes和变换矩阵输出以节省CPU
- OIS仅在设备支持时启用
- 严格的内存管理避免泄漏（使用applicationContext）

### 网络配置
- 所有设备需在同一局域网
- MQTT Broker默认端口1883
- 主题设计：
  - `gazecontrol/device/{deviceId}/status` (设备状态)
  - `gazecontrol/device/{deviceId}/gaze_status` (视线状态)
  - `gazecontrol/coordination/decision` (协调决策)

### 算法参数
```kotlin
// GazeDetectionAlgorithm.kt 关键参数
private const val CONFIDENCE_THRESHOLD = 0.55f   // 最低置信度阈值
private const val PUPIL_POSITION_TO_ANGLE_SCALE = 35.0  // 瞳孔比例->角度缩放
private const val HISTORY_SIZE = 8                // 时间平滑窗口大小
private val requiredConsecutiveFrames = 2         // 连续确认帧数

// 虹膜关键点索引
private const val LEFT_PUPIL = 468
private const val RIGHT_PUPIL = 473

// 校准缓冲带
val margin = span * 0.15  // 两基准间 15% 缓冲带
```

## 故障排除

### 常见问题
1. **摄像头初始化失败**: 检查权限和Camera2 API支持
2. **MediaPipe初始化失败**: 确认模型文件在assets目录
3. **MQTT连接失败**: 检查网络连接和Broker配置
4. **视线检测不准确**: 调整用户距离(40-60cm)和光照条件

### 性能监控
```bash
# 监控Android应用性能
adb shell top | grep com.gazeinteraction

# 监控网络流量
adb shell nethogs
```

## 关键文件说明

### Android核心文件
- **MainActivity.kt**: 应用主入口，管理组件生命周期
- **GazeDetectionAlgorithm.kt**: 核心视线检测逻辑
- **MqttClient.kt**: 网络通信封装，支持自动重连
- **FaceLandmarkerHelper.kt**: MediaPipe集成，处理人脸关键点

### 协调器文件
- **simple_coordinator.py**: 多设备数据融合和决策逻辑
- **test_coordinator.py**: 协调器功能测试脚本

## 扩展开发建议

### 短期优化
- 针对具体华为设备型号优化性能
- 基于测试反馈调整UI设计
- 优化视线检测算法参数
- 开发iOS版本应用

### 中期扩展
- 扩展多选项支持（3-5个选项）
- 添加语音播报功能
- 集成历史数据分析
- 支持云端协调器部署

**免责声明**: 本项目为研究和概念验证目的开发，请在实际医疗环境中使用前进行充分测试和验证。