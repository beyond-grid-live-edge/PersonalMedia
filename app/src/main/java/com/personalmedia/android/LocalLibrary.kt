package com.personalmedia.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** SAF scoped-storage port of LibraryStore + FolderScanner; never attempts raw /storage. */
data class MediaEntry(
    val uri: String, val title: String, val type: String,
    val folder: String = "", val favourite: Boolean = false,
    val positionMs: Long = 0L, val watched: Boolean = false,
    val art: String = "", val backdrop: String = "",
    // Additional fields are optional for compatibility with existing saved records.
    val sourceUri: String = "", val relativePath: String = "",
    val plot: String = "", val genres: String = "", val metadataSource: String = ""
)
class LocalLibrary(private val context: Context) {
    private val store = context.getSharedPreferences("personalmedia_local_library", Context.MODE_PRIVATE)
    fun get(): List<MediaEntry> = try {
        val arr = JSONArray(store.getString("items", "[]"))
        arr.objects().map { o -> MediaEntry(o.optString("uri"), o.optString("title"), o.optString("type"),
            o.optString("folder"), o.optBoolean("favourite"), o.optLong("positionMs"), o.optBoolean("watched"),
            o.optString("art"), o.optString("backdrop"),
            o.optString("sourceUri"), o.optString("relativePath"),
            o.optString("plot"), o.optString("genres"), o.optString("metadataSource")) }
            .filter { it.uri.isNotEmpty() }
    } catch (_: Exception) { emptyList() }
    private fun encode(records: List<MediaEntry>): String {
        val arr = JSONArray()
        records.filter { it.uri.isNotEmpty() }.forEach { e ->
            arr.put(JSONObject().put("uri", e.uri).put("title", e.title).put("type", e.type)
                .put("folder", e.folder).put("favourite", e.favourite)
                .put("positionMs", e.positionMs).put("watched", e.watched)
                .put("art", e.art).put("backdrop", e.backdrop)
                .put("sourceUri", e.sourceUri).put("relativePath", e.relativePath)
                .put("plot", e.plot).put("genres", e.genres).put("metadataSource", e.metadataSource))
        }
        return arr.toString()
    }

    /** Normal changes invalidate an older repair's undo snapshot rather than silently reverting them. */
    @Synchronized fun save(records: List<MediaEntry>) {
        store.edit().putString("items", encode(records))
            .remove("repairUndoBefore").remove("repairUndoAfter").apply()
    }

    /** Repairs only duplicate records that identify the exact same URI and media type. */
    @Synchronized fun repairExactDuplicates(): Pair<List<MediaEntry>, String> {
        val before = get()
        val duplicates = LocalLibraryOrganizer.exactDuplicateCount(before)
        if (duplicates == 0) return before to "No exact duplicate file records found. Different versions were preserved."
        val after = LocalLibraryOrganizer.repairExactDuplicates(before)
        if (store.edit().putString("repairUndoBefore", store.getString("items", "[]"))
                .putString("repairUndoAfter", encode(after))
                .putString("items", encode(after)).commit())
            return after to "Repaired $duplicates exact duplicate record(s). Undo is available until the next library change."
        return before to "Repair could not be saved; the existing library was preserved."
    }

    /** Only callers holding complete, permitted scan evidence should pass candidate IDs. */
    @Synchronized fun pruneConfirmedMissing(candidateUris: Set<String>): Pair<List<MediaEntry>, String> {
        if (candidateUris.isEmpty()) return get() to "No confirmed missing records to remove."
        val before = get()
        val after = before.filterNot { it.uri in candidateUris }
        if (after.size == before.size) return before to "No matching missing records remain."
        if (store.edit().putString("repairUndoBefore", store.getString("items", "[]"))
                .putString("repairUndoAfter", encode(after))
                .putString("items", encode(after)).commit())
            return after to "Removed ${before.size - after.size} missing index record(s). Original media files were not changed; Undo is available."
        return before to "Cleanup could not be saved; indexed files were preserved."
    }

    fun hasUndo(): Boolean = store.getString("repairUndoAfter", null) != null &&
        store.getString("repairUndoAfter", null) == store.getString("items", null)

    @Synchronized fun undoRepair(): Pair<List<MediaEntry>, String> {
        if (!hasUndo()) return get() to "No safe undo snapshot is available; a later library change may have invalidated it."
        val before = store.getString("repairUndoBefore", null)
            ?: return get() to "Undo snapshot missing."
        val success = store.edit().putString("items", before)
            .remove("repairUndoBefore").remove("repairUndoAfter").commit()
        return get() to if (success) "Restored the previous local catalog snapshot."
            else "Undo could not be saved; inspect the catalog before trying again."
    }
    /** Artwork is metadata only; never change a media URI, user flags or on-disk files.
     * Re-read immediately before committing so a background scan or playback state is not
     * overwritten by an older network snapshot. The same matched show/movie may have many files. */
    @Synchronized internal fun mergeArtwork(uris: Set<String>, match: ArtworkMatch,
                                   options: ArtworkPreferences): Pair<List<MediaEntry>, Int> {
        if (uris.isEmpty()) return get() to 0
        val before = get()
        var changed = 0
        val updated = before.map { item ->
            if (item.uri !in uris || item.type !in setOf("movie", "tv")) item else {
                val result = item.copy(
                    art = item.art.ifBlank {
                        if (options.fetchPosters && ArtworkMatchPolicy.safeImage(match.poster)) match.poster else ""
                    },
                    backdrop = item.backdrop.ifBlank {
                        if (options.fetchBackdrops && ArtworkMatchPolicy.safeImage(match.backdrop)) match.backdrop else ""
                    },
                    plot = item.plot.ifBlank { match.plot.take(2500) },
                    genres = item.genres.ifBlank { match.genres.take(200) },
                    metadataSource = item.metadataSource.ifBlank { match.source }
                )
                if (result != item) changed++
                result
            }
        }
        if (changed > 0) save(updated)
        return if (changed > 0) get() to changed else before to 0
    }

    fun addFiles(uris: List<Uri>): List<MediaEntry> {
        val existing = get().toMutableList()
        for (uri in uris) {
            val file = DocumentFile.fromSingleUri(context, uri) ?: continue
            val name = file.name ?: uri.lastPathSegment ?: "Media"
            val type = mediaType(name)
            if (type.isNotEmpty() && existing.none { it.uri == uri.toString() })
                existing += MediaEntry(uri.toString(), cleanTitle(name), type)
        }
        val decorated = associateFolderArtwork(existing)
        save(decorated)
        return decorated
    }
    suspend fun scanTree(uri: Uri, maxFiles: Int = 5000): Pair<List<MediaEntry>, String> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: return@withContext get() to "Cannot access selected directory"
        val records = get().toMutableList()
        val pending = ArrayDeque<Pair<DocumentFile, String>>()
        pending.add(root to (root.name ?: "Library"))
        var seen = 0
        var failures = 0
        while (pending.isNotEmpty() && seen < maxFiles) {
            val (node, path) = pending.removeFirst()
            val children = runCatching { node.listFiles().toList() }.getOrElse { failures++; emptyList() }
            for (file in children) {
                if (seen >= maxFiles) break
                seen++
                if (file.isDirectory) {
                    pending.add(file to "$path/${file.name.orEmpty()}")
                } else if (file.isFile) {
                    val name = file.name ?: continue
                    val kind = mediaType(name)
                    if (kind.isNotEmpty() && records.none { it.uri == file.uri.toString() }) {
                        records += MediaEntry(file.uri.toString(), cleanTitle(name), kind, path)
                    }
                }
            }
        }
        val decorated = associateFolderArtwork(records)
        save(decorated)
        decorated to "Indexed $seen documents (${records.size} media items; $failures inaccessible folders)" +
            if (seen >= maxFiles) "; 5,000-file limit reached" else ""
    }
    /** Import selected individual documents into a chosen category.
     * Folders are managed separately using OpenDocumentTree. */
    fun addFilesToCategory(uris: List<Uri>, category: String): Pair<List<MediaEntry>, Int> {
        require(category in LibraryFolderPolicy.categories)
        val records = get().toMutableList()
        var added = 0
        uris.forEach { uri ->
            val file = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull() ?: return@forEach
            val name = runCatching { file.name }.getOrNull() ?: return@forEach
            val kind = LibraryFolderPolicy.kindForFile(category, name) ?: return@forEach
            if (records.none { it.uri == uri.toString() }) {
                records += MediaEntry(uri.toString(), cleanTitle(name), kind, "Selected files")
                added++
            }
        }
        save(records)
        return get() to added
    }

    /** Associate user-selected sibling cover/backdrop images; never invent metadata or scrape artwork. */
    private fun associateFolderArtwork(records: List<MediaEntry>): List<MediaEntry> {
        val images = records.filter { it.type == "picture" }.groupBy { it.folder }
        return records.map { entry ->
            if (entry.type != "movie" && entry.type != "tv") entry else {
                val candidates = images[entry.folder].orEmpty()
                val titleKey = entry.title.lowercase().replace(Regex("[^a-z0-9]+"), "")
                fun match(names: Set<String>): String = candidates.firstOrNull { picture ->
                    val key = picture.title.lowercase().replace(Regex("[^a-z0-9]+"), "")
                    key in names || key == titleKey
                }?.uri.orEmpty()
                entry.copy(
                    art = entry.art.ifBlank { match(setOf("poster", "cover", "folder", "$titleKey")) },
                    backdrop = entry.backdrop.ifBlank { match(setOf("backdrop", "fanart", "background", "$titleKey")) })
            }
        }
    }

    fun update(item: MediaEntry): List<MediaEntry> = get().map { if (it.uri == item.uri) item else it }.also(::save)
    fun delete(uri: String): List<MediaEntry> = get().filterNot { it.uri == uri }.also(::save)
}

