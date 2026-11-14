# STR-5B — Real NDK libtorrent/Boost/OpenSSL linkage

**Baseline:** PersonalMedia Android STR-5A.1 (POST-redirect unit-test fix). Only files within the Android project are changed. The original HarmonyOS project, Android package ID, signing files, Freeview tuner, native MPV, Stremio HTTPS bridge and account sync are unchanged.

## What this stage provides

The previous Android CMake only enabled `PM_WITH_LIBTORRENT` when a complete in-tree libtorrent source checkout appeared, and its preflight asked for manually compiled Boost/OpenSSL archives. This was prone to incorrectly linking host binaries or silently generating an APK without torrents.

STR-5B provides an executable Windows PowerShell dependency-build workflow using the actual **Microsoft vcpkg `libtorrent` port** (libtorrent-rasterbar 2.x, *not* Rakshasa's libtorrent). vcpkg obtains the official libtorrent sources and compiles real Boost and OpenSSL dependencies using the Android NDK. No proprietary August binaries, HarmonyOS `.so` files, stub `.a` files or Linux/Windows-target libraries are shipped in this overlay.

STR-5B adds strict `-PpmTorrent=true` opt-in to the **Android Gradle + CMake** build, with imported `LibtorrentRasterbar::torrent-rasterbar` target linkage and per-ABI dependency prefixes. It defines `PM_WITH_LIBTORRENT=1` only when dependency configuration is successful, and prevents torrent-enabled mode from falling back to a torrent-disabled APK when dependencies are missing. Normal `assembleDebug` retains the current working P2P-disabled baseline.

**Four target ABIs:** `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`. Each is built with its own custom vcpkg triplet targeting Android API 26 to match this application's `minSdk=26`. The dependencies link statically into the app's existing `libpersonalmedia.so` through the exported package target. This retains one universal APK without four separately signed APKs.

## Windows 11 ARM64 WindowsVM setup

Requirements: your working unofficial ARM64 Android Studio, **Android NDK 27.0.12077973** or another appropriate installed version, CMake, Git, and a working Windows C/C++ build environment for any vcpkg host tools. Some x64 Windows host executables may need Windows-on-ARM emulation; actual host compatibility must be confirmed in your VM. `vcpkg` can automatically download prerequisite sources and host tools, but this needs internet access and substantial disk space.

**Important:** use the commands inside the `PersonalMedia` Android Studio project folder, not the HarmonyOS parent project. This script clones *official Microsoft vcpkg* into `%USERPROFILE%\CodeTools\vcpkg` by default. It does not overwrite your project or signing certificates.

### First attempt: ARM64-only device build

This is the faster way to capture actual native compiler/linker errors for your Honor 90 and other ARM64 Android test devices:

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR5B_NDK_Libtorrent_Overlay.zip" -DestinationPath . -Force
.\tools\STR5B-Build-Libtorrent.ps1 -Arm64Only
```

The build script downloads/builds genuine Android-target libtorrent, Boost and OpenSSL for **arm64-v8a** and then runs:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest -PpmTorrent=true -PpmTorrentAbis=arm64-v8a --stacktrace
```

This first APK is **ARM64-only**, by design. Its native library still needs on-device validation. **Do not run `Verify-UniversalApk.ps1` on an ARM64-only build**; it is not universal.

### After ARM64 works: one four-ABI APK

```powershell
.\tools\STR5B-Build-Libtorrent.ps1
```

The script installs dependencies for all four Android ABIs, runs strict native build/tests and then `Verify-UniversalApk.ps1`. A single resulting `app\build\outputs\apk\debug\app-debug.apk` contains the four target versions of `libpersonalmedia.so` if compilation and packaging succeed.

### Existing installed vcpkg / NDK

```powershell
.\tools\STR5B-Build-Libtorrent.ps1 -VcpkgRoot "C:\Tools\vcpkg" -NdkRoot "$env:LOCALAPPDATA\Android\Sdk\ndk\27.0.12077973" -Arm64Only
```

If a dependency fails, preserve **the first** vcpkg/CMake/C++ compiler/linker error (rather than only the final Gradle stack trace). The script exits without claiming success. Re-run after making a source-level fix; vcpkg reuses successfully built package archives. To check staged packages without rebuilding, use:

```powershell
.\tools\STR5B-Build-Libtorrent.ps1 -VerifyOnly -Arm64Only
.\tools\STR5-Native-Dependency-Preflight.ps1 -Arm64Only
```

The old preflight has been updated to recognise vcpkg's real Android build layout.

## Acceptance criteria once the real build passes

1. Android app reports `NativeBridge.engineAvailable() == true` on the actual device. A Gradle success with `pmTorrent` **disabled** is not evidence of this.
2. With P2P consent enabled, select a legitimate torrent source. Confirm that libtorrent fetches genuine hash-validated torrent metadata and peers and provides actual verified pieces to the native Range server.
3. Select the intended video file. Validate media picture/audio, pause, seeking and cancellation via the existing native MPV player.
4. Confirm saved Stremio watched progress, a working direct HTTPS bridge, T405 Live TV/Sky guide and Guardian remain unaffected. Stop should release the torrent session and port.
5. Test another supported ABI before claiming full universal torrent functionality.

## Security and limitations

- Peer-to-peer traffic exposes your public IP address to swarm peers. Only enable P2P for content you are authorised to access.
- The existing C++ code verifies pieces through libtorrent before serving bytes; this stage does **not** replace or relax that requirement.
- Do not mistake CMake configuration or archive presence for an actual successful linker build.
- A vcpkg build may require host C++ development tools on Windows ARM64 and could uncover genuine upstream/NDK compatibility errors. Those errors must be resolved from the real build output; this source archive cannot pre-verify them without an Android NDK.
- Torrent playback has **not** been demonstrated on the real Android phone as part of STR-5B. No torrent-enabled debug APK is supplied in this ZIP; you must run the dependency builder in your VM.
- This integration deliberately does **not** alter the functioning Stremio HTTPS bridge or August Freeview native decoder.

## Local checks in this source-production environment

- 12/12 existing portable/native host CMake/CTest cases passed, including the STR-5 verified file-selection test.
- CMake-on and CMake-off branches configured using four **mocked package layouts** solely to validate per-ABI CMake syntax; missing native dependencies correctly failed during configure. Mock libraries were **not** included in the delivery and were **not** used to produce an APK.
- No real Android libtorrent + Boost + OpenSSL cross-compile or physical peer/MPV playback test was possible here.

Official build references:
- https://vcpkg.io/en/package/libtorrent.html
- https://learn.microsoft.com/en-us/vcpkg/users/platforms/android
- https://developer.android.com/ndk/guides/cmake
- https://libtorrent.org/building.html
