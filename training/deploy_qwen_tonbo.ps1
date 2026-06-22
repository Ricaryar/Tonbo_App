# Deploy qwen-tonbo GGUF model to Android device
# Usage: .\deploy_qwen_tonbo.ps1

$ErrorActionPreference = "Stop"

$GGUF_SOURCE = "d:\FYP_USE\qwen-tonbo-q8_0.gguf"
$GGUF_NAME = "qwen-tonbo-q8_0.gguf"
$PACKAGE = "com.example.tonbo_app"
$DEVICE_TMP = "/data/local/tmp/llm/"
$DEVICE_EXTERNAL = "/sdcard/Android/data/$PACKAGE/files/llm/"
$DEVICE_DOWNLOAD = "/sdcard/Download/"

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $localProps = Join-Path (Split-Path $PSScriptRoot -Parent) "local.properties"
    if (Test-Path $localProps) {
        foreach ($line in Get-Content $localProps) {
            if ($line -match '^sdk\.dir=(.+)$') {
                $sdkDir = $Matches[1] -replace '\\\\', '\' -replace '\\:', ':'
                $adbPath = Join-Path $sdkDir "platform-tools\adb.exe"
                if (Test-Path $adbPath) { return $adbPath }
            }
        }
    }

    $defaultSdk = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $defaultSdk) { return $defaultSdk }

    return $null
}

Write-Host ""
Write-Host "=== Deploy Tonbo GGUF Model ===" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $GGUF_SOURCE)) {
    Write-Host "Model not found: $GGUF_SOURCE" -ForegroundColor Red
    exit 1
}

$sizeMB = [math]::Round((Get-Item $GGUF_SOURCE).Length / 1MB, 1)
Write-Host "Model: $GGUF_NAME ($sizeMB MB)" -ForegroundColor Green

$adb = Find-Adb
if (-not $adb) {
    Write-Host "adb not found. Install Android SDK platform-tools." -ForegroundColor Red
    exit 1
}
Write-Host "adb: $adb" -ForegroundColor Gray

$devices = & $adb devices | Select-String "device$"
if (-not $devices) {
    Write-Host "No Android device detected." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Pushing model (~$sizeMB MB), please wait..." -ForegroundColor Cyan

& $adb shell "mkdir -p $DEVICE_TMP"
& $adb push $GGUF_SOURCE "${DEVICE_TMP}${GGUF_NAME}"

Write-Host "Copying to app-readable paths..." -ForegroundColor Cyan
& $adb shell "mkdir -p $DEVICE_EXTERNAL"
& $adb shell "cp ${DEVICE_TMP}${GGUF_NAME} ${DEVICE_EXTERNAL}${GGUF_NAME}"
& $adb shell "cp ${DEVICE_TMP}${GGUF_NAME} ${DEVICE_DOWNLOAD}${GGUF_NAME}"
& $adb shell "run-as $PACKAGE mkdir -p files/llm"
& $adb shell "run-as $PACKAGE cp ${DEVICE_TMP}${GGUF_NAME} files/llm/${GGUF_NAME}"

Write-Host ""
Write-Host "Done!" -ForegroundColor Green
Write-Host "Primary path: ${DEVICE_EXTERNAL}${GGUF_NAME}" -ForegroundColor Gray
Write-Host "Restart the app and check Logcat for OfflineLLMClient" -ForegroundColor Yellow
