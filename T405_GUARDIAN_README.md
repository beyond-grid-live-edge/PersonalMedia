# PersonalMedia / August T405 Android Guardian (first integration stage)

This adds a user-activated connected-device foreground service to the existing Android Live TV receiver. It is NOT a new decoder or a replacement for the native Siano control/TS C++ code.

## What changed

- `app/src/main/java/com/personalmedia/android/T405GuardianService.kt` (new): active only after tapping Connect; holds Android Wi-Fi and multicast locks plus a partial CPU wake lock, displays a persistent notification with a Disconnect action, listens for Android Wi-Fi network events, and makes at most three 15-second-spaced attempts to reopen a **closed native UDP socket**. It does not mistake a successful socket bind for tuner communication. Native C++ already does an 8-second statistics request.
- `LiveTvScreen.kt`: starts Guardian after successful socket creation; stops it on explicit Disconnect; shows session state in the Live TV channel panel; no layout restructuring.
- `AndroidManifest.xml`: requests `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`; registers the `connectedDevice` service. Existing multicast permission satisfies the Android 14+ connected-device foreground-service type prerequisite.

## Important limitations

- Connect the Android device to the T405 Wi-Fi access point before tapping Connect. This service **cannot bypass Wi-Fi user consent, device power restrictions, or system connection policies**. It does **not** forcibly rejoin a network. Wi-Fi locks help keep the radio awake but do not guarantee association with a specific access point.
- No third-party proprietary August/EyeTV `.so` or Dex bytecode has been copied. The user-provided August v1.0.3 APK contains 11 *armeabi-v7a* (32-bit) native libraries, none for PersonalMedia's arm64-v8a / x86_64 build. Importing its app service directly is not supported.
- A foreground service holds radio and CPU locks and uses more battery. Use Disconnect (in TV settings or the notification) when finished. Because `START_NOT_STICKY` is used, a killed process will not silently restore a tuner session.
- Auto-reconnect is limited to socket-closed conditions. A missing tuner reply, TS packets, PMT or video stream is a separate connection, demux or playback issue.
- This code has not been Android-compiled here or tested with the T405 hardware. Compile in Android Studio; report errors and Live TV status to refine the integration.

## Hardware test sequence

1. Back up the working PersonalMedia project and apply the patch.
2. Build: `.\\gradlew.bat :app:assembleDebug` (PowerShell: `.\gradlew.bat :app:assembleDebug`).
3. Update PersonalMedia on your Android device using the same debug signing key; check system notification permission/settings if Guardian notifications are suppressed.
4. Connect your Android device to the actual T405 Wi-Fi SSID, confirm you can reach its control IP from the Android runtime if a diagnostic tool is available.
5. Live TV → Tuner settings → Connect (`192.168.1.1`, control UDP 5002, receive UDP 5001), then **Probe**, then **Scan UK Freeview**. Check `Siano replies`, `Last UDP`, `Packets`, and channel count; **Socket ready** alone is not success.
6. Select an unencrypted channel to test the embedded Media3 MPEG-TS video player.
7. Switch tabs, return, and test Guardian status / reconnect. Explicit Disconnect should stop the service and release locks.

## Further work after first device tests

Use actual T405 firmware replies and control/TS packet captures to validate handshake, stream-PID, battery, region and channel scanning semantics. Consider `WifiNetworkSpecifier` for opt-in association where the Android runtime exposes the actual host Wi-Fi. For selectively routing only the tuner sockets without breaking Stremio / local loopback, bind the specific UDP sockets to the intended `Network` rather than binding the whole Android process.
