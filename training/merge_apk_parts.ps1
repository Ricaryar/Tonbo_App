# 合并 releases/ 下的 APK 分片为完整安装包
param(
    [string]$OutputApk = "releases/tonbo-offline-debug.apk"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$parts = Get-ChildItem "releases/tonbo-offline-debug.apk.part*" | Sort-Object Name
if ($parts.Count -eq 0) {
    Write-Error "No APK parts found: releases/tonbo-offline-debug.apk.part*"
}

if (Test-Path $OutputApk) { Remove-Item $OutputApk -Force }

$out = [System.IO.File]::OpenWrite((Join-Path $root $OutputApk))
try {
    foreach ($part in $parts) {
        Write-Host "Merging $($part.Name) ..."
        $in = [System.IO.File]::OpenRead($part.FullName)
        try { $in.CopyTo($out) } finally { $in.Close() }
    }
} finally {
    $out.Close()
}

$size = (Get-Item $OutputApk).Length / 1GB
Write-Host "Done: $OutputApk ($([math]::Round($size, 2)) GB)"
