# Floweye — 用视线说话 / Speak with Your Eyes

[中文](#中文) | [English](#english)

---

## 中文

### 解决什么问题

一些严重失能的患者——比如 ALS（渐冻症）、高位脊髓损伤、脑干中风——意识完全清醒，但无法说话，也无法用手势交流。他们需要一种最低门槛的方式，让护理人员知道自己想要什么。

现有方案各有局限：

| 方案 | 问题 |
|------|------|
| 脑机接口（侵入式） | 需要开颅手术，数十万美元，全球仅几百人使用过 |
| 专业眼动仪（Tobii 等） | 数千美元，依赖 PC，需要校准，无法随身携带 |
| 眨眼/面部肌肉检测 | 难以区分自主眨眼和反射性眨眼，精度有限 |
| 护理人员逐项猜测 | 效率低，容易遗漏，患者容易产生无助感 |

本项目尝试用**两台带前置摄像头的手机 + 支架**（HOST 需为 Android；CLIENT 可以是 Android 或 iPhone，只要运行视线检测并连接 MQTT），让患者注视"是"/"否"按钮完成菜单选择，成本接近于零。

### 当前状态

目前是**原型阶段**，在华为真机上完成了双设备联调，健康人测试可用。尚未在真实患者身上验证。

- Android MVP 可构建、可运行
- 自托管模式：一台手机作为 HOST（内嵌 MQTT Broker + 协调引擎），另一台作为 CLIENT，两台即可独立运行，无需 PC
- PC 协调器模式保留为备用
- 已验证四种场景：单设备选择、双设备确认、双设备跳过、紧急呼叫
- 协调器自动化测试 `8 passed`
- 护理者微信推送已实现（Server酱，三级分级通知）
- 硬编码参数：注视选中 2s、跳过 1.5s、唤醒 2s、选项停留 15s、播报重复 2 遍（间隔 3s）

### 系统架构

```
Android HOST (是) ──内嵌 Broker──┐
                                 ├── CoordinatorEngine
Android CLIENT (否) ──MQTT──────┘
                                 │
                                 +-- MenuEngine (2层JSON菜单)
                                 +-- AndroidTTSManager (重复播报)
                                 +-- GazeInterpreter (可配置阈值)
                                 +-- ServerChanNotifier ──HTTPS──> Server酱 ──> 护理者微信
                                 +-- TelegramNotifier ──HTTPS──> Telegram ──> 护理者 Bot
```

两台手机面对面或并排放置。HOST 显示"是"，CLIENT 显示"否"。患者注视对应屏幕，HOST 统一处理视线信号并做出决策，通过 TTS 语音播报最终选项，同时通过 Server酱 将选择结果推送到护理者微信。

### 护理者通知

患者做出选择后，系统通过 [Server酱](https://sct.ftqq.com/) 将消息实时推送到护理者微信（「方糖」服务号），无需安装任何 App。也支持 Telegram Bot 作为国际用户通道，两者可同时使用。

配置步骤：
1. 注册 Server酱 获取 SendKey（或创建 Telegram Bot 获取 Token + Chat ID）
2. 护理者微信扫码关注「方糖」服务号（或 Telegram 上关注 Bot）
3. 在 Android 设置面板或 `coordinator_app/notify_config.json` 中填入配置，选择通道

消息按紧急程度分为三级：

| 级别 | 触发条件 | 微信推送 |
|------|---------|---------|
| 紧急 | 紧急呼叫、胸闷 | 红色标题「患者紧急呼叫！」 |
| 重要 | 头疼、伤口疼、想翻身等 | 黄色标题「患者请求帮助」 |
| 一般 | 想喝水、太热了等 | 普通消息 |

### 快速开始

#### 自托管模式（推荐）

1. 在两台 Android 设备上安装 APK
2. 在一台设备上长按右上角圆点 → 开启「操作者模式」→ 开启「强制主机」
3. 另一台设备自动连接为 CLIENT
4. 注视"你好"唤醒系统（2 秒），进入菜单扫描

#### 构建

```bash
cd android_mvp
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

PC 协调器模式详见 [CLAUDE.md](CLAUDE.md)。

### 技术栈

| 组件 | 技术 |
|------|------|
| Android 应用 | Kotlin + Camera2 API + MediaPipe Tasks Vision |
| 视线检测 | 虹膜关键点 + 瞳孔位置比例 + 时间平滑 |
| 通信 | MQTT (Eclipse Paho / Moquette 内嵌 Broker) |
| 协调器 | Kotlin 自托管引擎 / Python 3 备用 |
| TTS | Android TTS，可配置重复次数 1-3，间隔 3 秒 |

### 项目结构

```text
floweye3/
  android_mvp/                     # Android 客户端（含自托管协调器）
    .../coordinator/
      CoordinatorEngine.kt         # 状态机 + 菜单 + TTS
      GazeInterpreter.kt           # 视线参数判定
      HostManager.kt               # 角色检测
      BrokerService.kt             # 内嵌 MQTT Broker
      MenuEngine.kt                # 2层菜单导航
      AndroidTTSManager.kt         # TTS 队列 + 重复播报
      ServerChanNotifier.kt        # Server酱 微信推送
      TelegramNotifier.kt          # Telegram Bot 推送
      CompositeNotifier.kt         # 多通道调度器
      CaregiverNotifier.kt         # 通知接口
  coordinator_app/                 # Python 协调器（PC 模式备用）
    notifier.py                    # 通知模块：Server酱 + Telegram + 复合调度
    notify_config.json             # 通知配置（通道选择 + SendKey + Bot Token）
  docs/                            # 设计文档与状态
```

### 患者参数配置

编辑 `coordinator_app/patient_config.json`（自托管模式同步到 `android_mvp/app/src/main/assets/`）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `single_device.wake_gaze_seconds` | 2.0 | 唤醒注视时长 |
| `single_device.select_gaze_seconds` | 2.0 | 单设备选中注视时长 |
| `single_device.dwell_seconds` | 15 | 选项停留时间 |
| `dual_device.select_gaze_seconds` | 2.0 | 双设备"是"注视时长 |
| `dual_device.skip_gaze_seconds` | 1.5 | 双设备"否"注视时长 |
| `tts.rate` | 130 | TTS 语速 |
| `tts.repeat_count` | 2 | 播报重复次数（可在设置面板调整 1-3） |

### 已知局限

- 仅在健康人（开发者）和两台华为设备（NOH-AN01）上测试通过，双设备互斥已验证
- 未在真实患者身上验证
- 视线检测在强侧光、佩戴眼镜、头部大幅偏转等条件下可能不稳定
- 双设备并排放置时，一台设备的摄像头可能误检测到用户正在看另一台——预期通过注视时长阈值降低误触，但未做严格的互斥处理
- 未加密通信，仅适合局域网使用

### 硬件要求

- Android 9.0+（API 28+），前置摄像头
- 所有设备在同一局域网
- 推荐距离 40-60cm，双设备间距 15cm 以上

### 许可证与免责

MIT License。本项目处于原型阶段，尚未在临床或真实护理环境中验证。请在实际使用前充分测试。

---

## English

### The Problem

Some patients with severe motor disabilities — ALS, high spinal cord injury, brainstem stroke — remain fully conscious but cannot speak or gesture. They need a minimal-barrier way to communicate basic needs to caregivers.

Existing approaches each have significant barriers:

| Approach | Limitation |
|----------|------------|
| Invasive BCI | Requires brain surgery, costs hundreds of thousands of dollars, used by only a few hundred people worldwide |
| Professional eye trackers (Tobii, etc.) | Thousands of dollars, PC-dependent, requires calibration, not portable |
| Blink / facial muscle detection | Hard to distinguish voluntary blinks from reflexive ones; limited accuracy |
| Caregivers guessing | Inefficient, error-prone, can cause patient frustration and learned helplessness |

This project attempts to use **two camera-equipped phones + mounts** (HOST must be Android; CLIENT can be Android or iOS — whichever runs gaze detection and connects via MQTT), letting patients select "Yes"/"No" through a multi-level menu at near-zero cost.

### Current Status

**Prototype stage.** Tested on healthy individuals with Huawei hardware. Not yet validated with actual patients.

- Android MVP buildable and deployable
- Self-hosted mode: one phone as HOST (embedded MQTT Broker + coordination engine), the other as CLIENT. No PC required
- Four interaction scenarios verified: single-select, dual-confirm, dual-skip, emergency
- Coordinator tests: `8 passed`
- Caregiver WeChat push notification implemented (ServerChan, 3-tier urgency)
- Timing defaults: select gaze 2s, skip 1.5s, wake 2s, dwell 15s, TTS repeats 2× (3s interval)

### Caregiver Notification

When a patient makes a selection, the system pushes a real-time notification to the caregiver via [ServerChan](https://sct.ftqq.com/) (WeChat, for users in China) or a Telegram Bot (for international users). Both channels can be used simultaneously.

Setup:
1. ServerChan: register to get a SendKey, caregiver follows the "方糖" WeChat Official Account. Telegram: create a bot via @BotFather, caregiver sends /start to the bot, then retrieve the chat_id.
2. Enter the credentials in the Android settings panel or `coordinator_app/notify_config.json`
3. Select the channel mode: serverchan, telegram, or both

Three urgency levels:

| Level | Trigger | WeChat Message |
|-------|---------|----------------|
| Emergency | Emergency call, chest tightness | "Patient Emergency Call!" (red) |
| Important | Pain, repositioning, toilet needs | "Patient Needs Help" (yellow) |
| Normal | Water, temperature, etc. | Plain info message |

### Quick Start (Self-Hosted)

1. Install APK on both Android devices
2. On one: long-press top-right dot → Operator mode → Force Host
3. The other auto-connects as CLIENT
4. Gaze at "你好" (2s) on either device to wake

```bash
# Build
cd android_mvp
./gradlew assembleDebug
```

### Known Limitations

- Tested only on healthy individuals and two Huawei devices (NOH-AN01); mutual-exclusion between devices verified
- Not validated with patients
- Gaze detection may be unstable under strong side-lighting, glasses, or significant head movement
- Dual-device placement can cause cross-detection; mitigated by gaze-duration thresholds but not rigorously
- Unencrypted MQTT — intended for local network use only

### License & Disclaimer

MIT License. This project is a research prototype. It has not been validated in clinical or nursing environments. Test thoroughly before real-world use.

<br>

<p align="right">
  <sub>献给我的父亲<br>For my father</sub>
</p>
