# PersonalMedia Android STR-5F — Native Stremio add-on manager

Baseline: install this **source-only overlay over STR-5E** (the working ARM64 libtorrent build with Play/Download stream cards).

## Features

- Native Jetpack Compose add-on browser modelled on the supplied Stremio Android screenshots: **Installed / Official / Community** views, **All / Movies / Series / Anime / Channels / Podcasts / Others** content-type filters, add-on search, responsive cards, descriptions, logos and version labels.
- **Install** writes an updated server-side add-on collection using Stremio's `addonCollectionSet` API, not a local pretend toggle. **Uninstall** asks for confirmation and removes the selected add-on from the server-side collection. The client re-reads the collection to confirm the requested state before displaying success.
- The installation payload contains the transport URL and complete manifest; existing descriptors are retrieved immediately before each change and are retained verbatim (including unknown transports, configured URLs, flags and ordering). The code does not attempt to reproduce configured add-on secrets or edit another account's collection.
- Protected official add-ons are not removable. A configured add-on (e.g. an add-on with an API key encoded in its manifest URL) is never replaced by an unconfigured version as part of a refresh. Exact transport URL matching allows separately configured variants.
- Official catalogue: Stremio's official-addons index at `https://raw.githubusercontent.com/Stremio/stremio-official-addons/master/index.json`. Community catalogue: `https://api.strem.io/addonscollection.json`. Only HTTPS v3 manifest transport URLs are offered for installation. Unsupported legacy/local transports are preserved if already installed, not recreated.
- Custom/configured add-on installation: paste a supported HTTPS URL ending in `/manifest.json`, preview its manifest and install by explicit user action. Local/private IPs are rejected for previews; URLs and authentication tokens are not displayed in error messages or saved in plaintext.
- If a catalogue is temporarily unavailable, the installed list remains accessible. If Stremio rejects the account write, the UI keeps the server state and reports a failure.

## Installation in the Windows ARM64 VM

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\WindowsVM\Downloads\PersonalMedia_Android_STR5F_Stremio_Addon_Manager_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install `app\build\outputs\apk\debug\app-debug.apk` **over** the existing app, without uninstalling it. The compiled libtorrent, OpenSSL, Boost libraries, signing files, Freeview, and existing MPV/HTTPS bridge are unchanged.

## Real-device acceptance

1. Sign in and open **Stremio > Add-ons**. Confirm your current installed entries appear, including configurable add-ons.
2. Switch to **Community** and **Official**. Search or filter; install a harmless legitimate add-on with an HTTPS manifest.
3. Confirm the card changes to **Uninstall**, and **Refresh** still shows it. If the original Stremio app is signed into the same account, verify the add-on appears there after syncing.
4. Uninstall that new add-on. Accept confirmation and verify the change on both devices.
5. Confirm protected core official add-ons cannot be removed. A source requiring configuration should direct you to paste its **configured** URL rather than install a broken default.
6. Check Stremio home catalogs, playback, Freeview Live TV and normal Android swipe-back behaviour.

### Verification limits

- Host-side Kotlin compilation of the new directory/policy source and **15** policy/descriptor smoke checks passed with test stubs; the new **3/3** JUnit policy checks also passed in the same JVM harness.
- Source-only diff and archive integrity were audited.
- **Full Android Gradle/Compose compilation and live Stremio account writes have NOT been executed in this environment.** The add-on endpoints are not formally versioned; report server rejection messages or compile output for a targeted follow-up patch.
- Reference screenshots and supplied Stremio 2.5.1 APK were used for UI/behaviour research only. No APK contents or proprietary application binaries are copied into PersonalMedia.
