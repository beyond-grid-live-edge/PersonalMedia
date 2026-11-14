<# LTV-4 Android APK final packaging check. Run from the project root after assembleDebug. #>
[CmdletBinding()]
param(
    [string]$Apk = '',
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if ($Build) {
    $gradlew = Join-Path $project 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew)) { throw 'gradlew.bat not found. Run this inside the existing PersonalMedia Android Studio project.' }
    & $gradlew ':app:assembleDebug' '--stacktrace'
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit code $LASTEXITCODE)" }
}
if ([string]::IsNullOrWhiteSpace($Apk)) {
    $Apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
}
$Apk = (Resolve-Path -LiteralPath $Apk).Path
$gradleConfig = Get-Content -LiteralPath (Join-Path $project 'app\build.gradle.kts') -Raw
if ($gradleConfig -notmatch 'applicationId\s*=\s*"com\.personalmedia\.android"') {
    throw 'Application ID has unexpectedly changed; refusing to claim a safe update.'
}
Add-Type -AssemblyName 'System.IO.Compression'
Add-Type -AssemblyName 'System.IO.Compression.FileSystem'
$zip = [IO.Compression.ZipFile]::OpenRead($Apk)
try {
    $problems = @()
    foreach ($abi in @('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')) {
        foreach ($name in @('libpersonalmedia.so', 'libmpv.so')) {
            $entry = $zip.GetEntry("lib/$abi/$name")
            if ($null -eq $entry -or $entry.Length -lt 1024) {
                Write-Host "MISSING / TOO SMALL: $abi/$name" -ForegroundColor Red
                $problems += "$abi/$name"
            } else {
                Write-Host ("OK  {0}/{1} ({2:N0} bytes)" -f $abi, $name, $entry.Length) -ForegroundColor Green
            }
        }
    }
    if ($problems.Count -gt 0) { throw ('Incomplete universal native playback APK: ' + ($problems -join ', ')) }
} finally { $zip.Dispose() }
$live = Get-Content -LiteralPath (Join-Path $project 'app\src\main\java\com\personalmedia\android\LiveTvScreen.kt') -Raw
if ($live -match 'TvBatteryBars|Refresh battery|T405 BATTERY') { throw 'Obsolete experimental tuner battery UI is still present.' }
$receiver = Get-Content -LiteralPath (Join-Path $project 'app\src\main\cpp\portable\native_live_tv.cpp') -Raw
if ($receiver -match 'next_battery|now\s*>=\s*next_battery') { throw 'Experimental automatic battery polling is still enabled.' }
Write-Host 'PASS: universal Android APK contains PersonalMedia + MPV for four ABIs; experimental battery UI and polling disabled.' -ForegroundColor Green
Write-Host 'NOTE: This is packaging validation, not proof of long-duration playback or Guardian recovery on the actual tuner.'
