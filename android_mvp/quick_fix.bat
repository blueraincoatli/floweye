@echo off
echo === FloweEye 快速修复脚本 ===
echo.

echo 正在应用Gradle配置修复...

REM 拉取最新修复
git pull origin main

echo.
echo ✅ 修复已应用！
echo.
echo 现在请在Android Studio中：
echo 1. 点击 "Try Again" 重新同步
echo 2. 或者点击 "Sync Project with Gradle Files"
echo.
echo 如果仍有问题，请运行: fix_gradle.bat
echo.
pause