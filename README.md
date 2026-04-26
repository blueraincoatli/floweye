# Floweye - 失能患者视线交互系统

为失能患者开发的多设备协同视线交互系统。患者通过注视手机屏幕完成 `是/否`、单设备轮播和双设备扫描交互，协调器通过 MQTT 汇总设备消息并发布最终决策。

## 当前状态

- Android MVP 已实现并可构建
- Python 协调器已包含 `simple_coordinator.py` 和 `scanning_coordinator.py`
- `scanning_coordinator.py` 已完成本地 MQTT 联调
- 已验证场景：`single_select`、`dual_confirm`、`dual_skip`、`dual_emergency`
- 协调器自动化测试当前为 `8 passed`
- 当前主要待办是 Android 真机对接和临床/场景验证

当前状态以 [project_status.md](D:\floweye3\docs\project_status.md) 为准。

## 系统架构

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
| 视线检测 | 虹膜关键点(468-477) + 瞳孔位置比例 + 头部姿态融合 |
| 通信协议 | MQTT (Eclipse Paho) |
| 中央协调器 | Python 3.13 + paho-mqtt + pyttsx3 |
| 目标设备 | Android 手机（已实现），iPhone（未实现） |

## 快速开始

### 1. 部署 MQTT Broker

```bash
# macOS
brew install mosquitto && brew services start mosquitto

# Linux
sudo apt install mosquitto && sudo systemctl start mosquitto

# Windows: 下载安装 https://mosquitto.org/download/
```

### 2. 启动扫描协调器

```bash
cd coordinator_app
pip install -r requirements.txt
python scanning_coordinator.py --host <broker_ip> --port 1883 --menu coordinator_app/menu_config.json --patient coordinator_app/patient_config.json
```

### 3. 构建 Android 应用

```bash
cd android_mvp
./gradlew assembleDebug
```

生成的 APK 位于：

`android_mvp/app/build/outputs/apk/debug/app-debug.apk`

### 4. 本地联调

在第二个终端运行模拟器：

```bash
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario single_select
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario dual_confirm
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario dual_skip
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario dual_emergency
```

## 项目结构

```text
floweye3/
  android_mvp/                # Android 客户端
  coordinator_app/            # Python 协调器与联调脚本
  docs/                       # 当前状态文档与设计文档
  docs/archive/               # 历史调研/旧计划/归档材料
```

## 文档导航

- [project_status.md](D:\floweye3\docs\project_status.md): 当前开发状态、已验证范围、下一步
- [coordinator_implementation_plan.md](D:\floweye3\docs\coordinator_implementation_plan.md): 协调器实现和验证状态
- [scanning_interaction_design.md](D:\floweye3\docs\scanning_interaction_design.md): 双设备扫描交互设计参考
- [one_two_device_interaction_recommendation.md](D:\floweye3\docs\one_two_device_interaction_recommendation.md): 1-2 台设备产品建议
- [tiered_interaction_architecture.md](D:\floweye3\docs\tiered_interaction_architecture.md): 按设备数量分层的交互架构参考
- [archive/](D:\floweye3\docs\archive): 旧调研、旧计划、历史总结

## 硬件要求

- Android 9.0+ (API 28+)
- 前置摄像头
- 所有设备与 broker 在同一局域网
- 推荐使用距离 40-60cm

## 免责声明

本项目为研究和概念验证用途开发，尚未完成真实护理环境下的充分验证。
