@echo off
echo === FloweEye Gradle 修复脚本 ===
echo.

echo 正在清理Gradle缓存...

REM 删除.gradle缓存目录
if exist ".gradle" (
    echo 删除 .gradle 目录...
    rmdir /s /q .gradle
)

if exist "app\.gradle" (
    echo 删除 app\.gradle 目录...
    rmdir /s /q app\.gradle
)

REM 删除build目录
if exist "build" (
    echo 删除 build 目录...
    rmdir /s /q build
)

if exist "app\build" (
    echo 删除 app\build 目录...
    rmdir /s /q app\build
)

REM 删除.idea缓存（如果存在）
if exist ".idea\caches" (
    echo 删除 .idea\caches 目录...
    rmdir /s /q .idea\caches
)

echo.
echo ✅ 缓存清理完成！
echo.
echo 下一步：
echo 1. 在Android Studio中点击 "Try Again" 或 "Sync Project with Gradle Files"
echo 2. 如果仍然失败，请检查：
echo    - JDK版本（推荐JDK 17）
echo    - 网络连接
echo    - Android SDK安装
echo.
echo 详细解决方案请查看: GRADLE_FIX_GUIDE.md
echo.
pause