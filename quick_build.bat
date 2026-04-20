@echo off
echo 快速构建Android APK...
echo 1. 检查模型文件...
if not exist "android_mvp\app\src\main\assets\face_landmarker_v2_with_blendshapes.task" (
    echo 下载模型文件...
    curl -L -o "android_mvp\app\src\main\assets\face_landmarker_v2_with_blendshapes.task" https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker_v2_with_blendshapes/float16/1/face_landmarker_v2_with_blendshapes.task
)
echo 2. 启动Android Studio...
echo 请手动打开: %cd%\android_mvp
echo 3. 点击 Build -> Build APK(s)
echo 4. 找到APK: android_mvp\app\build\outputs\apk\debug\app-debug.apk
echo 5. 通过USB或无线ADB安装到华为手机
echo 6. 在应用中设置MQTT地址: 您的电脑IP地址 1883
pause