# PersonalMedia T405 — Siano C++ → native MPV

This overlay preserves the T405 Siano C++ tuner, 32/64-bit universal APK layout, Guardian,
Sky-style EPG/channels, and local Media3 movie playback. It changes only the **inline Live
TV rendering backend**. When libmpv is available to the current ABI, Live TV sends the
existing local MPEG-TS HTTP stream to libmpv EGL. Fullscreen can use the existing libmpv
screen. Stremio's existing MPV-preferred fullscreen routing is unchanged.

## Important: MPV runtime binaries are NOT in this source ZIP

A JNI EGL wrapper is not itself a decoder. To use MPV, supply an ABI-matched, Android-built
`libmpv.so` **and all required native dependencies**, from a reliable build whose license
terms you can satisfy. The open-source [mpv-android](https://github.com/mpv-android/mpv-android)
project shows how to build libmpv for Android, but its app APK is not a drop-in AAR.
Do not copy Linux desktop `.so`, HarmonyOS `.so`, or the proprietary August 32-bit
libraries into the MPV folder and assume they are compatible.

Example path for a self-built Android libmpv:

```
C:\Users\WindowsVM\Downloads\my-android-mpv-libs\arm64-v8a\libmpv.so
C:\Users\WindowsVM\Downloads\my-android-mpv-libs\armeabi-v7a\libmpv.so
```

In PowerShell from `PersonalMedia`:

```powershell
.\tools\Stage-AndroidMpv.ps1 -SourceDir 'C:\Users\WindowsVM\Downloads\my-android-mpv-libs'
.\gradlew.bat :app:assembleDebug
.\tools\Verify-UniversalApk.ps1
```

Use `-IncludeAdditionalLibraries` only when the source folder contains the exact
additional `.so` dependencies for that mpv build (not unrelated app/legacy libs).
An APK can have four PersonalMedia ABIs while bundling MPV for only one or two:
other ABIs will explicitly fall back to Media3. Native MPV availability is determined
by `dlopen()` at runtime, not by assumptions about the CPU or Android device.

## Live TV verification

1. Connect to August T405 Wi-Fi, rescan if necessary, and select BBC ONE Scotland.
2. In Settings, look for `Native MPV: Available`; Live TV footer should show
   `Native Siano C++ → MPV`, not `Media3 fallback`.
3. If it still shows black, copy T405 diagnostics; evaluate elementary-PID stream,
   local HTTP delivery, queue drops, and MPV demux/codec logs.
4. `MPV EGL started` means the renderer accepted the load command, **not** that
   the first video frame or audio sample was decoded.

The source patch is safe to build without libmpv; Media3 fallback stays available.
For UK DVB-T software MPEG-2/MP2 compatibility the MPV live-viewer initially
sets `hwdec=no`; after getting a picture and sound, hardware-decoding options can
be enabled where supported.

## What is not yet complete

This does not repair missing Siano control replies or battery parsing; those are
separate receiver tasks. It does not fix MPEG-TS packet loss/drops automatically.
It does not ship Android libmpv binaries or a full local Android Gradle build.
