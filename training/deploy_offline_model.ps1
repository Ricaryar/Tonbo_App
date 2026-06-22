# 部署自训练的 Tonbo 离线 LLM 到 Android 设备
# 用法: .\deploy_offline_model.ps1

$ErrorActionPreference = "Stop"

$ANDROID_DIR = Join-Path $PSScriptRoot "output\android"
$GGUF_FILE = "tonbo_assistant_q4.gguf"
$TASK_FILE = "gemma3_1b.task"
$DEVICE_PATH = "/data/local/tmp/llm/"

Write-Host ""
Write-Host "=== Tonbo 自训练模型部署 ===" -ForegroundColor Cyan
Write-Host ""

# 优先部署自训练的 GGUF 模型
$ggufPath = Join-Path $ANDROID_DIR $GGUF_FILE
$taskPath = Join-Path $ANDROID_DIR $TASK_FILE

if (Test-Path $ggufPath) {
    $modelPath = $ggufPath
    $modelName = $GGUF_FILE
    $modelType = "自训练 GGUF (Tonbo-Assistant-0.5B)"
} elseif (Test-Path $taskPath) {
    $modelPath = $taskPath
    $modelName = $TASK_FILE
    $modelType = "预训练基础模型 (Gemma-3 1B)"
} else {
    Write-Host "找不到模型文件!" -ForegroundColor Red
    Write-Host ""
    Write-Host "请先训练模型:" -ForegroundColor Yellow
    Write-Host "  .\run_training.ps1" -ForegroundColor Gray
    Write-Host ""
    Write-Host "或使用 Colab 训练后导出:" -ForegroundColor Yellow
    Write-Host "  python export_to_gguf.py --lora-dir output/tonbo_lora" -ForegroundColor Gray
    exit 1
}

$sizeMB = [math]::Round((Get-Item $modelPath).Length / 1MB, 1)
Write-Host "模型类型: $modelType" -ForegroundColor Green
Write-Host "模型文件: $modelPath ($sizeMB MB)" -ForegroundColor Green

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "找不到 adb，请确保 Android SDK platform-tools 在 PATH 中" -ForegroundColor Red
    exit 1
}

$devices = adb devices | Select-String "device$"
if (-not $devices) {
    Write-Host "没有检测到 Android 设备" -ForegroundColor Red
    exit 1
}

Write-Host "正在推送模型到设备..." -ForegroundColor Cyan
adb shell "mkdir -p $DEVICE_PATH"
adb push $modelPath "${DEVICE_PATH}${modelName}"

Write-Host ""
Write-Host "部署完成!" -ForegroundColor Green
Write-Host "设备路径: ${DEVICE_PATH}${modelName}" -ForegroundColor Gray
Write-Host ""
Write-Host "在 App 中测试:" -ForegroundColor Yellow
Write-Host "  1. 运行 App" -ForegroundColor Gray
Write-Host "  2. Logcat 搜索 OfflineLLMClient" -ForegroundColor Gray
Write-Host "  3. 进入语音对话功能测试" -ForegroundColor Gray
