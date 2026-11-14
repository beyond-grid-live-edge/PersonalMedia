# PersonalMedia Android — STR-6A: Persistent Downloads & Storage Management

This overlay applies **over STR-5J Harmony Library Parity** in your existing PersonalMedia Android Studio source tree. It changes **only the listed source/config files** and does not contain/replace Android signing keys or cloud-built libtorrent/Boost/OpenSSL binaries. The STR-5C `-Arm64Only` build script is still used.

## Implemented

- **Persistent queue:** Stremio stream cards add download tasks, not foreground exports tied to the screen. The Downloads tab lists queued, running, paused, failed and completed items. Jobs and their offsets survive app restarts in Android Keystore-backed **encrypted preferences** (signed provider URLs, request headers and tracker strings are never stored in plain-text settings).
- **Destination management:** Pick a writable folder through Android's system Storage Access Framework folder picker. Persistent read/write grants are used. Multiple items may share the default folder, and tasks keep their originally selected folder after you change that default.
- **Foreground background transfers:** A user-initiated `dataSync` foreground service continues downloads after leaving the Stremio screen, with status notifications and notification Pause action. On Android 13+, enable notifications for visible status updates. One download runs at a time; others stay queued.
- **Pause / Resume / Restart:** HTTP resumes use `Range: bytes=<saved offset>-`, validate strict `Content-Range` and `ETag`/`Last-Modified` where present, and refuse to join mismatched media. `Restart` explicitly truncates the previously created output document and begins again.
- **Verified torrent export:** Uses existing native libtorrent to fetch metadata and `torrentReadVerified()` for every written chunk. A single global torrent session is protected by a lease, so a background download will not replace an active torrent movie stream.
- **Storage actions:** Show totals and progress, Open completed media, Remove queue record **without deleting the file**, or Delete a completed file only after explicit confirmation. Never delete the local library's scanned media or source folders.
- **Crash recovery:** An interrupted transfer is restored as **Paused**, not deceptively marked complete. The partially written SAF document remains, ready for explicit Resume. Work never starts from a `BOOT_COMPLETED` receiver.
- **Security:** No all-files permission, no exposed document content server, no provider credentials sent across origin redirects, no sensitive source URLs in UI/log messages. Every torrent byte must already pass native piece verification before storage.

## Installation

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR6A_Persistent_Downloads_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** the installed PersonalMedia app. Do not uninstall (that deletes app-private saved library/download records). STR-6A does not need another GitHub Actions dependency build.

## Test on your Honor 90

1. Go to **Stremio → Downloads → Choose downloads folder**. Select a writable subfolder and grant access. (Some storage roots are restricted by Android.)
2. Find a freely distributable video. Tap its **Download** card button. The Downloads tab should show its queued/running status and progress.
3. Leave the Downloads screen; the foreground-service notification should remain during transfer. Pause a transfer and Resume it. For direct HTTPS, verify a non-zero saved position continues correctly; for torrents, verify native piece-verified progress.
4. Reopen the app after an interruption, select **Resume** on a Paused item, and confirm data is not duplicated or overwritten incorrectly.
5. After completion, tap **Open**. Test **Remove** (leaves the file) separately from **Delete file** (confirmation required).
6. Start a torrent movie while a torrent download is running. PersonalMedia should explain that the one native torrent engine is busy; pause the download and play the movie. Direct HTTPS streaming remains independent.
7. Check Freeview and the local library remain unchanged.

## Limits and next checkpoint

- The native engine has one torrent session: torrent download and native torrent playback are **serialized**, not simultaneous. Independent HTTP downloads may proceed while a torrent plays.
- Android's Storage Access Framework providers vary. Resume requires a **seekable writable document**; a provider that rejects seek operations produces a visible failure rather than file corruption.
- A saved signed HTTPS source may expire between sessions; the service will report the failure rather than bypass provider authorization.
- On Android 15+ (target SDK 35), foreground `dataSync` work has a **six-hour total background execution cap per 24 hours**. The app pauses/recoverably retains jobs when the system invokes its timeout; do not assume unlimited background processing.
- Downloads do **not** auto-resume after device reboot or process death. Users resume explicitly from Downloads. The service is user-started, not a perpetual job scheduler.
- The complete Android Studio build and on-device transfers are **not** runnable in this sandbox. Run the ARM64 build script above; share compiler errors if any. Existing STR-5 torrent video playback is not modified.

## Files in overlay

`DownloadQueuePolicy.kt`, `DownloadQueueRepository.kt`, `DownloadQueueService.kt`, `DownloadStorage.kt`, `DownloadTransfers.kt`, `TorrentEngineLease.kt`, `StremioDownloadQueueScreen.kt`, `DownloadQueuePolicyTest.kt`, plus updated `StremioScreen.kt`, `MainActivity.kt`, `AndroidManifest.xml` and this guide.
