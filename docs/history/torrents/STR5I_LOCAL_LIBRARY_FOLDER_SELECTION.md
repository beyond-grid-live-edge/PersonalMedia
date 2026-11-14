# PersonalMedia Android STR-5I — HarmonyOS-style saved local media folders

This **source-only overlay** builds on STR-5H. Install it in the existing Android Studio project as an overlay, retaining signing identity, user data, previously installed libmpv binaries and `native-deps/installed` from your successful STR-5C cloud libtorrent build.

## Why

Android Settings previously showed four placeholder cards under **Separate library folders**. The **Rescan all sources** button actually opened `OpenDocumentTree()` and scanned one arbitrary folder without storing it as a Movies, TV Shows, Music or Pictures source. No category/location registry existed, so a rescan after restarting PersonalMedia was impossible. Videos were sometimes categorised by filename alone, even if the user deliberately placed them in a TV Shows folder.

## What changed

- **Settings > Separate library folders** now has separate **Choose Movies / TV Shows / Music / Pictures folder**, **Import ... files**, and per-source **Scan**, **Reconnect**, **Remove** actions. The design follows the user-provided HarmonyOS settings screenshots while using native Android Material/SAF controls.
- Android's native **OpenDocumentTree** picker grants read access to the selected directory tree (user-controlled, no broad storage access). The selected folder URI and read permission are saved across app restarts using `takePersistableUriPermission`.
- Multiple saved folders per category; status shows saved sources, category counts, scan results and whether the permission was lost. **Rescan all sources** now actually scans every saved tree rather than launching the picker.
- **Movies/TV Shows** classification follows the selected source category (so TV videos are not misplaced under Movies just because `S01E01` is absent). **Music** accepts audio, **Pictures** accepts images. Existing generic imported files continue to work.
- Scanning traverses nested folders on an IO coroutine, with a 12,000-document per-source safety cap. Existing favourites, watched states, resume positions, artwork metadata, and manually selected file entries are preserved. Inaccessible/unmounted sources never trigger automatic removal of indexed entries.
- **Reconnect** re-selects a folder via Android Files. When the Android document URI changes (for example, removable storage remount), the scanner matches previously indexed entries by relative path, updating their URI while keeping watched/resume metadata. It does not silently erase missing entries.
- **Remove** is confirmed and removes only this folder registration and its indexed entries from PersonalMedia; **it does not delete or modify device files**. Other sources and manually imported items remain intact.
- The library tab's Rescan button uses saved category folders; if none exists, it opens the folder picker. All storage provider queries and full folder scans run off the UI thread.
- Android application ID, manifest, native MPV, libtorrent, Stremio account, Freeview C++, tuner, signing, Gradle build and Guardian are unchanged.

## Install

In PowerShell, from the existing `C:\Users\WindowsVM\Downloads\PersonalMedia` folder:

```powershell
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR5I_Local_Library_Folders_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** your existing installation. Do not uninstall first.

## Device acceptance

1. Settings > Separate library folders > **Choose Movies folder**. Select a folder with video files in Android Files; grant access. Confirm it appears under Movies with a scanned count and persists after reopening PersonalMedia.
2. Choose a **TV Shows** folder, including a video lacking an `SxxExx` marker. Confirm it appears in the TV Shows library rather than Movies.
3. Add Music and Pictures folders, rescan each, and use **Rescan all sources**. Confirm the picker does not open on rescan and duplicates are not created.
4. Choose **Import files** under a specific category and verify only appropriate formats are indexed there.
5. Disconnect a selected removable drive, rescan and verify indexed entries remain; reconnect the original location and verify favourites/resume state stay intact.
6. Use **Remove** on a test folder and confirm only PersonalMedia's registration/index entries disappear; verify the media files themselves are still present in Android Files.
7. Smoke-test existing Freeview, direct Stremio HTTPS and libtorrent video playback.

## Android SAF limitations

Android 11+ may forbid selecting certain directory roots, including the primary storage root, the Downloads directory itself, and `Android/data` or `Android/obb`. Choose a supported media subfolder exposed by Android's system file picker or use individual-file imports. USB/SD folders require their provider to expose those folders, and permissions may need regranting after removal/reconnection. See Android's official Storage Access Framework documentation.

## Verification limits

- Compiled and exercised the real folder policy/manager and LocalLibrary sources with a host Kotlin compiler plus isolated Android SAF/document-provider stubs. Simulated registration, scan, rescan, permission loss, app restart, URI change and removal passed.
- 8/8 focused classification/picker assertions passed.
- **Full Android Studio Gradle build and on-device document-provider test still required**; no APK was built here.

## Files in this overlay

- `app/src/main/java/com/personalmedia/android/MainActivity.kt`
- `app/src/main/java/com/personalmedia/android/LocalLibrary.kt`
- `app/src/main/java/com/personalmedia/android/LibraryFolderPolicy.kt` (new)
- `app/src/main/java/com/personalmedia/android/LibraryFolderManager.kt` (new)
- `app/src/main/java/com/personalmedia/android/LibraryFolderSettings.kt` (new)
- `app/src/test/java/com/personalmedia/android/LibraryFolderPolicyTest.kt` (new)
- `STR5I_LOCAL_LIBRARY_FOLDER_SELECTION.md` (this file)
