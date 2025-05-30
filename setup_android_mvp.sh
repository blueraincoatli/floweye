#!/bin/bash

# FlowEye Android MVP 项目创建脚本
# 在您的项目根目录下运行此脚本

echo "🚀 创建 FlowEye Android MVP 项目..."

# 检查是否在正确的目录
if [ ! -d "android_mvp" ]; then
    echo "❌ 请确保您在包含 android_mvp 文件夹的项目根目录下运行此脚本"
    exit 1
fi

cd android_mvp

echo "📁 创建目录结构..."
mkdir -p app/src/main/java/com/floweye/mvp
mkdir -p app/src/main/res/layout
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/xml
mkdir -p app/src/main/res/mipmap-hdpi
mkdir -p app/src/main/res/mipmap-mdpi
mkdir -p app/src/main/res/mipmap-xhdpi
mkdir -p app/src/main/res/mipmap-xxhdpi
mkdir -p app/src/main/res/mipmap-xxxhdpi
mkdir -p app/src/main/assets

echo "📄 创建配置文件..."

# settings.gradle.kts
cat > settings.gradle.kts << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FlowEye MVP"
include(":app")
EOF

# build.gradle.kts (项目级)
cat > build.gradle.kts << 'EOF'
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
}
EOF

# gradle.properties
cat > gradle.properties << 'EOF'
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.nonTransitiveRClass=true
EOF

# app/build.gradle.kts
cat > app/build.gradle.kts << 'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.floweye.mvp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.floweye.mvp"
        minSdk = 24  // MediaPipe要求最低API 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // CameraX
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // MediaPipe - 使用最新版本以获得更好的华为设备兼容性
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    
    // 权限处理
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // 测试
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
EOF

# app/proguard-rules.pro
cat > app/proguard-rules.pro << 'EOF'
# Add project specific ProGuard rules here.
# MediaPipe相关规则
-keep class com.google.mediapipe.** { *; }
-keep class org.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn org.mediapipe.**

# 保持应用类
-keep class com.floweye.mvp.** { *; }
EOF

echo "📱 创建Android清单文件..."

# AndroidManifest.xml
cat > app/src/main/AndroidManifest.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 摄像头权限 -->
    <uses-permission android:name="android.permission.CAMERA" />
    
    <!-- 摄像头特性声明 -->
    <uses-feature
        android:name="android.hardware.camera"
        android:required="true" />
    <uses-feature
        android:name="android.hardware.camera.front"
        android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.Light.DarkActionBar"
        tools:targetApi="31">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
    </application>

</manifest>
EOF

echo "🎨 创建资源文件..."

# strings.xml
cat > app/src/main/res/values/strings.xml << 'EOF'
<resources>
    <string name="app_name">FlowEye MVP</string>
    <string name="camera_permission_required">需要摄像头权限才能使用视线追踪功能</string>
    <string name="permission_denied">权限被拒绝</string>
    <string name="mediapipe_init_failed">MediaPipe初始化失败</string>
    <string name="camera_init_failed">摄像头初始化失败</string>
    <string name="looking_at_yes">正在看向：是</string>
    <string name="looking_at_no">正在看向：否</string>
    <string name="looking_away">未看向任何选项</string>
    <string name="face_detected">检测到人脸</string>
    <string name="no_face_detected">未检测到人脸</string>
</resources>
EOF

# activity_main.xml
cat > app/src/main/res/layout/activity_main.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000"
    tools:context=".MainActivity">

    <!-- 摄像头预览 -->
    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintBottom_toTopOf="@+id/controlPanel"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- 控制面板 -->
    <LinearLayout
        android:id="@+id/controlPanel"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="#80000000"
        android:orientation="vertical"
        android:padding="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent">

        <!-- 状态显示 -->
        <TextView
            android:id="@+id/statusText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="初始化中..."
            android:textColor="#FFFFFF"
            android:textSize="16sp"
            android:gravity="center" />

        <!-- 检测结果显示 -->
        <TextView
            android:id="@+id/detectionText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="等待检测..."
            android:textColor="#FFFF00"
            android:textSize="14sp"
            android:gravity="center" />

        <!-- 调试信息显示 -->
        <TextView
            android:id="@+id/debugText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text=""
            android:textColor="#CCCCCC"
            android:textSize="12sp"
            android:gravity="center" />

        <!-- 选择区域 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="horizontal">

            <Button
                android:id="@+id/yesButton"
                android:layout_width="0dp"
                android:layout_height="80dp"
                android:layout_weight="1"
                android:layout_marginEnd="8dp"
                android:text="是"
                android:textSize="24sp"
                android:backgroundTint="#4CAF50" />

            <Button
                android:id="@+id/noButton"
                android:layout_width="0dp"
                android:layout_height="80dp"
                android:layout_weight="1"
                android:layout_marginStart="8dp"
                android:text="否"
                android:textSize="24sp"
                android:backgroundTint="#F44336" />

        </LinearLayout>

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
EOF

echo "🔧 创建工具脚本..."

# download_model.sh
cat > download_model.sh << 'EOF'
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
EOF

chmod +x download_model.sh

echo "📚 创建文档..."

# README.md
cat > README.md << 'EOF'
# FlowEye MVP - 华为设备视线检测

## 📱 项目概述

这是一个专为华为手机（无GMS环境）设计的视线检测MVP应用，能够检测用户是否看向屏幕上的"是"/"否"区域。

## 🚀 快速开始

### 1. 下载MediaPipe模型
```bash
./download_model.sh
```

### 2. 构建项目
```bash
./gradlew assembleDebug
```

### 3. 安装到华为设备
```bash
./gradlew installDebug
```

## 📱 使用说明

1. 打开FlowEye MVP应用
2. 授予摄像头权限
3. 将手机正对面部，距离30-50cm
4. 观察按钮的高亮变化，表示检测到的视线方向

## 🔧 技术特性

- ✅ 基于MediaPipe Face Landmarker的人脸检测
- ✅ 实时视线方向判断
- ✅ 华为无GMS环境兼容
- ✅ CPU Delegate确保设备兼容性
- ✅ 实时视觉反馈

## 🐛 故障排除

### MediaPipe初始化失败
- 确认模型文件已下载到 `app/src/main/assets/face_landmarker.task`
- 检查设备存储空间是否充足

### 摄像头权限问题
- 确保已授予摄像头权限
- 重启应用重新请求权限

### 检测不稳定
- 改善光照条件
- 调整手机距离（30-50cm）
- 确保面部完全在摄像头视野内

## 📞 调试

查看应用日志：
```bash
adb logcat | grep FloweEyeMVP
```

---

**注意**: 这是一个MVP版本，主要用于验证技术可行性。
EOF

echo ""
echo "✅ 基础项目结构创建完成!"
echo ""
echo "📋 下一步："
echo "1. 我将为您提供 MainActivity.kt 和 GazeDetector.kt 的完整源代码"
echo "2. 创建这两个文件后，您就可以构建和测试项目了"
echo ""
echo "📁 项目结构："
find . -type f -name "*.kt" -o -name "*.xml" -o -name "*.sh" -o -name "*.md" -o -name "*.gradle*" | sort
EOF

chmod +x setup_android_mvp.sh

echo "✅ 创建脚本已生成！"
echo ""
echo "📋 请按以下步骤操作："
echo "1. 将此脚本复制到您的项目根目录（包含android_mvp文件夹的目录）"
echo "2. 运行: chmod +x setup_android_mvp.sh"
echo "3. 运行: ./setup_android_mvp.sh"
echo "4. 完成后告诉我，我会提供Kotlin源代码文件"