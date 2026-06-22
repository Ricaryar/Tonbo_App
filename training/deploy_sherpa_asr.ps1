# Deploy Sherpa-ONNX ASR model to Android device
# Usage: .\deploy_sherpa_asr.ps1

$ErrorActionPreference = "Stop"

$MODEL_NAME = "sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en"
$PACKAGE = "com.example.tonbo_app"
$LOCAL_DIR = Join-Path $PSScriptRoot "output\sherpa_asr\$MODEL_NAME"
$DEVICE_DIR = "/sdcard/Android/data/$PACKAGE/files/sherpa_asr/$MODEL_NAME"
$DEVICE_TMP = "/data/local/tmp/sherpa_asr/$MODEL_NAME"

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
Write-Host "=== Deploy Sherpa-ONNX ASR ===" -ForegroundColor Cyan

if (-not (Test-Path (Join-Path $LOCAL_DIR "encoder.int8.onnx"))) {
    Write-Host "Model not found. Run .\download_sherpa_asr_model.ps1 first" -ForegroundColor Red
    exit 1
}

$adb = Find-Adb
if (-not $adb) {
    Write-Host "adb not found" -ForegroundColor Red
    exit 1
}

$devices = & $adb devices | Select-String "device$"
if (-not $devices) {
    Write-Host "No Android device connected" -ForegroundColor Red
    exit 1
}

Write-Host "Pushing ASR model files..." -ForegroundColor Cyan
& $adb shell "mkdir -p $DEVICE_TMP"
& $adb push "$LOCAL_DIR\encoder.int8.onnx" "$DEVICE_TMP/encoder.int8.onnx"
& $adb push "$LOCAL_DIR\decoder.int8.onnx" "$DEVICE_TMP/decoder.int8.onnx"
& $adb push "$LOCAL_DIR\tokens.txt" "$DEVICE_TMP/tokens.txt"

& $adb shell "mkdir -p $DEVICE_DIR"
& $adb shell "cp $DEVICE_TMP/encoder.int8.onnx $DEVICE_DIR/"
& $adb shell "cp $DEVICE_TMP/decoder.int8.onnx $DEVICE_DIR/"
& $adb shell "cp $DEVICE_TMP/tokens.txt $DEVICE_DIR/"

& $adb shell "run-as $PACKAGE mkdir -p files/sherpa_asr/$MODEL_NAME"
& $adb shell "run-as $PACKAGE cp $DEVICE_TMP/encoder.int8.onnx files/sherpa_asr/$MODEL_NAME/"
& $adb shell "run-as $PACKAGE cp $DEVICE_TMP/decoder.int8.onnx files/sherpa_asr/$MODEL_NAME/"
& $adb shell "run-as $PACKAGE cp $DEVICE_TMP/tokens.txt files/sherpa_asr/$MODEL_NAME/"

Write-Host ""
Write-Host "Done!" -ForegroundColor Green
Write-Host "Device path: $DEVICE_DIR" -ForegroundColor Gray
Write-Host ""
Write-Host "Test:" -ForegroundColor Yellow
Write-Host "  1. Reinstall App" -ForegroundColor Gray
Write-Host "  2. Logcat filter: SherpaOnnxASR" -ForegroundColor Gray
Write-Host "  3. Offline: say Cantonese commands e.g. 打開環境識別" -ForegroundColor Gray
