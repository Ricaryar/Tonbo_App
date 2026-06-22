# 将 APK 拆成 <2GB 分片以便上传 GitHub LFS
param(
    [string]$ApkPath = "releases/tonbo-offline-debug.apk",
    [long]$ChunkBytes = 1800MB
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path $ApkPath)) {
    Write-Error "APK not found: $ApkPath"
}

Get-ChildItem "releases/tonbo-offline-debug.apk.part*" -ErrorAction SilentlyContinue | Remove-Item -Force

$in = [System.IO.File]::OpenRead((Join-Path $root $ApkPath))
try {
    $buffer = New-Object byte[] 8MB
    $part = 1
    $written = 0L
    $out = $null

    while ($true) {
        if ($null -eq $out -or $written -ge $ChunkBytes) {
            if ($null -ne $out) { $out.Close() }
            $partName = "releases/tonbo-offline-debug.apk.part{0:D3}" -f $part
            $out = [System.IO.File]::OpenWrite((Join-Path $root $partName))
            $written = 0L
            Write-Host "Writing $partName"
            $part++
        }

        $read = $in.Read($buffer, 0, $buffer.Length)
        if ($read -le 0) { break }

        $toWrite = [Math]::Min($read, [int]($ChunkBytes - $written))
        $out.Write($buffer, 0, $toWrite)
        $written += $toWrite

        if ($toWrite -lt $read) {
            $remaining = $read - $toWrite
            $out.Close()
            $partName = "releases/tonbo-offline-debug.apk.part{0:D3}" -f $part
            $out = [System.IO.File]::OpenWrite((Join-Path $root $partName))
            $written = 0L
            Write-Host "Writing $partName"
            $part++
            $out.Write($buffer, $toWrite, $remaining)
            $written += $remaining
        }
    }
    if ($null -ne $out) { $out.Close() }
} finally {
    $in.Close()
}

Write-Host "Split complete."
