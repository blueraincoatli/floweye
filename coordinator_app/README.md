# FlowEye Coordinator App

本目录包含 FlowEye 的 Python 协调器实现、联调脚本和测试。

> **注意**: 当前推荐使用 Android 自托管模式（两台设备独立运行，无需 PC）。本目录的 Python 协调器保留为 PC 模式和排障备用。详见项目 [README.md](../README.md)。

## 当前状态

当前主线协调器是 `scanning_coordinator.py`，已经完成本地 MQTT 联调并验证以下场景：

- `single_select`
- `dual_confirm`
- `dual_skip`
- `dual_emergency`

Android 端已实现自托管模式（CoordinatorEngine + 内嵌 Broker），可作为无需 PC 的替代方案。

保留的 `simple_coordinator.py` 主要用于：

- 最小 MQTT 决策基线
- smoke test
- 排障和回退对比

当前更完整的整体状态请看：

- [project_status.md](../docs/project_status.md)
- [coordinator_implementation_plan.md](../docs/coordinator_implementation_plan.md)

## 目录说明

- `simple_coordinator.py`: 最小 MQTT 决策协调器
- `scanning_coordinator.py`: 当前主线扫描协调器
- `simulate_scanning_flow.py`: 本地 MQTT 场景模拟脚本
- `menu_config.json`: 菜单树和播报文案
- `patient_config.json`: 患者参数和时序配置
- `test_coordinator.py`: 简单 broker 观察脚本
- `test_scanning_coordinator.py`: 协调器自动化测试

## 运行方式

### `simple_coordinator.py`

用于最小链路验证：

```bash
python coordinator_app/simple_coordinator.py <broker_ip> 1883
```

### `scanning_coordinator.py`

用于实际扫描交互原型：

```bash
python coordinator_app/scanning_coordinator.py --host <broker_ip> --port 1883 --menu coordinator_app/menu_config.json --patient coordinator_app/patient_config.json
```

当前已实现能力：

- 单设备唤醒和扫描
- 双设备 yes/no 交互
- 子菜单导航
- confirm / cancel / skip
- triple-yes emergency
- 自适应 dwell
- 异步 TTS 队列

## MQTT Topics

订阅：

- `gazecontrol/device/+/gaze_status`
- `gazecontrol/device/+/status`

发布：

- `gazecontrol/coordination/decision`

## Gaze Payload 约定

`scanning_coordinator.py` 当前依赖的主要字段：

- `deviceId`
- `role`
- `lookingAtScreen`
- `confidence`
- `calibrated`

## 开发与测试

安装依赖：

```bash
cd coordinator_app
pip install -r requirements.txt
```

运行测试：

```bash
python -m pytest coordinator_app/test_scanning_coordinator.py
python coordinator_app/test_coordinator.py
```

当前自动化测试状态：

- `test_scanning_coordinator.py`
- 结果：`8 passed`

## 本地 MQTT 联调

先启动扫描协调器：

```bash
python coordinator_app/scanning_coordinator.py --host localhost --port 1883 --menu coordinator_app/menu_config.json --patient coordinator_app/patient_config.json
```

再在第二个终端运行场景模拟：

```bash
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario single_select
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario dual_confirm
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario dual_skip
python coordinator_app/simulate_scanning_flow.py --host localhost --port 1883 --scenario dual_emergency
```

预期结果：

- `single_select`: 发布 `selection`
- `dual_confirm`: 发布 `selection`
- `dual_skip`: 至少触发 `skip`，并可继续完成最终 `selection`
- `dual_emergency`: 发布 `emergency`

## 日志说明

联调阶段会在本目录生成临时 `.log` 文件用于检查标准输出/错误输出，这些文件不作为项目文档，也不应作为开发状态来源。

如果需要查看中文日志，请优先按 UTF-8 读取。
