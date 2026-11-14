# Android architecture

This source tree is **independent from the HarmonyOS application**.

| System | Relevant files |
| --- | --- |
| Compose UI | `MainActivity.kt`, `HarmonyLayouts.kt` |
| Local library & safe grouping | `LocalLibrary.kt`, `LibraryFolderManager.kt`, `LocalLibraryOrganizer.kt` |
| Stremio & add-ons | `StremioClient.kt`, `StremioScreen.kt`, `StremioAddonManagerScreen.kt` |
| Native MPV and verified torrents | `StremioMpvPlayerScreen.kt`, `mpv_surface.cpp`, `streaming_torrent_session.cpp`, `mpv_range_server.cpp` |
| Media3 playback | `PlayerScreen.kt`, `UsTvPlayerScreen.kt` |
| UK tuner & EIT guide | `LiveTvScreen.kt`, `T405GuardianService.kt`, `native_live_tv.cpp` |
| US channels-only TV | `UsLiveTvScreen.kt`, `UsTvRepository.kt` |
| Queue/downloads | `DownloadQueueRepository.kt`, `DownloadQueueService.kt`, `DownloadTransfers.kt` |
| Preferences & recovery | `MediaTrackMemoryStore.kt`, `PlaybackPreferencesStore.kt`, `PersonalMediaBackup.kt`, `PersonalMediaDiagnostics.kt` |

**Native boundary:** NDK/CMake builds `libpersonalmedia.so`. Dynamic libmpv libraries in `app/src/main/jniLibs/` and ABI-specific static libtorrent/Boost/OpenSSL in `native-deps/installed/` are **external**, excluded from Git.

**Security boundary:** Android SAF grants control local file access. Stremio auth tokens and encrypted signed-download queue contents are excluded from portable JSON backups. Exported backups contain selected catalog and configuration records, are plaintext, and do **not** copy media or transfer SAF grants. The torrent network code permits only hash-verified piece reads.

**Source boundary:** US TV channel URLs are resolved from Global Free TV's US channel pages; the EPG provider is not a playback source. The UK DVB-T405 system retains its own Sky-style guide.

See [Build guide](BUILDING.md) and [original milestone notes](history/README.md).
