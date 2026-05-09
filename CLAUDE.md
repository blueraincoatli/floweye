# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Floweye - 失能患者视线交互通信系统。通过检测患者视线方向，实现"是/否"选择和多层菜单浏览，帮助失能患者表达需求（疼痛、护理、吃喝、环境、社交、紧急呼叫等）。

**技术栈**: Kotlin + MediaPipe + MQTT + Python
**目标设备**: 华为手机（无GMS）、iPhone、PC/Mac（协调器）

### 交互模式
- **单设备模式 (SINGLE_SWITCH)**: 一台设备，视线唤醒 + 扫描选择，适合认知能力较低的患者
- **双设备模式 (DUAL_SWITCH)**: 两台设备分别显示"是"/"否"，视线确认选择，适合认知能力较高的患者

### 运行模式
- **自托管模式 (Self-Hosted)**: 一台 Android 设备作为 HOST，内嵌 MQTT Broker + 协调引擎（CoordinatorEngine），另一台作为 CLIENT 连接。两台设备即可独立运行，无需 PC
- **PC 协调器模式**: Python `scanning_coordinator.py` 在 PC 上运行，Android 设备通过 MQTT 连接到 PC 上的 Broker

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
# Android 日志（华为设备 logcat 可能过滤自定义 TAG，建议用 run-as 读诊断文件）
adb logcat | grep -E "(GazeInteraction|MediaPipe|MQTT)"

# 读自托管模式诊断文件
adb shell "run-as com.gazeinteraction cat files/gaze_debug.txt"

# 协调器测试
python test_coordinator.py
python test_scanning_coordinator.py
```

## 系统架构

### 自托管模式（Android 独立运行）
```
Android HOST (是) ──内嵌 Broker──┐
                                 ├── CoordinatorEngine (Java/Kotlin)
Android CLIENT (否) ──MQTT──────┘
                                 |
                                 +-- MenuEngine (2层JSON配置)
                                 +-- AndroidTTSManager (TTS + 重复播报)
                                 +-- GazeInterpreter (视线参数可配置)
                                 +-- ServerChanNotifier ──HTTPS──> Server酱 ──> 护理者微信
                                 +-- TelegramNotifier ──HTTPS──> Telegram ──> 护理者 Bot
```

### PC 协调器模式
```
Android 设备 (是/否) ──MQTT──> Broker <──MQTT──> 扫描协调器 (PC)
                                                 |
                                                 +-- 菜单引擎 (2层JSON配置)
                                                 +-- TTS 语音播报
                                                 +-- 自适应停留时间
                                                 +-- 紧急触发逻辑
                                                 +-- notifier.py ──HTTPS──> Server酱/Telegram ──> 护理者手机
```

### MQTT 主题
- `gazecontrol/device/{deviceId}/status` - 设备上下线
- `gazecontrol/device/{deviceId}/gaze_status` - 视线状态 (looking/at_screen + confidence + gazeDurationMs)
- `gazecontrol/coordination/decision` - 协调器决策结果
- `gazecontrol/coordination/role_sync` - 角色同步（强制主机模式切换）
- `gazecontrol/device/{deviceId}/command` - 协调器向设备下发指令

### 数据流
1. 摄像头 -> Camera2 API -> MediaPipe Face Landmarker V2 -> 视线检测算法
2. 视线结果 -> MQTT（CLIENT 发布，HOST 订阅远程注视）
3. 协调器: 菜单引擎 + 状态机 (IDLE/SCAN/CONFIRM/ALERT/WAITING) -> 决策
4. 决策 -> MQTT -> 设备UI更新 + TTS播报
5. 决策结果 -> CompositeNotifier -> HTTPS -> Server酱（微信）/ Telegram Bot -> 护理者手机
6. 消息分级：紧急(critical)→红色标题、重要(high)→黄色标题、一般(normal)→普通消息
7. 通知通道可在设置面板切换：Server酱 / Telegram / 两者同时

### 双设备视线处理
- HOST 处理自己的本地注视（role=yes）和 CLIENT 的远程注视（role=no）
- announce（TTS播报）阶段阻止所有注视动作
- announce→select 过渡时，主动清除远程注视 latch（`pendingRemoteLatchClear`），防止 TTS 快于 CLIENT 发布间隔导致后续注视永久阻塞
- 过渡时忽略 CLIENT 消息中携带的旧 `gazeDurationMs`，从当前时刻重新计时

## 核心文件

### Android (`android_mvp/app/src/main/java/com/gazeinteraction/`)
| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 主界面，多状态UI引擎 (IDLE/TRANSITION/SCAN/CONFIRM/FEEDBACK)，双设备协调，进度条，呼吸动画 |
| `camera/CameraManager.kt` | Camera2 API，华为兼容，RenderScript YUV->ARGB |
| `mediapipe/FaceLandmarkerHelper.kt` | MediaPipe Face Landmarker V2 封装，CPU Delegate |
| `gaze/GazeDetectionAlgorithm.kt` | 视线检测：瞳孔居中 + 眼睛睁开度 + 时间平滑 + 校准 |
| `mqtt/MqttClient.kt` | MQTT 通信，自动重连，状态发布，角色同步 |
| `debug/FaceMeshOverlayView.kt` | 调试用面部网格叠加层 |
| `ui/GazeHaloView.kt` | 光圈引导视图（两段式反馈：感知→引导） |
| `ui/ArcProgressView.kt` | 弧线进度条组件 |
| `ui/ThemeConfig.kt` | 三主题配色方案 |
| `coordinator/CoordinatorEngine.kt` | 自托管协调引擎：状态机、菜单导航、TTS重复播报 |
| `coordinator/GazeInterpreter.kt` | 视线动作判定：注视时长阈值（从 patient_config 读取） |
| `coordinator/HostManager.kt` | 设备角色自动检测（HOST/CLIENT） |
| `coordinator/BrokerService.kt` | 内嵌 MQTT Broker（Moquette） |
| `coordinator/MenuEngine.kt` | 2层菜单导航引擎 |
| `coordinator/AndroidTTSManager.kt` | TTS 管理器：队列、重复播报、播完回调 |
| `coordinator/ServerChanNotifier.kt` | Server酱 微信推送：分级通知（紧急/重要/一般） |
| `coordinator/TelegramNotifier.kt` | Telegram Bot 推送：国际用户通知通道 |
| `coordinator/CompositeNotifier.kt` | 多通道调度器：同时分发到多个通知通道 |
| `coordinator/CaregiverNotifier.kt` | 通知通道接口 |

### 协调器 (`coordinator_app/`)
| 文件 | 职责 |
|------|------|
| `scanning_coordinator.py` | 扫描协调器主程序：状态机、菜单引擎、TTS、自适应、紧急触发 |
| `simple_coordinator.py` | 基线协调器：简单多设备是/否融合 |
| `menu_config.json` | 菜单配置：6大类（不舒服/护理/吃喝/环境/社交/紧急），2层结构 |
| `patient_config.json` | 患者配置：注视阈值、停留时间、TTS参数、重复播报次数 |
| `simulate_scanning_flow.py` | 模拟脚本：验证 single_select/dual_confirm/dual_skip/dual_emergency |
| `test_scanning_coordinator.py` | 自动化测试（当前 8 passed） |
| `gen.py` | 配置文件生成工具 |
| `notifier.py` | 护理者通知模块：Server酱/Telegram 双通道 + 复合调度器 |
| `notify_config.json` | 通知配置文件（channel + Server酱 + Telegram 参数） |

### Android UI 资源
- `activity_main.xml` - 主布局（双按钮布局：是/否，进度条，状态栏）
- `dialog_settings.xml` - 设置面板（主题、角色、操作者模式、强制主机、播报次数滑块、Server酱 SendKey）
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

### 自托管协调器状态机 (`CoordinatorEngine.kt`)
```
IDLE -> SCAN -> CONFIRM -> ALERT (或回到 SCAN/IDLE)
                 |
                 +-> WAITING (执行后等待，3s后回到IDLE)
```

### 患者配置参数 (`patient_config.json`)
- 单设备: wake_gaze=2s, select_gaze=2s, emergency_gaze=3s, dwell=15s
- 双设备: select_gaze=2s, skip_gaze=1.5s, hesitation=0.5s, dwell=15s
- TTS: rate=130, repeat_count=2（可配置1-3，设置面板滑块）, 夜间自动降音量(22:00-06:00)
- 自适应: 根据近5次选择历史调整停留时间(步长0.5s)

### Android 注视阈值
- 进度环转动周期: `GAZE_SELECT_THRESHOLD_MS = 2000L`（与 select_gaze 保持一致）
- 两段式反馈: 感知阶段 500ms，引导阶段至 2000ms
- MQTT 发布节流: 最小间隔 500ms

## 开发注意事项

### 华为设备兼容
- Camera2 API 替代 CameraX
- CPU Delegate（不用 GPU/NPU）
- 分辨率 640x480，目标 30fps
- RenderScript YUV->ARGB 直接转换
- 关闭 Blendshapes 和变换矩阵输出
- 使用 applicationContext 防内存泄漏
- 华为 logcat 可能过滤自定义 TAG，调试时考虑用文件日志（`run-as` 读取）

### MQTT 通信
- 所有设备同一局域网，Broker 默认端口 1883
- Android 端发布节流：最小间隔 500ms
- 协调器设备有效窗口 30s，离线超时 60s
- 自托管模式：HOST 设备运行内嵌 Broker，CLIENT 自动发现 HOST 的 IP

### 双设备模式注意事项
- CLIENT 的"否"按钮在 CONFIRM 阶段通过 `confirm_ready` 消息显示
- 视线检测在双设备并排放置时可能出现同时检测，通过 latch 清除机制和时长阈值控制
- `skipSec` 下限 1.0s 防止视线检测噪声导致误触发

### 代码规范
- Android 端不使用 GMS 服务
- 不在代码中使用 unicode 编码的 emoji 字符
- 协调器配置通过 JSON 文件管理，不硬编码
- TTS 异步解耦，不阻塞状态机
- 配置文件同步维护两处：`coordinator_app/` 和 `android_mvp/app/src/main/assets/`

## 当前进度

详细进度见 `todo.md`。核心状态：
- **已完成**: Android MVP、视线检测、MQTT通信、双协调器、模拟联调、自动化测试、Android UI 重设计（光圈引导系统）、真机联调（华为 NOH-AN01）、自托管模式（CoordinatorEngine + 内嵌 Broker）、双设备视线 latch 修复、TTS 延迟重复播报（可配置1-3次，间隔3秒）、设置面板重构（含播报次数滑块）、患者参数调优（2s注视/1.5s跳过/15s停留）、**护理端微信推送（Server酱，三级分级通知）**
- **进行中**: （无）
- **待开始**: iOS 版本、3+设备直接选择、TLS、临床测试

**免责声明**: 本项目为研究和概念验证目的开发，请在实际医疗环境中使用前进行充分测试和验证。
