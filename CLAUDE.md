# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Floweye - 失能患者视线交互通信系统。通过检测患者视线方向，实现"是/否"选择和多层菜单浏览，帮助失能患者表达需求（疼痛、护理、吃喝、环境、社交、紧急呼叫等）。

**技术栈**: Kotlin + MediaPipe + MQTT + Python
**目标设备**: 华为手机（无GMS）、iPhone、PC/Mac（协调器）

### 交互模式
- **单设备模式 (SINGLE_SWITCH)**: 一台设备，视线唤醒 + 扫描选择，适合认知能力较低的患者
- **双设备模式 (DUAL_SWITCH)**: 两台设备分别显示"是"/"否"，视线确认选择，适合认知能力较高的患者

## 常用开发命令

### Android 应用构建
```bash
cd android_mvp

# 下载 MediaPipe 模型（首次）
curl -L -o "app/src/main/assets/face_landmarker_v2_with_blendshapes.task" \
  https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker_v2_with_blendshapes/float16/1/face_landmarker_v2_with_blendshapes.task

# 构建
./gradlew assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk
```

### 协调器启动
```bash
cd coordinator_app
pip install -r requirements.txt

# 基线协调器（简单是/否融合）
python simple_coordinator.py [broker_host] [broker_port]

# 扫描协调器（菜单浏览 + TTS + 自适应）
python scanning_coordinator.py --menu menu_config.json --patient patient_config.json [broker_host] [broker_port]

# 模拟联调（无需真机）
python simulate_scanning_flow.py
```

### 调试
```bash
# Android 日志
adb logcat | grep -E "(GazeInteraction|MediaPipe|MQTT)"

# 协调器测试
python test_coordinator.py
python test_scanning_coordinator.py
```

## 系统架构

```
Android 设备 (是/否) ──MQTT──> Broker <──MQTT──> 扫描协调器 (PC)
                                                 |
                                                 +-- 菜单引擎 (2层JSON配置)
                                                 +-- TTS 语音播报
                                                 +-- 自适应停留时间
                                                 +-- 紧急触发逻辑
```

### MQTT 主题
- `gazecontrol/device/{deviceId}/status` - 设备上下线
- `gazecontrol/device/{deviceId}/gaze_status` - 视线状态 (looking/at_screen + confidence)
- `gazecontrol/coordination/decision` - 协调器决策结果
- `gazecontrol/device/{deviceId}/command` - 协调器向设备下发指令

### 数据流
1. 摄像头 -> Camera2 API -> MediaPipe Face Landmarker V2 -> 视线检测算法
2. 视线结果 -> MQTT -> 扫描协调器
3. 协调器: 菜单引擎 + 状态机 (IDLE/SCAN/CONFIRM/ALERT/WAITING) -> 决策
4. 决策 -> MQTT -> 设备UI更新 + TTS播报

## 核心文件

### Android (`android_mvp/app/src/main/java/com/gazeinteraction/`)
| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 主界面，多状态UI引擎 (IDLE/TRANSITION/SCAN/CONFIRM/FEEDBACK)，进度条，呼吸动画 |
| `camera/CameraManager.kt` | Camera2 API，华为兼容，RenderScript YUV->ARGB |
| `mediapipe/FaceLandmarkerHelper.kt` | MediaPipe Face Landmarker V2 封装，CPU Delegate |
| `gaze/GazeDetectionAlgorithm.kt` | 视线检测：瞳孔居中 + 眼睛睁开度 + 时间平滑 + 校准 |
| `mqtt/MqttClient.kt` | MQTT 通信，自动重连，状态发布 |
| `debug/FaceMeshOverlayView.kt` | 调试用面部网格叠加层 |

### 协调器 (`coordinator_app/`)
| 文件 | 职责 |
|------|------|
| `scanning_coordinator.py` | 扫描协调器主程序：状态机、菜单引擎、TTS、自适应、紧急触发 |
| `simple_coordinator.py` | 基线协调器：简单多设备是/否融合 |
| `menu_config.json` | 菜单配置：6大类（不舒服/护理/吃喝/环境/社交/紧急），2层结构 |
| `patient_config.json` | 患者配置：停留时间、TTS参数、自适应参数、交互模式 |
| `simulate_scanning_flow.py` | 模拟脚本：验证 single_select/dual_confirm/dual_skip/dual_emergency |
| `test_scanning_coordinator.py` | 自动化测试（当前 8 passed） |
| `gen.py` | 配置文件生成工具 |

### Android UI 资源
- `activity_main.xml` - 主布局（单按钮占满中央、进度条、状态栏）
- `layout-land/` - 横屏适配
- `debug_panel.xml` - 调试面板
- `drawable/btn_*` - 按钮状态背景（注视/普通，是/否）
- `drawable/progress_bar_gaze.xml` - 注视进度条样式

## 关键算法参数

### 视线检测 (`GazeDetectionAlgorithm.kt`)
```kotlin
CONFIDENCE_THRESHOLD = 0.55f    // 最低置信度
PUPIL_CENTER_THRESHOLD = 0.08   // 瞳孔居中判定
EYE_OPEN_MIN = 0.15             // 眼睛最低睁开度
HISTORY_SIZE = 8                // 时间平滑窗口
LEFT_PUPIL = 468                // 左虹膜中心关键点
RIGHT_PUPIL = 473               // 右虹膜中心关键点
```

### 协调器状态机 (`scanning_coordinator.py`)
```
IDLE -> SCAN -> CONFIRM -> ALERT (或回到 SCAN/IDLE)
                 |
                 +-> WAITING (双设备等待第二确认)
```

### 患者配置参数 (`patient_config.json`)
- 单设备: wake_gaze=3s, select_gaze=1.5s, dwell=3-7s
- 双设备: select_gaze=1.5s, skip_gaze=0.5s, hesitation=0.3s
- TTS: rate=150, 夜间自动降音量(22:00-06:00)
- 自适应: 根据近5次选择历史调整停留时间(步长0.5s)

## 开发注意事项

### 华为设备兼容
- Camera2 API 替代 CameraX
- CPU Delegate（不用 GPU/NPU）
- 分辨率 640x480，目标 30fps
- RenderScript YUV->ARGB 直接转换
- 关闭 Blendshapes 和变换矩阵输出
- 使用 applicationContext 防内存泄漏

### MQTT 通信
- 所有设备同一局域网，Broker 默认端口 1883
- Android 端发布节流：最小间隔 500ms
- 协调器设备有效窗口 30s，离线超时 60s
- 决策防抖：连续 2 次一致确认才生效

### 代码规范
- Android 端不使用 GMS 服务
- 不在代码中使用 unicode 编码的 emoji 字符
- 协调器配置通过 JSON 文件管理，不硬编码
- TTS 异步解耦，不阻塞状态机

## 当前进度

详细进度见 `todo.md`。核心状态：
- **已完成**: Android MVP、视线检测、MQTT通信、双协调器、模拟联调、自动化测试、Android UI 重设计（光圈引导系统，详见 `docs/superpowers/specs/2026-05-06-floweye-ui-redesign-design.md`）、真机联调（华为 NOH-AN01）
- **进行中**: （无）
- **待开始**: iOS 版本、3+设备直接选择、护理端 Web 界面、TLS、临床测试

**免责声明**: 本项目为研究和概念验证目的开发，请在实际医疗环境中使用前进行充分测试和验证。
