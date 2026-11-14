# STR-4 — Native MPV direct Stremio playback

This source-only patch applies **over STR-3b Watched Sync Visible**, preserving the original Android application ID, signing configuration, LTV-4 Freeview/Sky Guide/Guardian, and Media3 local playback.

## Implemented in this stage

- Routes **Direct HTTP** Stremio sources into the existing native libmpv Android EGL renderer; no WebView, server, or external Stremio app.
- Uses a new **`stremio` native player owner** with independent pause, seek, audio/subtitle tracks and playback status. Live TV continues using the `livetv` owner and its verified MPEG-TS/software-decoding settings.
- Does **not** force the DVB-T `mpegts` demuxer for Stremio. libmpv detects compatible media formats and HLS itself.
- Parses provider-supplied `behaviorHints.proxyHeaders.request` or top-level `proxyHeaders.request`. Applies approved HTTP fields via the libmpv node-array option (not ad-hoc comma-concatenation), with per-session User-Agent handling.
- Refuses malformed headers, credential-bearing plain HTTP, URL userinfo, and CR/LF header injection. Configured URLs, auth headers and cookies are never printed or saved to Continue Watching.
- Plays in immersive fullscreen (tap video to reveal controls), offers pause/resume, 10-second skipping, timeline seeking when seekable, detected audio and subtitle tracks, and separately supplied HTTPS subtitle tracks. For header-bearing streams, external subtitles are restricted to the same HTTPS origin to avoid leaking private headers to unrelated hosts.
- Stores actual MPV time-position in the existing account-specific Continue Watching store, uses it for playback resume, and updates on playback stop/rotation. Pauses on Android background.
- Leaves verified-piece BitTorrent streaming disabled until libtorrent integration in STR-5. Does not call Media3 in place of native MPV for authenticated Stremio sources.

## Apply on Windows VM (overlay)

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR4_Native_MPV_Direct_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` over the existing app; **do not uninstall**, to keep accounts, saved channels, and history. The source ZIP contains no prebuilt `libmpv.so`; retain the native libraries previously staged from the official mpv-android build.

## Device acceptance checklist

1. **Freeview regression:** play BBC ONE Scotland or 5USA; check picture, audio, Sky guide, CC and Guardian after patch.
2. **Stremio stream:** open a movie or episode, select **Find streams**, choose a legitimately accessible **Direct HTTP** source; native MPV should render picture and sound.
3. **Demux:** test a standard MP4 and an HLS stream, where provided; neither should be forced into the Freeview MPEG-TS path.
4. **Headers:** test a trusted add-on that needs Referer/User-Agent or another request header; confirm the video plays. Never paste private URLs/headers into debug logs.
5. **Controls:** pause, resume, seek back/forward, select a detected audio/subtitle track, add an external subtitle if supplied, and tap the video to hide/show controls.
6. **Continue Watching:** play a film for at least 20 seconds, close, reopen it, and verify resuming near the previous timestamp. Reboot/restart app and verify the same.
7. **Lifecycle:** background then reopen the app; rotate the phone if enabled. Verify no crash or stuck player session.
8. **Compatibility:** confirm local Media3 videos and saved Freeview channels remain working, and four-ABI verification passes.

**Limits:** `libmpv.so` must be present for the device ABI, and the provider must offer a stream the library can decode. MPV may follow HTTP redirects internally; request headers should only be supplied by trusted configured add-ons and their stream hosts. Playback HTTP errors are not yet reported via a dedicated MPV event observer; a stream may open the player surface but still fail during provider access. Full Android Studio compilation and real-device playback remain to be verified by the user.

## Local build/test record

- 5/5 Kotlin HTTP/header/subtitle-origin/resume policy tests passed using JVM stubs.
- 11/11 existing native tuner host tests passed in **Debug** configuration.
- The modified Android EGL/libmpv C++ file passed Clang C++17 syntax checks against mock Android/EGL declarations.
- These checks **do not replace** a full Android Gradle build or a device playback test.
