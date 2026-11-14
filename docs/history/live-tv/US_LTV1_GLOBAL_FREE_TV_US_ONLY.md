# US-LTV-1 — PersonalMedia Android US Internet TV (Global Free TV only)

## Scope

**Independent of the August DVB-T405 Freeview implementation.** Live TV now offers UK Freeview and a separate US Internet TV tab. Its channel records originate only in the US public directory pages at:

- `https://www.globalfreetv.com/countries/united-states`
- `https://www.globalfreetv.com/us-tv`

Channel IDs must end with `.us`; other countries and unrelated websites are rejected. Press Refresh to read the directory's currently public HTML. Results are cached; favorites and last-chosen source number persist.

### Provider limitation — important

Global Free TV currently describes over 1,000 US channels, but its complete catalogue and source selectors can be populated dynamically. A **documented complete public API / licensed bulk export was NOT verified**. US-LTV-1 can only discover channel links actually supplied in those public pages (including embedded JSON) and individual media links actually exposed in the selected channel's public HTML. Therefore **the entire US channel list and native playable URL for every channel are NOT guaranteed**.

If the channel page contains no native-playable media URL, the app clearly says so and offers **Open on Global Free TV**. This opens the original site and its Source 1/2/3 browser controls; it does not masquerade as native playback.

No third-party IPTV data source is used for streams, even for channels with missing links. It does not bypass site authentication, DRM, embedded-player restrictions or geo-restrictions.

## Features

- Live TV tabs: **UK Freeview · T405** and **US Internet TV**.
- US-only native searchable channel list, favorite toggle, remembered source selection, browser page fallback.
- On-demand per-channel source reading. Native libmpv via existing compact Stremio MPV player for valid HTTPS HLS/DASH/video links that the site's page exposes. Source buttons follow the order detected in HTML; this may differ from the site's rendered Source 1/2/3 order when some sources are browser-only.
- HTTP (unencrypted) sources require explicit per-session opt-in. Local/private media endpoints are refused.
- Independently configurable external US XMLTV programme guide, with current/next programme labels, exact name matching and manually overridden XMLTV IDs for local US affiliates.
- EPG source is **metadata only**. Selecting a guide row loads that channel's Global Free TV page and/or its extracted stream, *never* a stream link supplied by the guide provider.
- The default guide URL comes from IPTV-EPG.org's published US guide index: `https://iptv-epg.org/files/epg-us.xml.gz`. It can be changed in EPG settings. Its size and uptime are outside PersonalMedia's control. Refresh is deliberately user-triggered due to potentially very large US guide datasets.
- XMLTV parsing is streaming/bounded with no disk download; guide times display in the device time zone. Unmatched stations show **No guide data** rather than a guessed schedule.

## Install over the most recent STR-6A source

Extract `PersonalMedia_Android_USLTV1_Global_Free_TV_US_Only_Overlay.zip` **into the root of your existing PersonalMedia Android project**. This is a source-only overlay. It does not change any `.so`, NDK dependency, signing file, package identity, CMake or Live TV tuner source. Keep your already imported GitHub ARM64 libtorrent dependencies.

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_USLTV1_Global_Free_TV_US_Only_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install the APK as an update, not after uninstalling PersonalMedia.

## Acceptance checks

1. In Live TV, choose US Internet TV. Verify the UK Freeview mode remains unchanged when switching back.
2. Refresh US channels. The app states exactly how many US channel URLs the public page exposed; it must not claim all 1,000+ unless actually discovered.
3. Select a US channel. If the site page exposes a direct media URL, choose Source 1/2 and try native MPV. If not, use **Open on Global Free TV** and inspect the website's source selector.
4. Open EPG settings, refresh the external XMLTV feed, verify only exact channel matches get programmes. For ambiguous affiliates, enter an exact XMLTV channel ID on that channel's details screen and refresh.
5. Verify favorites, changing sources, navigating back, and switching to your August DVB-T405 Freeview feed.

## Validation and known constraints

- The local JVM policy suite covers US channel isolation, parsing, URL filtering, duplicate media URLs and XMLTV time zones.
- Real Global Free TV channel playback, complete catalogue coverage, XMLTV feed download and full Android Gradle compilation have **not been verified in this environment**. A site-supported bulk API or explicit provider permission would be required to guarantee complete live synchronisation of all US channels and every source.
- Provider streams may be subject to licensing/region restrictions; follow their access requirements.
