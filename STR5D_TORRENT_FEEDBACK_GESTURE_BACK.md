# PersonalMedia Android STR-5D — Torrent button feedback + back-swipe navigation

Base project: **STR-5C** with the successful ARM64 real-libtorrent NDK build. This is a **source-only overlay**, so preserve your existing `native-deps/installed/`, Android signing configuration, installed native `libmpv.so`, saved Stremio account and Freeview channels.

## What was wrong

- The **Stream verified pieces** button was silently disabled when P2P opt-in was unchecked or `NativeBridge.engineAvailable()` reported unavailable. There was no on-button reason.
- Once allowed, native torrent metadata can take time to arrive. The UI only showed progress while preparing, and after an error it removed useful preparation details. A silent-looking attempt might just be searching for seeders.
- Top-level Android back swipe had no handler outside video players. It could close the activity instead of going back to the previous tab. Stremio detail/section screens also had no BackHandler.

## Changes

- Torrent buttons now respond to taps and explain when P2P consent, the native engine or a valid 40-hex-character v1 info hash is missing. P2P **remains opt-in**; the patch does not enable it automatically.
- Shows "Native P2P engine: Available / Unavailable in installed APK", persistent status text after failed starts, a visible preparation spinner, metadata/peer/DHT/tracker counters and 10-second progress messages (up to the existing 120-second metadata wait).
- Explicit message when metadata/playable file is unavailable after two minutes; cancel is available during preparation. No source token, torrent identifier, tracker URL or peer IP is printed in the status UI.
- Android Back gesture returns from **Stremio details → Stremio section → earlier Stremio section or Home → previous app tab**. It also closes search/photo views and handles Live TV fullscreen. On main Home with no navigation history, Back is consumed instead of unexpectedly exiting.
- The native MPV player continues to use its own back handler and cleans up the torrent session as before. Existing Sky Guide, Guardian, HTTPS bridge, Media3, JNI and native torrent C++ remain unchanged.
- Added 6 pure-Kotlin unit assertions over navigation history and torrent-start eligibility.

## Install into your existing project

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR5D_Torrent_Feedback_Back_Gestures_Overlay.zip" -DestinationPath . -Force

# Reuse your already-imported ARM64 native dependencies; no need to re-download:
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

That script passes `-PpmTorrent=true` and `-PpmTorrentAbis=arm64-v8a` automatically. Do **not** run plain `assembleDebug`, which can build a torrent-disabled APK if the explicit flag is absent.

Install the resulting `app\build\outputs\apk\debug\app-debug.apk` over the existing PersonalMedia app; do not uninstall it.

## Device acceptance

1. In Stremio, open a title with a legitimate, well-seeded torrent. With P2P unchecked, tapping **Stream verified pieces** should explain how to enable it, not do nothing.
2. Enable the P2P checkbox. Confirm the panel says the native P2P engine is **Available**. Tap Stream verified pieces again.
3. Look for **Starting native libtorrent...** followed by metadata/peer/DHT counts. If peers remain zero or metadata times out, report the visible message; that is a network/source diagnosis, not necessarily a button failure.
4. On a working torrent, verify real video/audio and seeking in native MPV. Test a direct HTTPS source and Freeview to ensure no regression.
5. Swipe back from Stremio title details, then another Stremio section, and from Stremio to the previous PersonalMedia tab. Swipe back out of a video to its title page. Home should not unexpectedly exit.

## Verification limits

- The navigation/eligibility classes compiled and **6/6** new JVM tests passed locally.
- No Android SDK/Gradle build or on-device torrent playback test is possible in this environment. The attached source is intended for the existing Android Studio VM and native ARM64 dependency build.
- The observed "nothing happens" is consistent with silent disabled controls or a long metadata wait. The actual reason on the phone cannot be conclusively determined without the new on-device engine/status output.
