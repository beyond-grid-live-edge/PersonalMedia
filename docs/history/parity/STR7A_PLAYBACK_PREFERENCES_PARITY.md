# STR-7A — HarmonyOS playback-preference parity (Android)

Baseline: **PersonalMedia Android STR-6B**, including US-LTV-3 channels-only and the STR-5J library changes. This is a source-only overlay; keep your existing signing and native ARM64 dependencies.

## Confirmed code comparison

| Area | HarmonyOS native C20.0.23 reference | Android before STR-7A | STR-7A change |
| --- | --- | --- | --- |
| Saved audio language | Auto / English / French / German | Audio tracks selectable inside MPV only | Settings preference saved; applied to native MPV and local Media3 when tracks exist |
| Saved subtitle language | Auto / English / French / German | Player-level selection only | Settings preference saved; applied to native MPV and local Media3 |
| Show subtitles | Global on/off | Manual choice | Global default applied to Stremio MPV and local Media3, manual CC remains available |
| Screen awake | Global playback toggle | Most players forced screen-on | Toggle saved; local Media3, Stremio MPV, UK T405 and US Media3 honour it; ordinary playback pause clears flag where supported |
| Preferred stream quality | Auto / 4K / 1080p / 720p | Per-list stream filter only | Saved preferred resolution preselects matching Stremio filter, otherwise All; **no streams removed from the provider response** |
| UK Freeview | T405 tuner/guide with audio/CC | Native T405 already in Android | T405 native receiver/guide unchanged; awake flag now honours preference |
| US Internet TV | Android-only extension | Native Media3, multiple sources | Channel list and source fallback retained, awake flag honours preference |

## Explicitly still open for full HarmonyOS feature parity

1. **Local artwork and metadata providers:** HarmonyOS has TMDb / OMDb / Wikimedia / TVmaze integration and scan controls. Android still has only local sibling artwork association plus Stremio supplied artwork; this is the next substantial parity milestone.
2. **Per-film manual Audio/CC persistence:** STR-7A persists global defaults, not every manual track choice across a future replay. Automatic track preferences never override a choice made within the current playback session.
3. **Settings parity:** HarmonyOS includes more detailed artwork, backups, fallback strategy and quality controls. Android does not ship a native fallback transcoder, so STR-7A does not add a nonfunctional transcoding switch.
4. **Device-wide acceptance testing:** Build on Windows ARM64 Android Studio and test local Media3, Stremio MPV, US TV, UK Freeview, settings persistence and resumed downloads on the Honor 90. These were not run by this patch's author.

## Install over STR-6B

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_STR7A_Playback_Preferences_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install over your existing APK. **Do not uninstall**: the app's ID, existing library, Stremio account, saved downloads and Freeview channels are preserved.

## Test plan

1. Settings: set audio French, subtitles English + On, quality 1080p and screen awake Off. Restart PersonalMedia and verify choices remain.
2. Stremio: choose a movie with known multi-language tracks; the preferred available audio/CC track should be selected after MPV reports FILE_LOADED. Manual Audio and CC menus should still work.
3. Stremio: Find streams when provider returns 4K and 1080p; 1080p should be selected initially, and All should still show **every returned** source.
4. Local: Media3 plays a film; subtitle setting controls initial captions; pausing playback should release the keep-screen-awake flag.
5. US TV: Source 1/2 switching still works. UK Freeview: live guide, native tuning and Audio/CC should be unchanged.
6. STR-6: saved Backrooms file remains accessible; download queue and verified torrent playback are unaffected.

Pure Kotlin policy regression tests exercise language validation, resolution fallback, exact language matching and missing-track handling. Full Gradle and device testing are still required.
