# FlowEye 当前开发状态

最后整理时间：2026-04-26

## 概览

FlowEye 当前已经从“技术调研/方案设计”进入“Android MVP + Python 扫描协调器联调完成，准备真机对接”的阶段。

当前真实状态：

- Android 客户端代码已存在并可构建 debug APK
- Python 协调器存在两条线：
  - `simple_coordinator.py`：最小 MQTT 决策基线
  - `scanning_coordinator.py`：当前主线协调器
- `scanning_coordinator.py` 已实现：
  - 单设备唤醒与扫描
  - 双设备 yes/no 选择
  - skip/cancel/confirm
  - triple-yes emergency
  - 2 层菜单
  - MQTT decision 发布
  - 本地模拟联调脚本
  - 自动化测试

## 已完成

### Android 端

- Camera2 接入
- MediaPipe Face Landmarker 集成
- 视线检测算法
- 一键校准
- MQTT 发布/订阅
- Debug APK 构建

### Coordinator 端

- `simple_coordinator.py` 基线协调器
- `scanning_coordinator.py` 扫描协调器
- 配置文件：
  - `menu_config.json`
  - `patient_config.json`
- 本地 MQTT 模拟脚本：
  - `simulate_scanning_flow.py`
- 自动化测试：
  - `test_scanning_coordinator.py`

### 已验证场景

- `single_select`
- `dual_confirm`
- `dual_skip`
- `dual_emergency`

## 当前测试状态

- `python -m pytest coordinator_app/test_scanning_coordinator.py`
- 当前结果：`8 passed`

说明：

- 本地 broker 联调已经跑通过最终 `selection` / `emergency` decision
- `dual_emergency` 触发依赖异步 TTS；该问题已修复

## 仍在进行

- Android 真机和协调器真实消息联调
- 华为设备实测
- 扫描协调器与 Android 端真实 payload 对齐确认

## 尚未完成

- iPhone 客户端
- 多设备 3+ 直接选择模式的工程实现
- Web / 护理端可视化界面
- TLS / 安全加固
- 临床或真实护理场景验证

## 文档分类

### 当前状态来源

- 本文 `project_status.md`
- `README.md`
- `todo.md`
- `coordinator_app/README.md`
- `docs/coordinator_implementation_plan.md`

### 设计参考

- `docs/scanning_interaction_design.md`
- `docs/one_two_device_interaction_recommendation.md`
- `docs/tiered_interaction_architecture.md`
- `docs/interaction_design_proposal.md`

这些文档表达的是设计思路和产品方向，不再作为“当前是否完成”的判断依据。

### 历史归档

- `docs/archive/`

archive 目录下包含旧研究、旧计划和阶段性总结，不再代表当前实现状态。
