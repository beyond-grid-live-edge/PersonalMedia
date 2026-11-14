<#
Downloads the upstream mpv-android universal release and stages its *native libraries*
inside the existing PersonalMedia Android Studio project. Run from any working dir.
No August/Elgato proprietary binaries are copied.
Windows PowerShell 5.1 compatible.

Usage:
  .\tools\Install-OfficialMpv.ps1
  .\tools\Install-OfficialMpv.ps1 -ApkPath 'C:\Downloads\app-default-universal-release.apk'
#>
[CmdletBinding()]
param(
    [string]$ApkPath = '',
    [string]$ReleaseTag = '2026-09-17',
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName 'System.IO.Compression'
Add-Type -AssemblyName 'System.IO.Compression.FileSystem'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradleFile = Join-Path $project 'app/build.gradle.kts'
if (!(Test-Path -LiteralPath $gradleFile)) {
    throw "Not a PersonalMedia Android project: $project"
}
$abiInfo = @{
    'armeabi-v7a' = @(1, 40)
    'arm64-v8a'   = @(2, 183)
    'x86'         = @(1, 3)
    'x86_64'      = @(2, 62)
}
$abis = @('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')
$official = "https://github.com/mpv-android/mpv-android/releases/download/$ReleaseTag/app-default-universal-release.apk"
$cache = Join-Path $project 'vendor-lab/mpv-official'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $apk = Join-Path $cache ("mpv-android-$ReleaseTag-universal.apk")
    if (!(Test-Path -LiteralPath $apk) -or (Get-Item $apk).Length -lt 10000000) {
        Write-Host "Downloading upstream mpv-android universal APK ($ReleaseTag). This is a large archive." -ForegroundColor Cyan
        Write-Host $official
        $temp = "$apk.partial"
        if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Force }
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $official -OutFile $temp -UseBasicParsing -MaximumRedirection 12
            if ((Get-Item $temp).Length -lt 10000000) { throw 'Downloaded file is unexpectedly small.' }
            Move-Item -LiteralPath $temp -Destination $apk -Force
        } catch {
            Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
            throw "Official mpv-android download failed: $($_.Exception.Message). Download app-default-universal-release.apk manually from $official then run this script with -ApkPath 'C:\path\to\the.apk'."
        }
    } else {
        Write-Host "Using cached APK: $apk"
    }
} else {
    $apk = (Resolve-Path -LiteralPath $ApkPath).Path
}
Write-Host "Source: $apk"
$sha = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
Write-Host "Source SHA-256: $sha"
$src = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $entries = @($src.Entries | Where-Object { $_.FullName -match '^lib/(armeabi-v7a|arm64-v8a|x86|x86_64)/[^/]+\.so$' })
    if ($entries.Count -lt 4) { throw 'No expected Android native library tree found in the APK.' }
    $missing = @()
    foreach ($abi in $abis) {
        $hits = @($entries | Where-Object { $_.FullName -eq "lib/$abi/libmpv.so" })
        if ($hits.Count -eq 0) { $missing += $abi }
    }
    if ($missing.Count) { throw "This APK does not contain libmpv.so for: $($missing -join ', '). Install aborted; use the upstream universal release." }
    $temporaryRoot = Join-Path $cache 'extraction.tmp'
    if (Test-Path $temporaryRoot) { Remove-Item -LiteralPath $temporaryRoot -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $temporaryRoot | Out-Null
    $count = 0
    foreach ($entry in $entries) {
        $parts = $entry.FullName.Split('/')
        $abi = $parts[1]; $name = $parts[2]
        $dir = Join-Path $temporaryRoot $abi
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $file = Join-Path $dir $name
        $inputStream = $entry.Open()
        $outputStream = [IO.File]::Create($file)
        try { $inputStream.CopyTo($outputStream) }
        finally { $outputStream.Dispose(); $inputStream.Dispose() }
        # An ELF header / architecture gate prevents accidental cross-ABI packaging.
        $fileStream = [IO.File]::OpenRead($file)
        $header = New-Object byte[] 20
        try { if ($fileStream.Read($header, 0, 20) -ne 20) { throw "Short ELF: $($entry.FullName)" } }
        finally { $fileStream.Dispose() }
        if ($header[0] -ne 127 -or $header[1] -ne 69 -or $header[2] -ne 76 -or $header[3] -ne 70) {
            throw "Not an ELF native library: $($entry.FullName)"
        }
        $machine = [BitConverter]::ToUInt16($header, 18)
        if ($header[4] -ne $abiInfo[$abi][0] -or $machine -ne $abiInfo[$abi][1]) {
            throw "ELF architecture mismatch at $($entry.FullName): class=$($header[4]) machine=$machine"
        }
        $count++
    }
    # Avoid duplicate libc++_shared.so ownership: PersonalMedia uses c++_static.
    $cfg = Get-Content -Raw -LiteralPath $gradleFile
    if ($cfg -notmatch 'ANDROID_STL=c\+\+_static') {
        throw 'Project must be configured with ANDROID_STL=c++_static (included in this overlay). Apply the patch first.'
    }
    $jniRoot = Join-Path $project 'app/src/main/jniLibs'
    foreach ($abi in $abis) {
        $dir = Join-Path $jniRoot $abi
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        Get-ChildItem -LiteralPath (Join-Path $temporaryRoot $abi) -Filter '*.so' -File | ForEach-Object {
            if ($_.Name -eq 'libpersonalmedia.so') { throw 'Refusing to replace PersonalMedia native code.' }
            Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $dir $_.Name) -Force
        }
        Write-Host ("STAGED {0} libmpv.so and {1} native libraries" -f $abi, @(Get-ChildItem -LiteralPath (Join-Path $temporaryRoot $abi) -Filter '*.so').Count) -ForegroundColor Green
    }
    $provenance = @(
        'PersonalMedia private MPV native-library staging record',
        "Downloaded from: $official",
        "APK file: $apk",
        "SHA-256: $sha",
        'Source: mpv-android project, mpv-player/mpv and FFmpeg',
        'Licensing: see upstream GPL/LGPL components before redistribution',
        "Staged: $count ABI-specific libraries",
        "Date: $(Get-Date -Format o)"
    )
    Set-Content -Path (Join-Path $cache 'STAGING-PROVENANCE.txt') -Value $provenance -Encoding UTF8
    # Avoid keeping two additional extracted copies of the large native library set.
    Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    Write-Host "Finished. Original Android app ID/signing unchanged." -ForegroundColor Green
    if ($Build) {
        Push-Location $project
        try {
            & (Join-Path $project 'gradlew.bat') ':app:assembleDebug' '--stacktrace'
            if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: exit $LASTEXITCODE" }
            & (Join-Path $PSScriptRoot 'Verify-MpvApk.ps1')
        } finally { Pop-Location }
    } else {
        Write-Host 'Next: .\gradlew.bat :app:assembleDebug'
        Write-Host 'Then: .\tools\Verify-MpvApk.ps1'
    }
} finally {
    $src.Dispose()
}
