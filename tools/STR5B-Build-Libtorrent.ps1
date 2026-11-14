# STR-5B real libtorrent/Boost/OpenSSL NDK integration for PersonalMedia.
# vcpkg builds actual static libraries for Android; no HarmonyOS/Windows .so files.
# Run from anywhere in Windows PowerShell in your WindowsVM.
param(
    [string]$VcpkgRoot = "",
    [string]$NdkRoot = "",
    [switch]$VerifyOnly,
    [switch]$InstallOnly,
    [switch]$Arm64Only
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$tripletDir = (Resolve-Path (Join-Path $PSScriptRoot 'STR5B-triplets')).Path
if ([string]::IsNullOrWhiteSpace($VcpkgRoot)) {
    $VcpkgRoot = if (-not [string]::IsNullOrWhiteSpace($env:VCPKG_ROOT)) { $env:VCPKG_ROOT }
                 else { Join-Path $env:USERPROFILE 'CodeTools\vcpkg' }
}
$VcpkgRoot = [IO.Path]::GetFullPath($VcpkgRoot)

if ([string]::IsNullOrWhiteSpace($NdkRoot)) {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_NDK_HOME)) { $NdkRoot = $env:ANDROID_NDK_HOME }
    else {
        $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk\ndk'
        if (-not (Test-Path $sdk)) { throw "Android NDK not found at $sdk. Set -NdkRoot explicitly." }
        $ndks = @(Get-ChildItem $sdk -Directory | Sort-Object -Property Name -Descending)
        if ($ndks.Count -eq 0) { throw 'No side-by-side Android NDK installations found.' }
        $NdkRoot = $ndks[0].FullName
    }
}
$NdkRoot = [IO.Path]::GetFullPath($NdkRoot)
if (-not (Test-Path (Join-Path $NdkRoot 'build\cmake\android.toolchain.cmake'))) {
    throw "Invalid Android NDK: $NdkRoot"
}
$env:ANDROID_NDK_HOME = $NdkRoot
$env:ANDROID_NDK_ROOT = $NdkRoot
$env:ANDROID_NDK = $NdkRoot
$env:VCPKG_ROOT = $VcpkgRoot
$env:PM_TORRENT_DEPS_ROOT = Join-Path $VcpkgRoot 'installed'

$targets = @(
    @{Abi='armeabi-v7a'; Triplet='pm-arm-neon-android'},
    @{Abi='arm64-v8a'; Triplet='pm-arm64-android'},
    @{Abi='x86'; Triplet='pm-x86-android'},
    @{Abi='x86_64'; Triplet='pm-x64-android'}
)
if ($Arm64Only) { $targets = @($targets | Where-Object { $_.Abi -eq 'arm64-v8a' }) }
Write-Host 'STR-5B | Real libtorrent for PersonalMedia Android' -ForegroundColor Cyan
Write-Host "NDK: $NdkRoot"
Write-Host "vcpkg: $VcpkgRoot"
Write-Host "Targets: $(($targets | ForEach-Object { $_.Abi }) -join ', ')"

function Assert-AndroidLibtorrent([string]$Triplet) {
    $prefix = Join-Path $env:PM_TORRENT_DEPS_ROOT $Triplet
    if (-not (Test-Path (Join-Path $prefix 'include\libtorrent\session.hpp'))) {
        throw "Missing NDK libtorrent headers in $prefix. Real P2P is NOT enabled."
    }
    $torrentLibraries = @(Get-ChildItem (Join-Path $prefix 'lib') -Filter '*torrent*.a' -ErrorAction SilentlyContinue)
    if ($torrentLibraries.Count -eq 0) { throw "No Android static libtorrent archive in $prefix\lib" }
    foreach ($name in @('libcrypto.a','libssl.a')) {
        if (-not (Test-Path (Join-Path $prefix "lib\$name"))) {
            throw "Missing ABI-matched OpenSSL $name in $prefix\lib"
        }
    }
    $configs = @(Get-ChildItem (Join-Path $prefix 'share') -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^LibtorrentRasterbarConfig\.cmake$' })
    if ($configs.Count -eq 0) { throw "LibtorrentRasterbar CMake package config missing from $prefix\share" }
    Write-Host "OK: $Triplet native libtorrent + OpenSSL + CMake config" -ForegroundColor Green
}

if (-not $VerifyOnly) {
    $exe = Join-Path $VcpkgRoot 'vcpkg.exe'
    if (-not (Test-Path $exe)) {
        if (-not (Test-Path (Join-Path $VcpkgRoot 'bootstrap-vcpkg.bat'))) {
            if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
                throw 'Git is required to obtain the official vcpkg source. Install Git or supply -VcpkgRoot.'
            }
            $parent = Split-Path -Parent $VcpkgRoot
            if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
            if (Test-Path $VcpkgRoot) {
                throw "Directory exists but isn't an initialised vcpkg clone: $VcpkgRoot"
            }
            Write-Host 'Fetching official Microsoft vcpkg source from GitHub...' -ForegroundColor Cyan
            & git clone --depth 1 'https://github.com/microsoft/vcpkg.git' $VcpkgRoot
            if ($LASTEXITCODE -ne 0) { throw 'vcpkg git clone failed; no native dependencies were installed.' }
        }
        Write-Host 'Bootstrapping vcpkg host tool (Windows ARM64 may emulate its x64 executable)...'
        & (Join-Path $VcpkgRoot 'bootstrap-vcpkg.bat') -disableMetrics
        if ($LASTEXITCODE -ne 0) { throw 'vcpkg bootstrap failed; see first host-tool error.' }
    }
    foreach ($t in $targets) {
        Write-Host "Installing REAL Android-target libtorrent + Boost + OpenSSL: $($t.Abi)" -ForegroundColor Cyan
        # [core] skips optional iconv, Python, bindings and test tools. This is
        # libtorrent-rasterbar, NOT Rakshasa's similarly named libtorrent.
        & $exe install "libtorrent[core]:$($t.Triplet)" "--overlay-triplets=$tripletDir" --disable-metrics
        if ($LASTEXITCODE -ne 0) { throw "vcpkg native dependency build failed for $($t.Abi). Stop here; inspect the first actual compiler error." }
    }
}
foreach ($t in $targets) { Assert-AndroidLibtorrent $t.Triplet }

if (-not $InstallOnly -and -not $VerifyOnly) {
    Write-Host 'Compiling real native libtorrent into libpersonalmedia.so (strict mode)...' -ForegroundColor Cyan
    Push-Location $project
    try {
        $flags = @(':app:assembleDebug', ':app:testDebugUnitTest', '-PpmTorrent=true', '--stacktrace')
        if ($Arm64Only) { $flags += '-PpmTorrentAbis=arm64-v8a' }
        & .\gradlew.bat @flags
        if ($LASTEXITCODE -ne 0) {
            throw 'Android Gradle / NDK build failed. Do not install a stale or torrent-disabled APK.'
        }
        if (-not $Arm64Only) {
            & .\tools\Verify-UniversalApk.ps1
            if ($LASTEXITCODE -ne 0) { throw 'Native ABI packaging verification failed.' }
        }
        Write-Host 'STR-5B native build succeeded. Next verify engineAvailable() on the Android device.' -ForegroundColor Green
    } finally { Pop-Location }
} elseif ($VerifyOnly) {
    Write-Host 'Dependency verification complete. No build performed (VerifyOnly).' -ForegroundColor Cyan
} else {
    Write-Host 'Dependencies installed and checked. Run this script without -InstallOnly to build the APK.' -ForegroundColor Cyan
}
