# PersonalMedia Android Compose layout implementation

Based on the PersonalMedia C20.0.7 interface and Live TV source references. Only the independently buildable Android Studio project is included in this ZIP; the reference application source remains separate.

## Matching implementation

| Source interface element | Android implementation | Ported visual elements |
| --- | --- | --- |
| `Header()`, `NavPill()` | `MainActivity.kt` (`HarmonyHeader`) | Dark 58dp horizontal top bar, 9 tabs, icon, pill selection, search/import/profile actions, adaptive horizontally scrolling nav |
| `HomeSpotlight()` | `HarmonyLayouts.kt` (`PmHomeHero`) | Blue-purple cinema gradient, original PersonalMedia logo, headline and featured state, Play/Info controls, featured carousel, responsive height |
| `HomeQuickLinks()`, `Shelf()`, `Tile()`, `ContinueWatchingShelf()` | `HarmonyLayouts.kt` | Media quick links, landscape and portrait tiles, dark image overlays, horizontal rails and resume cards |
| `LibraryPage()`, `SeriesTile()`, `SeriesDetailsView()`, `TvEpisodeRow()`, `DetailsView()` | `HarmonyLayouts.kt` | 42sp library headings, category tabs, filtering/sorting, TV show group tiles, season/episode details and local favourites/watched actions |
| `Stremio()`, `StremioBrowser()`, `StremioCinemaHome()` | `StremioScreen.kt` | Your Cinema heading, account banner, sub-navigation, add-on featured hero, Discover Movies/Series rails, local Continue Watching, search, library |
| `StremioDetails()`, `StremioCinemaSourcePanel()` | `StremioScreen.kt` | Poster/backdrop detail header, season/episode picker, side-by-side (wide) or stacked (narrow) source cards |
| `LiveTvScreen.PlayerPanel()`, `.ChannelPanel()`, `.TunerSettings()` | `LiveTvScreen.kt` | Dark Freeview dashboard, 62/38 wide player and guide, embedded video surface, Now/Next, TS rate, lock state, scrollable channel list, tuner settings, battery diagnostics; stacks when narrow |
| `Settings()` | `MainActivity.kt` (`SettingsScreen`) | PersonalMedia heading, native status card, media sources, capability/backup/artwork sections |

Android-side files intentionally remain Kotlin + Compose with real native Android controls. No WebView or screenshot overlay replaces the working interface. T405 transport code, JNI, optional MPV frontend, and libtorrent-verified-piece C++ code are retained from the previous Android port.

## Important differences / limits

This is a source-guided **layout recreation**, not a verified pixel-for-pixel or full behavioural identity. A pixel-by-pixel visual comparison has not been performed. Visual sizes vary with Android device density, window size, fonts and system insets. Some reference application features remain unported (server migration, online local-metadata provider, exact film franchise grouping, specialized stream diagnostics and audio track UI). Local artwork uses user-provided companion image files where available; Stremio artwork uses live add-on metadata. The original catalog, its images and account/watch data were not included in the ZIP, so they cannot be preloaded.

Stremio watch progress is tracked for Android Media3 playback; the optional libmpv EGL path does not yet report accurate progress. Torrent playback remains gated until Android-target libtorrent/Boost/OpenSSL dependencies are supplied. The T405 embedded player uses native Android Media3; optional MPV is used for full-screen remote playback when a compatible library is present. This difference does not mean the MPV binary was bundled.

## Validation actually performed

- All five host-native CMake/CTest checks passed against the retained C++ test targets.
- Standalone Kotlin `MediaNaming.kt` and `MediaNamingSmoke.kt` compile and the smoke test passes.
- File inspection confirms `pm_mark.png` and the original `pm_logo_full.png` are present in Android resources.
- `./gradlew :app:assembleDebug` was attempted; Gradle bootstrap failed at `services.gradle.org` with `UnknownHostException`. The environment has no Android SDK/NDK, Gradle distribution, Android Compose dependencies, emulator or physical T405 tuner. **Android/Kotlin Compose compilation, pixel-diff and APK output were not verified.**

## Opening in Android Studio

Extract this ZIP, then open the `android/` folder in Android Studio. Install SDK 35, NDK and CMake through SDK Manager; allow Gradle sync to download project dependencies. Run `:app:assembleDebug` and address any Android-specific compiler diagnostics there before relying on an APK. See `README.md` and `BUILD_VERIFICATION.md` for native dependency instructions and test scope.
