# 创建 Android MVP 项目指南

## 📁 第一步：创建目录结构

在您的项目根目录下执行：

```bash
mkdir -p android_mvp/app/src/main/java/com/floweye/mvp
mkdir -p android_mvp/app/src/main/res/layout
mkdir -p android_mvp/app/src/main/res/values
mkdir -p android_mvp/app/src/main/res/xml
mkdir -p android_mvp/app/src/main/assets
```

## 📄 第二步：创建配置文件

### 1. `android_mvp/settings.gradle.kts`
```kotlin
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
```

### 2. `android_mvp/build.gradle.kts`
```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
}
```

### 3. `android_mvp/gradle.properties`
```properties
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

### 4. `android_mvp/app/build.gradle.kts`
```kotlin
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
```

## 📱 第三步：创建Android清单文件

### `android_mvp/app/src/main/AndroidManifest.xml`
```xml
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
```

## 🎨 第四步：创建UI资源文件

### `android_mvp/app/src/main/res/values/strings.xml`
```xml
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
```

### `android_mvp/app/src/main/res/layout/activity_main.xml`
```xml
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
```

## 💻 第五步：创建Kotlin源代码文件

这一步需要创建两个关键的Kotlin文件，内容较长，我建议您：

1. 先完成上述配置文件的创建
2. 然后告诉我，我会为您提供完整的Kotlin源代码

## 🔧 第六步：创建工具脚本

### `android_mvp/download_model.sh`
```bash
#!/bin/bash
echo "正在下载MediaPipe Face Landmarker模型..."
mkdir -p app/src/main/assets
MODEL_URL="https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
MODEL_FILE="app/src/main/assets/face_landmarker.task"

if command -v curl &> /dev/null; then
    curl -L -o "$MODEL_FILE" "$MODEL_URL"
elif command -v wget &> /dev/null; then
    wget -O "$MODEL_FILE" "$MODEL_URL"
else
    echo "错误: 需要curl或wget来下载文件"
    echo "请手动下载: $MODEL_URL"
    echo "保存为: $MODEL_FILE"
    exit 1
fi

echo "✅ 模型下载完成: $MODEL_FILE"
```

记得给脚本执行权限：
```bash
chmod +x android_mvp/download_model.sh
```

## ✅ 完成这些步骤后

请告诉我您已经创建了基础结构，我会为您提供：
1. 完整的 `MainActivity.kt` 源代码
2. 完整的 `GazeDetector.kt` 源代码
3. 构建和测试脚本

这样您就可以在华为设备上测试视线检测功能了！