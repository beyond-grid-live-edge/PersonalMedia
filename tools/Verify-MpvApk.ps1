<# Verify that PersonalMedia's debug APK genuinely contains native MPV for all four ABIs. #>
[CmdletBinding()]
param([string]$Apk = '')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.IO.Compression'
Add-Type -AssemblyName 'System.IO.Compression.FileSystem'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($Apk)) {
    $Apk = Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
}
$path = (Resolve-Path -LiteralPath $Apk).Path
$zip = [IO.Compression.ZipFile]::OpenRead($path)
$abis = @('armeabi-v7a','arm64-v8a','x86','x86_64')
$fail = @()
try {
    foreach ($abi in $abis) {
        $mpv = $zip.GetEntry("lib/$abi/libmpv.so")
        $personal = $zip.GetEntry("lib/$abi/libpersonalmedia.so")
        if ($null -eq $mpv -or $null -eq $personal) {
            $fail += $abi
            Write-Host "FAIL $abi (MPV or PersonalMedia missing)" -ForegroundColor Red
        } else {
            Write-Host ("PASS {0} MPV={1:N1} MB PersonalMedia={2:N1} MB" -f $abi, ($mpv.Length/1MB), ($personal.Length/1MB)) -ForegroundColor Green
        }
    }
} finally { $zip.Dispose() }
if ($fail.Count) { throw "The APK is missing libmpv.so and/or libpersonalmedia.so for $($fail -join ', ')" }
Write-Host 'PASS: MPV and PersonalMedia are packaged for all four Android ABIs.' -ForegroundColor Green
Write-Host 'Runtime loading, video and audio still need testing on the Android device.'
