package com.personalmedia.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** User-selected SAF locations. Never derives paths from raw /storage or deletes media. */
internal data class SavedMediaFolder(
    val treeUri: String,
    val category: String,
    val label: String,
    val lastScan: String = "Not scanned yet",
    val scanTimeMs: Long = 0L
)

internal data class LibraryScanResult(val items: List<MediaEntry>, val message: String)

internal class LibraryFolderManager(private val context: Context, private val library: LocalLibrary) {
    private val prefs = context.getSharedPreferences("personalmedia_media_folders", Context.MODE_PRIVATE)
    private val scanLock = Mutex()

    @Synchronized fun sources(): List<SavedMediaFolder> = runCatching {
        val arr = JSONArray(prefs.getString("sources", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val category = o.optString("category")
            val treeUri = o.optString("treeUri")
            if (category !in LibraryFolderPolicy.categories || treeUri.isBlank()) null else
                SavedMediaFolder(treeUri, category, o.optString("label"),
                    o.optString("lastScan", "Not scanned yet"), o.optLong("scanTimeMs"))
        }.distinctBy { it.treeUri }
    }.getOrDefault(emptyList())

    @Synchronized private fun putSources(sources: List<SavedMediaFolder>) {
        val arr = JSONArray()
        sources.distinctBy { it.treeUri }.forEach { folder ->
            arr.put(JSONObject().put("treeUri", folder.treeUri).put("category", folder.category)
                .put("label", folder.label).put("lastScan", folder.lastScan)
                .put("scanTimeMs", folder.scanTimeMs))
        }
        prefs.edit().putString("sources", arr.toString()).apply()
    }

    fun hasReadPermission(treeUri: String): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == treeUri && it.isReadPermission
        }
    }.getOrDefault(false)

    @Synchronized fun add(category: String, uri: Uri, reconnectFrom: String? = null): String {
        require(category in LibraryFolderPolicy.categories)
        val id = uri.toString()
        if (uri.scheme != "content" || !hasReadPermission(id))
            return "Folder permission was not saved. Choose a folder in Android Files and grant access."
        val existing = sources()
        val duplicate = existing.firstOrNull { it.treeUri == id && it.treeUri != reconnectFrom }
        if (duplicate != null) return "That folder is already assigned to ${duplicate.category}; choose a different location."
        val previous = existing.firstOrNull { it.treeUri == reconnectFrom && it.category == category }
        if (reconnectFrom != null && previous == null)
            return "Original $category folder is no longer registered; choose Add folder instead."
        val label = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
            .orEmpty().ifBlank { uri.lastPathSegment?.substringAfterLast(':') ?: category }
        val replacement = SavedMediaFolder(id, category, label, "Ready to scan")
        putSources(existing.filterNot { it.treeUri == reconnectFrom || it.treeUri == id } + replacement)
        clearMissingAudit(id)
        if (reconnectFrom != null) clearMissingAudit(reconnectFrom)
        if (previous != null && previous.treeUri != id) {
            // Only the source ID changes at this stage. The individual file URIs
            // are reconciled by relative path during the next successful scan.
            // All old index items and their watched/resume data are preserved.
            library.save(library.get().map { item ->
                if (item.sourceUri == previous.treeUri) item.copy(sourceUri = id) else item
            })
        }
        return if (previous == null) "Saved $category folder: $label" else "Reconnected $category folder: $label"
    }

    @Synchronized fun remove(treeUri: String): Pair<List<MediaEntry>, String> {
        val source = sources().firstOrNull { it.treeUri == treeUri }
            ?: return library.get() to "Folder was already removed"
        putSources(sources().filterNot { it.treeUri == treeUri })
        clearMissingAudit(treeUri)
        // Unindex only media added by this folder; never delete an actual file.
        val kept = library.get().filterNot { it.sourceUri == treeUri }
        library.save(kept)
        // The permission is no longer required for this saved tree.
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return kept to "Removed ${source.label} from ${source.category}; files on the device were not changed"
    }

    @Synchronized private fun status(treeUri: String, report: String, successful: Boolean) {
        putSources(sources().map { folder ->
            if (folder.treeUri == treeUri) folder.copy(lastScan = report,
                scanTimeMs = if (successful) System.currentTimeMillis() else folder.scanTimeMs)
            else folder
        })
    }


    /** Missing-file evidence comes ONLY from a completed scan of an authorised source. */
    @Synchronized private fun clearMissingAudit(treeUri: String) {
        val all = runCatching { JSONObject(prefs.getString("missingAudits", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        all.remove(treeUri)
        prefs.edit().putString("missingAudits", all.toString()).apply()
    }

    @Synchronized private fun saveMissingAudit(treeUri: String, missing: List<String>) {
        val all = runCatching { JSONObject(prefs.getString("missingAudits", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val previous = all.optJSONObject(treeUri)?.optJSONArray("pendingUris")
        val prior = mutableSetOf<String>()
        if (previous != null) for (i in 0 until previous.length()) prior += previous.optString(i)
        val obj = JSONObject().put("checkedMs", sources().firstOrNull { it.treeUri == treeUri }?.scanTimeMs ?: 0L)
        val arr = JSONArray()
        val pending = JSONArray()
        missing.distinct().forEach { pending.put(it) }
        LocalLibraryOrganizer.confirmedMissing(prior.toList(), missing).forEach { arr.put(it) }
        obj.put("missingUris", arr).put("pendingUris", pending)
        all.put(treeUri, obj)
        prefs.edit().putString("missingAudits", all.toString()).apply()
    }

    /** Preview only; no library data or files are changed. */
    @Synchronized fun missingCandidates(): List<String> {
        val json = runCatching { JSONObject(prefs.getString("missingAudits", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val registered = sources().associateBy { it.treeUri }
        val result = mutableListOf<String>()
        json.keys().forEach { treeUri ->
            val source = registered[treeUri] ?: return@forEach
            if (!hasReadPermission(treeUri) || !source.lastScan.contains("matching files") ||
                source.lastScan.contains("incomplete scan", true)) return@forEach
            val audit = json.optJSONObject(treeUri) ?: return@forEach
            if (audit.optLong("checkedMs") <= 0L || audit.optLong("checkedMs") != source.scanTimeMs) return@forEach
            val arr = audit.optJSONArray("missingUris") ?: return@forEach
            for (i in 0 until arr.length()) result.add(arr.optString(i))
        }
        return result.filter { it.isNotBlank() }.distinct()
    }

    suspend fun cleanupConfirmedMissing(): LibraryScanResult = scanLock.withLock {
        withContext(Dispatchers.IO) {
            val candidate = missingCandidates()
            if (candidate.isEmpty()) return@withContext LibraryScanResult(library.get(),
                "No missing records from completed, permission-verified scans. Scan folders before cleanup.")
            // Check each document URI again. If it can be opened, it isn't missing.
            // A permission loss aborts cleanup rather than interpreting it as deletion.
            val sourceIds = sources().map { it.treeUri }.toSet()
            val known = library.get().filter { it.uri in candidate && it.sourceUri in sourceIds }
            if (known.any { !hasReadPermission(it.sourceUri) }) return@withContext LibraryScanResult(
                library.get(), "A folder permission changed. Reconnect before attempting cleanup.")
            val allTreesReadable = known.map { it.sourceUri }.distinct().all { tree ->
                val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(tree)) }.getOrNull()
                root != null && runCatching { root.isDirectory && root.canRead() }.getOrDefault(false)
            }
            if (!allTreesReadable) return@withContext LibraryScanResult(library.get(),
                "A storage folder is not currently available. No records removed; reconnect it first.")
            val gone = known.filter { item ->
                runCatching { DocumentFile.fromSingleUri(context, Uri.parse(item.uri))?.exists() == false }
                    .getOrDefault(false)
            }.map { it.uri }.toSet()
            val (after, report) = library.pruneConfirmedMissing(gone)
            if (gone.isNotEmpty() && report.startsWith("Removed ")) {
                known.filter { it.uri in gone }.map { it.sourceUri }.distinct().forEach(::clearMissingAudit)
            }
            LibraryScanResult(after, if (gone.isEmpty())
                "Missing candidates are still reachable or could not be verified; no records removed." else report)
        }
    }

    suspend fun scan(treeUri: String, maxFiles: Int = 12000): LibraryScanResult = scanLock.withLock {
        withContext(Dispatchers.IO) {
            val source = sources().firstOrNull { it.treeUri == treeUri }
                ?: return@withContext LibraryScanResult(library.get(), "Folder is no longer registered")
            if (!hasReadPermission(treeUri)) {
                val text = "${source.label}: access unavailable — reconnect the original folder; saved entries were preserved"
                status(treeUri, text, false)
                return@withContext LibraryScanResult(library.get(), text)
            }
            val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
            if (root == null || !runCatching { root.isDirectory && root.canRead() }.getOrDefault(false)) {
                val text = "${source.label}: directory unavailable — reconnect the same folder; indexed media was preserved"
                status(treeUri, text, false)
                return@withContext LibraryScanResult(library.get(), text)
            }
            val queue = ArrayDeque<Pair<DocumentFile, String>>()
            queue.add(root to "")
            val found = mutableListOf<MediaEntry>()
            var examined = 0
            var failures = 0
            var truncated = false
            while (queue.isNotEmpty()) {
                val (folder, relativeFolder) = queue.removeFirst()
                val files = runCatching { folder.listFiles().toList() }.getOrElse {
                    failures++
                    emptyList()
                }
                for (file in files) {
                    if (examined >= maxFiles) { truncated = true; break }
                    examined++
                    val fileName = runCatching { file.name }.getOrNull().orEmpty()
                    if (fileName.isBlank()) continue
                    if (runCatching { file.isDirectory }.getOrDefault(false)) {
                        val relative = if (relativeFolder.isEmpty()) fileName else "$relativeFolder/$fileName"
                        queue.add(file to relative)
                    } else if (runCatching { file.isFile }.getOrDefault(false)) {
                        val kind = LibraryFolderPolicy.kindForFile(source.category, fileName) ?: continue
                        val relFile = if (relativeFolder.isEmpty()) fileName else "$relativeFolder/$fileName"
                        val path = if (relativeFolder.isEmpty()) source.label else "${source.label}/$relativeFolder"
                        found += MediaEntry(file.uri.toString(), cleanTitle(fileName), kind, path,
                            sourceUri = treeUri, relativePath = relFile)
                    }
                }
                if (truncated) break
            }
            // Don't persist a possibly incomplete/failed scan as a deletion. We
            // only update found records and retain indexed-but-missing items.
            val report: String
            val merged: List<MediaEntry>
            synchronized(this@LibraryFolderManager) {
                if (sources().none { it.treeUri == treeUri }) {
                    return@withContext LibraryScanResult(library.get(), "Source removed while scanning")
                }
                val original = library.get().toMutableList()
                var added = 0
                var recovered = 0
                found.distinctBy { it.relativePath }.forEach { item ->
                    val index = original.indexOfFirst { old -> old.uri == item.uri ||
                        (old.sourceUri == treeUri && old.relativePath.isNotEmpty() && old.relativePath == item.relativePath) }
                    if (index >= 0) {
                        val old = original[index]
                        original[index] = old.copy(uri = item.uri, type = item.type,
                            folder = item.folder, sourceUri = treeUri, relativePath = item.relativePath)
                        recovered++
                    } else {
                        original += item
                        added++
                    }
                }
                library.save(original)
                merged = library.get()
                val complete = failures == 0 && !truncated && queue.isEmpty()
                val foundPaths = found.map { it.relativePath }.toSet()
                val missing = original.filter { it.sourceUri == treeUri && it.relativePath.isNotEmpty() &&
                    it.relativePath !in foundPaths }.map { it.uri }
                report = "${source.label}: ${found.size} matching files, $added new, $recovered refreshed" +
                    if (!complete) " · incomplete scan ($failures inaccessible folders${if (truncated) ", file limit reached" else ""}); existing entries preserved" else
                        " · ${missing.size} possibly missing record(s); scan again to confirm before cleanup"
                status(treeUri, report, complete)
                if (complete) saveMissingAudit(treeUri, missing) else clearMissingAudit(treeUri)
            }
            LibraryScanResult(merged, report)
        }
    }
}
