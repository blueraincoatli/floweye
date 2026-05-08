# FlowEye 当前开发状态

最后整理时间：2026-05-08

## 概览

FlowEye 已从"技术调研/方案设计"阶段进入"Android 自托管双设备联调完成"阶段。

当前状态：

- Android 客户端代码已存在并可构建 debug APK
- **自托管模式已实现**：一台 Android 设备作为 HOST（内嵌 MQTT Broker + CoordinatorEngine），另一台作为 CLIENT，两台设备即可独立运行
- Python 协调器存在两条线：
  - `simple_coordinator.py`：最小 MQTT 决策基线
  - `scanning_coordinator.py`：当前主线协调器（PC 模式）
- `scanning_coordinator.py` 已实现：单设备唤醒与扫描、双设备 yes/no 选择、skip/cancel/confirm、triple-yes emergency、2 层菜单、MQTT decision 发布、本地模拟联调脚本、自动化测试
- 华为真机（NOH-AN01）实测通过：双设备自托管全流程（唤醒→扫描→跳过→确认→执行）

## 已完成

### Android 端

- Camera2 接入
- MediaPipe Face Landmarker 集成
- 视线检测算法（虹膜关键点 + 瞳孔居中 + 时间平滑）
- 一键校准
- MQTT 发布/订阅
- Debug APK 构建
- UI 重设计：光圈引导系统（GazeHaloView + ArcProgressView + 三主题 + 两段式反馈）
- **自托管协调引擎**：CoordinatorEngine + GazeInterpreter + MenuEngine + AndroidTTSManager
- **内嵌 MQTT Broker**（Moquette），HOST 设备独立运行 Broker
- **双设备视线 latch 修复**：announce→select 过渡时主动清除远程注视锁
- **TTS 延迟重复播报**：可配置 1-3 次，每遍间隔 3 秒
- **设置面板重构**：自定义布局 + 角色同步 + 播报次数滑块（1-3）
- **CONFIRM 阶段副机"否"按钮**：通过 confirm_ready MQTT 消息通知 CLIENT 显示
- **患者参数调优**：注视选中 2s / 注视跳过 1.5s / 唤醒 2s / 停留 15s / TTS 语速 130
- **配置文件同步**：coordinator_app/ 与 Android assets/ 两处一致

### Coordinator 端

- `simple_coordinator.py` 基线协调器
- `scanning_coordinator.py` 扫描协调器
- 配置文件：`menu_config.json`、`patient_config.json`
- 本地 MQTT 模拟脚本：`simulate_scanning_flow.py`
- 自动化测试：`test_scanning_coordinator.py`

### 已验证场景

- `single_select`
- `dual_confirm`
- `dual_skip`
- `dual_emergency`
- 华为真机双设备自托管全流程

## 当前测试状态

- `python -m pytest coordinator_app/test_scanning_coordinator.py`
- 当前结果：`8 passed`

## 尚未完成

- iPhone 客户端
- 多设备 3+ 直接选择模式的工程实现
- Web / 护理端可视化界面
- MQTT Broker 地址设置界面
- TLS / 安全加固
- 临床或真实护理场景验证

## 文档分类

### 当前状态来源

- 本文 `project_status.md`
- `README.md`
- `todo.md`
- `CLAUDE.md`
- `coordinator_app/README.md`
- `docs/coordinator_implementation_plan.md`

### 设计参考

- `docs/scanning_interaction_design.md`
- `docs/one_two_device_interaction_recommendation.md`
- `docs/tiered_interaction_architecture.md`
- `docs/interaction_design_proposal.md`

这些文档表达的是设计思路和产品方向，不再作为"当前是否完成"的判断依据。

### 历史归档

- `docs/archive/`

archive 目录下包含旧研究、旧计划和阶段性总结，不再代表当前实现状态。
