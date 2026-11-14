# PersonalMedia Android STR-7B — Artwork & Metadata Parity

## Apply to an existing STR-7A installation

From the existing PersonalMedia Android project directory:

```powershell
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_STR7B_Artwork_Metadata_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install over the existing APK; **do not uninstall**. This overlay contains only changed or added source/tests/docs and never changes signing, native libraries, application identity, download storage, UK Freeview or US Internet TV.

## New behaviour

- Settings > Metadata & Artwork offers Auto, TMDB, OMDb and Free.
- Provider priorities: Auto = TMDB (when supplied) -> OMDb (when supplied) -> Wikipedia (films) / TVmaze (series). Choosing TMDB or OMDb prioritises that provider and falls back to the appropriate free service. Free never calls a keyed service.
- TMDB API v3 keys or read tokens and OMDb API keys are **session memory only**; never saved in SharedPreferences/catalog, and not included in log/status messages. Provider preferences are persisted separately.
- Choose 25 unique titles per batch or up to 2,000. Manual fetch, Retry missing and Cancel are supported. Optional automatic 25-title fetch follows a completed folder scan only; it does not run for incomplete scans.
- One remote request sequence per grouped movie or TV series, not per edition or episode. Exact normalized title and known release year must agree. Different-year remakes are never merged.
- Existing artwork and catalog identities, original file URIs, favourites, watched state, resume positions, download records and folders are not replaced or deleted. Artwork fills missing fields only. Plots, genres and credits are displayed in local details.
- Metadata fields are backward compatible with earlier saved JSON; no migration or catalog reset is required.
- Fetch uses HTTPS endpoints with bounded JSON responses, timeouts, rate pacing and allowlisted artwork image hosts.
- TVmaze image/display data is attributed and TVmaze's public API is CC BY-SA. TMDB and Wikimedia credits appear in Settings; individual entries also identify their metadata source.

## API endpoints

- `https://api.themoviedb.org/3/search/movie` and `/search/tv` (optional user credential)
- `https://www.omdbapi.com/` (optional user credential)
- `https://api.tvmaze.com/singlesearch/shows` (free, conservative single-series match)
- `https://en.wikipedia.org/w/rest.php/v1/search/page` (free film page search)

## Important limitations

- This patch cannot guarantee a match for every title. Similar names, ambiguous entries, poor article images and unavailable providers are left unchanged rather than given incorrect artwork.
- Existing local artwork is preferred, and HTTP/non-HTTPS image addresses are not adopted.
- Artwork data is not packaged into the APK; internet access is required for fetching and viewing uncached remote images. Coil's normal local image cache remains in use.
- Full Android Gradle/NDK build, live API responses and on-device result verification require testing in the existing Windows ARM64/Android Studio environment.
- **Before distributing to users**: comply with each provider's artwork/metadata licence. TMDB also requires an approved TMDB logo in the app's credits/about section, beyond the included disclaimer and credits link; no unapproved logo asset is bundled in this overlay.
- This is STR-7B artwork and metadata parity, not manual per-film Audio/CC memory or comprehensive backup/export parity.

## Suggested device test

1. Open Settings > Metadata & Artwork. Choose **Free**, leave automatic fetching off, then click **Fetch artwork**. Test a folder with uniquely named movies and a TV series.
2. Inspect posters on Movies/TV Shows/Home, then the About section in a local movie/TV detail. Confirm existing file artwork is not overwritten.
3. Check an alternate movie edition: both files must keep their playback buttons and appear behind one poster. A remake with a different known year must stay separate.
4. Restart PersonalMedia; artwork and provider selection should remain, but API key fields should clear.
5. Optionally provide your own TMDB or OMDb credential and test these providers. Avoid sharing tokens in screenshots/logs.
