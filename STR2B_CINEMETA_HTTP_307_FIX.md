# PersonalMedia Android — STR-2B Stremio HTTP 307 Catalogue Fix

**Install over:** the STR-2 Cinematic Browsing & Episodes project currently installed on your Android test phone.

## Why Discover showed "Provider returned HTTP 307"

The Stremio JSON transport in `StremioClient.kt` set `instanceFollowRedirects = false` and immediately rejected anything outside HTTP 2xx. A temporary 307 redirect from a catalogue therefore displayed as an error and prevented posters/results from loading. Cinemeta uses redirected catalogue endpoints on some account configurations (notably `cinemeta-catalogs.strem.io` → `v3-cinemeta.strem.io`).

## Fix

- Keeps **POST** (login, `getUser`, account library/writes) redirect handling **disabled**, preventing inadvertent forwarding of session/authentication payloads.
- Allows **GET** catalogue, metadata and stream-list JSON requests to follow up to **five** validated redirects.
- Supports safe relative redirects, same-origin redirects and the specifically verified Cinemeta cross-host HTTPS migration.
- Refuses HTTPS-to-HTTP downgrade, unknown cross-host redirect, malformed targets and loops.
- Does not forward account headers or POST bodies when following GET redirects.
- Retains the existing **8 MiB response limit**, timeouts and sanitized provider error messages. Configured add-on URLs (which might contain tokens) are not printed.
- **Does not modify** Live TV, Sky EPG, Guardian, native MPV, Android Media3, the app ID, signing, the encrypted Stremio session, add-on configurations or Gradle/NDK files.

## Installation (Windows PowerShell in your existing Android Studio project)

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR2B_Cinemeta_307_Fix_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** the existing PersonalMedia installation. Do not uninstall or wipe app storage.

## Verify

1. Open PersonalMedia → **Stremio** → **Discover** → **Movies** → **Cinemeta · Popular**. Movie posters/results should populate instead of an HTTP 307 banner.
2. Try Cinemeta · New with a year selected if that catalogue needs one, **Series**, **Search** and **Load more**.
3. Open a film/series details page to confirm metadata still loads.
4. Restart the app; confirm the encrypted Stremio login and six installed add-ons are retained.
5. Confirm native August T405 Freeview and local Media3 playback still work.

If a provider returns a genuine 404/5xx or an unexpected cross-domain redirect, the app will report a provider error instead of claiming that content loaded. This fix does not install or replace any add-on.

## Verification already completed

- Pure Kotlin redirect policy compiled on JDK 21; all **8 smoke assertions** passed.
- Local in-process HTTP server test: 307 GET followed through to valid JSON, and a login POST deliberately **did not** follow a redirect.
- Full Android Studio Gradle build, real Cinemeta network response and on-device tests are **not performed** in this container; perform them in the Windows VM.
