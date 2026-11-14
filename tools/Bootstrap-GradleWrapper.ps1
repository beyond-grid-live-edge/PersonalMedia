# Compile this repository's custom Java Gradle launcher into a local ignored JAR.
[CmdletBinding()]
param([switch]$Force)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$src = Join-Path $root 'gradle\bootstrap-src\org\gradle\wrapper\GradleWrapperMain.java'
$dest = Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'
if (-not (Test-Path -LiteralPath $src -PathType Leaf)) { throw "Missing launcher source: $src" }
if ((Test-Path -LiteralPath $dest -PathType Leaf) -and -not $Force) {
    Write-Host "Existing wrapper JAR kept: $dest"
    exit 0
}
$javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javac.exe' } else { 'javac.exe' }
$jar = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jar.exe' } else { 'jar.exe' }
$tmp = Join-Path ([IO.Path]::GetTempPath()) ('pm-wrapper-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $tmp 'classes'
$packed = Join-Path $tmp 'gradle-wrapper.jar'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
try {
    & $javac '-encoding' 'UTF-8' '-d' $classes $src
    if ($LASTEXITCODE -ne 0) { throw 'javac failed. Check JDK 17+ and JAVA_HOME.' }
    & $jar 'cf' $packed '-C' $classes '.'
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $packed)) { throw 'JDK jar packaging failed.' }
    New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
    Move-Item -LiteralPath $packed -Destination $dest -Force
    Write-Host "Generated local wrapper: $dest" -ForegroundColor Green
} finally {
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force -Recurse }
}
