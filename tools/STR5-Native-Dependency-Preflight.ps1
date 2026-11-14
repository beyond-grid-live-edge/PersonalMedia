# Compatibility wrapper for the STR-5A preflight command.
# STR-5B uses vcpkg ABI-specific installed packages, not an in-tree libtorrent checkout.
param(
    [string]$VcpkgRoot = "",
    [string]$NdkRoot = "",
    [switch]$Assemble,
    [switch]$Arm64Only,
    [string]$BoostRoot = "", # Legacy parameter, now resolved through vcpkg.
    [string]$OpenSSLRoot = "" # Legacy parameter, now resolved through vcpkg.
)
$ErrorActionPreference = 'Stop'
if ($BoostRoot -or $OpenSSLRoot) {
    Write-Warning 'STR-5B gets ABI-matched Boost/OpenSSL from vcpkg; manual -BoostRoot/-OpenSSLRoot are not used.'
}
$script = Join-Path $PSScriptRoot 'STR5B-Build-Libtorrent.ps1'
$opts = @{ VerifyOnly = $true; Arm64Only = [bool]$Arm64Only }
if ($VcpkgRoot) { $opts.VcpkgRoot = $VcpkgRoot }
if ($NdkRoot) { $opts.NdkRoot = $NdkRoot }
& $script @opts
if ($Assemble) {
    $project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    Push-Location $project
    try {
        $args = @(':app:assembleDebug', ':app:testDebugUnitTest', '-PpmTorrent=true', '--stacktrace')
        if ($Arm64Only) { $args += '-PpmTorrentAbis=arm64-v8a' }
        & .\gradlew.bat @args
        if ($LASTEXITCODE -ne 0) { throw 'STR-5B Android NDK build failed' }
        if (-not $Arm64Only) {
            & .\tools\Verify-UniversalApk.ps1
            if ($LASTEXITCODE -ne 0) { throw 'Universal ABI verification failed' }
        }
    } finally { Pop-Location }
}
