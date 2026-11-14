# PersonalMedia Android STR-4c — Safe direct-stream redirect retry

Apply this **source-only** overlay to the working STR-4b Stremio Android Studio project. Keep installed native `libmpv.so` binaries and Android signing configuration unchanged.

## Why

The real-device STR-4b diagnostic for a Stremio film reported that its source URL redirects to a different host or protocol. STR-4b deliberately stopped at that point, so it could not tell whether the destination provided media. MPV also failed to load the original URL. A valid HTTPS cross-host CDN redirect is plausible, but **the screenshot does not establish the destination scheme or why MPV failed**.

## What changed

- Inspect up to five HTTPS redirects via bounded `GET` with `Range: bytes=0-0`, without consuming or retaining the response body.
- Preserve provider request headers only on HTTPS redirects with the same origin. **All provider headers are stripped before the first cross-origin request**, including `Authorization`, `Cookie`, `Referer` and custom `User-Agent`.
- Refuse HTTPS-to-HTTP downgrade, local/private redirect IPs, loopback hosts, file/custom protocols, userinfo, fragments and redirect loops.
- Identify whether the final HTTP status and Content-Type suggest an accessible media response. This is only an HTTP check, not a guarantee of playback or decodability.
- When a *different HTTPS origin* responds successfully with a non-HTML/non-JSON content type, offer **Try verified HTTPS redirect (without provider headers)**. This is user-initiated; it gives the endpoint directly to native MPV, without source-origin headers. Signed redirect URLs stay in volatile screen memory, never logs, saved history, or visible messages.
- Offer a **Return to original source** control if the redirected source also fails.
- No changes to native MPV renderer, Live TV, EPG/Guardian, Media3, watched sync, downloads or signing.

## Install

From Windows PowerShell, in your existing Android Studio source folder:

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR4C_CDN_Redirect_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install the resulting debug APK **over the current application**, not after uninstalling it.

## Acceptance check

1. Reopen *The Killer Shrews*, select **the same Direct HTTP** source, and wait for the status message.
2. If the status says **redirect reaches an HTTPS media endpoint**, press **Try verified HTTPS redirect (without provider headers)**. Check for real video, audio, and seek/position progress.
3. If the redirect is **insecure or unsupported**, the app will not retry an HTTP downgrade with authentication data. Instead choose a different source.
4. If the endpoint accepts the request but MPV cannot decode it, test a separate known-working direct video stream. Do not mistake a metadata 200/206 response for successful video playback.
5. Verify Freeview Live TV remains working afterward.

**Limits:** This does not bypass provider authentication, geo restrictions, DRM, dead sources or the missing torrent engine. A stream playing on HarmonyOS may be a *different* source (e.g. torrent). No Android Studio build or device playback was run in this environment.
