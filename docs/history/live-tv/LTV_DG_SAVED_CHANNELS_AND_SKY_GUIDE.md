# PersonalMedia Android — Live TV saved channels + Sky-style EPG

This is a SOURCE-ONLY overlay to apply to the existing Android Studio
`PersonalMedia` project. It does not change app identity, Gradle settings,
signing, Android MPV binaries, Stremio, or local Media3.

## What changes
- Real DVB EIT events are retained in a bounded native C++ event map; Now/Next is recalculated against UTC at read time. `tvChannels()` now includes `events`.
- Scanned channels and broadcast events are cached under private Android SharedPreferences keyed by `ONID:TSID:SID`. Native tuner connection reloads validated channel identities and tuning frequencies, permitting a channel to be tuned after restarting without a full scan. Live PAT/PMT acquisition is still mandatory.
- Disconnection and an empty/failed scan do not erase saved lineups. Scanning keeps existing services and merges fresh results.
- A Sky-style horizontally scrolling 30-minute EPG timeline uses only actual event times. Blank slots indicate missing broadcast data, not fake programmes.
- The status line shows a *transport active* state even when separate firmware control replies remain absent.
- Android app's main navigation applies safe drawing insets so Android status icons no longer overlap the header.

## Install
In PowerShell from `C:\Users\OseasyVM\Downloads\PersonalMedia` run:

```powershell
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_T405_Saved_Channels_Sky_Guide_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug --stacktrace
```

Do not delete the preinstalled MPV JNI libraries in `app/src/main/jniLibs`.
Your existing universal APK build will package them.

## Device checks
1. Watch 5USA (existing native Siano → MPV path).
2. Open TV Guide and check EIT listings; blanks are expected where schedule data has not yet arrived.
3. Leave/reopen Live TV and reboot the app; saved channel rows should be visible without a rescan.
4. Reconnect the tuner and select a saved service; it must re-tune and reacquire PAT/PMT (a short delay is normal).
5. Check the header no longer collides with phone status icons, and MPV remains the live player.

## Known limits
- This update caches the guide locally; it is not a full Room schema/migration or a network EPG.
- It does not guarantee all future events: EIT schedules depend on what the tuner receives, with a native bounded 96-event/channel map and a maximum of 48 persisted events per service.
- Long-duration MPV fullscreen continuity, subtitles, vendor battery, and Stremio's latest HarmonyOS episode/watch-sync improvements remain separate work.
- Actual Android Studio compilation and physical tuner tests must be run on the user's Android device; native host tests (8/8) and Kotlin channel-store stub compile succeeded here.
