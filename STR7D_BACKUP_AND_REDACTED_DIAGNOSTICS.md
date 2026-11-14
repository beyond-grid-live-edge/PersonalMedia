# PersonalMedia Android — STR-7D Backup & Redacted Diagnostics

Built on **STR-7C1** (Media3 track-API corrected), retaining all previous UK Freeview, US channels-only Live TV, native MPV, ARM64 libtorrent, Stremio, downloads and library improvements.

## Settings → Backup & Recovery

- **Create backup** opens Android's Create Document picker and exports a bounded JSON v1 archive. User selects a safe private destination. The JSON is **not encrypted** and can reveal movie titles and Android storage document identifiers.
- **Restore backup** opens Android's Open Document picker. Before changing anything, PersonalMedia fully reads and validates the schema (16 MiB maximum, bounded records), then displays a summary and asks for explicit **Merge backup** confirmation.
- Restore **merges** local library entries rather than deleting existing entries. Matching URI + media type uses OR for watched/favourite and maximum resume position; alternate editions remain distinct. Original media files are never moved, overwritten or deleted.
- Restored folder registrations are remembered, but Android does not transfer Storage Access Framework URI permissions between installations. Reconnect inaccessible folders through Settings before scanning. Missing-file audit evidence is cleared; disconnected storage never triggers cleanup.
- A local private undo snapshot is written and fsynced **before** applying a restore, with a durable pending marker for interrupted restores. **Undo restore** reverts selected backed-up app state if nothing backed up has changed since a completed restore (or if a restore was interrupted).
- File export is consciously separate from app sign-in/session, download storage and media. This is **not an APK backup** and does **not** copy downloaded videos or issue Android document permissions.

### Included sections

- Local media catalog: Movies, TV, Music, Pictures, favourite/watched flags, resume positions, titles, metadata and permitted artwork references.
- Saved library folder categories and registrations (permissions must be re-granted if missing).
- UK T405 Freeview service lineup and connection preferences (no transient programme schedules).
- US Global Free TV cached **US-only channel identities**, favourites and preferred Source 1/2/etc. (NO streaming URLs).
- Global playback and artwork **configuration**, per-title Audio/CC memories.
- Local **Stremio Continue Watching**, provided the *same Stremio account is already signed in* when importing. Account fingerprint checked; only title/video IDs, year and resume positions are copied, not account email or poster URLs.

### Deliberately excluded

- Stremio authentication credentials, session tokens, private add-on credentials and account email.
- Encrypted persistent download queue (may include URLs, signed headers, torrent trackers, authentication), downloaded files, SAF access grants and P2P consent.
- Pending Stremio two-way watched-sync edits, since replaying them onto another account/installation could overwrite remotely watched flags. The Stremio account can resync those records normally.
- Artwork API keys (they remain memory-only) and media source links.

## Settings → Support → Export support diagnostics

Creates a short `.txt` report with version/API/ABI, native availability and **counts only**: catalog entries by category, watched/favourites, folder count and permission count, duplicate count, saved T405 channel count, cached US channel count and download queue *status counts*. Includes signed-in yes/no only (never account email).

It NEVER includes media filenames, folder paths, receiver IPs, channel names, Stream URLs, auth headers, torrent hashes or private credentials. Review report contents before sharing.

## Installation

```powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
Expand-Archive -Path "$env:USERPROFILE\Downloads\PersonalMedia_Android_STR7D_Backup_Diagnostics_Overlay.zip" -DestinationPath . -Force
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
```

Install the freshly compiled APK as an **update over the same signed PersonalMedia app**. Do not uninstall or clear data. No signing, Gradle, manifest, native C++, libtorrent dependency or other player files changed.

## Validation

- Kotlin backup, restore and diagnostics implementation compiled in a local JVM check using Android and `org.json` API stubs.
- 5/5 pure-Kotlin backup policy tests passed.
- ZIP integrity and changed-file coverage checked against STR-7C1 source snapshot.
- Full Android Gradle build and real-device SAF export/import/undo still require checking on the user's Windows ARM64 VM / Honor 90.

## Recommended device tests

1. **Export backup** to a private folder; confirm Android Files contains a JSON file.
2. Change one local favourite, then **Restore backup** and review the preflight summary. Confirm original media remains intact and the favourite returns. Test **Undo restore** before performing another library change.
3. Test a missing/revoked folder permission: its registration should return but scanning must require reconnecting.
4. Check saved Freeview lineup and US favourite/source selection after restore (not video links).
5. Export diagnostics and inspect the text: it must contain only feature statuses and totals, with no media titles, IP addresses, credentials or source URLs.

### Limitations

- Backups are plaintext user-selected JSON, not password-encrypted archives. Keep them private.
- Restoring a user document does not grant the Android SAF permission for the document URIs inside it.
- A restore is not a transactional database operation across every preference file; it saves a pre-restore recovery snapshot to support rollback and process-death recovery.
