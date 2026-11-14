# PersonalMedia Android — LTV-4 final acceptance and battery retirement

## Applied to
The **tested LTV-3 + Sky Guide EIT/dedup** project, not the older LTV-2 project. Apply this **source-only overlay** to your existing Android Studio root. Preserve the local `jniLibs` / downloaded `libmpv.so`, application ID, signing and the private saved Freeview lineup. Do not uninstall the installed APK or clear its data.

## What LTV-4 changes
- Remove battery bars, the battery-settings panel, manual battery query controls, and battery status fields from the app's diagnostics.
- Stop battery requests sent by the Kotlin Connect flow, the Guardian reconnect flow, and the C++ receiver's 30-second timer.
- Remove the public Android battery JNI method. The read-only C++ lab parsing code is intentionally retained **unused** so a future explicitly designed investigation can reuse it, but it has no app-facing entry point or automatic queries.
- Add an Android APK verification script and host acceptance checks. No changes to the established Siano tuning, native MPV rendering, room/local preferences storage, EIT parser, audio/subtitle choices, Sky-only guide, or Stremio.

## Windows VM install / build
```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_T405_LTV4_Final_Acceptance_No_Battery_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug --stacktrace
.\tools\Verify-LTV4.ps1
```
Install `app\build\outputs\apk\debug\app-debug.apk` **over the existing app**. No clean reinstall is required or recommended.

## On-device acceptance checklist
1. Play 5USA or BBC ONE Scotland for 20–30 minutes; check audio/video remains synchronized and scrolling stays smooth.
2. Change channels within a multiplex and to another multiplex; verify each new audio/video stream appears and the Sky guide follows selection.
3. Check Now / Next, Later +4h and Tomorrow EIT listings while keeping the original broadcast programme source (no fabricated listings).
4. Pause/resume, CC Auto/Off, audio track selection, fullscreen/Back gestures and display-awake behaviour.
5. Leave and return to the Live TV tab, foreground/background the app, and verify the session does not crash or erase saved channels.
6. Temporarily interrupt tuner Wi-Fi, reconnect to the receiver, and verify the Guardian's bounded recovery; no uncontrolled continuous retune loop.
7. Force-close and reopen PersonalMedia: saved Freeview lineup still exists without scanning.
8. Verify the UI has **no** receiver battery bars, battery settings, or refresh button. The Android phone's normal system battery indicator is unchanged.
9. Run `Verify-LTV4.ps1` and confirm `libpersonalmedia.so` and `libmpv.so` are present for ARM32, ARM64, x86, x86_64.
10. Quickly test an MP4 and a Stremio direct stream to check neither has regressed.

## Deliberate scope exclusions
- Battery percentage and charging-state support: postponed until the hardware's response schema is validated. No invented values.
- Legacy August APK proprietary libraries: not bundled.
- libtorrent remains optional / unavailable until its sources and dependencies are integrated; this is **not** part of Freeview acceptance.
- Full Android Studio Gradle compilation and hardware tests require your Windows ARM64 VM / Android device. Host-side native tests do not substitute for them.

If these pass, LTV-4 is signed off and the next workstream is native HarmonyOS-style Stremio on Android.
