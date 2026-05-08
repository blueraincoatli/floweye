# Floweye - 失能患者视线交互系统

为失能患者开发的多设备协同视线交互系统。患者通过注视手机屏幕完成 `是/否`、单设备轮播和双设备扫描交互，协调器通过 MQTT 汇总设备消息并发布最终决策。

## 当前状态

- Android MVP 已实现并可构建
- **自托管模式已实现**：一台 Android 设备作为 HOST（内嵌 MQTT Broker + CoordinatorEngine），另一台作为 CLIENT，两台即可独立运行，无需 PC
- Python 协调器保留 `simple_coordinator.py` 和 `scanning_coordinator.py`
- `scanning_coordinator.py` 已完成本地 MQTT 联调
- 已验证场景：`single_select`、`dual_confirm`、`dual_skip`、`dual_emergency`
- 协调器自动化测试当前为 `8 passed`
- 华为真机（NOH-AN01）实测通过：双设备自托管全流程（唤醒→扫描→跳过→确认→执行）
- 患者参数已调优：注视选中2s、注视跳过1.5s、唤醒2s、停留15s、TTS重复播报2遍（间隔3秒）

当前状态以 [project_status.md](docs/project_status.md) 为准。

## 系统架构

### 自托管模式（推荐：两台 Android 设备独立运行）
```
Android HOST (是) ──内嵌 Broker──┐
                                 ├── CoordinatorEngine
Android CLIENT (否) ──MQTT──────┘
                                 |
                                 +-- MenuEngine (2层JSON)
                                 +-- AndroidTTSManager (重复播报)
                                 +-- GazeInterpreter (可配置阈值)
```

HOST 负责运行 MQTT Broker、协调引擎、TTS 播报。CLIENT 将视线检测结果通过 MQTT 发送到 HOST，HOST 统一处理双设备视线并做决策。

### PC 协调器模式
```text
[Android Device A] --+
[Android Device B] --+--> [MQTT Broker] --> [Python Coordinator] --> [Decision]
[Android Device C] --+
```

当前主协调器是 `coordinator_app/scanning_coordinator.py`。`simple_coordinator.py` 保留为最小基线和排障工具。

## 技术栈

| 组件 | 技术 |
|------|------|
| Android 应用 | Kotlin + Camera2 API + MediaPipe Tasks Vision |
| 视线检测 | 虹膜关键点(468-477) + 瞳孔位置比例 + 时间平滑 |
| 通信协议 | MQTT (Eclipse Paho / Moquette 内嵌 Broker) |
| 自托管协调器 | Kotlin (CoordinatorEngine + GazeInterpreter + MenuEngine) |
| PC 协调器 | Python 3 + paho-mqtt + pyttsx3 |
| TTS 播报 | Android TTS (可配置重复次数1-3, 间隔3秒) |
| 目标设备 | Android 手机（已实现），iPhone（未实现） |

## 快速开始

### 方式一：自托管模式（无需 PC）

1. 在两台 Android 设备上安装 APK
2. 在一台设备上长按右上角圆点 → 开启「操作者模式」→ 开启「强制主机」
3. 另一台设备自动连接，角色自动设为 CLIENT
4. 两台设备分别注视屏幕唤醒系统（2秒），开始使用

### 方式二：PC 协调器模式

#### 1. 部署 MQTT Broker

```bash
# macOS
brew install mosquitto && brew services start mosquitto

# Linux
sudo apt install mosquitto && sudo systemctl start mosquitto

# Windows: 下载安装 https://mosquitto.org/download/
```

#### 2. 启动扫描协调器

```bash
cd coordinator_app
pip install -r requirements.txt
python scanning_coordinator.py --host <broker_ip> --port 1883 --menu menu_config.json --patient patient_config.json
```

#### 3. 构建 Android 应用

```bash
cd android_mvp
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`

#### 4. 本地联调

```bash
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario single_select
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario dual_skip
```

## 患者配置

编辑 `coordinator_app/patient_config.json`（自托管模式同步到 `android_mvp/app/src/main/assets/`）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `single_device.select_gaze_seconds` | 2.0 | 单设备模式选中注视时长 |
| `single_device.wake_gaze_seconds` | 2.0 | 唤醒注视时长 |
| `single_device.dwell_seconds` | 15 | 选项停留时间（超时自动跳过） |
| `dual_device.select_gaze_seconds` | 2.0 | 双设备"是"注视时长 |
| `dual_device.skip_gaze_seconds` | 1.5 | 双设备"否"注视时长（下限1.0s） |
| `tts.rate` | 130 | TTS 语速 |
| `tts.repeat_count` | 2 | 播报重复次数（可在设置面板调整1-3） |

## 项目结构

```text
floweye3/
  android_mvp/                # Android 客户端（含自托管协调器）
    app/src/main/java/com/gazeinteraction/
      coordinator/            # 自托管组件
        CoordinatorEngine.kt  # 状态机 + 菜单 + TTS
        GazeInterpreter.kt    # 视线参数判定
        HostManager.kt        # 角色检测
        BrokerService.kt      # 内嵌 MQTT Broker
        MenuEngine.kt         # 2层菜单导航
        AndroidTTSManager.kt  # TTS 队列 + 重复播报
  coordinator_app/            # Python 协调器与联调脚本
  docs/                       # 设计文档与状态文档
  docs/archive/               # 历史调研/旧计划/归档材料
```

## 文档导航

- [project_status.md](docs/project_status.md): 当前开发状态、已验证范围、下一步
- [coordinator_implementation_plan.md](docs/coordinator_implementation_plan.md): 协调器实现和验证状态
- [scanning_interaction_design.md](docs/scanning_interaction_design.md): 双设备扫描交互设计参考
- [one_two_device_interaction_recommendation.md](docs/one_two_device_interaction_recommendation.md): 1-2 台设备产品建议
- [tiered_interaction_architecture.md](docs/tiered_interaction_architecture.md): 按设备数量分层的交互架构参考
- [archive/](docs/archive): 旧调研、旧计划、历史总结
- [CLAUDE.md](CLAUDE.md): 开发指南（给 Claude Code 的上下文）

## 硬件要求

- Android 9.0+ (API 28+)
- 前置摄像头
- 所有设备与 broker 在同一局域网（自托管模式由 HOST 自动建立热点或共享网络）
- 推荐使用距离 40-60cm，双设备间距 15cm 以上

## 免责声明

本项目为研究和概念验证用途开发，尚未完成真实护理环境下的充分验证。
