# PersonalMedia Android — LTV-3: Network Guardian + Sky-only guide

Base: verified-on-device LTV-2 project with native T405 Siano C++ → libmpv playback, working saved channels and improved scrolling.

## Changes

- **One guide only**: Removed the separate classic vertical channel-list panel. The real broadcast EIT **Sky-style horizontal timeline** is now the only channel tuning guide, always visible beneath the native video player. Channel/programme search remains; saved services remain visible without Wi-Fi.
- **Guardian**: Its 8-second inspection loop now runs on Android's IO dispatcher rather than the UI thread. A missing Siano control response **never**, on its own, triggers retuning: some working firmware streams without replying to the control probe.
- **Stall detection**: Checks that the *selected programme's elementary media packets* were previously received, then have been silent for at least 12 seconds. Does not treat gaps in EIT or unrelated SI traffic as playback stalls.
- **Staged recovery**: First re-request the selected media + service-information PIDs; only if still stalled and after a >=24-second cooldown, retune the **same** multiplex and restore those filters. Recovery never calls `Tune()`/`StopPlay()` and preserves the MPV HTTP server/stream URL and selected service. No recovery during scan/intentional Stop/no selection.
- **Bounded attempts**: After four consecutive attempts, require a 90-second cooldown; successful selected-media reception resets the streak. No unbounded aggressive reconnection.
- **Wi-Fi routing**: Recovery attempts only when Android exposes a route to the T405 Wi-Fi subnet. The Guardian still doesn't auto-join or force a different Wi-Fi network; other PersonalMedia internet traffic is untouched.
- **Socket faults**: At most three guarded socket reopen attempts after unexpected receiver failures, with 20-second backoff. The saved lineup is restored. If the loopback HTTP port changes, the visible Live TV page automatically retunes the previous channel through the new port; ordinary in-place PID recovery never reloads MPV.
- **Diagnostics**: `selectedMediaPackets`, `selectedMediaAgeMs`, `recoveryAttempts`, `recoverySuccesses`, `recoveryStreak` and `recoveryAction` appear in status JSON and Tuner Settings. No fabricated signal or battery percentages.
- Avoids a multicast lock, which isn't needed for the tuner's unicast UDP stream.

## Installation (existing Windows Android Studio project)

From `C:\Users\OseasyVM\Downloads\PersonalMedia`:

```powershell
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_T405_LTV3_Guardian_Sky_Only_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` **as an update**. Do not uninstall or clear app data; doing so could erase locally saved channels. The overlay does not alter the application ID, signing settings, Stremio, or the MPV binaries already staged in `jniLibs`.

## Suggested device checks

1. Open Live TV and verify **only the Sky timeline**, not a second classic channel list. Tune 5USA and another working channel from the Sky guide; check both audio and video.
2. Close and reopen PersonalMedia: saved services and EPG metadata remain; no mandatory rescan.
3. While playing, scroll the Sky timeline and use CC, Audio and fullscreen. The previous smooth-scrolling and LTV-2 behaviour should remain.
4. Leave the tuner playing normally for several minutes. `recoveryAttempts` should stay at 0 if selected audio/video TS packets keep arriving. A missing version/battery reply alone is not a reason to recover.
5. To test recovery, briefly disable/power-cycle the **tuner** or disconnect its Wi-Fi, then reconnect manually if Android doesn't rejoin. Look for first `Reissuing active PID filters`, then if still silent `Retuning selected multiplex`. Do not expect the app to override system Wi-Fi policy.
6. Use **Tuner settings → Copy T405 diagnostics** if playback does not resume; include `selectedMediaAgeMs`, `recoveryAction`, `recoveryAttempts`, `lastTsAgeMs`, and `httpPort` in the report.
7. Stop viewing, try Cancel Scan, and Disconnect. No Guardian retunes should happen after an intentional stop or during scanning.

## Verification scope

- 9/9 native host tests passed, including a new staged-recovery regression test that verifies the MPV HTTP port and selected service are preserved during soft and hard recovery, and that a scan or deliberate Stop disables recovery.
- JNI C++ syntax checked with Clang; changed Kotlin files were passed to `kotlinc` for parser diagnostics, but the Android/Compose dependencies are unavailable in this environment. No complete Android Studio build or physical tuner recovery test has been run here.

Battery decoding remains experimental. No original August APK proprietary binaries are included.
