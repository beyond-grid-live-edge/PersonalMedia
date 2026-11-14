# PersonalMedia Android — STR-5G: torrent cancel/restart + compact MPV controls

Baseline: **STR-5F Stremio Add-on Manager**, with the real ARM64 cloud-built libtorrent/Boost/OpenSSL libraries already installed in `native-deps/installed`. This is a SOURCE-ONLY overlay; it does not include or overwrite any native dependency archives, APK signing files, Gradle definitions, package identity, TV receiver code, or the HarmonyOS project.

## Problem demonstrated on the device

1. The native MPV screen's original Column allocated video only its remaining `.weight(1f)` height. Expanding the time slider, pause/seek buttons, multiple always-visible audio chips and subtitle chips consumed most of the video area.
2. After Cancel on a torrent stream, the UI's `torrentStopping` remained true while JNI `torrentStop()` synchronously waited for libtorrent's background networking session to release, preventing another torrent selection until application restart.
3. A late completion of a cancelled preparation coroutine could clear/rewrite the UI status or close a newer torrent's session.

## Changes

- The `SurfaceView` fills the entire player. Controls are a narrow, translucent overlay over its bottom; they never resize the video itself.
- Tap the video to show/hide controls. The bar auto-hides after 4.5 seconds of ordinary playback; opening Audio or CC pauses that timeout.
- Audio and CC/subtitles now use separate pop-up menus, including embedded tracks and previously supported safe external subtitles. Pause, ±10s seek, stop and the timeline remain accessible in the compact bottom bar.
- Error diagnostics/HTTP compatibility retries appear only when playback needs attention, not permanently over the film.
- On Cancel the active attempt's generation advances immediately. Late native operations cannot modify a new attempt's preparation state or invoke its cleanup.
- `torrentStop()` signals cancellation, closes the private MPV Range listener, then **detaches** the current torrent engine. A background native reaper joins the old scheduler and shuts down the old libtorrent network session; the next torrent is not forced to wait for slow peer sockets under normal load.
- The native reaper locks the old engine during handle destruction, cannot mutate the new `active` session, and caps simultaneous cleanup threads (in extreme rapid-cancel scenarios the fourth teardown may still block to bound resource use).
- New C++ cancellation/restart host test and Kotlin attempt-ownership policy test.

## Install (existing Windows ARM64 VM, PowerShell)

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR5G_Compact_Player_Torrent_Restart_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** the existing PersonalMedia application; do not uninstall or lose local settings, accounts and channel data.

## Device acceptance checks

1. Play a verified-piece torrent with picture/sound. Tap video several times and check that the video remains nearly full-screen, while only a small bottom control bar appears. Check auto-hide, seeking, Audio and CC menus.
2. Return to Stremio and start a different torrent, then **Cancel while gathering metadata**. Confirm the spinner clears immediately and Play becomes available again without restarting the app. Start another torrent and watch for peer stats.
3. Try cancelling multiple times in sequence and changing between different source cards. A late result from the first attempt should not close the second one.
4. Play the existing HTTPS direct/bridged Stremio stream and confirm it still works. Test August T405 Freeview afterward.
5. If another torrent still fails to start, capture the *new per-card message*, native-peer stats and the build log. LibTorrent peer availability and DRM/media codec support are separate from cancellation UI.

## Verification performed here

- **14/14** native host regression tests passed, including T405 and a new 40-iteration cancel/restart API smoke test. Host tests do NOT package libtorrent or simulate an Android networking shutdown.
- Android JNI `jni_bridge.cpp` C++17 syntax check passed against host JDK JNI headers.
- Kotlin `TorrentAttemptPolicy` compiled and passed 6 logical assertions.
- Source-only ZIP contents compared against STR-5F; signing, CMake, build config, networking bridge and original HarmonyOS source are not modified.

**Not yet verified here:** complete Android Gradle compilation and real Android libtorrent teardown/restart under network activity. Those need the user’s Windows ARM64 VM and phone.
