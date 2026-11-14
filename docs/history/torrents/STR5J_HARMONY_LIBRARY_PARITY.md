# PersonalMedia Android STR-5J — Local library grouping and safe catalog repair

This source-only patch applies **over STR-5I Local Library Folders**. It does not replace any APK signing file, app identity, libtorrent build, MPV library, tuner/Freeview code, or Stremio account code.

## Source-based comparison

The supplied HarmonyOS C20.0.23 archive includes `LibraryOrganizer.ets` and `Index.ets` collection logic. HarmonyOS groups films by **actual user-authorised directory hierarchy**, with optional known-franchise fallback for individually imported movies. Different movie editions remain separate file records. The older HarmonyOS code also describes TV series grouping by season and episode, including release-prefix cleanup. The user's current HarmonyOS screenshots demonstrate newer UI and additional repair functionality; this patch implements equivalents against Android's existing SAF database rather than copying ArkTS code verbatim.

## Android changes

- **Franchises & Collections** shelf on the Movies screen and Home, with collection detail pages. Two or more **distinct films** must belong to a collection; multiple editions of one film do not create a fake franchise. For SAF folder imports, the first-level folder name is authoritative. Generic category folders (e.g. Movies, Films, Action) do not form franchises. Known names are used only for individually imported legacy files.
- **Single poster / selectable editions:** movie names normalize year/release labels for *presentation only*. Same-title **same-year** movie editions appear under one poster; the details screen lists each original URI with its own Play/Resume control. Different known release years stay separate.
- **TV collections → series → seasons → episodes:** separate shows stay separate inside a parent collection (e.g. V collection with original miniseries, Final Battle and The Series). SxxExx, 1x02, episode and Season-folder patterns are recognised, and Part 1/Part 2 files remain distinct.
- **Catalog integrity Settings:** count exact duplicated records keyed by **(URI, media type)**. Explicit repair combines only those records and preserves watched, favourite, resume-position and artwork information. One atomic SharedPreferences snapshot can be undone if the catalog hasn't changed afterward.
- **Missing-file checks:** incomplete scans, file-limit truncation, inaccessible folders, revoked permissions and reconnects preserve indexed items. Missing candidates must recur on **two successful full scans**, and require an explicit confirmation and a final source/file availability check before removing *index entries only*. Actual media bytes are not modified or deleted. Undo is available until the next catalog mutation. This does not automatically delete unlocated library records.
- **Back navigation:** in-group Back now returns from an edition detail to its collection, then from the collection to the library tab.
- **Performance:** grouping results are memoized, and the Movies grid shows more cards in batches of 72 to avoid composing every poster simultaneously.

## Install (Windows 11 ARM64 VM)

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR5J_Harmony_Library_Parity_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install the new debug APK **over the existing app**, without uninstalling. Leave imported ARM64 native dependencies under `native-deps/installed` intact.

## Tests and honest limits

The local Kotlin/JVM grouping tests cover folder collections vs generic folder names, same-year editions vs remakes, nested V collection, season/multipart parsing, exact-URI-only repair, and two-successful-scan missing evidence. **6/6 pure policy tests pass**. Full Android Gradle/Compose compilation, document-provider permission checks, undo restoration and device UI behaviour still require your Android Studio VM and phone. The source-only ZIP has been checked against the exact STR-5I project for unintended native/build/signing changes.

This is local **organisation and catalog safety parity**, not an online metadata/artwork scraping port. It does not yet fetch missing posters or TVmaze episode descriptions, nor implement a persistent artwork-cache service. Those can be separate, opt-in enhancements; media files and provider credentials remain untouched.

## Suggested acceptance tests

1. Choose a Movies folder with `Back to the Future collection/` containing multiple films; verify one collection tile and individual film/edition choice.
2. Add two editions of a film with the same year, and a remake with another year; verify one movie poster with two editions, and a separate remake.
3. Use a TV source with `V collection/[three series]/Season XX/SxxExx` and `Part 1`/`Part 2`; verify separate series and sortable episodes.
4. Verify favourite, watched and resume state survives rescans and that Back navigates inside collection/detail views.
5. In Settings, run exact duplicate check/repair/undo. Create a genuinely missing file (outside the app) and complete two scans, then review and confirm any cleanup. Reconnect/unmount a folder and verify missing cleanup is blocked.
6. Smoke-test native Freeview, Stremio HTTPS playback and verified-piece torrents afterward.
