#!/bin/bash

# MediaPipe Face Landmarker模型下载脚本
# 用于华为无GMS设备的视线检测MVP

echo "正在下载MediaPipe Face Landmarker模型..."

# 创建assets目录
mkdir -p app/src/main/assets

# 模型文件URL
MODEL_URL="https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
MODEL_FILE="app/src/main/assets/face_landmarker.task"

# 检查是否已存在
if [ -f "$MODEL_FILE" ]; then
    echo "模型文件已存在: $MODEL_FILE"
    echo "文件大小: $(du -h "$MODEL_FILE" | cut -f1)"
    read -p "是否重新下载? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "跳过下载"
        exit 0
    fi
fi

# 下载模型文件
echo "正在从Google Storage下载模型文件..."
echo "URL: $MODEL_URL"

if command -v curl &> /dev/null; then
    curl -L -o "$MODEL_FILE" "$MODEL_URL"
elif command -v wget &> /dev/null; then
    wget -O "$MODEL_FILE" "$MODEL_URL"
else
    echo "错误: 需要curl或wget来下载文件"
    echo "请手动下载模型文件:"
    echo "1. 访问: $MODEL_URL"
    echo "2. 保存为: $MODEL_FILE"
    exit 1
fi

# 验证下载
if [ -f "$MODEL_FILE" ]; then
    FILE_SIZE=$(du -h "$MODEL_FILE" | cut -f1)
    echo "✅ 下载完成!"
    echo "文件位置: $MODEL_FILE"
    echo "文件大小: $FILE_SIZE"
    
    # 检查文件大小（应该约为100MB）
    FILE_SIZE_BYTES=$(stat -f%z "$MODEL_FILE" 2>/dev/null || stat -c%s "$MODEL_FILE" 2>/dev/null)
    if [ "$FILE_SIZE_BYTES" -lt 50000000 ]; then
        echo "⚠️  警告: 文件大小异常，可能下载不完整"
        echo "预期大小: ~100MB，实际大小: $FILE_SIZE"
    fi
else
    echo "❌ 下载失败"
    echo "请检查网络连接或手动下载模型文件"
    exit 1
fi

echo ""
echo "📱 下一步:"
echo "1. 构建项目: ./gradlew build"
echo "2. 安装到设备: ./gradlew installDebug"
echo "3. 在华为设备上测试视线检测功能"