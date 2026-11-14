# PersonalMedia Android — STR-1 Account & Add-ons

Baseline: Android LTV-4 source, retaining completed T405 native MPV receiver and Sky-style guide. This is an **Android source-only overlay**.

## What's changed

* Existing Android Keystore-backed encrypted session storage is retained.
* After a valid Stremio login or restored session, UI session status updates immediately. A temporary add-on sync failure no longer makes a successful login appear to have failed.
* Password/auth-key text fields clear after the sign-in attempt, including failure paths.
* Compatible installed add-ons now have a dedicated capability list, not just a flat list of catalog names: title, version, summary, supported content types/resources, number of catalogs and searchable catalogs.
* Add-on data is derived from the account's `addonCollectionGet` response; no new add-ons are installed automatically.
* Configured transport URLs (which can contain add-on tokens) are not displayed. External URL substrings in provider descriptions are redacted.
* Refresh discards replies that belong to a session that has since signed out or changed account. Duplicate catalog identifiers are suppressed per source.

## Intentionally unchanged

* `app/build.gradle.kts`, Android application ID, signing, and all ABI filters.
* Native libmpv binaries installed separately by the user; native T405 playback/Guardian/EPG; local Media3.
* Native libtorrent remains missing in the existing Android source baseline, so torrent streams are **not** made playable by this overlay.
* The project's existing Stremio account-library and basic browse/source screens remain as-is. The later watched-state, download and torrent features described in the supplied full specification are future milestones, not claimed by STR-1.

## Apply (Windows PowerShell)

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR1_Account_Addons_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug --stacktrace
.\tools\Verify-UniversalApk.ps1
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** the current application; do not uninstall or clear data.

## Real-device acceptance

1. Verify T405 Sky-style guide and MPV playback remain correct.
2. Sign in to the user's own Stremio account. Confirm it shows connected even if a provider is temporarily unavailable.
3. Open **Stremio > Add-ons**; check the account-provided names, content types, resources, and catalog count. Some legacy or non-manifest providers may be skipped.
4. Close/reopen the app; verify saved account restores without re-entering the password.
5. Use Refresh; verify no account token, configured add-on URL, or provider secret is shown in the UI or logs.
6. Sign out; verify add-on information and connected state clear. Re-sign in as desired.
7. Build/verify one universal APK with matching `libpersonalmedia.so`/`libmpv.so` for installed ABIs.

## Test scope and limitations

The three modified Kotlin files were checked for syntax-level compiler diagnostics, with no parser errors found. Full Compose/Android compilation was unavailable in the artifact environment, and an actual signed-in account/API roundtrip was **not** performed. Stremio account APIs may change; if account authentication fails, capture only the **redacted** error, never a password, auth key or token-bearing URL.
