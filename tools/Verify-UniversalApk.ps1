param([string]$Apk = '..\app\build\outputs\apk\debug\app-debug.apk')
$ErrorActionPreference = 'Stop'
if (-not [System.IO.Path]::IsPathRooted($Apk)) { $Apk = Join-Path $PSScriptRoot $Apk }
$Apk = (Resolve-Path $Apk).Path
Add-Type -AssemblyName System.IO.Compression
$zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
try {
    $abis = @('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')
    $bad = $false
    foreach ($abi in $abis) {
        $entry = $zip.GetEntry("lib/$abi/libpersonalmedia.so")
        if ($null -ne $entry) {
            Write-Host ('OK  ' + $abi + ' / libpersonalmedia.so (' + $entry.Length + ' bytes)')
        } else {
            Write-Host ('MISSING  ' + $abi + ' / libpersonalmedia.so')
            $bad = $true
        }
    }
    if ($bad) { throw 'This APK does not contain PersonalMedia native code for every requested ABI.' }
    Write-Host 'PASS: one Android APK includes all four PersonalMedia native ABIs.'
}
finally { $zip.Dispose() }
