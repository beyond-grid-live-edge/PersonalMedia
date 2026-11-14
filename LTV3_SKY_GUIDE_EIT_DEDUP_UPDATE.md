# PersonalMedia Android — Sky guide unique lineup and extended EIT schedules

**Baseline:** Apply over the working **LTV-3 Guardian + Sky-only** Android Studio project. The user has confirmed native MPV live video/audio, saving channels across restarts, LTV-2 player controls and Guardian source build. This patch does **not** replace the playback engine.

## Changes

- The Sky guide now displays **one preferred service per positive Freeview LCN**. The underlying saved service database, true ONID:TSID:SID identities and tuning frequencies remain intact. Preference is given to the currently selected service, actual current/future EIT events, known media PIDs and a usable frequency. Unnumbered services stay separate. This is UI presentation deduplication, **not deletion of duplicate records**.
- EIT present/following and EIT schedule programme titles are selected from *broadcast timestamps*. The current/next values are recomputed from cached events after restart or merge. A maximum of **96 real events per service** is stored, matching the native receiver; the native receiver continues to bound memory at 96 events. This is still an on-device rolling EPG cache, not a guaranteed 7-day internet guide.
- **Now / Earlier / Later +4h / Tomorrow** controls advance the Sky timeline within a 7-day horizon. The programme boxes highlight actual current and following events; tapping a box shows any broadcast description and tunes a different selected channel. Tapping a programme on the already-playing channel no longer unnecessarily restarts MPV.
- Searching matches real channel names, numbers, Now, Next and cached later EIT titles. UI shows how many duplicates are hidden, without altering the saved lineup.
- Native Siano/TS EIT diagnostics distinguish present/following section count, schedule section count and accepted programme entries, useful when future listings remain blank. EIT schedule is received on PID 0x12 for the current multiplex or other multiplexes when the broadcast supplies it. **We do not retune away from live TV to fetch EPG**, because one T405 receiver cannot stay on two frequencies at once.

## Install

1. Back up your current Windows VM Android Studio project (or commit the working source).
2. Extract `PersonalMedia_T405_Sky_Guide_EIT_Dedup_Overlay.zip` directly over `C:\\Users\\OseasyVM\\Downloads\\PersonalMedia` using `Expand-Archive -Force`.
3. Run `./gradlew.bat :app:assembleDebug` from PowerShell (use `\\.\\gradlew.bat` on Windows), then run `\\.\\tools\\Verify-UniversalApk.ps1`.
4. Install the new `app\\build\\outputs\\apk\\debug\\app-debug.apk` **over** the old installation, without uninstalling or clearing data. Previously downloaded MPV binaries in `jniLibs` are untouched.

## Device acceptance

1. Confirm 5USA still plays with picture/audio and the Guardian works; confirm MPV renderer stays smooth.
2. Open the Sky guide; repeated numbers such as **4 Channel 4**, **5 5**, **6 ITV2** should appear once in the guide, while the saved-service counter still honestly reports the raw count (e.g., 212).
3. Tune BBC ONE Scotland; confirm the guide displays **Now** and **Next** titles when transmitted. Try `Later +4h` and `Tomorrow`, which should show later programmes **only where EIT schedule sections are received**.
4. Check EIT counts in the guide footer. If future cells are blank with `schedule 0 sections`, this is not fabricated or a decoding failure: wait on the currently tuned multiplex to collect schedule packets.
5. Completely close/reopen PersonalMedia and verify channel lineup + recent EPG cache persist. Test searching for a programme title that is due later.
6. If selecting a duplicate preferred service cannot play, collect the DVB identity and tuner status; an alternative same-LCN multiplex may be stronger, in which case we will refine the preference using observed tuning success.

## Validation here

- 10/10 native host tests pass, including a synthetic DVB EIT 0x4e and 0x50 now/next/future schedule test.
- Pure Kotlin LCN-dedup policy test passes, including stable selected-service preference and preservation of unnumbered services.
- Kotlin source passed a parser diagnostic pass; no complete Android SDK/Gradle compile or physical T405 test was available here. Your Windows VM build is the decisive compile check.

**No changes to:** app ID, signing, Stremio, libtorrent, installed libmpv/FFmpeg, renderer, Guardian workers, network routing, decoder, permissions, or any local data stored by the already installed app.
