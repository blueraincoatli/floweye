# Floweye 自托管方案设计：手机内置协调器

日期：2026-05-06
状态：Design Approved

## 1. 目标

消除 PC 依赖。两台手机通过 WiFi 热点直连即可完成完整的视线交互流程。同时为 iOS 客户端留出扩展空间。

## 2. 架构

### 2.1 总览

```
主机 (任意 Android, 开热点 192.168.43.1:1883)
├── Moquette MQTT Broker (内嵌, 端口 1883)
├── CoordinatorEngine (Kotlin 状态机, 移植自 scanning_coordinator.py)
├── MenuEngine (JSON菜单导航)
├── GazeInterpreter (注视解读 + 自适应)
├── AndroidTTSManager (Android 原生 TTS)
├── GazeDetection (Camera2 + MediaPipe, 现有)
└── UI (光圈引导系统, 现有)
        │
   WiFi 热点
        │
客户端 (Android/iOS)
├── MQTT Client → tcp://192.168.43.1:1883
├── GazeDetection
└── UI (光圈引导系统)
```

### 2.2 角色自动判定

- 检测本机是否开启 WiFi 热点 → 是则**主机模式**，启 broker + 协调器
- 检测当前 WiFi 是否为热点网段 (192.168.43.x) → 是则**客户端模式**
- 设置界面提供手动切换

### 2.3 主机 vs 客户端对比

| 能力 | 主机 | 客户端 |
|------|------|--------|
| 视线检测 + UI | 是 | 是 |
| MQTT Broker | 是 | 否 |
| 协调器状态机 | 是 | 否 |
| TTS 播报 | 是 | 否 |
| 菜单管理 | 是 | 否 |
| 角色: 是/否按钮 | 可切换 | 可切换 |
| 平台 | Android 6+ | Android 6+ / iOS (将来) |

### 2.4 客户端收到决策后的行为

与现有 PC 协调器方案完全一致——客户端订阅 `gazecontrol/coordination/decision`，根据 `type` 字段驱动 UI 状态机（IDLE→TRANSITION→SCAN→CONFIRM→FEEDBACK），**客户端不做任何决策逻辑**。

## 3. 移植模块

### 3.1 CoordinatorEngine（状态机）

从 `scanning_coordinator.py` 移植核心状态转换：

```
State: IDLE → SCAN → CONFIRM → WAITING → IDLE
                          ↘ ALERT
```

- IDLE: 等待注视唤醒 (wake_gaze_seconds=3s)，收到 wake → 进入 SCAN
- SCAN: 逐项播报选项 (announce→select)，收到 select → CONFIRM 或 子菜单
- CONFIRM: 确认选择，收到 confirm → 执行 → WAITING
- WAITING: 等待冷却 → IDLE
- ALERT: 紧急触发（去特殊化，与正常流程一致）

### 3.2 MenuEngine（菜单引擎）

从 `menu_config.json` 加载两层菜单结构，提供：
- `getCurrentOptions()` → 当前层级的选项列表
- `selectCurrent()` → 选中当前选项（叶子→执行，非叶子→深入）
- `goBack()` → 返回上级
- `reset()` → 回到根级

### 3.3 GazeInterpreter（注视解读）

从现有 `GazeDetectionAlgorithm` 的 gaze 数据 + MQTT topic 中提取，主机上直接接收内部回调：
- 统计各设备是/否注视时长
- 判断 select / skip / wake 动作
- 防抖 + 犹豫重置逻辑

### 3.4 AdaptiveDwell（自适应停留时间）

根据患者近期响应时间调整 dwell 时长（现有 `scanning_coordinator.py` 逻辑的 Kotlin 移植）。

### 3.5 AndroidTTSManager

封装 Android `TextToSpeech` API，替代 pyttsx3：
- 使用系统中文语音引擎
- 支持速率/音量调节
- 夜间模式（22:00-06:00 降音量）
- 所有 TTS 调用在后台线程，不阻塞状态机

## 4. MQTT Broker 内嵌

### 4.1 选型: Moquette

- 纯 Java 实现，无原生依赖
- APK 增加约 800KB
- 支持 MQTT 3.1.1，QoS 0/1/2
- 允许匿名连接（内网热点安全）

### 4.2 配置

```kotlin
val broker = Server()
broker.startServer(ServerConfig().apply {
    host = "0.0.0.0"
    port = 1883
    allowAnonymous = true
})
```

Topic 结构不变：
- `gazecontrol/device/{deviceId}/gaze_status` — 视线状态
- `gazecontrol/device/{deviceId}/status` — 设备上下线
- `gazecontrol/coordination/decision` — 协调器决策

### 4.3 生命周期

- 主机模式：broker 在应用启动时启动，退出时关闭
- 客户端模式：broker 不启动，直接连主机 IP
- 热点状态变化时自动切换模式 + 重启 broker

## 5. 网络与发现

### 5.1 WiFi 热点

主机开启热点后：
- 热点 IP 固定为 192.168.43.1（Android 默认）
- MQTT broker 监听 `0.0.0.0:1883`
- 客户端连接 `tcp://192.168.43.1:1883`

### 5.2 设备发现（可选，二期）

一期手动输入 IP。二期可加入：
- UDP 广播发现（主机广播 `floweye-host`，客户端扫描）
- 或二维码扫描（主机显示含 IP 的二维码）

## 6. iOS 客户端预留

### 6.1 架构兼容性

MQTT 协议是跨平台的，iOS 端只需：
- Swift + ARKit/AVFoundation（视线检测）
- CocoaMQTT（MQTT 客户端）
- 光圈引导 UI（SwiftUI 重绘）

iOS 端**不**需要内嵌 broker 或移植协调器——始终作为客户端连接 Android 主机。

### 6.2 本期范围

- 不实现 iOS 代码
- Android 端 MQTT topic 和 payload 格式保持不变，确保将来 iOS 客户端可直接对接

## 7. 改动清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `CoordinatorEngine.kt` | 新增 | 状态机核心，移植自 scanning_coordinator.py |
| `MenuEngine.kt` | 新增 | 菜单导航逻辑 |
| `GazeInterpreter.kt` | 新增 | 注视解读 + dwell 管理 |
| `AdaptiveDwell.kt` | 新增 | 自适应停留时间 |
| `AndroidTTSManager.kt` | 新增 | Android 原生 TTS 封装 |
| `BrokerService.kt` | 新增 | Moquette broker 生命周期管理 |
| `HostManager.kt` | 新增 | 主机/客户端模式判断与切换 |
| `MqttClient.kt` | 修改 | 动态 broker 地址（热点 IP vs 配置 IP） |
| `MainActivity.kt` | 修改 | 新增主机管理 UI 入口 |
| `build.gradle.kts` | 修改 | 添加 moquette 依赖 |

## 8. 不改的

- 视线检测算法（GazeDetectionAlgorithm.kt）
- FaceLandmarkerHelper、CameraManager
- 光圈引导 UI（GazeHaloView、ArcProgressView）
- 三主题系统
- 菜单配置格式（menu_config.json、patient_config.json）
- MQTT topic 和 payload 结构

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| Moquette 在低端机上性能 | 热点网只有 2-3 个客户端，QoS 0 为主，负载极低 |
| Android 热点兼容性差异 | 使用标准 API `WifiManager.LocalOnlyHotspot`，不支持则降级到手动配置 |
| 后台运行被杀 | 主机必须保持前台或使用前台 Service，加 WakeLock |
