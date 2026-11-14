# PersonalMedia Android STR-4e — Automatic native MPV HTTPS fallback

## Baseline and device evidence

Apply **on top of STR-4d**, the version where the user confirmed that **Try Android HTTPS bridge (native MPV)** successfully played a Stremio direct HTTPS film on Android. Do not uninstall PersonalMedia or replace the signed project configuration.

STR-4d required the user to manually choose a redirected HTTPS URL and then the Android HTTPS bridge. STR-4e performs that safe compatibility fallback automatically when direct native libmpv reports a genuine media loading failure.

## Changes

- Start each selected Stremio direct URL with native libmpv as before. Directly working sources remain direct.
- If libmpv reports **`MPV_ERROR_LOADING_FAILED` (-13)**, perform the existing bounded Android HTTPS diagnostic in a background coroutine.
- Automatically activate the already working Android `127.0.0.1` Range bridge **only if** a media endpoint has been verified to return **HTTP 206 Partial Content**, and its final URL is an eligible public HTTPS address on port 443.
- On cross-origin redirects, the old provider request headers are cleared before the diagnostic follows the new host. The bridge uses **no provider authentication/cookie/referer headers**.
- Keep provider URLs, signed token query parameters and session tokens in memory only. Never include them in UI messages, diagnostics, logging or watched-history records.
- Only one automatic bridge attempt is made per selected media source. If it fails, the existing manual retry and source-selection options remain available. No automatic retry loop.
- Safely replace the MPV source on the existing Android Surface; stop the old MPV media session before closing any old bridge. The loopback listener is closed on player shutdown and source changes.
- Display a short, non-sensitive "Automatic Android HTTPS bridge · Native MPV" status while using the fallback.
- Reject insecure redirects, local/private-IP targets, non-HTTPS destinations, nonstandard ports, missing/invalid byte-range responses and unplayable webpages. A provider requiring private headers on the final media host is **not** silently proxied.
- Do **not** modify the existing T405 Live TV MPV C++, Guardian, Sky EPG, Media3, JNI, application identity, Gradle settings or signing configurations.

## Overlay installation (Windows ARM64 VM / PowerShell)

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR4E_Auto_HTTPS_Bridge_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` as an *update*. Do **not** uninstall PersonalMedia (which would remove your local history, keys and saved TV channels). Previously staged `libmpv.so` binaries are deliberately not included in this overlay or source snapshot.

## Actual device acceptance

1. In Stremio, open the same direct stream of *The Killer Shrews* that succeeded through the manual STR-4d Android HTTPS bridge.
2. Select Play once. It may briefly try direct MPV; after a failed open, it should check the HTTPS media endpoint and transition to the bridge **without** requiring the old two manual retry taps.
3. Confirm **real video and sound**, not just a successful HTTP 206 probe or the MPV window.
4. Seek forward and back; pause and resume. Verify playback continues after a seek.
5. Exit the player, reopen the same item and check Continue Watching resumes near the saved position.
6. Check fullscreen and audio/subtitles. Switch to Live TV and verify the T405 MPV pipeline still works.
7. Try another genuine direct HTTPS source; if it already plays directly, no bridge should start.
8. If a provider lacks a verified header-free HTTPS 206 endpoint, expect a clear failure and manual source-choice options rather than an insecure automatic retry.

## Local verification

- **7/7** Kotlin policy tests passed (3 new automatic-fallback tests and 4 existing source/redirect tests), using a local JVM compiler with an isolated substitute for Android-only dependencies.
- The existing STR-4d loopback bridge regression runner passed both range-policy validation and simulated `206`/`403`/`416` cases.
- **Full Android Studio Gradle build and real STR-4e playback tests have not been run in this environment.** The previously confirmed device success belongs to STR-4d's *manual* bridge, not yet this automatic selection.

## Files in the overlay

- `app/src/main/java/com/personalmedia/android/StremioMpvPlayerScreen.kt` — automatic transition and status.
- `app/src/main/java/com/personalmedia/android/StremioSourceDiagnostic.kt` — typed eligible bridge URL and HTTP response metadata (transient only).
- `app/src/main/java/com/personalmedia/android/StremioBridgeFallbackPolicy.kt` — strict one-shot decision policy.
- `app/src/test/java/com/personalmedia/android/StremioBridgeFallbackPolicyTest.kt` — policy tests.
- `STR4E_AUTOMATIC_NATIVE_MPV_HTTPS_FALLBACK.md` — these notes.
