# Repository Guidelines

> 本文件面向 AI Coding Agent。读者被假设对项目一无所知，所有信息均基于实际代码和文档，不做无依据的推测。

## 项目概述

Floweye（视线交互助手）是一个为失能患者开发的多设备协同视线交互概念验证系统。患者通过注视手机屏幕完成"是/否"选择，系统支持单设备轮播扫描和双设备固定左右（是/否）两种交互模式。每台 Android 设备通过前置摄像头检测用户是否注视本屏幕，并通过 MQTT 将视线状态上报给中央 Python 协调器；协调器汇总多设备输入后驱动菜单扫描、TTS 语音播报，并做出最终决策。

当前阶段（截至 2026-04-26）：Android MVP 可构建，Python 扫描协调器 `scanning_coordinator.py` 已完成本地 MQTT 联调并通过自动化测试（8 passed），主要待办为 Android 真机对接与临床场景验证。

## 项目结构与模块划分

```text
floweye3/
├── android_mvp/                    # Android 客户端（Kotlin）
│   ├── app/src/main/java/com/gazeinteraction/
│   │   ├── MainActivity.kt         # 主 Activity：双屏模式，管理校准、角色切换、MQTT 节流
│   │   ├── camera/CameraManager.kt # Camera2 API 封装（华为无 GMS 兼容）
│   │   ├── debug/FaceMeshOverlayView.kt  # 调试用的 478 点人脸关键点绘制
│   │   ├── gaze/GazeDetectionAlgorithm.kt # 核心视线检测：瞳孔居中 + 睁眼度 + 时域平滑
│   │   ├── mediapipe/FaceLandmarkerHelper.kt # MediaPipe Tasks Vision 封装
│   │   └── mqtt/MqttClient.kt      # Eclipse Paho MQTT 客户端（含自动重连）
│   ├── app/src/main/res/           # 布局、主题、字符串、图标等资源
│   ├── app/src/main/assets/        # MediaPipe 模型 face_landmarker_v2_with_blendshapes.task
│   └── app/build.gradle            # 模块构建配置
├── coordinator_app/                # Python 中央协调器与调试脚本
│   ├── scanning_coordinator.py     # 主协调器：扫描状态机、菜单引擎、TTS、自适应驻留时间
│   ├── simple_coordinator.py       # 最小基线协调器（多设备投票聚合，用于排障）
│   ├── simulate_scanning_flow.py   # MQTT 联调模拟器（验证单/双设备场景）
│   ├── test_scanning_coordinator.py# 自动化单元测试（unittest，8 个用例）
│   ├── test_coordinator.py         # 早期 bring-up 脚本（旧 payload 格式）
│   ├── menu_config.json            # 2 层菜单定义（中文标签、TTS 文本、优先级、紧急标记）
│   ├── patient_config.json         # 患者参数（驻留时间、TTS 音量、自适应开关等）
│   └── requirements.txt            # Python 依赖
├── docs/                           # 当前设计文档与状态文档
│   ├── project_status.md           # 当前开发状态（唯一可信进度源）
│   ├── coordinator_implementation_plan.md  # 协调器实现与验证状态
│   ├── scanning_interaction_design.md      # 扫描交互设计参考
│   ├── interaction_design_proposal.md      # 早期交互设计提案
│   ├── one_two_device_interaction_recommendation.md  # 1-2 台设备产品建议
│   ├── tiered_interaction_architecture.md  # 按设备数量分层的交互架构
│   └── archive/                    # 历史调研、旧计划、归档材料
├── quick_build.bat                 # Windows 快速构建脚本（检查模型 + 提示 Android Studio 流程）
└── AGENTS.md                       # 本文件
```

## 技术栈

| 组件 | 技术 |
|------|------|
| Android 应用 | Kotlin 1.9.21 + Android Gradle Plugin 8.7.3 |
| 最低 SDK / 目标 SDK | API 28 (Android 9.0) / API 34 |
| 摄像头 | Camera2 API（兼容华为无 GMS 设备），RenderScript YUV→Bitmap |
| 视线检测 | MediaPipe Tasks Vision 0.10.8（Face Landmarker V2，CPU Delegate） |
| 关键点 | 虹膜瞳孔索引 468（左眼）、473（右眼） |
| MQTT 客户端 | Eclipse Paho 1.2.5（Android Service） |
| JSON | Gson 2.10.1 |
| UI | ViewBinding、ConstraintLayout、Material3 |
| 中央协调器 | Python 3 + paho-mqtt 1.6.1 + pyttsx3 2.90 |
| 目标设备 | Android 手机（已实现），iPhone（未实现） |

## 构建、测试与开发命令

### Android

```bash
# 构建 Debug APK
cd android_mvp
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk

# 运行 JVM 单元测试（当前 test/ 目录为空，需后续补充）
./gradlew test

# 运行 instrumentation 测试（当前 androidTest/ 目录为空，需后续补充）
./gradlew connectedAndroidTest
```

### 中央协调器

```bash
cd coordinator_app
pip install -r requirements.txt

# 启动主协调器（扫描模式）
python scanning_coordinator.py --host <broker_ip> --port 1883 \
  --menu coordinator_app/menu_config.json \
  --patient coordinator_app/patient_config.json

# 启动最小基线协调器（排障）
python simple_coordinator.py <broker_ip> 1883

# 运行自动化测试
python -m pytest coordinator_app/test_scanning_coordinator.py

# 本地联调模拟（在另一终端运行）
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario single_select
# 可选场景：dual_confirm, dual_skip, dual_emergency
```

### 快速构建

Windows 环境下可运行 `quick_build.bat`，该脚本会：
1. 检查并提示下载 MediaPipe 模型文件到 `app/src/main/assets/`；
2. 提示在 Android Studio 中打开 `android_mvp` 并执行 Build → Build APK(s)；
3. 指出 APK 输出路径并建议通过 ADB 安装到华为手机。

## 代码风格规范

### Kotlin（Android）
- 缩进：4 个空格。
- 类名：`PascalCase`；函数与属性：`camelCase`；常量：`UPPER_SNAKE_CASE`。
- 包级职责分离：`camera`、`gaze`、`mediapipe`、`mqtt`、`debug`。
- 优先使用 Kotlin 惯用法：`lateinit var`、`by lazy`、`?: return` 等。
- 中文注释与文档字符串：核心类（如 `MainActivity`、`GazeDetectionAlgorithm`）使用中文 KDoc，说明设计意图和参数含义。
- 硬编码的算法参数集中在 `companion object` 中，如 `CONFIDENCE_THRESHOLD = 0.55f`、`HISTORY_SIZE = 8`。
- 避免在绘制或相机回调中分配对象（`FaceMeshOverlayView` 使用缓存 `FloatArray`）。

### Python（协调器）
- 缩进：4 个空格。
- 命名：`snake_case` 函数/变量，`PascalCase` 类。
- 保持小类职责单一：`ConfigLoader`、`MenuEngine`、`TTSEngine`、`GazeInterpreter`、`DeviceManager`、`ScanningCoordinator`。
- MQTT Topic 使用显式字符串常量，统一前缀 `gazecontrol/`。
- 日志使用标准库 `logging`，格式 `'%(asctime)s [%(levelname)s] %(message)s'`。
- 中文日志与注释：用户可见的日志和文档以中文为主，代码内部保持英文标识符。

### 通用
- 不要提交生成的构建产物（`build/`、`.gradle/`、`pytest-cache-*`）或本地环境文件。
- 不要提交私有 broker 地址、患者身份信息或凭证。

## 测试策略

### Android
- 单元测试目录：`android_mvp/app/src/test/java/...`（当前为空，待补充）。
- Instrumentation 测试目录：`android_mvp/app/src/androidTest/java/...`（当前为空，待补充）。
- 测试命名建议：行为驱动，例如 `GazeDetectionAlgorithmTest`、`MqttReconnectionTest`。
- 涉及 gaze 阈值、MQTT payload、协调器逻辑变更时，应补充自动化测试或记录手动验证步骤。

### Python 协调器
- 自动化测试文件：`coordinator_app/test_scanning_coordinator.py`（基于 `unittest`，8 个用例全部通过）。
- 测试覆盖范围：状态机流转、子菜单导航、叶节点选择→确认→执行、取消回退、跳过确认（`skip_confirm`）、MQTT 主题路由、`GazeInterpreter` 防抖、三击紧急触发。
- 测试中通过 `sys.modules` mock 掉 `paho.mqtt.client`，并用 `FakeTTS` 替换 `pyttsx3`，确保无需真实 broker 即可运行。
- 本地联调脚本：`simulate_scanning_flow.py` 用于在真实设备接入前验证端到端场景。

## MQTT 主题与 Payload 约定

| Topic 模式 | 方向 | 说明 |
|-----------|------|------|
| `gazecontrol/device/{deviceId}/gaze_status` | 设备 → Broker | 视线状态：含 `isLookingAtScreen`、`gazeTarget`（yes/no）、`confidence`、`timestamp` |
| `gazecontrol/device/{deviceId}/status` | 设备 → Broker | 在线状态：`online`/`offline` |
| `gazecontrol/coordination/decision` | 协调器 → Broker | 决策结果：含 `type`（selection/emergency 等）、`optionId`、`optionLabel`、`ttsPrompt`、`urgency` |

- 所有设备和协调器须与 Broker 处于同一局域网，默认端口 `1883`。
- Android 端默认 broker 地址为 `192.168.1.100:1883`，可在应用内修改（持久化到 `SharedPreferences`）。

## 安全与配置注意事项

- **隐私**：禁止在仓库中提交真实患者姓名、病历号、broker 公网地址或 TLS 私钥。
- **模型文件**：MediaPipe `.task` 模型（约 11–13 MB）保存在 `android_mvp/app/src/main/assets/`； redistribution 前需确认许可证合规。
- **设备兼容性**：Android 应用专为华为无 GMS 设备优化——强制使用 Camera2 API（而非 CameraX）、强制 CPU Delegate、分辨率上限 1280×720、关闭 Blendshapes 与变换矩阵输出。
- **临床免责声明**：本项目为研究与概念验证用途，尚未在真实护理环境中完成充分验证，不可直接用于医疗诊断或监护。

## Commit 与 Pull Request 规范

- Commit 前缀使用 `feat:`、`fix:`、`docs:`、`test:`、`refactor:` 等，后接简短中文描述。
- 示例：`feat: 添加双设备跳过逻辑`、`fix: 处理 MQTT 重连时空指针异常`。
- PR 描述应包含：行为变更说明、已运行的 Android/协调器命令、MQTT 与模型配置说明、UI/相机/交互相关的截图或日志。

## 关键文件速查

| 文件 | 作用 |
|------|------|
| `android_mvp/app/build.gradle` | Android 依赖与构建配置（compileSdk 34、MediaPipe 0.10.8、Paho 1.2.5 等） |
| `android_mvp/app/src/main/AndroidManifest.xml` | 权限声明（CAMERA、INTERNET、WAKE_LOCK）、MqttService 注册、MainActivity 入口 |
| `android_mvp/app/src/main/java/com/gazeinteraction/MainActivity.kt` | 应用主入口，生命周期管理，校准、角色切换、MQTT 节流（500ms） |
| `android_mvp/app/src/main/java/com/gazeinteraction/gaze/GazeDetectionAlgorithm.kt` | 瞳孔比例检测、时域平滑（8 帧历史）、2 帧连续确认、校准基准持久化 |
| `coordinator_app/scanning_coordinator.py` | 主协调器：IDLE/SCAN/CONFIRM/ALERT/WAITING 状态机、自适应驻留（3–7s）、TTS、菜单引擎 |
| `coordinator_app/menu_config.json` | 2 层菜单：不舒服、身体护理、吃喝、环境、社交、紧急 |
| `coordinator_app/patient_config.json` | 单/双设备驻留时间、TTS 参数、夜间模式（22:00–06:00）、自适应开关 |
| `docs/project_status.md` | 当前进度唯一可信来源 |
