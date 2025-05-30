# FloweEye 华为手机MVP实施计划

## 项目目标
在华为无GMS设备上实现一个基于视线追踪的"是/否"选择MVP应用。

## 技术架构

### 核心组件
1. **CameraX** - 摄像头预览和图像捕获
2. **MediaPipe Face Landmarker** - 人脸检测和特征点提取
3. **自定义视线算法** - 基于虹膜位置的视线方向判断
4. **MQTT通信** - 状态数据传输到协调器

### 数据流
```
摄像头 → 图像分析 → MediaPipe → 视线算法 → UI更新 → MQTT发送
```

## 实施阶段

### 阶段1：基础验证 (已完成)
- ✅ Android项目搭建
- ✅ 依赖配置
- ✅ 基础UI界面
- ✅ 摄像头集成
- ✅ MediaPipe基础集成

### 阶段2：核心算法实现 (1-2天)

#### 2.1 下载MediaPipe模型
```bash
# 下载地址
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task

# 放置位置
app/src/main/assets/face_landmarker_v2_with_blendshapes.task
```

#### 2.2 实现视线方向判断
创建 `GazeTracker.kt`:
```kotlin
class GazeTracker {
    fun analyzeGaze(landmarks: List<Landmark>): GazeDirection
    fun mapGazeToButton(gazeDirection: GazeDirection, screenSize: Size): ButtonTarget
}
```

关键特征点：
- 左眼虹膜中心：特征点468
- 右眼虹膜中心：特征点473
- 鼻尖：特征点1
- 眼角：特征点33, 133, 362, 263

#### 2.3 视线算法逻辑
```kotlin
// 伪代码
fun calculateGazeDirection(leftIris: Point, rightIris: Point, noseTip: Point): Vector3D {
    val eyeCenter = (leftIris + rightIris) / 2
    val gazeVector = eyeCenter - noseTip
    return normalizeVector(gazeVector)
}

fun isLookingAtButton(gazeVector: Vector3D, buttonBounds: Rect): Boolean {
    val screenPoint = projectToScreen(gazeVector)
    return buttonBounds.contains(screenPoint)
}
```

### 阶段3：UI增强和反馈 (1天)

#### 3.1 视觉反馈
- 按钮高亮显示
- 视线轨迹可视化
- 检测置信度显示

#### 3.2 交互逻辑
- 持续注视时间阈值（如2秒）
- 防误触机制
- 状态切换动画

### 阶段4：MQTT通信 (1天)

#### 4.1 创建MqttManager
```kotlin
class MqttManager {
    fun connect(brokerUrl: String, clientId: String)
    fun publishGazeState(state: GazeState)
    fun disconnect()
}
```

#### 4.2 数据格式
```json
{
    "deviceId": "huawei_device_001",
    "timestamp": 1640995200000,
    "gazeState": {
        "target": "yes|no|none",
        "confidence": 0.85,
        "duration": 2.1
    },
    "faceDetected": true
}
```

### 阶段5：华为设备优化 (1天)

#### 5.1 性能优化
- 降低检测频率（15-20 FPS）
- 内存管理优化
- 电池使用优化

#### 5.2 兼容性测试
- 不同华为机型测试
- 无GMS环境验证
- HMS Core集成（如需要）

## 开发环境设置

### 必需工具
1. Android Studio Arctic Fox或更新版本
2. Android SDK API 24+
3. 华为测试设备（无GMS）

### 依赖下载
```bash
# MediaPipe模型文件
wget https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task

# 重命名并移动到assets目录
mv face_landmarker.task app/src/main/assets/face_landmarker_v2_with_blendshapes.task
```

## 测试计划

### 功能测试
1. 摄像头预览正常显示
2. 人脸检测准确性
3. 视线方向判断精度
4. 按钮选择响应
5. MQTT数据传输

### 性能测试
1. 帧率稳定性
2. CPU使用率
3. 内存占用
4. 电池消耗

### 兼容性测试
1. 华为P系列设备
2. 华为Mate系列设备
3. 华为Nova系列设备
4. 不同Android版本

## 预期挑战和解决方案

### 挑战1：MediaPipe在华为设备上的性能
**解决方案：**
- 降低模型精度换取性能
- 优化检测频率
- 使用华为NPU加速（如可用）

### 挑战2：视线追踪精度
**解决方案：**
- 校准算法
- 多帧平滑处理
- 用户个性化调整

### 挑战3：无GMS环境限制
**解决方案：**
- 使用HMS Core替代
- 纯本地处理
- 自建网络通信

## 成功标准

### MVP最低要求
1. 能在华为设备上启动并运行
2. 检测到人脸并显示特征点
3. 基本的"是/否"选择功能
4. 通过MQTT发送状态数据

### 理想目标
1. 视线追踪精度>80%
2. 响应延迟<500ms
3. 连续运行稳定性>30分钟
4. 支持多种华为设备型号

## 时间估算

- **总开发时间：5-7天**
- 阶段2：2天
- 阶段3：1天
- 阶段4：1天
- 阶段5：1天
- 测试和调优：1-2天

## 下一步行动

1. **立即开始：** 下载MediaPipe模型文件
2. **今天完成：** 实现基础视线算法
3. **明天完成：** UI反馈和MQTT通信
4. **后天完成：** 华为设备测试和优化

---

**注意：** 这是一个MVP版本，重点是验证可行性。后续可以基于反馈进行功能扩展和性能优化。