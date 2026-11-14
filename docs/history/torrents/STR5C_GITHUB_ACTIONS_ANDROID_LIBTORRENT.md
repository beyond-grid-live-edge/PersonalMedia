# PersonalMedia STR-5C — GitHub Actions Android libtorrent cross-compilation

## What was installed in GitHub

The **private** GitHub repository `beyond-grid-live-edge/PersonalMedia` has been initialized, and its `main` branch now contains a self-contained workflow:

https://github.com/beyond-grid-live-edge/PersonalMedia/actions/workflows/personalmedia-android-libtorrent.yml

**No PersonalMedia app source, account details, signing keys, passwords, private add-on settings or August tuner binary libraries were uploaded.** The repository currently needs only a README and the self-contained dependency workflow. The workflow is a manual `workflow_dispatch`; uploading it does **not** trigger a billable build automatically.

The project overlay includes an equivalent self-contained workflow file plus local dependency packaging and import scripts. The GitHub repository's checked-in workflow performs the same build, checks, package layout and artifact naming; the locally included version also maintains separate triplet and packaging source files for review and future GitHub integration.

## Step 1 — Run ARM64 on GitHub

1. Open the GitHub Actions workflow link above, sign in to GitHub if required, and open the workflow's **Run workflow** menu.
2. Select branch `main`.
3. Set `targets` = `arm64-only` (default). Keep `vcpkg_revision` = `master` for the first run. The resolved vcpkg Git SHA is recorded inside each artifact for later pinning.
4. Click **Run workflow**. The Ubuntu 24.04 runner installs Android NDK `27.0.12077973`, uses API 26 and compiles actual `libtorrent[core]`, Boost and OpenSSL into static Android ARM64 archives.
5. When the run succeeds, open its **Artifacts** section and download `PersonalMedia-libtorrent-arm64-v8a`. GitHub delivers a ZIP (commonly `PersonalMedia-libtorrent-arm64-v8a.zip`) containing `pm-libtorrent-arm64-v8a.zip` and a matching `.sha256` file.

If the workflow fails, open the **first actual error** in the Ubuntu job log. Do not assume the dependencies compiled merely because the runner started. Private-repository GitHub Actions usage may count against your account's included minutes or incur charges depending on plan and settings.

## Step 2 — Install source-only overlay locally

Extract the supplied STR-5C overlay on top of **STR-5B** in the existing Windows 11 ARM64 VM project. The project path and signing credentials are not changed:

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR5C_GitHub_Actions_Overlay.zip" -DestinationPath . -Force
```

The overlay includes the local import/build scripts and a `.gitignore` update to prevent accidentally committing large private local native dependencies.

## Step 3 — Install the GitHub Action artifact and build PersonalMedia

Download the **outer GitHub artifact ZIP** to Windows Downloads without extracting it manually. Then:

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
.\tools\STR5C-Install-GitHub-Dependencies.ps1 `
  -ArtifactZip "$env:USERPROFILE\Downloads\PersonalMedia-libtorrent-arm64-v8a.zip" `
  -Arm64Only -Build
```

The installer checks the SHA-256, ZIP paths, ABI, Android API level, C++ runtime type, actual `.a` archive magic and libtorrent/OpenSSL headers + CMake configs. It stages files under `native-deps\installed\pm-arm64-android`, then runs:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest -PpmTorrent=true -PpmTorrentAbis=arm64-v8a --stacktrace
```

The **ARM64-only** APK is intended for your ARM64 Android phone. **Do not run `Verify-UniversalApk.ps1` on it.** Do not uninstall the current app when installing an updated APK; uninstalling loses saved local state.

### Build again without re-importing

```powershell
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

These scripts **never run vcpkg inside Windows**. Only the normal Gradle/NDK native app build runs locally, using the archives already built by GitHub Actions.

## Optional: one universal APK for four ABIs

Run the same GitHub workflow again using `targets` = `all-four`. It creates distinct artifacts for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. Download all four outer ZIPs, then:

```powershell
.\tools\STR5C-Install-GitHub-Dependencies.ps1 `
  -ArtifactZip @(
    "$env:USERPROFILE\Downloads\PersonalMedia-libtorrent-arm64-v8a.zip",
    "$env:USERPROFILE\Downloads\PersonalMedia-libtorrent-armeabi-v7a.zip",
    "$env:USERPROFILE\Downloads\PersonalMedia-libtorrent-x86.zip",
    "$env:USERPROFILE\Downloads\PersonalMedia-libtorrent-x86_64.zip"
  ) -Build
```

The import script preserves four separate triplet folders. The subsequent Gradle build's `-PpmTorrent=true` links them into all four `libpersonalmedia.so` variants in a single APK and invokes `Verify-UniversalApk.ps1`.

## Preserved architecture and limitations

- No modifications to the project's package/application ID, signing, Android Media3, Freeview/Siano tuner, Sky guide, Guardian, or the STR-4e HTTPS/MPV bridge.
- The previously implemented verified-piece scheduler, JNI and MPV Range server remain the torrent-side code; this stage supplies their actual libtorrent dependencies without using Windows vcpkg.
- Libtorrent builds are not the same as on-device verification. We must still confirm a **real successful Android NDK link**, `engineAvailable()`, metadata acquisition, verified pieces, seek, shutdown and picture/audio on the phone.
- Provider permission to use P2P and copyright/licence restrictions still apply. Peers can observe your IP address.
- The static dependencies are built with `c++_static`, matching PersonalMedia's current Android Gradle CMake configuration. If a dependency refuses this configuration, use the GitHub job's *first* compiler error to correct it rather than changing the working player blindly.
- The `vcpkg_revision` default `master` is recorded in the ZIP metadata but is not a fixed version. Once a run succeeds, pin a specific commit SHA in future runs for repeatability.

## Local validation and delivery status

- Workflow YAML parsed and embedded Bash syntax checked locally.
- Python package builder compiles; the ZIP layout and SHA-256 process passed a **synthetic static-archive fixture test** (fixture files are *not* shipped as real dependencies).
- The GitHub workflow was committed to the private repository on `main`. This does **not** mean that the GitHub build has run or succeeded.
- Windows PowerShell scripts and Android-target libtorrent linking are **not** executed here: final verification occurs in your Windows ARM64 VM and in GitHub Actions.

References:
- https://learn.microsoft.com/en-us/vcpkg/users/platforms/android
- https://vcpkg.io/en/package/libtorrent.html
- https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
