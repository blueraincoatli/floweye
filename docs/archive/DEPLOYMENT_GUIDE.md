# 失能患者视线检测交互应用 - 部署指南

> 历史归档说明（2026-04-26）：本文基于早期 MVP 假设编写，包含的启动方式和组件角色可能已过时。当前部署入口请以根目录 `README.md` 和 `coordinator_app/README.md` 为准。

**作者**: MiniMax Agent  
**创建时间**: 2025-06-23  
**版本**: MVP 1.0

## 项目概述

本项目为失能患者开发了一套多设备协同视线交互系统，通过检测患者的视线方向，让患者能够通过眼神与外界进行简单的"是/否"交互。

### 核心特性
- ✅ 基于MediaPipe Face Landmarker V5的高精度视线检测
- ✅ 专门针对华为无GMS设备优化
- ✅ 多设备协同工作，提高交互可靠性
- ✅ 实时MQTT通信，低延迟响应
- ✅ 简洁易用的界面设计

## 系统架构

```
[华为手机A - "是"] ──┐
[华为手机B - "否"] ──┼─→ [MQTT Broker] ──→ [中央协调器] ──→ [最终决策]
[iPhone - "是"]     ──┘
```

## 部署环境要求

### 移动设备
- **Android设备**: Android 9.0 (API 28) 及以上
- **华为设备**: 支持无GMS设备，推荐麒麟710+芯片
- **iOS设备**: iOS 13.0+ (可选)
- **摄像头**: 前置摄像头必须
- **网络**: WiFi连接

### 协调器设备
- **操作系统**: Windows 10+, macOS 10.15+, 或 Linux
- **Python**: 3.7+
- **网络**: 与移动设备在同一局域网

### 网络环境
- **MQTT Broker**: Mosquitto或其他MQTT服务器
- **局域网**: 所有设备需在同一网络

## 快速部署

### 第一步：设置MQTT Broker

#### 选项1：使用现有MQTT服务器
如果您已有MQTT服务器，请记录其IP地址和端口（通常是1883）。

#### 选项2：安装Mosquitto（推荐）

**Windows:**
```bash
# 下载并安装Mosquitto
# https://mosquitto.org/download/
# 启动服务
net start mosquitto
```

**macOS:**
```bash
brew install mosquitto
brew services start mosquitto
```

**Linux (Ubuntu):**
```bash
sudo apt update
sudo apt install mosquitto mosquitto-clients
sudo systemctl start mosquitto
sudo systemctl enable mosquitto
```

### 第二步：部署中央协调器

```bash
# 1. 进入协调器目录
cd coordinator_app

# 2. 安装Python依赖
pip install -r requirements.txt

# 3. 运行协调器（替换IP地址为您的MQTT Broker地址）
python simple_coordinator.py 192.168.1.100 1883
```

### 第三步：准备Android应用

#### 1. 获取MediaPipe模型文件
```bash
# 下载模型文件
wget https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker_v2_with_blendshapes/float16/1/face_landmarker_v2_with_blendshapes.task

# 将文件放置到Android项目的assets目录
cp face_landmarker_v2_with_blendshapes.task android_mvp/app/src/main/assets/
```

#### 2. 编译Android应用
```bash
cd android_mvp

# 使用Android Studio打开项目，或使用命令行编译
./gradlew assembleDebug

# 生成的APK位于：app/build/outputs/apk/debug/app-debug.apk
```

### 第四步：安装和配置移动应用

#### 1. 安装应用
将APK文件传输到华为手机并安装：
```bash
# 通过ADB安装
adb install app-debug.apk

# 或直接将APK文件复制到手机并手动安装
```

#### 2. 首次配置
1. 打开应用，授予摄像头权限
2. 点击设置按钮，配置MQTT Broker地址
3. 检查设备ID是否正确显示
4. 确认摄像头、MediaPipe和MQTT状态均为绿色

## 使用说明

### 基本操作流程

1. **启动系统**
   - 先启动MQTT Broker
   - 启动中央协调器
   - 在各移动设备上启动应用

2. **设备配置**
   - 设备A显示"是"
   - 设备B显示"否"
   - 可根据需要添加更多设备

3. **交互测试**
   - 患者面对摄像头，距离约50cm
   - 注视"是"或"否"按钮区域
   - 观察中央协调器的决策输出

### 状态指示器说明

#### Android应用状态
- 🟢 **绿色**: 组件正常工作
- 🟡 **黄色**: 正在初始化
- 🔴 **红色**: 出现错误

#### 中央协调器状态
- 🟢 **设备在线**: 5秒内有数据更新
- 🟡 **设备延迟**: 5-15秒内有数据更新
- 🔴 **设备离线**: 15秒以上无数据更新

### 校准和优化

#### 1. 用户位置校准
- 患者面部正对摄像头
- 距离保持在40-60cm
- 避免强光或背光环境

#### 2. 算法参数调整
可在代码中调整以下参数：
```kotlin
// GazeDetectionAlgorithm.kt
private const val CONFIDENCE_THRESHOLD = 0.6f  // 置信度阈值
private const val GAZE_ANGLE_THRESHOLD = 15.0   // 视线角度阈值
private const val SCREEN_DISTANCE_CM = 50.0     // 屏幕距离
```

## 故障排除

### 常见问题及解决方案

#### 1. 摄像头初始化失败
**问题**: 摄像头状态显示红色
**解决**: 
- 检查摄像头权限
- 确认设备支持Camera2 API
- 重启应用

#### 2. MediaPipe初始化失败
**问题**: MediaPipe状态显示红色
**解决**:
- 确认模型文件已正确放置在assets目录
- 检查设备CPU性能是否足够
- 尝试降低摄像头分辨率

#### 3. MQTT连接失败
**问题**: MQTT状态显示红色
**解决**:
- 检查网络连接
- 确认MQTT Broker地址和端口
- 检查防火墙设置

#### 4. 视线检测不准确
**问题**: 置信度低或误判
**解决**:
- 调整用户位置和距离
- 改善光照条件
- 降低置信度阈值进行测试
- 检查用户是否佩戴眼镜（可能影响检测）

### 性能优化

#### 华为设备优化建议
1. **降低分辨率**: 从720p改为480p
2. **减少帧率**: 从30fps改为20fps
3. **调整算法频率**: 每隔几帧处理一次

#### 网络优化
1. 使用5GHz WiFi频段
2. 确保MQTT Broker与设备距离较近
3. 考虑使用有线网络连接协调器

## 扩展开发

### 添加新功能
1. **多选项支持**: 扩展"是/否"为更多选项
2. **语音反馈**: 集成TTS播报决策结果
3. **历史记录**: 保存交互历史和统计数据
4. **个性化校准**: 为不同用户保存校准参数

### 适配更多设备
1. **iOS版本开发**: 基于相同算法开发iOS版本
2. **Web版本**: 使用WebRTC实现浏览器版本
3. **硬件按钮**: 集成物理按钮作为备选输入

## 技术支持

### 日志收集
Android应用日志：
```bash
adb logcat | grep -E "(GazeInteraction|MediaPipe|MQTT)"
```

协调器日志：
```bash
python simple_coordinator.py 2>&1 | tee coordinator.log
```

### 性能监控
```bash
# 监控设备性能
adb shell top | grep com.gazeinteraction

# 监控网络流量
adb shell nethogs
```

## 联系信息

如有技术问题或改进建议，请：
1. 查看项目文档和代码注释
2. 检查日志文件中的错误信息
3. 参考MediaPipe官方文档
4. 在项目仓库中提交Issue

---

**免责声明**: 本项目为研究和概念验证目的开发，请在实际医疗环境中使用前进行充分测试和验证。
