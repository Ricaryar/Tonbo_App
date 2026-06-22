# Copy offline models into app assets before building APK
# Usage: .\prepare_apk_assets.ps1

$ErrorActionPreference = "Stop"

$ROOT = Split-Path $PSScriptRoot -Parent
$ASSETS = Join-Path $ROOT "app\src\main\assets"
$GGUF_SOURCE = "d:\FYP_USE\qwen-tonbo-q8_0.gguf"
$GGUF_NAME = "qwen-tonbo-q8_0.gguf"
$SHERPA_NAME = "sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en"
$SHERPA_SOURCE = Join-Path $PSScriptRoot "output\sherpa_asr\$SHERPA_NAME"

# Read overrides from local.properties
$localProps = Join-Path $ROOT "local.properties"
if (Test-Path $localProps) {
    foreach ($line in Get-Content $localProps) {
        if ($line -match '^tonbo\.gguf\.path=(.+)$') {
            $GGUF_SOURCE = $Matches[1] -replace '\\\\', '\' -replace '\\:', ':'
        }
        if ($line -match '^tonbo\.sherpa\.dir=(.+)$') {
            $SHERPA_SOURCE = $Matches[1] -replace '\\\\', '\' -replace '\\:', ':'
        }
    }
}

Write-Host ""
Write-Host "=== Prepare APK bundled models ===" -ForegroundColor Cyan

if (-not (Test-Path $GGUF_SOURCE)) {
    Write-Host "GGUF not found: $GGUF_SOURCE" -ForegroundColor Red
    Write-Host "Set tonbo.gguf.path in local.properties" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path (Join-Path $SHERPA_SOURCE "encoder.int8.onnx"))) {
    Write-Host "Sherpa ASR not found: $SHERPA_SOURCE" -ForegroundColor Red
    Write-Host "Run .\download_sherpa_asr_model.ps1 first" -ForegroundColor Yellow
    exit 1
}

$llmDir = Join-Path $ASSETS "llm"
$sherpaDir = Join-Path $ASSETS "sherpa_asr\$SHERPA_NAME"
New-Item -ItemType Directory -Force -Path $llmDir | Out-Null
New-Item -ItemType Directory -Force -Path $sherpaDir | Out-Null

Write-Host "Copying LLM..." -ForegroundColor Cyan
Copy-Item -Path $GGUF_SOURCE -Destination (Join-Path $llmDir $GGUF_NAME) -Force

Write-Host "Copying Sherpa ASR..." -ForegroundColor Cyan
Copy-Item -Path (Join-Path $SHERPA_SOURCE "encoder.int8.onnx") -Destination $sherpaDir -Force
Copy-Item -Path (Join-Path $SHERPA_SOURCE "decoder.int8.onnx") -Destination $sherpaDir -Force
Copy-Item -Path (Join-Path $SHERPA_SOURCE "tokens.txt") -Destination $sherpaDir -Force

$ggufMb = [math]::Round((Get-Item (Join-Path $llmDir $GGUF_NAME)).Length / 1MB, 1)
Write-Host ""
Write-Host "Done! Assets ready (~$ggufMb MB LLM + ~228 MB ASR)" -ForegroundColor Green
Write-Host "Next: Android Studio Build > Build APK" -ForegroundColor Yellow
Write-Host "Or: .\gradlew.bat :app:assembleDebug" -ForegroundColor Gray
