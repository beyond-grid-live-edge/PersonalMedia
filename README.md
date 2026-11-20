# PersonalMedia — Android

**PersonalMedia for Android** is an independent native Android port of the PersonalMedia HarmonyOS application. It combines a local media library, Stremio, UK Freeview, US Internet TV and persistent media downloads.

**Current source:** STR-7D (November 2026). Native Kotlin/Jetpack Compose + Media3, with JNI/C++ for the August tuner, native MPV and hash-verified libtorrent streams. **Development source, not a published APK release.**

## Features

| Subsystem | Current Android implementation |
| --- | --- |
| Local media | Movies, TV Shows, Music and Pictures; SAF folder selection, collections, movie editions, TV seasons/episodes, favourite and watched tracking, safe rescan/repair |
| Stremio | Account and add-on management, browsing, full stream lists, account library/watch sync, native MPV HTTPS and verified torrent streams |
| Download manager | Persistent encrypted queue, custom storage folder, HTTP Range resume, verified-piece torrent saving, Pause/Resume, recovery and checks |
| UK Live TV | August DVB-T405 Wi-Fi tuner, native MPEG-TS stream, Sky-style EIT guide and connection Guardian |
| US Live TV | **Global Free TV US-only** channel list, search, favourites, Media3 playback and Source 1/2/etc. fallback; **no US programme guide** |
| Playback & artwork | Global preferences, per-title Audio/CC memory, optional TMDb/OMDb/Wikipedia/TVmaze metadata |
| Recovery & support | Selective portable JSON backup/merge/undo and redacted diagnostics |

### Verification

User-reported **Honor 90** testing has confirmed real libtorrent video **and audio**, seeking, torrent cancel/source switching, a completed Backrooms torrent download and US channel playback in Media3. Later STR-7 features (especially backup/restore and per-title track memory) still require targeted on-device validation. Code presence alone is not a release guarantee.

## Build

This repository contains the **full first-party source**, but **not** native MPV libraries, ABI-specific libtorrent/Boost/OpenSSL static archives, device-specific signing credentials or your app data. A fresh clone needs setup before compiling the torrent-enabled APK.

**[Fresh clone & build instructions](docs/BUILDING.md)** · **[Architecture](docs/ARCHITECTURE.md)** · **[Milestone history](docs/history/README.md)**

For the existing Windows ARM64 project with cloud dependencies already imported:

~~~powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
~~~

Successful output: `app/build/outputs/apk/debug/app-debug.apk`. **Do not install a stale APK after a build error.** Keep the original package signing identity and install **over** the existing app; uninstalling may erase saved channels, library, queue and account data.

## Repository layout

- `app/src/main/java/com/personalmedia/android/` — Compose UI, local library, Stremio, Media3, downloads, preferences, backup
- `app/src/main/cpp/` — JNI bridge, native MPV rendering, verified torrent engine, DVB-T405 receiver
- `app/src/test/` and `tests/` — Kotlin and CMake/native regression tests
- `tools/` — ARM64 build, native import, MPV staging, wrapper bootstrap and verification
- `.github/workflows/personalmedia-android-libtorrent.yml` — manually run native dependency build
- `docs/history/` — preserved STR/LTV notes from earlier milestones

## Security, permissions & licensing

Application ID: `com.personalmedia.android`. The library uses Android's Storage Access Framework instead of broad file-system permissions. Stremio credentials and signed stream links are excluded from portable backups; **JSON backups are not encrypted** and can contain media titles and document URIs. Torrent peers can see your IP address. Use only authorised media and providers.

Native MPV/FFmpeg, libtorrent, Boost, OpenSSL and TV/metadata providers have separate licences and terms; review them before redistribution. Historical milestone notes may contain outdated limitations or errors: use the current README and build guide for this snapshot.

## Download our Android ARM64 libtorrent dependencies

**[Download the verified ARM64 libtorrent / Boost / OpenSSL build (GitHub Actions artifact)](https://github.com/beyond-grid-live-edge/PersonalMedia/actions/runs/43246458194/artifacts/13958421987)**

This is the **native dependency package**, **not an Android APK**. It is the `PersonalMedia-libtorrent-arm64-v8a` artifact produced by successful [workflow run #2](https://github.com/beyond-grid-live-edge/PersonalMedia/actions/runs/43246458194) on **12 November 2026**, for Android **arm64-v8a / API 26 / c++_static**. The artifact expires on **26 November 2026 at 15:31 UTC**; sign in to GitHub with access to this private repository to download it.

When the artifact expires, **[run the libtorrent build workflow again](https://github.com/beyond-grid-live-edge/PersonalMedia/actions/workflows/personalmedia-android-libtorrent.yml)** using `targets = arm64-only`, then download the new artifact from that successful run.

To import the downloaded **outer** ZIP into an existing PersonalMedia checkout and build the torrent-enabled ARM64 APK:

```powershell
.\tools\STR5C-Install-GitHub-Dependencies.ps1 `
  -ArtifactZip "$env:USERPROFILE\Downloads\PersonalMedia-libtorrent-arm64-v8a.zip" `
  -Arm64Only -Build
```

The importer checks the inner SHA-256 and ABI-specific native archives. Already installed these libraries? Build again with `.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only` instead. See [BUILDING.md](docs/BUILDING.md) for prerequisites and MPV staging.

## Open-source licence

Copyright © 2026 **Shaun Hargadon**.

The original PersonalMedia Android code that Shaun Hargadon owns in this repository is licensed under the **GNU General Public License version 3.0 only (GPL-3.0-only)**. See the full [LICENSE](LICENSE) text. Modifications and redistribution of GPL-covered material must follow that licence's terms.

This project licence does **not** relicense or grant extra rights to third-party components, including native MPV/FFmpeg, libtorrent, Boost, OpenSSL, Android libraries, TV streams, artwork, or metadata. Their respective upstream licences and service terms continue to apply. The separate HarmonyOS project is not covered by this repository's licence merely because this Android port is published.
