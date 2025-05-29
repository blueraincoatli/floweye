# 华为手机MVP实施计划

## 🎯 目标
在华为手机（无GMS环境）上实现一个可运行的视线检测MVP，能够：
1. 检测用户是否看向屏幕上的"是"/"否"区域
2. 提供基本的视觉反馈
3. 验证MediaPipe在无GMS环境下的可行性

## 📋 实施阶段

### 阶段1：技术风险验证（1-2天）
**目标**：验证MediaPipe在华为无GMS环境下是否可用

#### 1.1 创建原生Android测试项目
- [ ] 创建独立的Android Studio项目
- [ ] 集成MediaPipe Tasks Vision Android库
- [ ] 验证在华为设备上的基本运行能力

#### 1.2 MediaPipe基础功能测试
- [ ] 测试Face Landmarker初始化
- [ ] 测试基本人脸检测
- [ ] 测试Blendshapes输出
- [ ] 记录性能数据（帧率、CPU使用率）

#### 1.3 摄像头集成测试
- [ ] Camera2 API基础功能
- [ ] 图像格式转换（YUV to RGB）
- [ ] 实时预览与MediaPipe集成

**风险评估点**：
- MediaPipe库是否能正常加载
- TFLite CPU delegate性能是否可接受
- 是否存在GMS相关的运行时错误

### 阶段2：核心算法实现（2-3天）
**目标**：实现基础的视线方向判断算法

#### 2.1 数据提取模块
- [ ] 从FaceLandmarkerResult提取关键数据
- [ ] 解析头部姿态矩阵
- [ ] 提取eyeLook Blendshapes

#### 2.2 视线计算算法
- [ ] 实现Blendshape到角度的映射
- [ ] 头部姿态与眼球旋转融合
- [ ] 视线向量计算

#### 2.3 屏幕区域判断
- [ ] 定义"是"/"否"区域在相机坐标系中的位置
- [ ] 实现视线与区域的夹角计算
- [ ] 置信度评估机制

### 阶段3：MVP应用开发（2-3天）
**目标**：创建可用的MVP应用

#### 3.1 UI设计
- [ ] 极简界面：大按钮显示"是"/"否"
- [ ] 视线检测状态指示器
- [ ] 调试信息显示（可选）

#### 3.2 应用集成
- [ ] 权限管理
- [ ] 摄像头生命周期管理
- [ ] 实时结果显示

#### 3.3 基础优化
- [ ] 性能调优
- [ ] 错误处理
- [ ] 用户体验优化

### 阶段4：测试与调优（1-2天）
**目标**：确保MVP在目标设备上稳定运行

#### 4.1 功能测试
- [ ] 不同光照条件测试
- [ ] 不同头部角度测试
- [ ] 长时间运行稳定性测试

#### 4.2 性能优化
- [ ] 帧率优化
- [ ] 内存使用优化
- [ ] 电池消耗测试

## 🛠 技术栈决策

### 开发方式选择
**推荐**：原生Android开发（而非Flutter插件）
- **原因**：更直接的MediaPipe集成，更好的性能控制
- **语言**：Kotlin
- **架构**：MVVM + Repository模式

### 关键依赖
```gradle
// MediaPipe
implementation 'com.google.mediapipe:tasks-vision-android:0.10.14'

// Camera
implementation 'androidx.camera:camera-core:1.3.4'
implementation 'androidx.camera:camera-camera2:1.3.4'
implementation 'androidx.camera:camera-lifecycle:1.3.4'
implementation 'androidx.camera:camera-view:1.3.4'

// UI
implementation 'androidx.appcompat:appcompat:1.7.0'
implementation 'com.google.android.material:material:1.12.0'
```

### 华为无GMS适配策略
1. **强制使用CPU Delegate**
2. **避免CameraX，使用Camera2 API**
3. **依赖检查**：确保无GMS间接依赖

## 📊 成功标准

### MVP最低要求
- [ ] 应用能在华为设备上启动并运行
- [ ] 能检测到人脸并显示基本特征点
- [ ] 能区分用户看向"是"或"否"区域
- [ ] 帧率达到15fps以上
- [ ] 无崩溃运行30分钟以上

### 理想目标
- [ ] 帧率达到20-25fps
- [ ] 视线检测准确率>80%
- [ ] 响应延迟<500ms
- [ ] 支持不同光照条件

## 🚨 风险缓解

### 高风险项目
1. **MediaPipe兼容性**
   - 缓解：准备Google ML Kit作为备选方案
   - 测试：在多个华为设备型号上验证

2. **性能问题**
   - 缓解：降低输入分辨率，优化算法
   - 监控：实时性能指标

3. **算法精度**
   - 缓解：简化判断逻辑，增大容错范围
   - 迭代：基于测试结果调整参数

## 📅 时间线

| 阶段 | 时间 | 关键里程碑 |
|------|------|------------|
| 阶段1 | Day 1-2 | MediaPipe基础功能验证 |
| 阶段2 | Day 3-5 | 核心算法实现完成 |
| 阶段3 | Day 6-8 | MVP应用可运行 |
| 阶段4 | Day 9-10 | 测试调优完成 |

**总计**：10个工作日内完成MVP

## 🔄 下一步行动

1. **立即开始**：创建Android Studio项目并集成MediaPipe
2. **并行准备**：准备华为测试设备
3. **风险监控**：每日评估技术风险和进度