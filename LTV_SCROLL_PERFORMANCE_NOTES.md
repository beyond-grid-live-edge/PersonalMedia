# PersonalMedia Android — Live TV scroll-responsiveness patch

## Baseline

Apply after **T405 Saved Channels + Sky Guide** and after the official MPV binaries have been installed in your existing Android Studio project. This is a source-only overlay; it does not overwrite `jniLibs`, saved channels, application ID, signing configuration, Stremio or the tuner protocol.

The live T405 picture/audio already works. This patch targets Android scrolling and input responsiveness during active playback, not decoding compatibility.

## What changed

1. `LiveTvScreen.kt`: Status polling is scheduled every 1.5 s rather than 850 ms; the JNI status call and large channel/EPG JSON parse/merge/persistence run on `Dispatchers.IO` rather than the Compose UI thread. Status refreshes cannot overlap. Full channel-list refreshes are every 10 s normally, 4 s with the TV guide visible, or 2.5 s during a scan. UI state updates only when the saved list actually changes. Filtered channel rows, selected-channel lookup and multiplex counting are cached across telemetry updates.
2. `mpv_surface.cpp`: Uses libmpv's `mpv_render_context_set_update_callback` + `mpv_render_context_update` to redraw **when MPV requests a new frame** instead of forcing an EGL swap on every 16 ms timer tick. It remains native MPV, with software codec decoding unchanged. No T405 transport or audio-PID modifications.

## Installation

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_T405_Smooth_Scrolling_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug --stacktrace
```

Update the same installed app with `app\build\outputs\apk\debug\app-debug.apk`. Do not uninstall or clear app data; preserving the signing key allows saved channels to survive.

## Real-device checks

- Open **5USA** or another DVB-T channel; verify picture and sound continue normally.
- Swipe vertically through the Live TV screen and channel list while the video is running. Compare with the previous APK.
- Open the Sky-style guide; test horizontal and vertical scrolling, channel selection and Now/Next updates.
- Pause and resume MPV and test channel switching and fullscreen.
- Close and reopen PersonalMedia; verify the channel lineup remains.
- If lag remains, compare responsiveness while Live TV is paused versus playing. Smoothness changes may be affected by the phone's software MPEG-2 decoding load or system compositor; this patch does not switch to MediaCodec/hardware decoding.

## Verification boundaries

- Eight native C++ host tests pass, including channel persistence and PMT/PID tests.
- `mpv_surface.cpp` syntax-checks with Android EGL/JNI header stubs.
- Actual Android Studio Gradle build and phone scrolling FPS/jank have **not** been validated here.

## Safe rollback

Back up the two existing files before applying the overlay (or restore them from the previous Saved Channels + Sky Guide source ZIP): `app/src/main/java/com/personalmedia/android/LiveTvScreen.kt` and `app/src/main/cpp/mpv_surface.cpp`. This patch changes no stored data schema.
