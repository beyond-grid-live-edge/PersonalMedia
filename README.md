# PersonalMedia — Android native port (C20.0.7 source-based)

This directory is an **independent Android Studio project**. It can be built and installed on supported Android devices independently. Android package: `com.personalmedia.android`. It uses **Kotlin, Jetpack Compose, AGP 8.7.3, Kotlin 2.0.21, Android NDK/CMake, JNI, and a Media3 player**. A libmpv EGL renderer is implemented but needs compatible MPV binaries. The unchanged native torrent streaming/range-server and Siano T405 receiver are copied into `app/src/main/cpp/portable/`.

## Actual implementation status

| Subsystem | Android implementation | Validation |
|---|---|---|
| Native horizontal navigation / Home / Movies / TV Shows / Music / Pictures / Favourites / Settings | Source-mapped Compose layouts, cinematic Home and rails, grouped seasons, portrait tiles, global search, native file/picture viewer, favourites | A prior revision compiled in Android Studio; this update requires a new build |
| Local media library | Android Storage Access Framework, persistent user-selected folder/file grants, recursive scan, private record persistence, watch-position tracking | Local media playback and scanning still require specific on-device checks |
| Stremio account integration + cinema layout | Native Kotlin HTTPS login/key verification, Android Keystore-backed key persistence, add-on cinema hero, poster rails, local Continue Watching, catalog search, seasons/episodes, streams, account library get/put | Real Stremio service/account not tested |
| Local/direct video playback | Native Media3 ExoPlayer/PlayerView for SAF, HTTP and HLS/TS | Local media playback and scanning still require specific on-device checks |
| MPV | Optional native EGL/libmpv SurfaceView frontend in `mpv_surface.cpp`, uses `dlopen`/public libmpv render API | **No libmpv binaries in source ZIP; renderer not run/tested** |
| Libtorrent verified pieces | Original persistent libtorrent code and authenticated-piece range server, Android JNI interface and gated playback | Host fail-closed test passed; **libtorrent checkout absent, playback disabled by default** |
| August T405 Live TV | Original native Siano UDP tuner, scans, PID and EIT guide, local MPEG-TS server, two-column embedded player/guide and battery diagnostics | Six host-native tests passed; Android screenshot shows 5 multiplexes, 212 services and active TS packets; video still tuning |
| Android debug APK | `:app:assembleDebug` target defined | A prior debug APK was built and installed by the user; this source update has not been APK-built here |

**Do not regard this update as a fully validated, feature-parity release.** In particular, MPV binaries and libtorrent source/dependencies are external requirements. Android Compose screens were recreated from the provided PersonalMedia interface references, but are **not verified pixel-identical replicas**; see `LAYOUT_PORT_REPORT.md`. Receiver transport and channel discovery have been observed on an Android device; video decoding, MPV, torrent playback, and account synchronization still require targeted validation.

## Layout port reference

See [`LAYOUT_PORT_REPORT.md`](LAYOUT_PORT_REPORT.md) for a screen-by-screen overview of the interface and its native Compose implementation and an explicit list of differences.

## Build on a connected Android Studio machine

1. Open the **`android/`** directory itself in Android Studio. Allow Gradle sync to resolve the wrapper version and dependencies; install Android SDK platform **35**, SDK Build Tools, **CMake 3.22.1**, and a compatible **Android NDK**. Use a Java version compatible with the selected Gradle wrapper. Default ABIs are `arm64-v8a` and `x86_64`.
2. Run `./gradlew :app:assembleDebug` (Windows PowerShell: `.\gradlew.bat :app:assembleDebug`). Output when successful: `android/app/build/outputs/apk/debug/app-debug.apk`.
3. The supplied `gradle-wrapper.jar` is an **auditable standalone bootstrap**, compiled from `gradle/bootstrap-src/` because the official Gradle wrapper could not be downloaded. It downloads Gradle 8.9 from the official Gradle distribution endpoint, verifies the published SHA-256, then launches it. Once Gradle is installed, you may replace the bootstrap with the official wrapper by running `gradle wrapper --gradle-version 8.9` (or using Android Studio's Gradle settings). If your environment disallows internet, install the SDK and Gradle distribution locally first.
4. Install the resulting debug APK on a device connected to the correct Wi-Fi to use the T405. Grant only user-selected SAF media URI permissions.

## Optional native libraries

**Libtorrent**: The C20.0.7 ZIP does **not** include the libtorrent 2.x source or third-party Boost and target-ABI OpenSSL dependencies. To enable torrents, provide libtorrent sources under `app/src/main/cpp/third_party/libtorrent`, compatible Boost headers (for example `third_party/boost_1_89_0`), and Android-target OpenSSL headers/libraries. The CMake configuration detects the libtorrent tree and enables `PM_WITH_LIBTORRENT=1` only when present. Android CMake must resolve `OpenSSL::SSL` and `OpenSSL::Crypto` for each ABI; never link host `.so` files. Do **not** enable the legacy unverified network probe as a fallback. `ReadStreamingVerified` checks `have_piece` before reading each byte range and fails closed if content is missing.

**MPV**: To enable the native libmpv renderer, build an Android-compatible `libmpv.so` and all its ABI-compatible dependent libraries; place them in `app/src/main/jniLibs/arm64-v8a/` (and `x86_64/` if desired). `mpv_surface.cpp` uses dynamic symbol resolution and an EGL2 surface, so the project compiles with or without these binaries. Only remote HTTP(S) streams are sent through the MPV EGL path; SAF content URIs use Media3 because libmpv does not automatically possess Android content-provider grants. Actual MPV binary version, EGL rendering and hardware decode need device testing; if MPV fails, the UI switches to Media3 rather than claiming MPV playback succeeded.

**T405**: Connect device Wi-Fi to the August DVB-T405 access point (usually `192.168.1.1`) and ensure the Android Wi-Fi route actually reaches the tuner. The app listens on UDP 5001 and sends control messages to UDP 5002. Connect, probe for a **real firmware response**, scan `uk_freeview` or `eu_uhf`, then select a discovered unencrypted service. The C++ TS server binds only `127.0.0.1`. No DVB-T2, USB driver, encrypted channels, or verified battery percentage support is claimed.

## Security / privacy notes

- Stremio passwords are used only for HTTPS authentication and are not persisted; session keys are encrypted with Android Keystore-backed preferences. Add-on metadata and stream links may still carry sensitive provider tokens in memory: do not log them.
- Cleartext HTTP is enabled to support local T405 playback and optional HTTP-based add-ons; external add-ons are third-party endpoints, not trusted first-party servers. Stremio auth requests always use `https://api.strem.io`.
- Torrent peer-to-peer networks expose client IP addresses. Download and share only content you are authorized to access.
- File selection uses scoped SAF APIs; no broad storage access is requested. Removing library rows does not delete originals.

## Verification available without Android SDK

```sh
cmake -S tests -B /tmp/pm-tests -G Ninja
cmake --build /tmp/pm-tests
ctest --test-dir /tmp/pm-tests --output-on-failure
```

Tests compile directly from `app/src/main/cpp/portable/` and exercise the mock Siano tuner, channel discovery, TS localhost response, opaque battery protocol, receiver lifecycle, and libtorrent fail-closed behavior. JNI compilation was also validated against the installed Java 21 `jni.h`. This **does not** replace compiling the real Android target with the NDK or exercising real T405 hardware and Stremio.

See [BUILD_VERIFICATION.md](BUILD_VERIFICATION.md) for exact checks, results, and current blocking error.
