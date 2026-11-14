# PersonalMedia Android STR-4d — Android HTTPS Range Bridge (optional native MPV retry)

## Why

On the Honor 90, the STR-4c check reached the final HTTPS provider and received **206 Partial Content**, but libmpv still returned **MPV_ERROR_LOADING_FAILED (-13)** for the same final URL. This proves Android's HTTP client can reach a byte-range endpoint, not that the media is decodable. Direct MPV loading remains the default.

This overlay adds **Try Android HTTPS bridge (native MPV)** after a failed header-free HTTPS source responds to the diagnostic with HTTP 206. It is an optional test of MPV's native HTTPS transport versus Android's HTTPS stack; it is not a codec replacement, a browser player, a bypass for provider authorisation, or libtorrent.

## Playback path

```
Stremio selected HTTPS media URL (volatile memory only)
        | Android HttpURLConnection / TLS
        v
private 127.0.0.1:ephemeral /<random 192-bit token>/media
        | HTTP/1.1 GET/HEAD + Range / Content-Range
        v
existing native libmpv EGL player -> Android Surface
```

- The loopback listener is bound **only to 127.0.0.1** and requires a cryptographically random per-session path token, preventing casual access by another local application.
- The bridge is activated only by explicit user interaction, **only for HTTPS**, and **only with empty provider request headers** (for example, the verified cross-origin redirect already displayed in STR-4c).
- Provider URL, signed token and resolved endpoint remain in volatile memory. No private header is sent to another provider or logged.
- HTTPS certificate checks remain enabled in Android; local-network/private-IP redirects and insecure destinations are refused, with a six-request redirect limit. The bridge deliberately uses port 443 only.
- Requests are processed off the Android main/UI thread. Only 4 concurrent requests and 8 queued connections are accepted.
- Single HTTP byte ranges and response content lengths are forwarded. Inconsistent Range replies, webpage responses and provider errors are rejected instead of serving incorrect bytes.
- The bridge is closed after MPV stops, when the video Surface is destroyed, or when the screen is disposed. Media3, torrent components and Live TV code are unchanged.
- No response body is stored: video is relayed in bounded 64 KiB buffers. It is not a persistent download/cache.

## Install

Apply this **source-only overlay over STR-4c** in the existing Windows ARM64 VM project. Do not replace project signing or previously staged `libmpv.so` libraries.

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR4D_HTTPS_Range_Bridge_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` as an update without uninstalling PersonalMedia.

## Device test

1. Open the same **Direct HTTP** source for *The Killer Shrews*.
2. Let the original MPV failure + source check appear. Follow **Try verified HTTPS redirect (without provider headers)** as previously.
3. Once the direct verified URL fails and shows **HTTP 206**, tap **Try Android HTTPS bridge (native MPV)**.
4. Verify actual decoded picture/sound and seek forward/backward; a 206 message or player UI by itself does not count as successful playback.
5. Exit and reopen, then check saved playback progress. Test Freeview afterward.
6. If it still fails, report the new bridge message. Do not post a signed stream URL or a secret add-on configuration.

## Tests and limits

- The standalone Kotlin bridge compiles with the JVM Kotlin compiler.
- A local in-process mock verifies single-range HTTP 206 response, bearer-path authorisation (403), invalid Range (416), Range metadata checks and source shutdown; **no real provider or Android MPV playback has been validated here**.
- The full Android Studio/Gradle build requires the user's Android SDK and device. This is a testing candidate, not an assertion that MPV playback is fixed.
- Some sources may be HLS playlists, encrypted/DRM content, malformed media, nonstandard-port URLs, or providers requiring authentication/cookies. The bridge will not resolve those cases.
