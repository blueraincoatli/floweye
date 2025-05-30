# Gradle同步失败解决方案

## 🔧 常见解决方法（按优先级排序）

### 方法1：更新Gradle Wrapper版本
在Android Studio中：
1. 打开 `gradle/wrapper/gradle-wrapper.properties`
2. 确保使用兼容的Gradle版本：
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
```

### 方法2：检查JDK版本
1. 在Android Studio中：`File` → `Project Structure` → `SDK Location`
2. 确保使用 **JDK 17** 或 **JDK 11**
3. 如果没有，下载并设置正确的JDK版本

### 方法3：清理并重新同步
在Android Studio中执行：
1. `Build` → `Clean Project`
2. `File` → `Invalidate Caches and Restart`
3. 重启后点击 `Sync Project with Gradle Files`

### 方法4：检查网络连接
如果在中国大陆，可能需要配置代理：
1. 在 `gradle.properties` 中添加：
```properties
# 如果需要代理（根据实际情况配置）
systemProp.http.proxyHost=your.proxy.host
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=your.proxy.host
systemProp.https.proxyPort=8080
```

### 方法5：降级到稳定版本
如果问题持续，可以使用更稳定的版本配置。

## 🚀 快速修复脚本

创建 `fix_gradle.bat` 文件：
```batch
@echo off
echo 正在修复Gradle配置...

REM 删除.gradle缓存
if exist ".gradle" rmdir /s /q .gradle
if exist "app\.gradle" rmdir /s /q app\.gradle

REM 删除build目录
if exist "build" rmdir /s /q build
if exist "app\build" rmdir /s /q app\build

echo 缓存已清理，请在Android Studio中重新同步项目
pause
```

## 📋 检查清单

- [ ] JDK版本正确（推荐JDK 17）
- [ ] Gradle版本兼容
- [ ] 网络连接正常
- [ ] Android SDK已安装
- [ ] 清理了缓存
- [ ] 重启了Android Studio

## 🆘 如果仍然失败

请查看Android Studio的 `Build` 窗口中的具体错误信息，并：
1. 复制完整的错误日志
2. 检查是否缺少特定的SDK组件
3. 考虑使用Android Studio的 `SDK Manager` 更新组件