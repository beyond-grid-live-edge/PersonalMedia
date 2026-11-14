# PersonalMedia Android STR-7C — Per-title Audio & Subtitle Memory

## Overlay baseline

Apply to the **STR-7B1 corrected Android project** that already builds with your ARM64 cloud libtorrent libraries. Overlay contains only changed or newly added files; it does **not** replace package identity, signing, native C++, Gradle, MPV, Freeview, US channels-only TV, downloads or media library.

## Behaviour

- **Stremio MPV (torrent and direct HTTPS):** audio tracks and embedded subtitle tracks are remembered for the Stremio account + movie/series ID + video/episode ID; the selection survives changing provider source URLs and torrent loopback endpoints. The existing Audio and CC menus also offer **Use global default**, clearing this title's choice.
- **Local Media3 playback:** adds Audio and CC menus above the video. Selecting a track, Auto or CC Off saves a choice for that library document. Changes via Media3's built-in track selection controls are also observed when they change track-selection parameters.
- **External Stremio subtitles:** remembers language and a list position, **not the provider URL**. When replaying, restores only from the currently supplied safe subtitle list; no URL gets saved.
- Tracks are chosen by language and actual metadata title (plus duplicate occurrence), never a raw MPV track number. If a previously selected track is missing or ambiguous, global default applies.
- When no title-specific setting exists, all STR-7A global playback preferences remain active.
- On Android, the original media file is never modified and no library watched/resume/download metadata is rewritten by this feature.
- No website identifiers, URLs, torrent/magnet links, provider headers or media document URIs are persisted in track memory. Keys are SHA-256 hashes of account/title/episode or local content URI; values contain only the chosen language, track title and mode.

## Build on Windows ARM64

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_STR7C_Per_Title_Audio_Subtitle_Memory_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install the updated APK **over** your existing PersonalMedia. Do not uninstall or clear data.

## Device tests

1. In Stremio, play a film containing two audio tracks. Select Audio > the alternate language, set CC Off or an available subtitle. Close and replay the **same title**; check the stored choices are restored.
2. Switch to another source or torrent of the **same film**. If that source contains an identically identified language/track, the choice should follow it. If not, global defaults should take over.
3. Play a different film or a second episode; ensure the first film's choices do not leak.
4. Select **Use global audio default** and **Use global subtitle default**; replay and confirm STR-7A global preferences are applied.
5. Import or play a local video with multiple tracks, use Media3 Audio and CC selectors, close and replay it. Restart the app and try again.
6. Verify existing UK Freeview/US Live TV, libtorrent torrent playback and Downloads still function.

## Validation performed here

- 8/8 pure Kotlin memory-policy tests passed (identity, audio/CC match, dub/commentary distinction, source ID remapping, external subtitle and defaults).
- Policy and preference store compiled against local Android/JSON stubs.
- A full Android Gradle/NDK build and device playback are **not available** in this environment. If a Kotlin error appears, share the first `e:` lines from `compileDebugKotlin`.

## Design constraints

- Sound/subtitle tracks without identifying language *or* title cannot be reliably restored; the app will not guess based solely on transient track indexes.
- Some provider/external subtitles are unavailable on later playbacks; this is handled by global fallback, not a stale URL.
- Local files reconnected under a completely new Android document URI receive a new memory key; the file's content, history and scanned metadata are unaffected.
