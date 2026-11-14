# US-LTV-2 — Native live-stream playback and source recovery

Apply this overlay ON TOP OF US-LTV-1. It is **not** a replacement for the project or its signing keys.

## Device screenshot addressed

The US Internet TV tab opened `Stremio · Native MPV` and reported MPV failure -13 despite a provider response of HTTP 206. HTTP 206 is only a partial-content network response, **not** proof of decodable video. The on-demand HTTPS Range bridge is not a live-HLS segment player, and the fallback may not recover this situation.

## Changes

- US TV now uses a dedicated, full-area **Media3** player, with HLS playlist/segment handling and native Android buffering. It handles HLS (`.m3u8`), DASH (`.mpd`), TS (`.ts`), MP4 (`.mp4`) and M4V (`.m4v`) using the existing Media3 stack.
- Adds the matching `media3-exoplayer-dash:1.5.1` dependency (one new Gradle dependency; package identity/signing unchanged).
- Exposes source chips, Next source and Retry in the player. Each exposed source is attempted at most once automatically. Sources that remain unavailable can be retried explicitly.
- 30-second opening deadline per source, to advance when a live provider never returns a playback error.
- HTTP sources require the already-existing user opt-in; no other website is imported. EPG data is not used for stream playback.
- If no stream plays, the viewer can open that channel on Global Free TV in a browser. Availability and geo restrictions still apply.
- The Live TV tab hides top navigation while the US player is active, mirroring the Freeview fullscreen behaviour.
- Removes the irrelevant HarmonyOS comparison from a shared Android MPV error message; Stremio playback behaviour unchanged.

## Existing features untouched

No native C++, Freeview T405, libtorrent, MPV binaries, library index, download queue, application ID, signing or manifest changes. All UK and torrent playback paths remain as before.

## Local verification

- Pure Kotlin policy smoke: 16/16 assertions passed.
- JUnit test source added to Android project for policy regression.
- ZIP and altered-file integrity audit completed.

**Android Gradle build / actual provider playback has NOT been run here.** Builds on the user's Android Studio ARM64 setup with the existing cloud-built torrent libraries; Gradle will need the additional DASH dependency available.

### Install

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_USLTV2_Live_Player_Source_Fallback_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install the new APK over the existing installation. Do not uninstall; signing data, saved channels and downloads remain in the app's existing data directories.

### Device test

1. Select Live TV > US Internet TV and the channel that failed earlier.
2. Verify that the top bar says **US Live TV · Media3**, not **Stremio · Native MPV**.
3. If Source 1 fails, verify the player tries Source 2 (when one is publicly exposed), without returning to the wrong player.
4. Try Next source, Retry and Back to US TV guide, and verify the UK Freeview channel guide is unaffected.
5. Test one publicly available US HLS feed and one direct MP4 feed if present. The site can supply broken/geo-restricted URLs that no player can fix.
