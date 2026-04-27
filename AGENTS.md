# AGENTS.md

## 项目概要

Floweye — 面向失能患者的多设备协同视线交互概念验证系统。Android 设备通过前置摄像头检测用户是否注视本屏幕，经 MQTT 上报给 Python 协调器；协调器汇总多设备输入后驱动菜单扫描与 TTS 语音播报。

当前阶段（2026-04-26）：Android MVP 可构建，`scanning_coordinator.py` 完成本地 MQTT 联调，自动化测试 8 passed。

## 结构与入口

```
floweye3/
├── android_mvp/                    # Kotlin Android 客户端
│   └── app/src/main/java/com/gazeinteraction/
│       ├── MainActivity.kt         # 主入口，校准/角色切换/MQTT 节流(500ms)
│       ├── camera/CameraManager.kt # Camera2 API（华为无 GMS 兼容）
│       ├── gaze/GazeDetectionAlgorithm.kt # 瞳孔居中+睁眼度+时域平滑
│       ├── mediapipe/FaceLandmarkerHelper.kt # MediaPipe Tasks Vision 封装
│       ├── debug/FaceMeshOverlayView.kt     # 调试 478 点人脸关键点绘制
│       └── mqtt/MqttClient.kt      # Eclipse Paho MQTT（自动重连）
├── coordinator_app/                # Python 协调器
│   ├── scanning_coordinator.py     # 主线协调器：IDLE/SCAN/CONFIRM/ALERT/WAITING 状态机
│   ├── simple_coordinator.py       # 最小 MQTT 基线（排障/回退用）
│   ├── simulate_scanning_flow.py   # 本地 MQTT 联调模拟器
│   ├── test_scanning_coordinator.py# unittest，8 用例全部 mock，无需 broker
│   ├── menu_config.json            # 2 层菜单（中文、优先级、紧急标记）
│   └── patient_config.json         # 驻留时间、TTS、夜间模式、自适应
├── docs/project_status.md          # 唯一可信进度源
└── CLAUDE.md                       # 含故障排除、算法参数、adb 调试命令
```

## 关键命令

```bash
# coordinator 测试（mock broker，无外部依赖）
cd coordinator_app && python -m pytest test_scanning_coordinator.py

# 本地联调（需要运行 MQTT broker，如 mosquitto）
cd coordinator_app
python scanning_coordinator.py --host localhost --port 1883 --menu menu_config.json --patient patient_config.json
# 另一终端
python simulate_scanning_flow.py --host localhost --port 1883 --scenario single_select
# 场景可选：single_select, dual_confirm, dual_skip, dual_emergency

# Android 构建
cd android_mvp && gradlew.bat assembleDebug
# APK: android_mvp/app/build/outputs/apk/debug/app-debug.apk
```

## MQTT 约定

Topics: `gazecontrol/device/{deviceId}/{gaze_status|status}` （设备→Broker），`gazecontrol/coordination/decision`（协调器→Broker）

**gaze_status payload**（Android → 协调器）必须含：
- `deviceId`、`role`（"yes"/"no"）、`lookingAtScreen`（布尔）、`confidence`（float）、`calibrated`（布尔）

**decision payload**（协调器 → Android）含：
- `type`（selection/emergency 等）、`optionId`、`optionLabel`、`ttsPrompt`、`urgency`、`activeDevices`

**status payload**（设备上线/下线）：`deviceId`、`status`（online/offline）

Android 端默认 broker `192.168.1.100:1883`，在应用内修改后持久化到 SharedPreferences。

## 测试要点

- `test_scanning_coordinator.py`（在 `coordinator_app/` 目录下，不是独立的 tests/ 目录）
- 通过 `sys.modules` mock 掉 `paho.mqtt.client`，用 `FakeTTS` 替换 `pyttsx3`，无需 broker
- 测试覆盖：状态机流转、子菜单导航、选择→确认→执行、取消回退、跳过确认（skip_confirm）、GazeInterpreter 防抖、三击紧急触发
- 本地联调使用 `simulate_scanning_flow.py`，通过 MQTT 发布模拟视线事件
- Android test/ 和 androidTest/ 目录当前为空

## 华为兼容要点

- 强制 Camera2（非 CameraX）+ CPU Delegate + 分辨率上限 1280×720
- 关闭 Blendshapes 和变换矩阵输出以节省 CPU
- MediaPipe 模型 `face_landmarker_v2_with_blendshapes.task`（~11-13MB）需在 `app/src/main/assets/`

## 算法关键参数

GazeDetectionAlgorithm 常量（companion object）：
- `LEFT_PUPIL = 468`、`RIGHT_PUPIL = 473`、`PUPIL_CENTER_THRESHOLD = 0.08`、`EYE_OPEN_MIN = 0.15`
- `CONFIDENCE_THRESHOLD = 0.55f`、`HISTORY_SIZE = 8`、`requiredConsecutiveFrames = 2`

扫描协调器状态机：`IDLE → SCAN → CONFIRM → (exec/skip/back)`，自适应 dwell 3–7s。

## 安全提醒

- 禁止提交真实患者身份、broker 公网地址或 TLS 私钥
- 项目为研究/概念验证，尚未完成真实护理环境验证
