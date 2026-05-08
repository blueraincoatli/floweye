# Floweye - 开发待办

## 已完成

- [x] 技术调研与方案选型（见 `docs/archive/`）
- [x] Android MVP 应用开发（Kotlin + Camera2 + MediaPipe）
- [x] 视线检测算法（基于虹膜关键点 468-477）
- [x] 一键校准流程
- [x] MQTT 通信集成
- [x] `simple_coordinator.py` 基线协调器
- [x] `scanning_coordinator.py` 扫描协调器
- [x] 协调器 2 层菜单、确认/取消/跳过、紧急触发逻辑
- [x] 协调器本地 MQTT 模拟联调脚本
- [x] 已验证场景：`single_select`、`dual_confirm`、`dual_skip`、`dual_emergency`
- [x] 协调器自动化测试（当前 `8 passed`）
- [x] 异步 TTS 解耦，修复紧急触发时序问题
- [x] Android UI 重设计 — 光圈引导系统（GazeHaloView + ArcProgressView + 三主题 + 两段式反馈）
- [x] Android 真机对接 `scanning_coordinator.py`（MQTT 联调通过）
- [x] 华为设备实测（NOH-AN01，视线检测 + 光圈引导 + 协调器联调全流程）
- [x] 按钮图标、主题适配、弧线进度、辉光裁切等 UI 细节修复
- [x] 自托管模式（CoordinatorEngine + 内嵌 MQTT Broker + 双设备无需 PC）
- [x] 双设备视线 latch 修复（announce→select 过渡时主动清除 actionConsumed）
- [x] TTS 延迟重复播报（可配置 1-3 次，间隔 3 秒）
- [x] 设置面板重构（自定义布局 + 角色同步 + 播报次数滑块）
- [x] CONFIRM 阶段副机"否"按钮修复（confirm_ready 消息通知）
- [x] 患者参数调优（注视2s/跳过1.5s/唤醒2s/停留15s/TTS语速130）
- [x] 配置文件同步（coordinator_app/ 与 Android assets/）

## 进行中

（无）

## 待开始

- [ ] iOS 版本开发
- [ ] 多设备 3+ 直接选择工程实现
- [ ] 护理端 / Web 可视化界面
- [ ] MQTT Broker 地址设置界面
- [ ] TLS 加密通信
- [ ] 临床场景测试
