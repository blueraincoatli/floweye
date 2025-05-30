#!/bin/bash

echo "=== FloweEye Android MVP 设置脚本 ==="

# 创建assets目录
mkdir -p app/src/main/assets

# 检查MediaPipe模型文件
MODEL_FILE="app/src/main/assets/face_landmarker_v2_with_blendshapes.task"
if [ ! -f "$MODEL_FILE" ]; then
    echo "⚠️  需要下载MediaPipe模型文件"
    echo "请从以下地址下载模型文件："
    echo "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
    echo "并重命名为: face_landmarker_v2_with_blendshapes.task"
    echo "放置到: $MODEL_FILE"
    echo ""
fi

# 检查Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  请设置ANDROID_HOME环境变量"
    echo "例如: export ANDROID_HOME=/path/to/android/sdk"
    echo ""
fi

# 创建必要的XML文件
mkdir -p app/src/main/res/xml

cat > app/src/main/res/xml/backup_rules.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <!-- 排除敏感数据 -->
</full-backup-content>
EOF

cat > app/src/main/res/xml/data_extraction_rules.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <!-- 云备份规则 -->
    </cloud-backup>
    <device-transfer>
        <!-- 设备传输规则 -->
    </device-transfer>
</data-extraction-rules>
EOF

# 创建图标资源（简单的占位符）
mkdir -p app/src/main/res/mipmap-hdpi
mkdir -p app/src/main/res/mipmap-mdpi
mkdir -p app/src/main/res/mipmap-xhdpi
mkdir -p app/src/main/res/mipmap-xxhdpi
mkdir -p app/src/main/res/mipmap-xxxhdpi

echo "✅ 基础设置完成"
echo ""
echo "下一步："
echo "1. 下载MediaPipe模型文件"
echo "2. 在Android Studio中打开项目"
echo "3. 连接华为设备进行测试"
echo ""
echo "开发指南: 查看 DEVELOPMENT_GUIDE.md"