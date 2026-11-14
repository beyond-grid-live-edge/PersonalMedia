# PersonalMedia Android — STR-3 Account Library & Watched Episode Synchronisation

## Apply over

**STR-2D Fast Home & Discover** (the already working Android Studio project). This is a **source-only overlay**; it preserves the existing Android app identity, signing setup, universal native libraries, MPV, Media3, DVB-T405 Live TV and Sky guide.

## What STR-3 implements

- Retains the existing official-account **Library** add/remove endpoint, preserving existing `libraryItem` record fields including `state`, timestamps and behavior hints wherever supplied.
- Adds **Refresh library** and clearer Movies / TV Series account-library presentation.
- Imports watched episode state **read-only by default** using Stremio `datastoreMeta` and `datastoreGet`, including temporary or removed library records that may carry watched history.
- Decodes Stremio's real `state.watched` encoded field: `{anchorVideoId}:{anchorLength}:{zlib/base64 LSB-first bitfield}`. Uses episode metadata sorted by season, episode, release date to align the anchored bitfield correctly. Corrupt, undecodable or missing-anchor history is **not** assumed to be unwatched or overwritten.
- Per-series **watched count**, All / Watched / Unwatched episode filters, local Mark watched / Mark unwatched buttons.
- Saves local watched flags and a **revisioned pending queue** separately for each account, across app restarts. Continue Watching/resume progress stays in the existing separate watch store.
- Imports preserve pending local edits, even when offline. Each episode's pending change overrides imported account state until acknowledged.
- **Two-way sync is off by default.** Enable it explicitly in Stremio Settings, then press **Sync queued watched changes**. This is the only new network upload path for watched flags.
- Sync fetches the latest existing Stremio library record before each series update, edits only `state.watched` and `_mtime`, and preserves unknown server fields. If the record does not exist, it creates a temporary/removed record rather than silently adding the series to the user's visible library.
- If the earlier bitfield cannot be safely aligned with known episodes, the upload is blocked and the pending changes remain queued.
- Successful uploads clear only the exact change revisions included in that upload, preserving edits made while sync was running. Account-switch and logout guards ignore stale responses.
- No direct HTTP stream, torrent, token or configured add-on URLs are saved in the watched queue.

## Limits and important safety notes

- Complete movie watch-state syncing and automated playback completion markers are **not** included in STR-3. This stage covers **series episode flags** and the already-working account library. Native MPV progress and post-playback watched marking are later playback stages.
- Stremio's account API and provider-specific episode order can change. The bitfield codec follows the currently published Stremio-core format and passed local sample/round-trip tests; **actual account read/write compatibility must be tested on the device** before relying on the sync for your real watch history.
- A connected account is required for importing or uploading. Local watched edits remain available offline.
- Missing episode metadata blocks that series' outbound sync rather than creating an incorrect watched bitfield.
- Updating an account record through `datastorePut` is an external action: test on an unimportant series first, verify from the official Stremio app, and keep two-way sync off until comfortable.
- No source code or MPV binaries are downloaded automatically by this overlay.

## Install in Windows PowerShell

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR3_Account_Library_Watched_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** the existing app. Do not uninstall or clear data; this preserves Freeview services, existing app settings, Stremio session and catalogue cache.

## Device acceptance

1. Open **Stremio → Library**: confirm Movies and TV Series appear from the signed-in account.
2. Add a title from its details and remove it; confirm both actions through official Stremio.
3. Open a series watched previously in official Stremio; select a season and tap **Import account watched history**. Confirm its watched count and episode checkmarks.
4. Use **All / Watched / Unwatched** to filter episodes. When the episode list is incomplete or an anchor is missing, the app should show **Unknown** rather than inventing a watched state.
5. Mark a test episode watched locally and another unwatched. Exit/relaunch the app; the local flags and queued count must survive.
6. While two-way sync is still **off**, verify the official app's watched flags do not change. Re-importing must not discard queued changes.
7. Enable **Settings → Two-way account sync** and explicitly press **Sync queued watched changes**. Check the same test episodes in the official Stremio app. Verify the queued count decreases only for acknowledged edits.
8. Switch accounts (if applicable) and confirm no watched markers leak between accounts.
9. Check the separate Continue Watching rail, native T405 Live TV and Sky guide, and local Media3 playback still work as before.

## Verification performed

- 5/5 Stremio watched codec tests passed locally, including the published Stremio-core example, bitfield byte boundary, anchor shift, invalid input, and episode ordering.
- Android watched store and the codec compiled with Kotlin against Android/JSON API stubs (static compatibility only).
- Full Android Studio Gradle/Compose build and live account acceptance tests are **not available in this environment** and must be performed in the user's Windows VM and test phone.

Reference: https://github.com/Stremio/stremio-core/tree/development/stremio-watched-bitfield and Stremio-core `LibraryItemState`.
