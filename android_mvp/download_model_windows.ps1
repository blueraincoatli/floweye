# FloweEye MediaPipe模型下载脚本 (Windows PowerShell)

Write-Host "=== 下载MediaPipe模型文件 ===" -ForegroundColor Green

# 创建assets目录
$assetsDir = "app\src\main\assets"
if (!(Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Path $assetsDir -Force
    Write-Host "✅ 创建了assets目录" -ForegroundColor Green
}

# 模型文件路径
$modelFile = "$assetsDir\face_landmarker_v2_with_blendshapes.task"
$downloadUrl = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"

# 检查文件是否已存在
if (Test-Path $modelFile) {
    Write-Host "✅ 模型文件已存在: $modelFile" -ForegroundColor Green
    $fileSize = (Get-Item $modelFile).Length / 1MB
    Write-Host "文件大小: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Cyan
    exit 0
}

Write-Host "📥 开始下载MediaPipe模型文件..." -ForegroundColor Yellow
Write-Host "下载地址: $downloadUrl" -ForegroundColor Cyan
Write-Host "保存位置: $modelFile" -ForegroundColor Cyan

try {
    # 使用Invoke-WebRequest下载文件
    Invoke-WebRequest -Uri $downloadUrl -OutFile $modelFile -UseBasicParsing
    
    # 验证下载
    if (Test-Path $modelFile) {
        $fileSize = (Get-Item $modelFile).Length / 1MB
        Write-Host "✅ 下载成功!" -ForegroundColor Green
        Write-Host "文件大小: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Cyan
        
        # 验证文件大小（应该约为6MB）
        if ($fileSize -gt 5 -and $fileSize -lt 10) {
            Write-Host "✅ 文件大小正常" -ForegroundColor Green
        } else {
            Write-Host "⚠️  文件大小异常，请检查下载是否完整" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ 下载失败：文件不存在" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ 下载失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host "" -ForegroundColor White
    Write-Host "请尝试手动下载：" -ForegroundColor Yellow
    Write-Host "1. 打开浏览器访问：$downloadUrl" -ForegroundColor Cyan
    Write-Host "2. 将下载的文件重命名为：face_landmarker_v2_with_blendshapes.task" -ForegroundColor Cyan
    Write-Host "3. 将文件放置到：$modelFile" -ForegroundColor Cyan
    exit 1
}

Write-Host "" -ForegroundColor White
Write-Host "🎉 设置完成！现在可以在Android Studio中打开项目了。" -ForegroundColor Green