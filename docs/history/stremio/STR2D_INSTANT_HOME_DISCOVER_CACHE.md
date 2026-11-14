# PersonalMedia Android — STR-2D: Faster Home and Discover

**Apply over:** STR-2C Cinemeta Cross-Host Fix (the currently working Android Studio project).

## Changes

- Home no longer waits for Stremio account-library sync before requesting catalogue titles.
- Featured movie and series catalogues load **concurrently**, showing results as each provider replies instead of waiting for all six requests.
- Home uses an **encrypted, account-scoped catalogue cache**. Following a successful first load, previously seen poster/title metadata is displayed immediately on subsequent visits and refreshed in the background (stale-while-revalidate).
- Discover now opens on **All catalogues**, merging the featured catalogues across several providers automatically. It no longer requires tapping Cinemeta Popular to begin fetching content.
- Switching Home → Discover reuses catalogue results already in memory without another initial blank screen. Previously visited genre/catalogue selections use the encrypted cache before their refresh completes.
- Prefer popular/top catalogues and diversity across installed providers so one add-on cannot monopolise the initial results. Every configured add-on catalogue remains selectable by name.
- Keep rendering responsive: Home rails use lazy horizontal lists; Discover poster grid reveals titles in batches of 24.
- Cache only poster/title metadata for the first page (up to 60 per catalogue), never login keys, provider transport URLs, video URLs, torrent hashes or source-selection data. Cache keys are SHA-256 derived from account and catalogue identity; values are encrypted with Android Keystore-backed EncryptedSharedPreferences and expire after 7 days.
- Original existing Stremio browsing, add-on refresh, Search, Library, programme detail, MPV, and all working Live TV code remain untouched outside the changed Stremio files.

## Limits

An uncached catalogue still requires a network request: content cannot appear instantly on the **very first** run or before the installed add-on list is retrieved. Subsequent visits and app restarts can use cached titles while the current content is fetched. Some providers may omit titles or return errors; only actual provider metadata is displayed. The default combined Discover view requests up to four suitable catalogues concurrently to limit resource usage; manually selecting a provider allows access to its specific catalogues and pagination.

No pre-installed/demo/fake titles, web wrapper, or automatic new add-ons are introduced.

## Install

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR2D_Fast_Home_Discover_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` as an update. **Do not uninstall PersonalMedia or clear its data**: you would discard the new cache and your existing settings/Freeview lineup.

## Device test

1. Open **Stremio → Home**. On the first run, let movie and series catalogues finish loading.
2. Switch to **Discover**. Movie tiles should appear without first tapping an add-on catalogue. Toggle **Series**; switch back.
3. Open Cinemeta Popular specifically and a genre. Return to **All catalogues**.
4. Close/reopen the app; confirm Home displays cached posters promptly while its background refresh runs.
5. Temporarily disconnect internet **after** the add-on list has been loaded; cached results should remain visible. New catalogues cannot appear without internet.
6. Confirm the already-working Stremio account, movie/series details, MPV routes, Live TV Sky guide, Guardian recovery and Media3 playback are unaffected.

## Verification status

- Kotlin browsing-policy regression tests: 3/3 passed using a local JVM test harness.
- New Android Keystore cache compiled with Kotlin 1.9 and Android/crypto/JSON API stubs (static compatibility check only).
- StremioScreen Kotlin syntax was inspected by the Kotlin compiler; a full Android/Compose build is unavailable in the packaging environment.
- Complete `:app:assembleDebug :app:testDebugUnitTest` and physical device timing must be verified in your Android Studio VM.
