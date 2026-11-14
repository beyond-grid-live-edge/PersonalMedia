<#
Stage user-provided, Android-built libmpv.so binaries for PersonalMedia.
Supply a folder with ABI subdirectories: arm64-v8a/libmpv.so etc.
This script does not download binaries, import the August 405 libraries, or
satisfy missing libmpv transitive dependencies automatically.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$SourceDir,
    [switch]$IncludeAdditionalLibraries
)
$ErrorActionPreference = 'Stop'
$base = (Resolve-Path $SourceDir).Path
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dstRoot = Join-Path $project 'app/src/main/jniLibs'
$abis = @('armeabi-v7a','arm64-v8a','x86','x86_64')
$count = 0
foreach ($abi in $abis) {
    $src = Join-Path $base $abi
    $mpv = Join-Path $src 'libmpv.so'
    if (!(Test-Path $mpv -PathType Leaf)) { Write-Host "SKIP $abi (no libmpv.so)"; continue }
    $dst = Join-Path $dstRoot $abi
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    $files = @(Get-Item $mpv)
    if ($IncludeAdditionalLibraries) {
        $files = @(Get-ChildItem $src -Filter '*.so' -File | Where-Object { $_.Name -ne 'libpersonalmedia.so' })
    }
    foreach ($file in $files) {
        Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $dst $file.Name) -Force
        Write-Host ("STAGED {0} {1} SHA256 {2}" -f $abi,$file.Name,(Get-FileHash $file.FullName -Algorithm SHA256).Hash)
    }
    $count++
}
if ($count -eq 0) { throw 'No Android libmpv.so found. Supply ABI directories containing compatible libmpv.so files.' }
Write-Host "Staged libmpv for $count ABIs. Rebuild APK and check Settings > Native MPV."
Write-Warning 'Check libmpv transitive dependencies and comply with its and FFmpeg third-party licenses before distribution.'
