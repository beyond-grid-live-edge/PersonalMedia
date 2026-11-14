# PersonalMedia Android — STR-2 Cinema Browsing & Episode Details

**Apply over:** the user-verified STR-1 Account & Add-ons Android source.

This is a **source-only overlay**, not a new app. It changes only the Stremio Kotlin client and UI and adds a pure Kotlin metadata policy plus a JUnit unit test. No `AndroidManifest`, Gradle build configuration, signing files, application ID, native player, torrent engine, Live TV, or Media3 sources are modified.

## STR-2 features

- Search and Discover keep **separate results**, so switching tabs no longer shows a stale result from another section.
- Search has All / Movies / Series filters; provider search results are deduplicated using **type + stable media ID**, not just title.
- Discover shows **catalogue provider + name**, has genre selectors when offered by the add-on, and retains explicit next-page support for paginated catalogues.
- Search, Discover and Library use responsive cinematic poster grids (2/3/5 columns depending on available width). Results display incrementally in groups of 24 to avoid composing 100 large poster images at once.
- Movie and series details retrieve metadata from **up to four compatible installed Stremio add-ons**, in parallel, and merge only exact matching media type/ID. Individual errors do not break the entire details page.
- Series details display **seasons, specials when available, numbered episodes, stills, descriptions and release dates when actually provided**. Episodes render in batches of 32 to avoid loading hundreds of cards on a phone at once.
- Episode selection retains the *original Stremio video ID*, clears stale source results and continues to use the existing **Find streams** route. No fake episodes, video URLs, timestamps or artwork are created.
- Asynchronous responses for changed media selection/catalogue/genre are ignored where possible. No new add-ons are installed or configured automatically.

## Preserve and known limits

- Account login and encrypted sessions from STR-1 are unchanged.
- Existing library add/remove and stream-source selection are retained. Native MPV playback remains as in the installed project.
- The Android native libtorrent runtime has **not** been added in STR-2; BitTorrent playback belongs to STR-5.
- This is **not** STR-3: official watched-state history import, two-way watched sync and full Continue Watching work are separate milestones.
- Optional TVmaze enrichment from the later HarmonyOS edition is not included here. This update uses only the user's configured Stremio add-ons; rich episode fields appear where providers actually return them.
- Some add-ons have no meta resource or omit episode artwork/descriptions; the UI must gracefully show what's available instead of inventing it.
- Configured add-on transport URLs may contain tokens. This update doesn't display them in the UI or write them to logs.

## Apply in Windows PowerShell

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR2_Cinema_Episodes_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug --stacktrace
.\gradlew.bat :app:testDebugUnitTest
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** the working installation; do not uninstall or clear data. The local T405 Freeview lineup and any previous app data should remain.

## Android device checks

1. Sign in using your existing Stremio account (session should remain saved).
2. From Home, open a film and check backdrop, poster and synopsis.
3. Search a film or show; filter by All / Movies / Series; open and back out of details.
4. Open Discover, switch Movies/Series, choose an installed add-on catalogue, filter a genre, and (if supported) Load more.
5. Open a TV series and test Season 1, another season, and Specials if offered. Confirm episode numbers, descriptions, stills and air dates appear only if the source provides them.
6. Select an episode, press Find streams, and confirm the stream request uses **that episode**, not the first episode of the series. Do the same after switching seasons.
7. Verify Library still separates movies and series and existing Play/source functionality remains accessible.
8. Recheck **Freeview / Sky Guide / Guardian** and a local Media3 movie, which this patch intentionally does not modify.

## Verification scope

- Pure Kotlin metadata/episode policy compiled and passed an isolated JVM regression test using minimal model stubs.
- The updated Stremio Kotlin files produced no syntax/parser diagnostics in a compiler invocation without Android dependencies. This **does not substitute for a real Android Gradle compilation**.
- The Android Studio build, Stremio API behaviour, artwork loading and real-device UI remain to be verified on the user's Windows VM and Android device.
