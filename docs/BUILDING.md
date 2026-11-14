# Build guide — Android STR-7D

The project uses Android Gradle Plugin **8.7.3**, Kotlin **2.0.21**, Gradle **8.9**, Android SDK **35** (minimum API 26), C++17 and **CMake 3.22.1**.

## Prerequisites

Android Studio, Android SDK Platform 35, a compatible NDK, CMake 3.22.1, **JDK 17+** including `javac` and `jar`. Tested development setup: Windows 11 ARM64 VM. The first-party source repository deliberately excludes native binaries and signing secrets.

## Step 1 — Fresh-clone wrapper bootstrap

~~~powershell
.\tools\Bootstrap-GradleWrapper.ps1
~~~

This builds the custom checked-in Java Gradle launcher into ignored `gradle/wrapper/gradle-wrapper.jar`. It leaves an existing JAR untouched. The bootstrap downloads **Gradle 8.9** and checks the pinned SHA-256. It is **not** the standard Gradle wrapper; you may generate the official wrapper with a separately installed compatible Gradle version.

## Step 2 — Native MPV

Stage Android-compatible `libmpv.so` and every required ABI dependency, for example using:

~~~powershell
.\tools\Install-OfficialMpv.ps1
~~~

Or use `tools/Stage-AndroidMpv.ps1` with a vetted local source. The resulting `app/src/main/jniLibs/` is excluded from Git. Review native redistribution licences before sharing APKs.

## Step 3 — libtorrent for ARM64

Run the **manual** [GitHub Actions dependency workflow](../.github/workflows/personalmedia-android-libtorrent.yml), using `targets = arm64-only`. Download its outer `PersonalMedia-libtorrent-arm64-v8a` artifact ZIP. Then:

~~~powershell
.\tools\STR5C-Install-GitHub-Dependencies.ps1 -ArtifactZip "$env:USERPROFILE\Downloads\PersonalMedia-libtorrent-arm64-v8a.zip" -Arm64Only -Build
~~~

This validates the inner SHA-256, headers, actual static libraries, ABI and CMake configuration. Installed artifacts live in ignored `native-deps/installed/`.

Once imported, subsequent source builds are simply:

~~~powershell
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
~~~

The script enables `pmTorrent=true`, compiles and runs unit tests. A build failure does not produce a new installable APK—**do not install a stale APK**. For four ABIs, import all four matching artifacts; ARM64-only is not universal.

## Baseline (no libtorrent)

~~~powershell
.\gradlew.bat :app:assembleDebug
~~~

This builds the non-torrent baseline. Do not accidentally use it to replace a working torrent-enabled installation.

## Tests and output

`app/build/outputs/apk/debug/app-debug.apk` is the debug output after a successful build. Native host regressions are available with:

~~~text
cmake -S tests -B out/host-tests -G Ninja
cmake --build out/host-tests
ctest --test-dir out/host-tests --output-on-failure
~~~

Host tests do not replace Android hardware validation. SAF folder grants may need reconnecting on a new installation. Torrent playback and torrent downloads share one native session. Android restricts long-running foreground download services; resume may be required. Install over the existing same-signed application to keep data.
