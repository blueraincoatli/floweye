# MediaPipe 模型文件说明

## 需要的模型文件

请下载以下MediaPipe模型文件并放置在此目录中：

### face_landmarker_v2_with_blendshapes.task

这是MediaPipe Face Landmarker V5模型文件，支持：
- 478个人脸关键点检测
- 头部姿态变换矩阵
- 眼球Blendshape系数

**下载地址：**
```
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
```

**或者使用支持Blendshapes的版本：**
```
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker_v2_with_blendshapes/float16/1/face_landmarker_v2_with_blendshapes.task
```

## 安装方法

1. 下载上述模型文件
2. 将文件重命名为 `face_landmarker_v2_with_blendshapes.task`
3. 将文件放置在 `app/src/main/assets/` 目录中
4. 重新编译应用

## 文件验证

模型文件应该：
- 文件大小约为 11-13 MB
- 文件扩展名为 `.task`
- 放置在正确的assets目录中

## 华为设备注意事项

由于使用CPU delegate，模型推理速度可能稍慢，但兼容性最佳。如果需要优化性能，可以考虑：
1. 降低输入图像分辨率
2. 减少处理帧率
3. 在应用设置中提供性能调节选项
