# PersonalMedia Android STR-7B1 — Kotlin visibility compile fix

## Cause
`LocalLibrary.mergeArtwork(...)` was public but accepted the `internal` Kotlin types `ArtworkMatch` and `ArtworkPreferences`, producing two compile errors (LocalLibrary.kt:92-93).

## Fix
Change only `@Synchronized fun mergeArtwork(...)` to `@Synchronized internal fun mergeArtwork(...)`. The caller in `MainActivity.kt` is in the same Gradle module, so it retains access. No changes to behaviour, on-device data, signing, native libraries, Gradle or manifest.

## Install over STR-7B
```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_STR7B1_Kotlin_Visibility_Fix_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install the APK over your existing app only if Gradle reports `BUILD SUCCESSFUL`; do not uninstall it.

## Verification
The access-modifier change was audited against the two compiler messages; Kotlin compiler visibility reproducer tested. Full Android Gradle compilation requires your Android SDK/build environment.
