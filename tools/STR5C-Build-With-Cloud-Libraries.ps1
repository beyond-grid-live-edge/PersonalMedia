# Build PersonalMedia using *already downloaded* GitHub Actions Android static dependencies.
# Unlike STR5B-Build-Libtorrent.ps1, this NEVER invokes vcpkg on Windows ARM64.
param([switch]$Arm64Only, [switch]$SkipTests)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$installed = Join-Path $project 'native-deps\installed'
$triplets = if ($Arm64Only) { @('pm-arm64-android') } else {
    @('pm-arm64-android','pm-arm-neon-android','pm-x86-android','pm-x64-android')
}
foreach ($triplet in $triplets) {
    $dir = Join-Path $installed $triplet
    if (-not (Test-Path (Join-Path $dir 'PM_STR5C_BUILD_INFO.json'))) {
        throw "Missing GitHub Actions package: $triplet. Import its Actions artifact first."
    }
    if (-not (Test-Path (Join-Path $dir 'include\libtorrent\session.hpp')) -or
        -not (Test-Path (Join-Path $dir 'lib\libssl.a')) -or
        -not (Test-Path (Join-Path $dir 'lib\libcrypto.a'))) {
        throw "Incomplete libtorrent/OpenSSL package for $triplet"
    }
    $torrent = @(Get-ChildItem (Join-Path $dir 'lib') -Filter '*torrent*.a' -ErrorAction SilentlyContinue)
    $config = @(Get-ChildItem (Join-Path $dir 'share') -Recurse -Filter 'LibtorrentRasterbarConfig.cmake' -File -ErrorAction SilentlyContinue)
    if ($torrent.Count -lt 1 -or $config.Count -lt 1) { throw "Missing real libtorrent archive or CMake config for $triplet" }
    $manifest = Get-Content (Join-Path $dir 'PM_STR5C_BUILD_INFO.json') -Raw | ConvertFrom-Json
    if ($manifest.triplet -ne $triplet -or $manifest.cxx_stl -ne 'c++_static' -or $manifest.android_api -ne 26) {
        throw "Incompatible GitHub-built dependency metadata: $triplet"
    }
}
Write-Host 'Cloud native dependencies present. Compiling real torrent-enabled PersonalMedia...' -ForegroundColor Cyan
$old = $env:PM_TORRENT_DEPS_ROOT
$env:PM_TORRENT_DEPS_ROOT = $installed
Push-Location $project
try {
    $args = @(':app:assembleDebug', '-PpmTorrent=true', '--stacktrace')
    if (-not $SkipTests) { $args += ':app:testDebugUnitTest' }
    if ($Arm64Only) { $args += '-PpmTorrentAbis=arm64-v8a' }
    & .\gradlew.bat @args
    if ($LASTEXITCODE -ne 0) { throw 'Gradle/NDK compilation failed. Do NOT install a stale APK.' }
    if (-not $Arm64Only) {
        & .\tools\Verify-UniversalApk.ps1
        if ($LASTEXITCODE -ne 0) { throw 'Universal APK verification failed.' }
    }
    Write-Host 'Torrent-enabled Android build completed. Check engineAvailable() and test verified-piece streaming on device.' -ForegroundColor Green
} finally {
    Pop-Location
    $env:PM_TORRENT_DEPS_ROOT = $old
}
