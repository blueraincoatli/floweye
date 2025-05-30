@echo off
echo === FloweEye Android MVP Windows 设置脚本 ===
echo.

REM 创建assets目录
if not exist "app\src\main\assets" (
    mkdir app\src\main\assets
    echo ✅ 创建了assets目录
) else (
    echo ✅ assets目录已存在
)

REM 检查MediaPipe模型文件
set MODEL_FILE=app\src\main\assets\face_landmarker_v2_with_blendshapes.task
if not exist "%MODEL_FILE%" (
    echo.
    echo ⚠️  需要下载MediaPipe模型文件
    echo 请手动下载模型文件：
    echo 下载地址: https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
    echo 保存为: %MODEL_FILE%
    echo.
    echo 或者使用PowerShell下载：
    echo powershell -Command "Invoke-WebRequest -Uri 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task' -OutFile '%MODEL_FILE%'"
    echo.
) else (
    echo ✅ MediaPipe模型文件已存在
)

REM 创建必要的XML文件
if not exist "app\src\main\res\xml" (
    mkdir app\src\main\res\xml
)

if not exist "app\src\main\res\xml\backup_rules.xml" (
    echo ^<?xml version="1.0" encoding="utf-8"?^> > app\src\main\res\xml\backup_rules.xml
    echo ^<full-backup-content^> >> app\src\main\res\xml\backup_rules.xml
    echo     ^<!-- 排除敏感数据 --^> >> app\src\main\res\xml\backup_rules.xml
    echo ^</full-backup-content^> >> app\src\main\res\xml\backup_rules.xml
    echo ✅ 创建了backup_rules.xml
)

if not exist "app\src\main\res\xml\data_extraction_rules.xml" (
    echo ^<?xml version="1.0" encoding="utf-8"?^> > app\src\main\res\xml\data_extraction_rules.xml
    echo ^<data-extraction-rules^> >> app\src\main\res\xml\data_extraction_rules.xml
    echo     ^<cloud-backup^> >> app\src\main\res\xml\data_extraction_rules.xml
    echo         ^<!-- 云备份规则 --^> >> app\src\main\res\xml\data_extraction_rules.xml
    echo     ^</cloud-backup^> >> app\src\main\res\xml\data_extraction_rules.xml
    echo     ^<device-transfer^> >> app\src\main\res\xml\data_extraction_rules.xml
    echo         ^<!-- 设备传输规则 --^> >> app\src\main\res\xml\data_extraction_rules.xml
    echo     ^</device-transfer^> >> app\src\main\res\xml\data_extraction_rules.xml
    echo ^</data-extraction-rules^> >> app\src\main\res\xml\data_extraction_rules.xml
    echo ✅ 创建了data_extraction_rules.xml
)

REM 创建图标资源目录
for %%d in (hdpi mdpi xhdpi xxhdpi xxxhdpi) do (
    if not exist "app\src\main\res\mipmap-%%d" (
        mkdir app\src\main\res\mipmap-%%d
    )
)

echo.
echo ✅ Windows设置完成
echo.
echo 下一步：
echo 1. 下载MediaPipe模型文件（如果还没有）
echo 2. 在Android Studio中打开项目
echo 3. 连接华为设备进行测试
echo.
echo 开发指南: 查看 DEVELOPMENT_GUIDE.md
echo 实施计划: 查看 MVP_IMPLEMENTATION_PLAN.md
echo.
pause