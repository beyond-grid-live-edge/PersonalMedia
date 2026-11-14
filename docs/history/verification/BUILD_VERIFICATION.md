# Build / source verification (18 October 2026)

Environment: Linux x86_64, Java 21, Kotlin compiler 1.9, GCC 14, CMake and Ninja.

### Passed

- CMake/Ninja host compilation of copied `native_live_tv.cpp`, `streaming_torrent_session.cpp`, and `mpv_range_server.cpp` (native C++17).
- JNI bridge compiled with `c++ -std=c++17 -Wall -Wextra -Werror` against JDK JNI headers.
- Copied Matroska probe/cluster/seek/timeline/cues/MP4 sources compiled separately with host C++17.
- `ctest` reports **5/5 passing tests**: native fail-closed smoke, full simulated UDP tuner + HTTP MPEG-TS, battery probe, receiver lifecycle, and native T405 host test.
- Standard `javac` successfully compiled the Java Gradle bootstrap launcher; JAR manifest/classes inspected.

### Blocked / not verified

- `./gradlew :app:assembleDebug` was **actually attempted**. It failed at bootstrap dependency download with:

  `java.net.UnknownHostException: services.gradle.org`

- No Android SDK, Android NDK, Android Studio, Gradle distribution, `android.jar`, native libmpv binaries, target-ABI OpenSSL/Boost, or libtorrent source checkout were present.
- The installed standalone `kotlinc` cannot compile Compose/Android classes without their Maven/SDK dependencies; its errors are unresolved Android/Compose references, not an Android Gradle compile diagnostic. Kotlin/Compose/AGP compiler errors **could not be checked or corrected on the real Android toolchain** here.
- **No debug APK was generated**, and no emulator/device, live Stremio account, real T405 tuner, Android native libtorrent session, or MPV EGL rendering was tested.

Only the independent Android Studio project is included in this ZIP; the provided reference project is not modified.
- Optional libmpv renderer passed host C++17 `-Wall -Wextra -Werror` syntax/type compilation against API-shaped local EGL/NativeWindow stubs; **not a substitute for real Android NDK headers**.
- Portable `MediaNaming.kt` compiled and its 7 classification assertions passed with the installed Kotlin JVM compiler. Compose sources cannot be fully type-checked without SDK/Maven artifacts.

## Layout-matching update (18 October 2026)

- Added `HarmonyLayouts.kt`: source-mapped Home cinema spotlight, local poster/landscape rails, grouped TV shows with seasons/episodes, library details, filters and favourites.
- Reworked `MainActivity.kt` horizontal header, Settings visual sections and global library search.
- Reworked `StremioScreen.kt` cinematic add-on Home, films/series rails, browse/search/library/detail/source cards, local Continue Watching (`StremioWatchStore.kt`). Added optional background artwork from catalog response in `StremioClient.kt`.
- Reworked `LiveTvScreen.kt` integrated player + fixed-height independent guide scrolling at the original responsive breakpoint, tuner controls and battery status.
- Copied the exact original `pm_logo_full.png` logo into Android drawable resources, retained `pm_mark.png`.
- CMake + Ninja build and CTest: **5/5 passed** again. Kotlin media-naming standalone smoke: **PASS**.
- Reattempted the Android Gradle assemble command: still stopped before compilation at `UnknownHostException: services.gradle.org`. No compiled APK or on-device visual comparison; **cannot claim pixel-perfect layout or full Android compiler success**.
