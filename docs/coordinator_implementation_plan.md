# FlowEye Coordinator Implementation Status

最后整理时间：2026-04-26

## 当前结论

`coordinator_app/scanning_coordinator.py` 已经从“实现计划”进入“已实现并完成本地联调验证”的状态。

它现在是项目中的主线协调器，`simple_coordinator.py` 则保留为最小基线和排障工具。

## 已实现能力

- MQTT 订阅：
  - `gazecontrol/device/+/gaze_status`
  - `gazecontrol/device/+/status`
- MQTT 决策发布：
  - `gazecontrol/coordination/decision`
- 设备在线跟踪和单/双设备模式判断
- 2 层菜单扫描
- `wake / select / confirm / skip / cancel / emergency`
- `IDLE / SCAN / CONFIRM / ALERT / WAITING` 状态机
- 自适应 dwell 时间
- 异步 TTS 队列，避免阻塞消息处理线程
- 本地 MQTT 场景模拟脚本

## 已验证范围

通过 `simulate_scanning_flow.py` 已本地验证：

- `single_select`
- `dual_confirm`
- `dual_skip`
- `dual_emergency`

通过自动化测试已验证：

- gaze 事件去抖
- triple-yes 触发条件
- coordinator 状态机关键流转
- 4 段 MQTT topic 路由

当前测试状态：

- `python -m pytest coordinator_app/test_scanning_coordinator.py`
- 结果：`8 passed`

## 当前文件角色

- `scanning_coordinator.py`: 主协调器
- `simple_coordinator.py`: 基线工具
- `simulate_scanning_flow.py`: 本地联调脚本
- `test_scanning_coordinator.py`: 协调器自动化测试
- `menu_config.json`: 菜单定义
- `patient_config.json`: 患者参数定义

## 下一步

高优先级：

- Android 真机与 `scanning_coordinator.py` 的真实联调
- 华为设备验证
- 真实 payload 和设备角色映射确认

中优先级：

- 3+ 设备模式工程实现
- 可视化护理端 / Web 界面
- 更完整的 coordinator 测试覆盖

低优先级：

- 重构 `simple_coordinator.py`
- 清理联调阶段临时日志
