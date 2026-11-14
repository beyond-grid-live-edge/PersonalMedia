# PersonalMedia Android — LTV-2, Native MPV subtitle/audio/fullscreen controls

**Baseline:** The user-tested T405 Smooth Scrolling build with working Freeview picture/audio, stored channel lineup and Sky-style guide. Apply this update *over the same Android Studio project*. The overlay intentionally does not contain or overwrite any `libmpv.so`, app signing, channel-store data or Stremio sources.

## Changes

- **Native MPV tracks:** `mpv_surface.cpp` now reads real `track-list/count` and the corresponding audio/subtitle IDs, titles and languages through the public libmpv property API. Empty lists are reported as *no detected tracks*, not as proof subtitles are supported. JNI track selection is restricted to the **Live TV** MPV session, to prevent accidental changes to Stremio playback.
- **CC controls:** Inline and fullscreen MPV playback expose **CC Auto**, **CC Off**, and individual subtitle tracks **only if detected**. Auto/Off preference survives channel changes and app restarts. A channel-specific track ID is *not* reused on a different channel because the IDs can differ.
- **Audio controls:** Select **Auto** or a detected audio track (e.g., a different broadcast language) with the MPV `aid` property. Media3 fallback remains present but it does not falsely advertise these MPV-only track controls.
- **Fullscreen:** Live TV enters fullscreen **in place** using the same Compose `AndroidView`/`SurfaceView` and the same MPV session. Unlike the previous parent-player path, fullscreen does not issue another `mpvStart` or `loadfile`. Android system bars are hidden while fullscreen and restored afterward. The native EGL renderer is nudged to redraw on surface resize without resuming 60-Hz unnecessary swaps.
- **Screen awake:** Android `FLAG_KEEP_SCREEN_ON` is active only during foreground live playback and is released on Stop/Pause or screen disposal.
- **UI:** Inline CC/Audio dropdowns and a compact fullscreen overlay with Exit, Play/Pause, Stop, CC and Audio. Other navigation and the persistent channel database are unchanged.

## Install — Windows PowerShell

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_T405_LTV2_Subtitles_Audio_Fullscreen_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Build outputs to `app\build\outputs\apk\debug\app-debug.apk`. **Install as an update; do not uninstall or clear storage**, so the saved Freeview lineup survives. Existing official MPV libraries must remain at `app/src/main/jniLibs/<ABI>/libmpv.so`; a *source-only full project* will still need them re-staged.

## Real-device LTV-2 checklist

1. Start 5USA or BBC ONE Scotland; verify both video and sound and that swiping remains smooth.
2. Open **CC**: choose **Off** and **Auto**. Only expect actual subtitle tracks to appear on programmes broadcasting supported DVB subtitles. If no tracks are detected, the menu should explicitly say so.
3. Open **Audio** and verify only real MPV-reported tracks appear; switch to a second track only if the broadcaster provides one. Test returning to **Auto**.
4. Press **Fullscreen**, then **Exit fullscreen** or the Android Back gesture. Check that picture/sound continue, without rebuffering or retuning. Test pause/resume while fullscreen.
5. Verify auto-rotation if enabled, the device stays awake while actively watching, and system bars return after exiting fullscreen.
6. Change channel, then close/reopen PersonalMedia: saved channels should still exist; CC Auto/Off preference should remain.
7. Open a local MP4 and a Stremio stream to ensure no regression to those playback paths.

## Known limitations

- The Android Gradle APK compilation and real-device CC/fullscreen behaviour **have not** been run in this workspace. Confirm by building in Android Studio and testing on the Honor 90.
- DVB subtitle support depends on the broadcast subtitle PIDs/descriptors and the compiled MPV/FFmpeg demuxer and decoder features. A visible CC button does *not* mean the selected service has captions.
- Fullscreen is in-app immersive and does not force landscape orientation; the user may rotate the phone. It does not implement Android Picture-in-Picture.
- Audio track selection changes playback only; it doesn't independently rewrite tuner PID filters. If MPV detects a track but it has no audio data, capture the diagnostic data for a receiver/PID follow-up.
- Battery and verified tuner signal strength remain separate LTV-J work; this patch does not invent values.

## Validation here

- 8/8 native Siano/DVB host regression tests passed.
- The changed JNI/EGL C++ source passed a host Clang syntax check using Android EGL/JNI stubs.
- Kotlin source was checked for parsing errors, but without the Android SDK/Compose classpath, **this is not a completed Gradle build**.
