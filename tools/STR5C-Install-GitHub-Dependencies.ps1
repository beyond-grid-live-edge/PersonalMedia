# Import GitHub Actions libtorrent dependencies into PersonalMedia, without invoking Windows vcpkg.
# Actions downloads are outer ZIPs, each holding a signed-hash-listed inner package ZIP.
param(
    [Parameter(Mandatory = $true)][string[]]$ArtifactZip,
    [switch]$Arm64Only,
    [switch]$Build,
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$destRoot = Join-Path $project 'native-deps\installed'
New-Item -ItemType Directory -Path $destRoot -Force | Out-Null
$abiTriplets = @{
    'arm64-v8a' = 'pm-arm64-android'; 'armeabi-v7a' = 'pm-arm-neon-android';
    'x86' = 'pm-x86-android'; 'x86_64' = 'pm-x64-android'
}
# Stop before extracting if an archive attempts to escape the staging directory.
function Assert-SafeZip([string]$Archive) {
    $archiveObj = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        foreach ($entry in $archiveObj.Entries) {
            $n = $entry.FullName.Replace('\','/')
            if ($n.StartsWith('/') -or $n -match '^[A-Za-z]:' -or $n -match '(^|/)\.\.(?:/|$)' -or $n.Contains([char]0)) {
                throw "Unsafe ZIP entry rejected: $n"
            }
            # Reject Unix symlink entries (we only expect packaged regular files).
            $type = ($entry.ExternalAttributes -shr 16) -band 0xF000
            if ($type -eq 0xA000) { throw "ZIP contains a symbolic link: $n" }
        }
    } finally { $archiveObj.Dispose() }
}
function Assert-RealArchives([string]$Folder) {
    foreach ($n in @('libssl.a', 'libcrypto.a')) {
        $file = Join-Path $Folder "lib\$n"
        if (-not (Test-Path $file)) { throw "Missing required native library: $n" }
    }
    $torrent = @(Get-ChildItem (Join-Path $Folder 'lib') -Filter '*torrent*.a' -File -ErrorAction SilentlyContinue)
    if ($torrent.Count -eq 0) { throw 'Real torrent archive not found' }
    foreach ($file in @($torrent) + @(
        (Get-Item (Join-Path $Folder 'lib\libssl.a')),
        (Get-Item (Join-Path $Folder 'lib\libcrypto.a')))) {
        $stream = [IO.File]::OpenRead($file.FullName)
        try {
            $data = New-Object byte[] 8
            if ($stream.Read($data, 0, 8) -ne 8 -or
                ([Text.Encoding]::ASCII.GetString($data) -notin @("!<arch>`n", "!<thin>`n"))) {
                throw "Invalid .a static archive: $($file.Name)"
            }
        } finally { $stream.Close() }
    }
}
$tmp = Join-Path ([IO.Path]::GetTempPath()) ('PersonalMedia-STR5C-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
$seen = @{}
try {
    foreach ($path in $ArtifactZip) {
        $source = (Resolve-Path $path).Path
        Assert-SafeZip $source
        $work = Join-Path $tmp ([guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $work -Force | Out-Null
        Expand-Archive -LiteralPath $source -DestinationPath $work -Force
        $inner = @(Get-ChildItem $work -Recurse -Filter 'pm-libtorrent-*.zip' -File)
        if ($inner.Count -ne 1) { throw "Expected exactly one pm-libtorrent-ABI.zip inside GitHub Actions artifact: $source" }
        $package = $inner[0]
        if ($package.Name -notmatch '^pm-libtorrent-(arm64-v8a|armeabi-v7a|x86|x86_64)\.zip$') {
            throw "Unexpected inner archive name: $($package.Name)"
        }
        $abi = $Matches[1]
        if ($seen.ContainsKey($abi)) { throw "Duplicate ABI package: $abi" }
        $seen[$abi] = $true
        $sha = "$($package.FullName).sha256"
        if (-not (Test-Path $sha)) { throw "Missing SHA-256 file for $abi" }
        $expected = ((Get-Content $sha -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
        if ($expected -notmatch '^[a-f0-9]{64}$') { throw "Invalid SHA-256 metadata for $abi" }
        $actual = (Get-FileHash $package.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($expected -ne $actual) { throw "SHA-256 mismatch for $abi. Archive rejected." }
        Assert-SafeZip $package.FullName
        $unpacked = Join-Path $work 'unpacked'
        Expand-Archive -LiteralPath $package.FullName -DestinationPath $unpacked -Force
        $triplet = $abiTriplets[$abi]
        $prefix = Join-Path $unpacked $triplet
        if (-not (Test-Path $prefix)) { throw "Package directory mismatch: expected $triplet" }
        $manifestPath = Join-Path $prefix 'PM_STR5C_BUILD_INFO.json'
        if (-not (Test-Path $manifestPath)) { throw "Missing ABI provenance metadata: $abi" }
        $m = Get-Content $manifestPath -Raw | ConvertFrom-Json
        if ($m.artifact_schema -ne 1 -or $m.project -ne 'PersonalMedia-STR5C' -or
            $m.abi -ne $abi -or $m.triplet -ne $triplet -or
            $m.android_api -ne 26 -or $m.android_ndk -ne '27.0.12077973' -or
            $m.cxx_stl -ne 'c++_static') { throw "Incompatible Android dependency metadata: $abi" }
        if (-not (Test-Path (Join-Path $prefix 'include\libtorrent\session.hpp'))) {
            throw "libtorrent headers absent in ABI archive: $abi"
        }
        Assert-RealArchives $prefix
        $configs = @(Get-ChildItem (Join-Path $prefix 'share') -Recurse -Filter 'LibtorrentRasterbarConfig.cmake' -File -ErrorAction SilentlyContinue)
        if ($configs.Count -eq 0) { throw "libtorrent CMake target missing for $abi" }
        $destination = Join-Path $destRoot $triplet
        if (Test-Path $destination) {
            $old = "$destination.previous"
            if (Test-Path $old) { throw "Old dependency backup already exists: $old. Resolve it before importing." }
            Move-Item -LiteralPath $destination -Destination $old
            try { Move-Item -LiteralPath $prefix -Destination $destination }
            catch { Move-Item $old $destination; throw }
            Remove-Item -LiteralPath $old -Recurse -Force
        } else { Move-Item -LiteralPath $prefix -Destination $destination }
        Write-Host "Imported verified Android package: $abi ($triplet), vcpkg $($m.vcpkg_commit)" -ForegroundColor Green
    }
} finally {
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "Native dependencies installed under: $destRoot" -ForegroundColor Cyan
if ($Build) {
    $script = Join-Path $PSScriptRoot 'STR5C-Build-With-Cloud-Libraries.ps1'
    & $script -Arm64Only:$Arm64Only -SkipTests:$SkipTests
} else {
    Write-Host 'Build later with .\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only' -ForegroundColor Cyan
}
