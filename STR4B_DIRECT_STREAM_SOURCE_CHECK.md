# PersonalMedia Android STR-4b — Direct-stream source check / User-Agent retry

Base: apply to the working STR-4a Black Screen Fix source. Existing package identity, signing, native MPV binaries, Live TV, Media3, and watched sync are unchanged.

## Why this patch
The phone reported: "The stream failed to open. Check internet access, provider availability or its required headers." This is MPV_ERROR_LOADING_FAILED (-13), which is not proof that the phone is offline. HarmonyOS playing the same film does not prove that the Android app chose the same source or transport; the HarmonyOS edition also supports native verified BitTorrent whereas this Android stage still only has direct HTTP MPV.

## What's changed
- After an MPV_ERROR_LOADING_FAILED event, make a bounded Android HttpURLConnection GET with Range: bytes=0-0 for the *selected* stream and its declared request headers. Do not read the media body, follow cross-origin redirects, log private URLs, display request headers or persist source details.
- Report status in privacy-safe terms: authentication/403, expired/404, rate limiting, HTML masquerading as media, HTTPS/TLS error, provider redirection, timeout, or accepted HTTP with an unresolved MPV-specific failure. HTTP 200/206 alone does not prove media is playable.
- When a compatible HTTPS stream has no provider-declared User-Agent, offer a user-initiated "Retry with Stremio User-Agent". It restarts only this Stremio MPV session. Do not overwrite a provider-supplied User-Agent or loosen the transport policy.
- Only suggest software decoding for streams that made it past opening; MPV -13 is a loading failure, not evidence of a decoder issue.
- Update the MPV error notice to avoid implying that internet access is the root cause.

## Install (Windows VM PowerShell)
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR4B_Source_Diagnostics_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1

Install the APK over the existing PersonalMedia app (do not uninstall).

## Test
1. Open the same movie and **same named Direct HTTP source** in Android.
2. If MPV fails, read the new "Source check" message. Do not share URL/token-bearing details; the message is safe to share.
3. If offered, try "Retry with Stremio User-Agent".
4. Compare the exact source *kind* in HarmonyOS; a torrent on HarmonyOS isn't equivalent to Android's direct HTTP source. Torrent playback remains STR-5.
5. Confirm Live TV and two-way watched sync still work.

A failure after HTTP 200/206, or a redirect to another host, needs further device/source-specific investigation. This patch is not a guarantee of video playback. Build and source checks run here; full Android Studio build and actual-phone results still required.
