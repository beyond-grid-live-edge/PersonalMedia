# PersonalMedia Android STR-5E — Inline torrent controls, immediate cancellation & real Save buttons

Based on **STR-5D Torrent Feedback & Back Gestures**, including the working GitHub-built **STR-5C ARM64 libtorrent** integration. Apply the ZIP as an **overlay only**. Do not delete or replace your Android project, installed Android libtorrent libraries, signing keys or saved channels.

## Changes

1. **Play and Download actions appear on each individual Stremio source card.** Torrent playback still opens the existing verified-piece native MPV path. Direct HTTP playback still uses the automatic Android HTTPS bridge when needed. Cards show the selected source's preparation status and cancellation controls inline; you no longer need to scroll back to the top of the list to see these.
2. **Cancel responds immediately in the interface.** Kotlin clears the preparation spinner on tap and sends a small JNI cancellation signal. The native engine has a cancellation-generation guard covering even a session that is still being created, so a late-opened session will not continue after cancellation. Actual libtorrent shutdown may still take a short time off the UI thread; the card reports “Stopping native torrent session” while another torrent start is temporarily blocked to prevent races.
3. **Download is functional, not a placeholder.** Android's `CreateDocument` picker lets you choose a destination. Direct HTTP media copies via Android HTTPS with safe cross-origin redirect policy. Torrent media calls `torrentDemand` and `torrentReadVerified`; the JNI method returns data only after libtorrent confirms all requested pieces are hash-verified. The UI shows percent and saved MiB, and makes no “complete” claim until all bytes are written. Failed/cancelled exports attempt to remove the partial document; document providers may refuse deletion, in which case manually remove any partial file. Large files require enough space on the selected destination.
4. **Foreground only:** This is not the persistent, resumable transfer manager planned for **STR-6**. Keep PersonalMedia running while exporting. No background service, persistent queue or auto-resume is claimed. For a download that lacks peers, progress may remain at zero or time out; choose a sufficiently well-seeded, authorised source.
5. **Existing P2P opt-in is respected.** Torrent sources expose the current session's peers, DHT nodes, tracker replies and verified bytes only on the relevant card. Private URLs, infohash tokens, tracker secrets and source headers are not shown in diagnostics.
6. **Isolation:** Live TV (T405), Guardian, Sky guide, MainActivity navigation, Java/Kotlin Stremio account synchronisation, Media3, `mpv_surface.cpp` and MPV HTTPS bridge are unchanged. The overlay adds only a small native cancellation API, verified-buffer reads, SAF save helpers and card UI.

## Install over existing STR-5D

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR5E_Inline_Controls_Cancel_Download_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Run the torrent-enabled cloud-library build script; plain `:app:assembleDebug` may produce a **torrent-disabled** APK. Keep `native-deps\installed` from the earlier successful STR-5C import. Do NOT uninstall the installed app before updating its APK.

## Device acceptance checks

- Open Stremio > movie details. Each stream displays **Play** and **Download**. For a torrent, tap Play and watch only that source's card display metadata/peer stats.
- While it says `Acquiring metadata`, tap **Cancel play**: the spinner should stop on the first tap; the native cleanup note may briefly remain. After cleanup, tap Play again and confirm it starts a fresh attempt.
- Tap **Download** on a legal, well-seeded video; Android shows a system save-document picker. Choose a destination, confirm bytes/progress and verify the resulting file when it reaches 100%. Test cancel while exporting; delete any partial file left by a provider refusing deletion.
- Test a direct-HTTP Download separately: downloaded files are source bytes, not BitTorrent-verified pieces.
- Check swipe-back returns to details/previous Stremio section; test the Freeview tuner and the previously working direct HTTPS MPV playback.
- If the torrent reports `Peers 0` and `Tracker replies 0` with rising DHT nodes, peer discovery has **not yet been proven to work on that source**; a UI/cancellation fix cannot manufacture peers or metadata. Test a healthy, legitimately distributed torrent instead.

## Local verification

- **13/13 C++ native host tests passed**, including a new cancellation-API smoke test and the existing T405 regression checks. These build in a no-libtorrent host configuration and do not prove an Android P2P session.
- JNI C++17 syntax verified with OpenJDK JNI headers.
- Both save helpers compiled under JVM Kotlin with stubbed Android types and genuine coroutines library. A Kotlin nullable verified-byte issue was corrected during this validation.
- **7/7** local filename and progress assertions passed.
- **Full Android Studio ARM64 build and actual torrent/device export have not been tested here.** They require your installed Android SDK and the real compiled ARM64 libtorrent library.
