# PersonalMedia Android — STR-6B: Resilient downloads and storage checks

**Base:** US-LTV-3 Channels Only (includes STR-6A, STR-5J and earlier work). Apply **overlay only** to the existing signed project. Do not uninstall the app or replace its native cloud-built libraries.

## Changes in STR-6B

- **Crash-safe resume tail:** The download service checkpoints progress after flushing/forcing its SAF output descriptor. If a crash leaves bytes beyond the last saved checkpoint, reopening trims only the uncommitted tail before seeking to the saved position. A document *shorter* than recorded progress fails clearly and requires an explicit Restart; the app never fabricates or silently skips bytes.
- **Completion checks:** On the successful transfer path, the writer is closed, and the saved SAF document is reopened and compared with recorded progress. Missing or mismatched files are not marked Completed. If a process died after finishing the last checkpoint but before saving the Completed status, a user-initiated Resume can recognise the complete file from its saved length.
- **Clearer controls:** Each active task shows download speed and approximate time remaining when both size and speed are known. Queue-wide **Pause all** and **Resume all** controls; per-item Pause, Resume, Restart, Open, Remove and explicitly confirmed Delete remain.
- **Check file:** Completed rows have a non-destructive size check. It detects unavailable files or size mismatches; it is **not a checksum/hash verification** of HTTP files. Torrent bytes still originate from the existing native libtorrent piece-verification interface.
- **Playlist safety:** Reject direct HLS (`.m3u8`) and DASH (`.mpd`) playlist manifests as whole-file downloads; downloading the text manifest alone is not a video export. Live TV playback is unaffected.
- **Paused on interruption:** A foreground service that stops while an item is active records Paused, not Completed. Android 15 service limits still apply. Tasks are never auto-resumed after a reboot; resuming is user-controlled.

## Preserved

- Existing STR-6A queue records, completed files and saved download-folder grants. No new schema is required; temporary speed is not saved as an old-state dependency.
- STR-5J Movies/TV Shows grouping, Freeview August T405 receiver and guide, Stremio browsing and native torrent playback, and US-LTV-3 **channels-only** Live TV.
- Application ID, signing, version, AndroidManifest.xml, Gradle configuration and native C++ files.

## Install (Windows ARM64 VM)

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_STR6B_Download_Recovery_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** the installed application. Keep the app data and existing verified ARM64 libraries.

## Device checks

1. Open **Stremio → Downloads**, confirm the completed Backrooms record is still present, and select **Check file** (checks saved byte length, not content hash).
2. Queue two downloadable video files and test **Pause all** and **Resume all**. On-device Android notification permissions may affect notifications, not the queue list.
3. Observe per-item speed and ETA after approximately a second of active transfer. Transfer estimates can vary with peers and network traffic.
4. Pause halfway, close/reopen PersonalMedia, then select Resume; no progress should be duplicated. Restart is required if a storage provider shortened the partial file or the HTTP source changed.
5. Verify **US Internet TV → channels-only** and UK Freeview remain available. STR-6B does not change those screens.

## Important limitations

- Source-only code was tested with pure Kotlin queue-policy tests. A complete Android Gradle/NDK build and real Honor 90 SAF/FGS tests are required to validate the integration.
- SAF providers differ in whether they allow seek/truncate/force and report a reliable byte count. Unsupported providers fail rather than silently producing corrupt output.
- HTTPS sources with expiring tokens may need a new link to resume. An HLS/DASH playlist is not a standalone downloadable movie.
- A file-size check cannot guarantee a direct HTTP video's content is cryptographically intact. Torrent pieces retain native hash verification.
- Native torrent playback and torrent downloading use one session and remain mutually exclusive.
