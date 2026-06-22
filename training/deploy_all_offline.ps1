# Deploy both offline models: Sherpa ASR (ears) + Qwen LLM (brain)
# Usage: .\deploy_all_offline.ps1

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot

Write-Host ""
Write-Host "=== Deploy All Offline Models ===" -ForegroundColor Cyan
Write-Host ""

& (Join-Path $here "deploy_sherpa_asr.ps1")
Write-Host ""
& (Join-Path $here "deploy_qwen_tonbo.ps1")

Write-Host ""
Write-Host "All done! Reinstall App and test offline voice." -ForegroundColor Green
