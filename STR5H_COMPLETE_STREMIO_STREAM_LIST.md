# PersonalMedia Android STR-5H — Complete Stremio streams / quality filters

## Reproduced cause

STR-5G's `StremioClient.sources()` called `.optJSONArray("streams").orEmpty().objects().take(40)` for every add-on. Providers such as Torrentio often order returned streams from higher quality down to lower quality. When the first 40 entries are all 4K, PersonalMedia discarded every lower-resolution entry before the UI saw it. The stream list was therefore not equivalent to the official Stremio app.

The official Stremio add-on protocol supplies `streams` as a list of stream objects. Its API doesn't specify a fixed 40-entry display limit.

The official Stremio APK supplied for comparison contains `libstremio_core_kotlin.so` and other runtime components, but no proprietary source/binary was copied into PersonalMedia. The source fix is based on the public add-on protocol and the user's screenshots.

## Changes in this overlay

- Read the **complete** stream array returned by every installed, compatible add-on; remove the artificial `.take(40)` cap. The existing JSON network response has an 8 MiB safety limit per provider, not a quality/result count limit.
- Query all compatible installed stream add-ons concurrently, rather than checking a maximum of 15 sequentially. Preserve provider order and duplicates, because separate entries can have different sizes, languages, trackers, or variants.
- Classify 4K/2160p, 1440p, 1080p, 720p, 480p and other resolutions using the release title first, rather than a sometimes misleading provider name.
- Add quality chips showing **All / 4K / 1440p / 1080p / 720p / 480p / Other** with the actual returned counts. Only show a chip for qualities that exist.
- Render cards in batches of 48 with a **Show more streams** button. This limits Compose work without discarding any fetched sources.
- Show total stream count, queried provider count and the count of failed providers without leaking configured add-on addresses, account tokens or private stream URLs.
- Keep existing Play, Download, native verified-piece torrent playback, HTTP bridge, source-specific failure/cancel status and navigation unchanged.

## Install over STR-5G

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR5H_Full_Stremio_Streams_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install the resulting `app\build\outputs\apk\debug\app-debug.apk` as an update. Do not uninstall the existing app; this preserves signing identity and local saved data. Do not run the ordinary torrent-disabled build.

## Device acceptance tests

1. In both PersonalMedia and Stremio, open the **same Backrooms title or episode** with the same Stremio account/add-on configuration.
2. Tap **Find streams** in PersonalMedia; read the total count and provider count.
3. Tap **1080p** or **720p** rather than scrolling through 4K torrents. If there are many results, use **Show more streams**.
4. Confirm a stream Play button still prepares torrents or starts direct MPV playback as it did before.
5. Test a different film with 4K and lower-quality sources, then test a series episode.
6. If the original Stremio app still displays more streams: compare add-on versions/configuration and the exact title/video ID; any failed-provider count or provider-side limits must be investigated separately. PersonalMedia cannot show streams a provider excludes or fails to return.
7. Verify Freeview, compact player, cancellation and installed add-on manager remain working.

## Validation

4/4 JVM tests passed for quality parsing, >40-return stream availability, full-list batched visibility and preserving provider entries. This is a source-only overlay. Full Android Gradle compile and live add-on responses have **not** been tested in this container.
