# STR-7C1 — Android Media3 TrackSelectionParameters API fix

This patch fixes the three Kotlin errors in PlayerScreen.kt lines 157, 158 and 161:
`Unresolved reference 'isTrackTypeDisabled'`.

Media3 1.5.1 exposes `TrackSelectionParameters.disabledTrackTypes` (a set of track type IDs).
The correct check is `parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)`
instead of the nonexistent `parameters.isTrackTypeDisabled(...)` call.
This is a source-level compatibility fix; no database, player policy, native library,
Gradle, application identity, or signing files are altered.

## Apply in Windows PowerShell

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_STR7C1_Media3_Track_API_Fix_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

This requires STR-7C to be applied first. Install only a freshly successful APK over the existing app.

## Scope

* Updated: `app/src/main/java/com/personalmedia/android/PlayerScreen.kt`
* Added: this note
* Audio/CC memory behaviour is unchanged
* Full Windows Gradle build and real-device test are not run in this environment
