package com.personalmedia.android

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** On-demand support report. NEVER reads media URLs, account/session values or storage paths. */
internal object PersonalMediaDiagnostics {
    fun report(context: Context, accountSignedIn: Boolean): String {
        val app = context.applicationContext
        val library = LocalLibrary(app)
        val items = library.get()
        val folders = LibraryFolderManager(app, library)
        val registered = folders.sources()
        val accessible = registered.count { folders.hasReadPermission(it.treeUri) }
        val mpv = runCatching { NativeBridge.loaded && NativeBridge.mpvAvailable() }.getOrDefault(false)
        val torrent = runCatching { NativeBridge.isTorrentReady() }.getOrDefault(false)
        val freeview = runCatching { T405ChannelStore.load(app).size }.getOrDefault(0)
        val us = runCatching { UsTvRepository(app).cached().size }.getOrDefault(0)
        val queue = runCatching {
            DownloadQueueRepository.init(app)
            DownloadQueueRepository.rows.value.groupingBy { it.state }.eachCount()
        }.getOrNull()
        val safeTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())
        return buildString {
            appendLine("PersonalMedia Android — STR-7D support diagnostics")
            appendLine("Generated: $safeTime")
            appendLine("App version: ${BuildConfig.VERSION_NAME}")
            appendLine("Android API: ${Build.VERSION.SDK_INT}")
            appendLine("Architecture: ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty().take(40)}")
            appendLine("Media3 player: bundled")
            appendLine("Native MPV: ${if (mpv) "available" else "unavailable"}")
            appendLine("Native torrent: ${if (torrent) "available" else "unavailable"}")
            appendLine("Stremio signed in: ${if (accountSignedIn) "yes" else "no"}")
            appendLine("Local catalog entries: ${items.size}")
            listOf("movie", "tv", "music", "picture").forEach { kind ->
                appendLine("Library $kind: ${items.count { it.type == kind }}")
            }
            appendLine("Watched local entries: ${items.count { it.watched }}")
            appendLine("Favourite local entries: ${items.count { it.favourite }}")
            appendLine("Exact duplicate catalog records: ${LocalLibraryOrganizer.exactDuplicateCount(items)}")
            appendLine("Saved library folders: ${registered.size}")
            appendLine("Folders with active read permissions: $accessible")
            appendLine("Saved UK Freeview channels: $freeview")
            appendLine("Cached US channel listings: $us")
            if (queue == null) appendLine("Encrypted downloads: unavailable for count-only report")
            else {
                appendLine("Download queue items: ${queue.values.sum()}")
                queue.toSortedMap().forEach { (state, amount) ->
                    // Status keys are internal enum values, not downloaded file names.
                    if (state in setOf("queued", "running", "paused", "completed", "failed", "cancelled"))
                        appendLine("Download $state: $amount")
                }
            }
            appendLine("Excluded: file names and paths; source URLs; private IPs; channel names; " +
                "stream headers; user email; passwords; Stremio tokens; download hashes.")
        }
    }
}
