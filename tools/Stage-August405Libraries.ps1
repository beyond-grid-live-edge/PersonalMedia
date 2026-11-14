# PersonalMedia developer-only lab; not part of normal build.
# Use the original APK you own. This does not port Geniatech's 32-bit code
# or make it callable from the arm64-v8a PersonalMedia process.
param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [switch]$PackageForLocal32BitTest
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$apkPath = (Resolve-Path $Apk).Path
$staging = Join-Path $project 'vendor-lab\august405\armeabi-v7a'
New-Item -ItemType Directory -Path $staging -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression
$archive = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    $entries = @($archive.Entries | Where-Object {
        $_.FullName -like 'lib/armeabi-v7a/*.so' -and $_.Name -like '*.so'
    })
    if ($entries.Count -eq 0) { throw 'The supplied APK contains no armeabi-v7a native libraries.' }
    foreach ($entry in $entries) {
        $file = Join-Path $staging $entry.Name
        $inputStream = $entry.Open()
        try {
            $outputStream = [System.IO.File]::Create($file)
            try { $inputStream.CopyTo($outputStream) }
            finally { $outputStream.Dispose() }
        }
        finally { $inputStream.Dispose() }
        $checksum = (Get-FileHash -Algorithm SHA256 -Path $file).Hash
        Write-Host ($entry.Name + '  ' + $checksum)
    }
}
finally { $archive.Dispose() }
Write-Host ('Staged ' + $entries.Count + ' libraries at ' + $staging)
Write-Warning 'These are 32-bit binaries. Staging does NOT load or link them.'
Write-Warning 'libsmscontrollite.so depends on private libcutils.so, which can prevent loading on modern Android.'
if ($PackageForLocal32BitTest) {
    $target = Join-Path $project 'app\src\main\jniLibs\armeabi-v7a'
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Copy-Item -Path (Join-Path $staging '*.so') -Destination $target -Force
    Write-Warning ('LOCAL EXPERIMENT: copied proprietary libraries to ' + $target)
    Write-Warning 'Packaging is NOT integration. PersonalMedia does not invoke these APIs.'
    Write-Warning 'Remove this directory before distributing the APK. Only use with permission to bundle the libraries.'
}
