# PersonalMedia Android — MPV playback binaries (2026-09-17)

This source-only overlay adds an installer for the **official mpv-android** universal release and improves native MPV startup diagnostics. PersonalMedia retains its existing app ID, signing and user data when you apply the overlay to the existing project.

## Why this is needed

The previous Android MPV overlay added an EGL/libmpv renderer and routing, but **did not ship `libmpv.so`**. On such builds, Live TV uses Media3 fallback and may still show a black screen for some DVB-T video/audio codecs. The APK is not made MPV-capable merely by adding the wrapper source.

**Upstream**: https://github.com/mpv-android/mpv-android/releases/tag/2026-09-17  
**Asset**: `app-default-universal-release.apk` (~129 MB compressed). The release contains ARMv7, ARM64, x86 and x86_64 native libraries. Source code: https://github.com/mpv-android/mpv-android .

mpv-android is *not an AAR/library*; this script extracts the Android native `.so` files, rather than integrating mpv-android's standalone player interface.

## One-command installation on your Windows 11 ARM64 VM

From **PowerShell** in `C:\Users\OseasyVM\Downloads\PersonalMedia` after extracting the overlay:

```powershell
.\tools\Install-OfficialMpv.ps1 -Build
```

This:

1. Downloads the official universal APK into `vendor-lab/mpv-official` (or uses a cached copy).
2. Opens the APK as a ZIP, requires `libmpv.so` for all **four** ABI targets, and verifies each native ELF's architecture header.
3. Copies `lib/<ABI>/*.so` from the APK into `app/src/main/jniLibs/<ABI>/` without overwriting `libpersonalmedia.so`.
4. Records SHA-256 and upstream origin in `vendor-lab/mpv-official/STAGING-PROVENANCE.txt`.
5. Runs `:app:assembleDebug` and verifies that MPV and PersonalMedia are present in the generated APK for all ABIs.

### If GitHub download fails in PowerShell

Open the [official release](https://github.com/mpv-android/mpv-android/releases/tag/2026-09-17), download **`app-default-universal-release.apk`**, and run:

```powershell
.\tools\Install-OfficialMpv.ps1 -ApkPath "C:\Users\OseasyVM\Downloads\app-default-universal-release.apk" -Build
```

If running PowerShell's execution policy prevents local scripts, use a **process-scoped** setting (not a machine-wide change):

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

### APK verification by itself

```powershell
.\tools\Verify-MpvApk.ps1
```

Expected: `PASS` for both `libmpv.so` and `libpersonalmedia.so` in `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`.

**Note:** Verified packaging does *not* prove a video frame or audio was decoded. To test, install the new APK on the Android phone and tune BBC ONE Scotland. In Tuner Settings, tap **Copy T405 diagnostics**, which now includes `MPV diagnostics`. A decoder-reported nonzero `video=...x...` size helps distinguish missing data from renderer issues.

## Changes and limitations

- T405 receive path remains **native Siano C++ → MPEG-TS HTTP localhost → native MPV/FFmpeg**. Media3 remains a fallback and stays the local file player.
- To avoid duplicate `libc++_shared.so` when importing the upstream APK, PersonalMedia's own C++ code is now statically linked against the Android C++ runtime using `-DANDROID_STL=c++_static`. MPV retains the upstream libraries supplied in the original APK.
- This script does *not* copy proprietary August/Elgato `.so` libraries, replace the T405 receiver, repair malformed MPEG-TS, or restore missing torrent/libtorrent sources.
- If MPV still produces a black screen, `MPV diagnostics` and T405 TS-drop/PAT/PMT counters are the next evidence to investigate. Source-only changes here cannot validate physical playback.
- **Licensing:** mpv-android's app code has its own MIT license, but mpv, FFmpeg and native dependencies can be GPL/LGPL or other licenses depending on build. If you redistribute an APK containing those binaries, obtain and comply with all relevant upstream notices and source-code obligations. Keep the release tag, SHA-256 and corresponding source references. This overlay does **not** redistribute those binary libraries.

## Offline development

If the source computer cannot reach GitHub, download the official APK elsewhere and move it into Windows Downloads. Point `-ApkPath` at that downloaded file. No network connection is needed after it is cached.
