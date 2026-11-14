# US-LTV-3 — US channels only (no US programme guide)

Apply on top of **US-LTV-2**. This is a source-only overlay; it does not replace signing credentials, original files, media libraries, downloads or native dependencies.

## What's different

- **Live TV → US Internet TV** now opens a US **channel browser**, not an XMLTV programme grid.
- Removed US EPG settings, XMLTV URL field, per-station XMLTV ID, Now/Next and schedule information from the US screens. **No US EPG fetching is performed by the UI.**
- Search, favourites, refresh and the Global Free TV site link remain.
- Tap a channel to resolve its own Global Free TV page; HTTPS playback can open automatically in the working **US-LTV-2 Media3** player. Its Source 1 / Source 2 / Next source / Retry functionality is unchanged.
- Player and detail-screen back buttons now say **US channels**, and Back from playback returns directly to the channel list.
- **UK Freeview** keeps its existing August T405 tuner and full Sky-style TV guide.

Only two Kotlin screen files change:

- `app/src/main/java/com/personalmedia/android/UsLiveTvScreen.kt`
- `app/src/main/java/com/personalmedia/android/UsTvPlayerScreen.kt`

The legacy `UsTvEpg.kt` helper remains in the project for safe overlay compatibility but is no longer called from the US screen. It does not download guide data on its own; no existing saved settings are deleted.

## Install

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_USLTV3_Channels_Only_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install the APK **over** the existing app. Do not uninstall; the existing data, downloaded films, saved channels and signing must be preserved.

## Local checks

- 19 source/UI static assertions passed: no EPG, XMLTV, Now/Next or schedule controls in the US screen; channel-to-Media3 handoff still present.
- 13/13 Kotlin US-only, HTTP opt-in, HLS/DASH and source-fallback policy tests passed.
- Source ZIP diff verification: only the two listed Kotlin files and this README added/changed; no UK Freeview, MPV, native torrent, Gradle, signing or Android manifest files modified.
- Full Android Gradle compilation and on-device playback were **not** run in this environment.

## Test on Honor 90

1. Open **Live TV → US Internet TV**. Confirm a channel list appears with **no EPG settings, Now/Next, or programme guide**.
2. Tap the One America News Network US channel that already played on US-LTV-2. Confirm Media3 video still appears.
3. Try Source 2, source fallback, Retry and Back to US channels.
4. Switch to **UK Freeview** and confirm its Sky-style guide and tuner still work.
