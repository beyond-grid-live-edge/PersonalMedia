# PersonalMedia Android STR-4a — Native MPV black-screen diagnostics and compatibility retry

This is a **source-only** overlay to apply over the existing STR-4 Native MPV Direct Android Studio project. It does not replace `libmpv.so`, package/signing configuration, Android Media3, or the proven T405 Live TV pipeline.

## Why

Native MPV's `loadfile` command only accepts a request to open media; it does not guarantee a decodable video was loaded. The original STR-4 player set `started = true` once EGL and `loadfile` were initialized, even if an HTTP stream later failed. The player could then stay black with `00:00 / 00:00` and a disabled Retry button.

## Changes

- Use the **documented libmpv `mpv_wait_event()` events** to distinguish stream opening, file loaded, actual end of file and failed load. This happens in JNI status polling (off the Compose UI thread) rather than logging private provider responses.
- Show safe, actionable errors on failed loads (generic error code classification only), a loading message, a prolonged-opening warning, and buffering/no-video-track hints.
- Enable Retry when the source fails, ends, or appears stuck.
- After twelve seconds, offer an explicit **Try software video decoding** option for Android GPU/libmpv interop problems. This starts only a new Stremio MPV session with `hwdec=no` and does **not** change the Freeview decoder.
- Report loss of the EGL video surface instead of silently staying black.
- Never include stream URLs, tokens, cookies or proxy headers in the status JSON or user-facing errors.

## Install in PowerShell

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR4A_Black_Screen_Fix_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install the resulting `app\build\outputs\apk\debug\app-debug.apk` **over** PersonalMedia, without uninstalling it.

## Real-device checks

1. Play the same **Direct HTTP** source (The Killer Shrews in the screenshot). It should now show **Opening provider stream** and eventually picture, buffering, or a meaningful error.
2. If still black after 12 seconds, press **Try software video decoding**. This distinguishes Android GPU decoder interop issues from provider problems.
3. If there is an MPV loading/format error, test a *different legitimate direct HTTP source* with the app on an internet-connected Wi-Fi or mobile-data connection. The August T405's own hotspot usually does not provide internet; Android may route network traffic differently while joined to it.
4. Confirm a playing video can seek and resume and its audio/subtitle controls work.
5. Re-check Freeview playback and the Sky-style guide; Live TV's software MPEG-TS path is unchanged.

This patch does **not** bypass missing media, dead providers, DRM, unsupported codecs, HTTP authorization or Android network restrictions. It makes those failures visible and offers an optional decoder fallback. It does not claim the specific screenshot's provider link is functional.

## Local verification

- Modified native EGL/libmpv source passed GCC C++17 syntax checking against JNI and Android surface stubs.
- 4 of 4 added Kotlin playback-state tests passed using a JVM test harness.
- 11 of 11 existing native tuner regression tests passed.
- Full Android Gradle build and physical-device playback remain for your Windows VM/phone.
