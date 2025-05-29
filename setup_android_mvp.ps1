# FlowEye Android MVP 项目创建脚本 (PowerShell版本)
# 在您的项目根目录下运行此脚本

Write-Host "🚀 创建 FlowEye Android MVP 项目..." -ForegroundColor Green

# 检查是否在正确的目录
if (-not (Test-Path "android_mvp")) {
    Write-Host "❌ 请确保您在包含 android_mvp 文件夹的项目根目录下运行此脚本" -ForegroundColor Red
    exit 1
}

Set-Location android_mvp

Write-Host "📁 创建目录结构..." -ForegroundColor Yellow
New-Item -ItemType Directory -Path "app\src\main\java\com\floweye\mvp" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\res\layout" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\res\values" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\res\xml" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\res\mipmap-hdpi" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\res\mipmap-mdpi" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\res\mipmap-xhdpi" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\res\mipmap-xxhdpi" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\res\mipmap-xxxhdpi" -Force | Out-Null
New-Item -ItemType Directory -Path "app\src\main\assets" -Force | Out-Null

Write-Host "📄 创建配置文件..." -ForegroundColor Yellow

# settings.gradle.kts
@"
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
"@ | Out-File -FilePath "settings.gradle.kts" -Encoding UTF8

# build.gradle.kts (项目级)
@"
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
}
"@ | Out-File -FilePath "build.gradle.kts" -Encoding UTF8

# gradle.properties
@"
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.nonTransitiveRClass=true
"@ | Out-File -FilePath "gradle.properties" -Encoding UTF8

# app/build.gradle.kts
@"
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
"@ | Out-File -FilePath "app\build.gradle.kts" -Encoding UTF8

# app/proguard-rules.pro
@"
# Add project specific ProGuard rules here.
# MediaPipe相关规则
-keep class com.google.mediapipe.** { *; }
-keep class org.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn org.mediapipe.**

# 保持应用类
-keep class com.floweye.mvp.** { *; }
"@ | Out-File -FilePath "app\proguard-rules.pro" -Encoding UTF8

Write-Host "📱 创建Android清单文件..." -ForegroundColor Yellow

# AndroidManifest.xml
@"
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
"@ | Out-File -FilePath "app\src\main\AndroidManifest.xml" -Encoding UTF8

Write-Host "🎨 创建资源文件..." -ForegroundColor Yellow

# strings.xml
@"
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
"@ | Out-File -FilePath "app\src\main\res\values\strings.xml" -Encoding UTF8

# activity_main.xml
@"
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
"@ | Out-File -FilePath "app\src\main\res\layout\activity_main.xml" -Encoding UTF8

Write-Host "🔧 创建下载脚本..." -ForegroundColor Yellow

# download_model.ps1 (PowerShell版本)
@"
# MediaPipe Face Landmarker模型下载脚本 (PowerShell版本)
Write-Host "正在下载MediaPipe Face Landmarker模型..." -ForegroundColor Green

# 创建assets目录
New-Item -ItemType Directory -Path "app\src\main\assets" -Force | Out-Null

# 模型文件URL和路径
`$MODEL_URL = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
`$MODEL_FILE = "app\src\main\assets\face_landmarker.task"

# 检查是否已存在
if (Test-Path `$MODEL_FILE) {
    `$fileSize = (Get-Item `$MODEL_FILE).Length / 1MB
    Write-Host "模型文件已存在: `$MODEL_FILE" -ForegroundColor Yellow
    Write-Host "文件大小: $([math]::Round(`$fileSize, 2)) MB" -ForegroundColor Yellow
    `$response = Read-Host "是否重新下载? (y/N)"
    if (`$response -ne "y" -and `$response -ne "Y") {
        Write-Host "跳过下载" -ForegroundColor Yellow
        exit 0
    }
}

# 下载模型文件
Write-Host "正在从Google Storage下载模型文件..." -ForegroundColor Yellow
Write-Host "URL: `$MODEL_URL" -ForegroundColor Cyan

try {
    Invoke-WebRequest -Uri `$MODEL_URL -OutFile `$MODEL_FILE -UseBasicParsing
    
    if (Test-Path `$MODEL_FILE) {
        `$fileSize = (Get-Item `$MODEL_FILE).Length / 1MB
        Write-Host "✅ 下载完成!" -ForegroundColor Green
        Write-Host "文件位置: `$MODEL_FILE" -ForegroundColor Green
        Write-Host "文件大小: $([math]::Round(`$fileSize, 2)) MB" -ForegroundColor Green
        
        # 检查文件大小（应该约为100MB）
        if (`$fileSize -lt 50) {
            Write-Host "⚠️  警告: 文件大小异常，可能下载不完整" -ForegroundColor Red
            Write-Host "预期大小: ~100MB，实际大小: $([math]::Round(`$fileSize, 2)) MB" -ForegroundColor Red
        }
    } else {
        Write-Host "❌ 下载失败" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ 下载失败: `$_" -ForegroundColor Red
    Write-Host "请检查网络连接或手动下载模型文件" -ForegroundColor Red
    Write-Host "手动下载地址: `$MODEL_URL" -ForegroundColor Cyan
    Write-Host "保存位置: `$MODEL_FILE" -ForegroundColor Cyan
    exit 1
}

Write-Host ""
Write-Host "📱 下一步:" -ForegroundColor Cyan
Write-Host "1. 构建项目: .\gradlew.bat assembleDebug" -ForegroundColor White
Write-Host "2. 安装到设备: .\gradlew.bat installDebug" -ForegroundColor White
Write-Host "3. 在华为设备上测试视线检测功能" -ForegroundColor White
"@ | Out-File -FilePath "download_model.ps1" -Encoding UTF8

Write-Host "📚 创建文档..." -ForegroundColor Yellow

# README.md
@"
# FlowEye MVP - 华为设备视线检测

## 📱 项目概述

这是一个专为华为手机（无GMS环境）设计的视线检测MVP应用，能够检测用户是否看向屏幕上的"是"/"否"区域。

## 🚀 快速开始

### 1. 下载MediaPipe模型
```powershell
.\download_model.ps1
```

### 2. 构建项目
```cmd
.\gradlew.bat assembleDebug
```

### 3. 安装到华为设备
```cmd
.\gradlew.bat installDebug
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
- 确认模型文件已下载到 `app\src\main\assets\face_landmarker.task`
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
```cmd
adb logcat | findstr FloweEyeMVP
```

---

**注意**: 这是一个MVP版本，主要用于验证技术可行性。
"@ | Out-File -FilePath "README.md" -Encoding UTF8

Write-Host ""
Write-Host "✅ 基础项目结构创建完成!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 下一步：" -ForegroundColor Cyan
Write-Host "1. 我将为您提供 MainActivity.kt 和 GazeDetector.kt 的完整源代码" -ForegroundColor White
Write-Host "2. 创建这两个文件后，您就可以构建和测试项目了" -ForegroundColor White
Write-Host ""
Write-Host "📁 项目结构：" -ForegroundColor Cyan
Get-ChildItem -Recurse -Include "*.kt", "*.xml", "*.ps1", "*.md", "*.gradle*" | Select-Object FullName | Sort-Object FullName