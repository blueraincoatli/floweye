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

## 进行中

- [ ] Android 真机对接 `scanning_coordinator.py`
- [ ] 华为设备实测
- [ ] Android 端真实 payload 与协调器联调确认

## 待开始

- [ ] iOS 版本开发
- [ ] 多设备 3+ 直接选择工程实现
- [ ] 护理端 / Web 可视化界面
- [ ] MQTT Broker 地址设置界面
- [ ] TLS 加密通信
- [ ] 临床场景测试
