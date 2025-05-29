# FlowEye MVP - 华为设备视线检测

## 📱 项目概述

这是一个专为华为手机（无GMS环境）设计的视线检测MVP应用，能够检测用户是否看向屏幕上的"是"/"否"区域。

## 🎯 功能特性

- ✅ 基于MediaPipe Face Landmarker的人脸检测
- ✅ 实时视线方向判断
- ✅ 华为无GMS环境兼容
- ✅ 简洁的用户界面
- ✅ 实时视觉反馈

## 🛠 技术栈

- **开发语言**: Kotlin
- **架构**: MVVM + Repository
- **人脸检测**: MediaPipe Tasks Vision
- **摄像头**: CameraX API
- **最低SDK**: Android 7.0 (API 24)
- **目标SDK**: Android 14 (API 34)

## 📋 前置要求

### 开发环境
- Android Studio Hedgehog | 2023.1.1 或更高版本
- Kotlin 1.9+
- Gradle 8.0+

### 设备要求
- 华为手机（支持无GMS环境）
- 前置摄像头
- Android 7.0+
- 至少2GB RAM

## 🚀 快速开始

### 1. 克隆项目
```bash
git clone <repository-url>
cd floweye/android_mvp
```

### 2. 自动构建和测试
```bash
./build_and_test.sh
```

这个脚本会自动：
- 下载MediaPipe模型文件
- 构建项目
- 安装到连接的设备
- 提供测试指南

### 3. 手动构建（可选）
```bash
# 下载模型文件
./download_model.sh

# 构建项目
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 📱 使用说明

### 启动应用
1. 打开FlowEye MVP应用
2. 授予摄像头权限
3. 将手机正对面部，距离30-50cm

### 视线检测
- **按钮变暗**: 检测到看向另一个选项
- **按钮正常**: 检测到看向该选项或未检测到视线
- **状态文本**: 显示当前视线目标和置信度
- **调试信息**: 显示角度和技术参数

## 🔧 配置参数

### 视线检测参数
在 `GazeDetector.kt` 中可调整：

```kotlin
private const val GAZE_THRESHOLD_ANGLE = 25.0f // 视线判断阈值角度
private const val MIN_CONFIDENCE_THRESHOLD = 0.5f // 最小置信度
private const val MAX_HORIZONTAL_EYE_ANGLE = 20.0f // 眼球水平转动范围
private const val MAX_VERTICAL_EYE_ANGLE = 15.0f // 眼球垂直转动范围
```

### 摄像头参数
在 `MainActivity.kt` 中可调整：

```kotlin
// MediaPipe检测参数
.setMinFaceDetectionConfidence(0.5f)
.setMinFacePresenceConfidence(0.5f)
.setMinTrackingConfidence(0.5f)
```

## 🐛 故障排除

### 常见问题

#### 1. MediaPipe初始化失败
**症状**: 应用启动时显示"MediaPipe初始化失败"
**解决方案**:
- 确认 `face_landmarker.task` 文件在assets目录
- 检查文件大小是否正确（约100MB）
- 确认设备有足够存储空间

#### 2. 摄像头无法启动
**症状**: 黑屏或权限错误
**解决方案**:
- 检查摄像头权限是否授予
- 确认前置摄像头可用
- 重启应用或设备

#### 3. 检测不稳定
**症状**: 频繁显示"检测不稳定"
**解决方案**:
- 改善光照条件
- 调整手机距离（30-50cm）
- 确保面部完全在摄像头视野内

#### 4. 华为设备兼容性问题
**症状**: 应用崩溃或功能异常
**解决方案**:
- 确认使用CPU Delegate
- 检查是否有GMS相关依赖
- 查看logcat错误信息

### 调试命令
```bash
# 查看应用日志
adb logcat | grep FloweEyeMVP

# 查看设备信息
adb shell getprop ro.product.model
adb shell getprop ro.product.brand

# 检查应用权限
adb shell dumpsys package com.floweye.mvp | grep permission
```

## 📊 性能指标

### 目标性能
- **帧率**: 15-25 FPS
- **延迟**: <500ms
- **准确率**: >80%
- **CPU使用**: <30%

### 测试设备
- Huawei P30 Pro
- Huawei Mate 40
- Honor 20 Pro

## 🔄 开发路线图

### 阶段1: 基础功能 ✅
- [x] MediaPipe集成
- [x] 基础视线检测
- [x] UI界面
- [x] 华为设备兼容性

### 阶段2: 优化改进 🚧
- [ ] 算法精度提升
- [ ] 性能优化
- [ ] 错误处理完善
- [ ] 用户体验改进

### 阶段3: 高级功能 📋
- [ ] 多人脸支持
- [ ] 自适应校准
- [ ] 数据分析
- [ ] MQTT通信集成

## 📁 项目结构

```
android_mvp/
├── app/
│   ├── src/main/
│   │   ├── java/com/floweye/mvp/
│   │   │   ├── MainActivity.kt          # 主活动
│   │   │   └── GazeDetector.kt         # 视线检测算法
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml   # 主界面布局
│   │   │   └── values/
│   │   │       └── strings.xml         # 字符串资源
│   │   ├── assets/
│   │   │   └── face_landmarker.task    # MediaPipe模型
│   │   └── AndroidManifest.xml         # 应用清单
│   └── build.gradle.kts                # 应用构建配置
├── build.gradle.kts                    # 项目构建配置
├── download_model.sh                   # 模型下载脚本
├── build_and_test.sh                   # 构建测试脚本
└── README.md                           # 项目文档
```

## 🤝 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

## 📄 许可证

MIT License - 详见LICENSE文件

## 📞 支持

如有问题或建议，请：
1. 查看故障排除部分
2. 搜索已知问题
3. 创建新的Issue

---

**注意**: 这是一个MVP版本，主要用于验证技术可行性。生产环境使用前需要进一步优化和测试。