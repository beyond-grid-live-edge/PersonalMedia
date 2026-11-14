# PersonalMedia Android: August T405 Live TV test build

## What this adds

This is a **source update**, not an independently tested T405 hardware release. It keeps the original cinematic Live TV dashboard, Android Media3 MPEG-TS playback, native Siano/DVB transport, and the Android foreground-service Guardian.

- Native T405 Connect now immediately triggers a version probe and a read-only battery query.
- If Android exposes a Wi-Fi network with a route to the tuner IPv4 address, **only the native control UDP socket** is bound to that network using `Network.bindSocket(FileDescriptor)` on a temporary duplicate. Other app connections (Stremio and 127.0.0.1 playback) stay on their existing routes. If Android exposes no matching Wi-Fi route, this optional binding is skipped.
- Guardian sends limited follow-up probes on an open but non-responsive socket without interrupting a working video stream. A closed receiver socket can still be retried three times, with Wi-Fi binding re-applied on recovery.
- The receiver reports separate ages for a valid Siano reply and actual MPEG transport packets. A successful UDP bind is **not** evidence that the physical T405 answered.
- Live TV adds an Android Wi-Fi route check, persisted tuner IP/ports/frequency, scan/re-scan from live playback, visible decoder errors, and an August-style four-segment battery display.
- The battery segments remain **grey / unknown** until real T405 response semantics can be validated. The app already records `batteryReplies`, `batteryRawHex` and `batteryRawWords`; it deliberately does **not** treat an arbitrary response word as a percentage. Do not claim working battery percentage yet.

## What is deliberately not bundled

The August v1.0.3 APK contains proprietary 32-bit ARM (`armeabi-v7a`) libraries; this project still targets `arm64-v8a` and `x86_64`. Importing APK code/libs into the 64-bit process is not supported, and redistribution requires the relevant rights. This project uses original/ported C++ instead, without an August binary.

## Apply to your proven Android Studio build

Download `PersonalMedia_T405_Live_TV_Patch.zip` into `C:\Users\WindowsVM\Downloads` and run this in PowerShell **inside** `C:\Users\WindowsVM\Downloads\PersonalMedia`:

```powershell
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_T405_Live_TV_Patch.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug --stacktrace
```

The debug APK should appear at `app\build\outputs\apk\debug\app-debug.apk`. Updating the installed app preserves its package ID; user data preservation depends on keeping the same signing key and avoiding uninstall.

## Hardware sequence (August app is a useful working reference)

1. In the working August app's Wi-Fi TV settings, consider setting the **T405 hardware sleep timer** to "Never" during testing. August's manual says this prevents the receiver from going into sleep mode; the Android Guardian cannot override the receiver's own sleep settings.
2. Connect the Android device directly to the **actual T405 Wi-Fi**. Leave the August app closed during the PersonalMedia test so it does not own the tuner session.
3. In PersonalMedia → Live TV → Tuner settings, inspect `Check Android Wi-Fi route`, then Connect using `192.168.1.1` / UDP `5002` control / `5001` receive **only if these are correct for your T405**.
4. Wait for **Siano replies** and a finite **Last Siano reply** age. `Socket ready` alone is not connectivity proof. A battery reply count can establish that the tuner answered, but not the meaning of its battery bytes.
5. Select `Scan UK Freeview` or `Scan EU UHF`; verify **TS packet count increases**, a recent `Last TS packet` age, multiplex/service counts, and non-encrypted channels.
6. Select a service and watch for picture/sound. If no video is shown, read the new `Live TV decoder` message. An Android Media3 decoder may lack support for a broadcast codec even when TS packets and channel scanning work.
7. Check the battery diagnostic: `batteryReplies`, `batteryRawHex` and, if available, battery bars in the original August app at the same time. **Use **Copy T405 diagnostics** in Tuner settings, then send the captured raw T405 payload and a contemporaneous August battery display** so the response format can be matched before turning the percentage bars on.
8. Switch tabs; Guardian should remain active. Tap Disconnect to release wake/Wi-Fi locks. The Guardian cannot force Android to remain connected to a specific Wi-Fi network if the system changes networks.

## Verification boundary

- Native C++: six simulated tests passed with Linux host Clang/CMake, including transport, battery query, socket lifecycle, and new telemetry/descriptor test.
- Kotlin route/Guardian/controller: compiled against Android-API-shaped test stubs with local Kotlin compiler.
- JNI bridge: host Clang syntax check against JDK JNI headers passed.
- **Android Gradle/NDK build, APK installation and physical T405 parity are not validated in this environment**. Build in Android Studio and test the resulting APK on an Android device. Do not mistake successful Gradle sync for an installed/live-TV-working release.
