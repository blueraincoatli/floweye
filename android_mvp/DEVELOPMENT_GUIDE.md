# FloweEye Android MVP 开发指南

## 项目概述

这是一个针对华为无GMS设备的视线追踪MVP应用，使用MediaPipe实现人脸检测和视线方向判断。

## 当前实现状态

### ✅ 已完成
1. **项目结构搭建**
   - Android项目基础架构
   - 依赖配置（CameraX, MediaPipe, MQTT）
   - 权限配置

2. **基础UI界面**
   - 摄像头预览界面
   - 是/否选择按钮
   - 状态显示区域

3. **摄像头集成**
   - CameraX集成
   - 前置摄像头预览
   - 图像分析管道

4. **MediaPipe基础集成**
   - Face Landmarker初始化
   - 人脸检测基础功能

### 🚧 待实现
1. **视线方向判断算法**
   - 虹膜检测
   - 视线向量计算
   - 按钮区域映射

2. **MQTT通信**
   - 连接配置
   - 状态数据发送

3. **性能优化**
   - 检测频率控制
   - 内存管理

## 下一步开发计划

### 第一步：下载MediaPipe模型文件
```bash
# 需要下载face_landmarker_v2_with_blendshapes.task文件
# 放置到app/src/main/assets/目录下
```

### 第二步：实现视线方向判断
在`MainActivity.kt`中的`handleFaceLandmarkerResult`方法中添加：
- 虹膜位置检测
- 视线向量计算
- 按钮区域判断逻辑

### 第三步：添加MQTT通信
创建`MqttManager.kt`类处理：
- MQTT连接管理
- 状态数据发送
- 连接状态监控

### 第四步：华为设备测试
- 在华为无GMS设备上测试
- 性能调优
- 兼容性验证

## 技术要点

### MediaPipe Face Landmarker
- 使用468个面部特征点
- 重点关注眼部区域特征点
- 实时检测性能优化

### 视线追踪算法
- 基于虹膜中心位置
- 考虑头部姿态影响
- 屏幕坐标系转换

### 华为设备兼容性
- 无GMS环境适配
- HMS Core替代方案
- 性能优化考虑

## 构建和运行

1. 确保Android Studio已安装
2. 下载MediaPipe模型文件到assets目录
3. 连接华为设备（开启开发者模式）
4. 运行项目

## 调试建议

1. 使用Logcat查看详细日志
2. 监控MediaPipe初始化状态
3. 检查摄像头权限授予情况
4. 观察人脸检测结果

## 已知问题

1. MediaPipe模型文件需要手动下载
2. 视线方向判断算法待实现
3. MQTT通信模块待开发

## 联系信息

如有问题请查看项目文档或提交Issue。