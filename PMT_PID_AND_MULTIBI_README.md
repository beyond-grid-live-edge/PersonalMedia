# PersonalMedia Android — PMT/PID tuning stage + universal APK

## What's changed

- Selective T405 filter programming: the receiver does not blindly add every PMT PID advertised on the active multiplex, which may exceed firmware PID-filter capacity.
- On channel selection, tune to its actual multiplex, wait for the broadcast PAT, and request that channel's **one** mapped PMT PID; retry the request at a bounded interval if a full PMT is not received.
- Wait for a **complete assembled PMT section**, including multi-packet programme maps, then request only the selected programme's PCR and elementary stream PIDs.
- Reconstruct full MPEG-TS PSI prefix for Media3: a programme-specific PAT (with MPEG CRC) and the complete multi-packet PMT. The former one-packet table cache could contain only a PMT fragment and was unsuitable for decoder startup.
- Display actual PAT/PMT counters and progression in the existing Live TV layout; selecting another channel stops the previous native stream before retuning.
- One universal debug APK includes native `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64` libraries; no APK splits.

## August APK library experiments

The user-supplied August APK has twelve libraries under `lib/armeabi-v7a/`, but **none** under `lib/arm64-v8a/`. In particular `libsmscontrollite.so` is an ELF32 ARM binary and depends on `libcutils.so` (a private Android platform library on modern systems). This is not automatically compatible with a modern 32-bit Android process, even if the original August APK works on a particular device.

`tools/Stage-August405Libraries.ps1` reads **your own local APK**, extracts a private developer lab copy into the Gradle-ignored `vendor-lab` folder, and reports SHA-256 checksums. No third-party August native libraries are shipped in this project or patch.

For private packaging experiments on devices with **32-bit Android support**, the script offers an explicit `-PackageForLocal32BitTest` flag. This simply stages binaries under `app/src/main/jniLibs/armeabi-v7a`; it **does not** add JNI bindings or enable the original August device APIs. Do not distribute such builds without rights to those libraries. Delete the staged directory before production builds. A native 64-bit PersonalMedia process cannot load a 32-bit `.so`.

For the battery indicator, retain the current native Siano battery request/reply capture. Battery bars must not be calculated from unknown payload bytes; check the raw diagnostic payload against known readings from the working August app before deriving a mapping.

## Build and test on Windows ARM64

In PowerShell, from `C:\Users\WindowsVM\Downloads\PersonalMedia`:

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace
.\tools\Verify-UniversalApk.ps1
```

The patch ZIP contains relative file paths for **overlay-only extraction** into that folder. It does not modify the HarmonyOS source or your signing setup.

Native host verification: seven tests (`ctest --output-on-failure`) including a selected PMT spanning more than one TS packet.

**Still needs device verification**: build all four ABIs with your Windows NDK installation; physically tune BBC ONE Scotland, inspect PMT diagnostics, confirm Media3 picture/audio and successful change between multiplexes. A host test is not a replacement for actual T405 playback testing. No physical receiver or Android SDK was available in the ZIP authoring environment.
