#!/bin/bash

# FlowEye MVP 构建和测试脚本
# 用于华为无GMS设备的视线检测MVP

set -e  # 遇到错误时退出

echo "🚀 FlowEye MVP - 华为设备视线检测"
echo "=================================="

# 检查必要工具
echo "📋 检查开发环境..."

if ! command -v ./gradlew &> /dev/null; then
    echo "❌ Gradle wrapper未找到"
    exit 1
fi

if ! command -v adb &> /dev/null; then
    echo "⚠️  ADB未找到，无法自动安装到设备"
    ADB_AVAILABLE=false
else
    ADB_AVAILABLE=true
fi

# 检查模型文件
MODEL_FILE="app/src/main/assets/face_landmarker.task"
if [ ! -f "$MODEL_FILE" ]; then
    echo "📥 MediaPipe模型文件未找到，正在下载..."
    ./download_model.sh
    if [ ! -f "$MODEL_FILE" ]; then
        echo "❌ 模型文件下载失败，请手动下载"
        exit 1
    fi
else
    echo "✅ MediaPipe模型文件已存在"
fi

# 清理项目
echo "🧹 清理项目..."
./gradlew clean

# 构建项目
echo "🔨 构建项目..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ 构建成功!"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    echo "📱 APK位置: $APK_PATH"
else
    echo "❌ 构建失败"
    exit 1
fi

# 检查连接的设备
if [ "$ADB_AVAILABLE" = true ]; then
    echo "📱 检查连接的设备..."
    DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    
    if [ "$DEVICES" -eq 0 ]; then
        echo "⚠️  未检测到连接的设备"
        echo "请连接华为设备并启用USB调试"
    elif [ "$DEVICES" -eq 1 ]; then
        echo "✅ 检测到1个设备"
        
        # 获取设备信息
        DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null || echo "未知")
        DEVICE_BRAND=$(adb shell getprop ro.product.brand 2>/dev/null || echo "未知")
        ANDROID_VERSION=$(adb shell getprop ro.build.version.release 2>/dev/null || echo "未知")
        
        echo "设备信息:"
        echo "  品牌: $DEVICE_BRAND"
        echo "  型号: $DEVICE_MODEL"
        echo "  Android版本: $ANDROID_VERSION"
        
        # 检查是否为华为设备
        if [[ "$DEVICE_BRAND" == *"HUAWEI"* ]] || [[ "$DEVICE_BRAND" == *"HONOR"* ]]; then
            echo "✅ 检测到华为/荣耀设备，适合测试"
        else
            echo "⚠️  非华为设备，可能无法完全验证兼容性"
        fi
        
        # 安装APK
        echo "📲 安装应用到设备..."
        adb install -r "$APK_PATH"
        
        if [ $? -eq 0 ]; then
            echo "✅ 安装成功!"
            echo "🎯 请在设备上启动 'FlowEye MVP' 应用进行测试"
        else
            echo "❌ 安装失败"
        fi
        
    else
        echo "⚠️  检测到多个设备($DEVICES个)，请只连接一个设备"
    fi
fi

echo ""
echo "📋 测试指南:"
echo "============"
echo "1. 启动应用并授予摄像头权限"
echo "2. 将手机正对面部，距离30-50cm"
echo "3. 观察状态文本显示的检测结果"
echo "4. 尝试看向'是'和'否'按钮"
echo "5. 检查按钮是否正确高亮显示"
echo ""
echo "🔍 故障排除:"
echo "============"
echo "- 如果MediaPipe初始化失败，检查模型文件是否完整"
echo "- 如果摄像头无法启动，检查权限设置"
echo "- 如果检测不稳定，改善光照条件"
echo "- 查看logcat获取详细错误信息: adb logcat | grep FloweEyeMVP"
echo ""
echo "🎉 构建完成!"