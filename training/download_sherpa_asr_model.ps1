# Download Sherpa-ONNX trilingual streaming ASR model (Cantonese + Mandarin + English)
# Usage: .\download_sherpa_asr_model.ps1

$ErrorActionPreference = "Stop"

$MODEL_NAME = "sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en"
$ARCHIVE = "$MODEL_NAME.tar.bz2"
$URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ARCHIVE"
$OUT_DIR = Join-Path $PSScriptRoot "output\sherpa_asr"

Write-Host ""
Write-Host "=== Download Sherpa-ONNX ASR Model ===" -ForegroundColor Cyan
Write-Host "Model: $MODEL_NAME" -ForegroundColor Gray
Write-Host "Supports: Cantonese, Mandarin, English (streaming)" -ForegroundColor Gray
Write-Host "URL: $URL" -ForegroundColor Gray
Write-Host ""

New-Item -ItemType Directory -Force -Path $OUT_DIR | Out-Null
$archivePath = Join-Path $OUT_DIR $ARCHIVE
$modelDir = Join-Path $OUT_DIR $MODEL_NAME

if ((Test-Path (Join-Path $modelDir "encoder.int8.onnx")) -and (Test-Path (Join-Path $modelDir "decoder.int8.onnx"))) {
    Write-Host "Model already extracted: $modelDir" -ForegroundColor Green
    exit 0
}

if (-not (Test-Path $archivePath)) {
    Write-Host "Downloading (~250MB compressed)..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $URL -OutFile $archivePath -UseBasicParsing
}

Write-Host "Extracting..." -ForegroundColor Cyan
if (Test-Path $modelDir) {
    Remove-Item -Recurse -Force $modelDir
}
tar -xjf $archivePath -C $OUT_DIR

$encoder = Join-Path $modelDir "encoder.int8.onnx"
$decoder = Join-Path $modelDir "decoder.int8.onnx"
$tokens = Join-Path $modelDir "tokens.txt"

if (-not (Test-Path $encoder)) {
    Write-Host "encoder.int8.onnx not found after extract" -ForegroundColor Red
    exit 1
}

$sizeMB = [math]::Round(((Get-Item $encoder).Length + (Get-Item $decoder).Length) / 1MB, 1)
Write-Host ""
Write-Host "Done!" -ForegroundColor Green
Write-Host "Model dir: $modelDir" -ForegroundColor Gray
Write-Host "int8 onnx total: ~$sizeMB MB" -ForegroundColor Gray
Write-Host ""
Write-Host "Next: .\deploy_sherpa_asr.ps1" -ForegroundColor Yellow
