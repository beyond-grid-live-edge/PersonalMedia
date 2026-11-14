# PersonalMedia Android — STR-2C Cinemeta catalogue cross-host redirect fix

Apply this **source-only overlay over the STR-2B JVM test fix**. Preserve the existing signing, MPV binaries, installed account, Sky guide and Live TV data.

## Problem

The previous patch supported `cinemeta-catalogs.strem.io -> v3-cinemeta.strem.io`. Some Cinemeta catalogue endpoints redirect the **opposite direction**: `https://v3-cinemeta.strem.io/catalog/movie/top.json` -> `https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json`. The error shown on the phone was `Provider redirected to a different host; blocked for account safety`.

## Change

The revised `StremioRedirectPolicy.kt` accepts this exact HTTPS Cinemeta catalogue host pair *only for properly matched public catalogue paths*. It checks the catalogue type and id, the exact target path, default HTTPS ports, and unchanged query; redirects from other add-ons, wrong routes and HTTPS-to-HTTP downgrades remain blocked. The original reverse migration compatibility is retained for public JSON routes.

`StremioJsonHttp.kt` is **unchanged**: GET requests carry only `Accept: application/json`; account POSTs still refuse all redirects; there is no forwarding of auth tokens or configured private add-on headers. This is NOT a generic allow-all redirect patch.

## Install on your Windows VM

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR2C_Cinemeta_Cross_Host_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` as an **update**, without uninstalling. Test Stremio → Discover → Movies → Cinemeta · Popular, then Series, Search and Load more. Test account restoration, and verify Live TV still works.

## Test status

JVM policy and local HTTP transport tests are run separately in the development container. Full Android Gradle/physical-device validation is required on your machine and phone. If a provider redirects to a genuinely different untrusted host or returns an unrelated HTTP error, it stays blocked and the UI reports the failure rather than exposing credentials.
