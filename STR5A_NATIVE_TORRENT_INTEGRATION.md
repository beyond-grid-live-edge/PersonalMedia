# PersonalMedia Android — STR-5a native torrent integration (source-only)

**Baseline:** Apply on the user's STR-4e Android project, the same app in which the STR-4d Android HTTPS bridge was confirmed playing a Stremio film. **This is the first STR-5 integration overlay, not an enabled/verified BitTorrent binary release.** The supplied HarmonyOS and Android source archives do not contain the libtorrent 2.x library source, Boost dependency build or Android-ABI OpenSSL. The existing CMake intentionally compiles without `PM_WITH_LIBTORRENT` when the dependency source is absent. Do not interpret an APK build success as confirmation torrent streaming is enabled.

## Port work completed in STR-5a

1. **Native Stremio source handoff:** A torrent's authenticated 127.0.0.1 Range URL now enters the **same native Stremio libmpv player** as direct streams, rather than the generic MPV/Media3 fallback path. Playback, seek, audio/subtitle controls and watched-position updates remain in the existing Stremio player. Playback still waits for *actual libtorrent-verified bytes*, not simulated pieces.
2. **Real-file selection:** Stremio add-ons commonly omit `fileIdx` (which becomes `-1`). After metadata arrives, JNI now resolves `-1` to the native libtorrent-selected video file index. Requests that specify an incorrect file index are refused. This fixes a permanent timeout caused by passing `-1` literally to the previous Range server.
3. **Correct container type:** The loopback MPV Range server uses constant, safe media types/extensions derived from the selected authenticated torrent metadata (`mkv`, `mp4`, `webm`, `avi`, `ts`, `mpeg`, `mov`), instead of advertising every film as Matroska. No untrusted file path is placed in the HTTP URL.
4. **P2P opt-in:** Peer-to-peer streaming requires an explicit consent toggle under Stremio settings or the source-selection panel. The toggle explains that public peers can see the device IP; disabling it cancels preparation. Preferences are stored in the application's private storage and cleared on sign-out.
5. **Preparation and cancellation:** Metadata polling lasts up to two minutes, displays peers/speed/verified-ahead counters from native JNI, and is cancellable. On failure/cancellation the worker stops the native session. A playing torrent is handed off to the MPV player, which stops libmpv **before** closing the torrent Range server and libtorrent session.
6. **No invented availability:** `engineAvailable()` still returns false in binaries without the *actual* linked libtorrent dependency. Torrent play buttons stay disabled and warn honestly. Local Media3, direct Stremio HTTPS bridge, T405, EPG and Guardian are not modified.

## Apply this overlay

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR5A_Torrent_Integration_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

This build can succeed with torrent support **disabled**, as in earlier releases. It preserves your existing signing data, installed package, MPV binaries and four-ABI build configuration.

## Enabling the real native engine (external prerequisites)

The existing CMake file (`app/src/main/cpp/CMakeLists.txt`) explicitly expects the **actual** libtorrent-rasterbar 2.x source checkout under:

`app/src/main/cpp/third_party/libtorrent/`

It calls `add_subdirectory()` and links `torrent-rasterbar` with `OpenSSL::SSL` and `OpenSSL::Crypto`, then defines `PM_WITH_LIBTORRENT=1`. Libtorrent itself requires compatible Boost headers/libs and OpenSSL **compiled for the actual Android NDK targets**. You cannot drop in `.so` files compiled for HarmonyOS, Windows ARM64 or GNU/Linux and expect them to link on Android.

Use `tools/STR5-Native-Dependency-Preflight.ps1` to check whether you have the source and ABI-matched prerequisites. `-Assemble` refuses to claim a torrent-enabled build when they are missing. Its path checks are only a preflight, **not a substitute for compiling/linking libtorrent with the NDK**. CMake must resolve compatible Boost and OpenSSL builds for each ABI, using the same public compile settings as the libtorrent library.

Official references:
- https://libtorrent.org/building.html
- https://libtorrent.org/reference-Torrent_Handle.html
- https://libtorrent.org/reference-Alerts.html

**Next build milestone:** stage real libtorrent/Boost/OpenSSL and resolve the first actual Android NDK/CMake compiler/linker failures, then verify torrent metadata, peers, byte hashing and MPV picture/audio on the Android device. The dependencies are not present in either supplied source archive and could not be built in this environment. The user should not use this overlay as proof that BitTorrent is operational.

## Tests run in this environment

- **12/12** host-native CMake/CTest checks passed; includes all 11 existing Freeview/Guardian tests plus STR-5 file selection/MIME and fail-closed checks.
- JNI bridge C++17 syntax checked with JDK JNI headers.
- **2/2** standalone Kotlin tests passed for strict loopback URL acceptance.
- **Not run:** full Android Gradle compilation, linked libtorrent NDK build, peer connectivity, hash verification of actual torrents, torrent video decoding, or real-device acceptance.

## Device acceptance after the dependency is linked

Choose only content you have rights to access. Enable P2P explicitly; select a torrent source; wait for authentic metadata and peers; verify that `engineAvailable()` returns true; use the **Native libmpv** player and test actual picture/audio, seeking, subtitles, cancellation, app back navigation, and no leftover torrent session. Recheck Freeview, Sky guide, Guardian and the HTTPS bridge afterward.
