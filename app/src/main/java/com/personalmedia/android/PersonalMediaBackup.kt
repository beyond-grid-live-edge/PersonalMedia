package com.personalmedia.android

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Portable, opt-in backup of strictly allowlisted non-secret application state.
 *  The JSON is NOT encrypted: the chosen document can contain media names and SAF URIs.
 *  Authentication, download URLs/headers, torrents and encrypted app files are NEVER exported.
 */
internal data class PersonalMediaBackupCandidate(val sections: JSONObject, val preview: String)

internal class PersonalMediaBackup(private val context: Context) {
    private val app = context.applicationContext
    private val undoFile = File(app.filesDir, "pm_str7d_restore_undo.json")
    private val undoPreferences = app.getSharedPreferences("pm_str7d_restore_undo", Context.MODE_PRIVATE)
    private val library get() = LocalLibrary(app)

    private fun prefs(name: String) = app.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun accountHash(email: String) = if (email.isBlank()) "" else
        hex(MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase(Locale.ROOT).toByteArray()))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun digest(value: String) = hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { key ->
            JSONObject.quote(key) + ":" + canonical(value.opt(key))
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
    private fun JSONObject.safeText(key: String, max: Int) = optString(key).take(max)
    private fun JSONArray.objects(max: Int): List<JSONObject> =
        (0 until minOf(length(), max)).mapNotNull { optJSONObject(it) }

    private fun safeArtwork(value: String): String = value.take(2049).takeIf { s ->
        s.isEmpty() || (s.length <= 2048 && (BackupRestorePolicy.validContentUri(s) ||
            (s.startsWith("https://") && !s.contains('@') && !s.contains('?') &&
                !s.contains('#') && !s.contains('\n') && !s.contains('\r'))))
    } ?: ""

    private fun mediaJson(e: MediaEntry) = JSONObject().apply {
        put("uri", e.uri); put("title", e.title); put("type", e.type); put("folder", e.folder)
        put("favourite", e.favourite); put("watched", e.watched); put("positionMs", e.positionMs)
        put("art", safeArtwork(e.art)); put("backdrop", safeArtwork(e.backdrop)); put("sourceUri", e.sourceUri)
        put("relativePath", e.relativePath); put("plot", e.plot); put("genres", e.genres)
        put("metadataSource", e.metadataSource)
    }
    private fun parseMedia(o: JSONObject): MediaEntry {
        val uri = o.safeText("uri", 4097)
        val kind = o.optString("type")
        require(BackupRestorePolicy.validContentUri(uri) && BackupRestorePolicy.validKind(kind)) { "Invalid media record" }
        val source = o.safeText("sourceUri", 4097)
        require(source.isEmpty() || BackupRestorePolicy.validContentUri(source)) { "Invalid source folder URI" }
        fun art(key: String) = safeArtwork(o.safeText(key, 2049))
        return MediaEntry(uri, o.safeText("title", 300), kind, o.safeText("folder", 700),
            o.optBoolean("favourite"), o.optLong("positionMs").coerceIn(0, 30L * 24 * 3600 * 1000),
            o.optBoolean("watched"), art("art"), art("backdrop"), source,
            o.safeText("relativePath", 900), o.safeText("plot", 2500),
            o.safeText("genres", 200), o.safeText("metadataSource", 50))
    }

    private fun snapshot(accountEmail: String): JSONObject {
        val rows = JSONArray()
        library.get().filter { BackupRestorePolicy.validContentUri(it.uri) &&
            BackupRestorePolicy.validKind(it.type) }.take(BackupRestorePolicy.MAX_MEDIA)
            .forEach { rows.put(mediaJson(it)) }
        val locations = JSONArray()
        LibraryFolderManager(app, library).sources().take(BackupRestorePolicy.MAX_FOLDERS)
            .forEach { f -> locations.put(JSONObject().put("treeUri", f.treeUri)
                .put("category", f.category).put("label", f.label)
                .put("lastScan", f.lastScan).put("scanTimeMs", f.scanTimeMs)) }
        val channels = JSONArray()
        T405ChannelStore.load(app).take(BackupRestorePolicy.MAX_CHANNELS).forEach { source ->
            // Save the receiver lineup, not potentially huge/stale EIT programme schedules.
            channels.put(JSONObject().put("key", source.optString("key"))
                .put("name", source.optString("name")).put("provider", source.optString("provider"))
                .put("number", source.optInt("number"))
                .put("frequencyHz", source.optLong("frequencyHz"))
                .put("bandwidthMHz", source.optInt("bandwidthMHz", 8))
                .put("encrypted", source.optBoolean("encrypted")))
        }
        val playback = PlaybackPreferencesStore(app).read()
        val settings = JSONObject().put("keepScreenAwake", playback.keepScreenAwake)
            .put("audioLanguage", playback.audioLanguage).put("subtitleLanguage", playback.subtitleLanguage)
            .put("subtitlesEnabled", playback.subtitlesEnabled).put("preferredQuality", playback.preferredQuality)
        val art = ArtworkPreferencesStore(app).read()
        val artSettings = JSONObject().put("provider", art.provider).put("language", art.language)
            .put("fetchPosters", art.fetchPosters).put("fetchBackdrops", art.fetchBackdrops)
            .put("automatic", art.automatic).put("batchSize", art.batchSize)
        val tracks = JSONObject()
        prefs("personalmedia_per_title_track_memory").all.forEach { (key, value) ->
            if (BackupRestorePolicy.validTrackKey(key) && value is String && value.length <= 2048 &&
                tracks.length() < BackupRestorePolicy.MAX_TRACK_KEYS) {
                runCatching { JSONObject(value) }.getOrNull()?.let { old ->
                    val sanitized = JSONObject()
                    for (type in listOf("audio", "subtitle")) {
                        old.optJSONObject(type)?.let { choice ->
                            val mode = choice.optString("mode")
                            if (mode in listOf("off", "track")) sanitized.put(type,
                                JSONObject().put("mode", mode)
                                    .put("language", choice.safeText("language", 24))
                                    .put("title", choice.safeText("title", 120))
                                    .put("occurrence", choice.optInt("occurrence").coerceIn(0,1000)))
                        }
                    }
                    if (sanitized.length() > 0) tracks.put(key, sanitized)
                }
            }
        }
        val us = JSONObject()
        prefs("pm_us_tv").all.forEach { (key, value) ->
            val channelId = when {
                key.startsWith("fav.") -> key.removePrefix("fav.")
                key.startsWith("source.") -> key.removePrefix("source.")
                else -> ""
            }
            if (UsTvPolicy.channelPage(channelId) != null &&
                ((key.startsWith("fav.") && value is Boolean) ||
                 (key.startsWith("source.") && value is Int && value in 1..16))) us.put(key, value)
        }
        val usCatalog = JSONArray()
        UsTvRepository(app).cached().take(2500).forEach { channel ->
            if (UsTvPolicy.channelPage(channel.id) != null)
                usCatalog.put(JSONObject().put("id", channel.id).put("name", channel.name.take(200)))
        }
        val receiver = JSONObject()
        prefs("t405_receiver").let { p ->
            for (key in listOf("host", "control", "receive", "frequency", "subtitleMode")) {
                val value = p.getString(key, null)
                if (value != null && value.length <= 100) receiver.put(key, value)
            }
        }
        val current = accountHash(accountEmail)
        val accountWatch = JSONObject().put("accountFingerprint", current)
        if (current.isNotEmpty()) {
            val namespace = accountEmail.trim().lowercase(Locale.ROOT).hashCode().toUInt().toString(16)
            val raw = prefs("pm_stremio_watch_$namespace").getString("watch", "[]") ?: "[]"
            val safeWatch = JSONArray()
            runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
                .objects(BackupRestorePolicy.MAX_WATCH).forEach { entry ->
                    if (entry.optString("id").isNotBlank() && entry.optString("videoId").isNotBlank())
                        safeWatch.put(JSONObject().put("id", entry.safeText("id", 180))
                            .put("type", entry.safeText("type", 12))
                            .put("videoId", entry.safeText("videoId", 200))
                            .put("title", entry.safeText("title", 250))
                            .put("year", entry.safeText("year", 8))
                            .put("positionMs", entry.optLong("positionMs").coerceAtLeast(0)))
                }
            accountWatch.put("entries", safeWatch)
        } else accountWatch.put("entries", JSONArray())
        return JSONObject().put("library", rows).put("folders", locations)
            .put("freeview", channels).put("receiver", receiver)
            .put("playback", settings).put("artwork", artSettings)
            .put("tracks", tracks).put("us", us).put("usCatalog", usCatalog)
            .put("stremioWatch", accountWatch)
    }

    fun export(destination: Uri, accountEmail: String): String {
        require(destination.scheme == "content") { "Choose an Android Files destination" }
        val data = JSONObject().put("format", BackupRestorePolicy.FORMAT)
            .put("version", BackupRestorePolicy.VERSION)
            .put("createdMs", System.currentTimeMillis())
            .put("sections", snapshot(accountEmail)).toString().toByteArray(Charsets.UTF_8)
        require(data.size <= BackupRestorePolicy.MAX_BYTES) { "Backup exceeds the safe 16 MiB limit" }
        val stream = app.contentResolver.openOutputStream(destination, "wt")
            ?: error("Android Files did not allow writing the selected document")
        stream.use { it.write(data); it.flush() }
        return "Backup exported (${data.size / 1024} KiB). Keep it private: JSON includes media titles and folder document IDs."
    }

    fun read(source: Uri): PersonalMediaBackupCandidate {
        require(source.scheme == "content") { "Choose a backup from Android Files" }
        val input = app.contentResolver.openInputStream(source) ?: error("Could not open the selected backup")
        val output = java.io.ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(16384)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                require(output.size() + n <= BackupRestorePolicy.MAX_BYTES) { "Backup is too large (16 MiB maximum)" }
                output.write(buffer, 0, n)
            }
        }
        val root = JSONObject(output.toString("UTF-8"))
        require(root.optString("format") == BackupRestorePolicy.FORMAT &&
            root.optInt("version") == BackupRestorePolicy.VERSION) { "Unsupported PersonalMedia backup version" }
        val sections = root.optJSONObject("sections") ?: error("Backup has no sections")
        validate(sections)
        return PersonalMediaBackupCandidate(sections, preview(sections))
    }

    private fun validate(s: JSONObject) {
        val media = s.optJSONArray("library") ?: error("Backup library section missing")
        val folders = s.optJSONArray("folders") ?: error("Backup folders section missing")
        val freeview = s.optJSONArray("freeview") ?: error("Backup Freeview section missing")
        val tracks = s.optJSONObject("tracks") ?: error("Backup tracks section missing")
        val us = s.optJSONObject("us") ?: error("Backup US channels section missing")
        val usCatalog = s.optJSONArray("usCatalog") ?: error("Backup US catalogue section missing")
        require(media.length() <= BackupRestorePolicy.MAX_MEDIA &&
            folders.length() <= BackupRestorePolicy.MAX_FOLDERS &&
            freeview.length() <= BackupRestorePolicy.MAX_CHANNELS &&
            tracks.length() <= BackupRestorePolicy.MAX_TRACK_KEYS && us.length() <= 10000 &&
            usCatalog.length() <= 2500) { "Backup exceeds safe record limits" }
        for (i in 0 until media.length()) parseMedia(media.optJSONObject(i) ?: error("Invalid library entry"))
        for (i in 0 until folders.length()) {
            val row = folders.optJSONObject(i) ?: error("Invalid folder entry")
            require(BackupRestorePolicy.validContentUri(row.optString("treeUri")) &&
                row.optString("category") in LibraryFolderPolicy.categories) { "Invalid saved library location" }
        }
        for (i in 0 until freeview.length()) {
            val row = freeview.optJSONObject(i) ?: error("Invalid Freeview entry")
            require(row.optString("key").matches(Regex("[0-9]{1,6}:[0-9]{1,6}:[0-9]{1,6}")) &&
                row.optString("name").length in 1..180) { "Invalid Freeview channel record" }
        }
        for (i in 0 until usCatalog.length()) {
            val row = usCatalog.optJSONObject(i) ?: error("Invalid US channel row")
            require(UsTvPolicy.channelPage(row.optString("id")) != null &&
                row.optString("name").length <= 200) { "Invalid US channel catalogue record" }
        }
        tracks.keys().forEach { key ->
            require(BackupRestorePolicy.validTrackKey(key)) { "Unexpected title preference key" }
            require(tracks.optJSONObject(key) != null) { "Invalid title preference" }
        }
        us.keys().forEach { key ->
            val id = key.substringAfter('.', "")
            require((key.startsWith("fav.") || key.startsWith("source.")) &&
                UsTvPolicy.channelPage(id) != null) { "Invalid US channel setting" }
        }
        require(s.optJSONObject("playback") != null && s.optJSONObject("artwork") != null &&
            s.optJSONObject("receiver") != null && s.optJSONObject("stremioWatch") != null) {
            "A backup settings section is missing"
        }
        val watch = s.getJSONObject("stremioWatch").optJSONArray("entries") ?: error("Invalid watch history")
        require(watch.length() <= BackupRestorePolicy.MAX_WATCH) { "Watch history exceeds supported size" }
    }

    private fun preview(s: JSONObject): String =
        "${s.getJSONArray("library").length()} library items, " +
        "${s.getJSONArray("folders").length()} folder registrations, " +
        "${s.getJSONArray("freeview").length()} Freeview channels, " +
        "${s.getJSONArray("usCatalog").length()} cached US channels, " +
        "${s.getJSONObject("tracks").length()} remembered track choices, " +
        "${s.getJSONObject("stremioWatch").getJSONArray("entries").length()} Stremio continue-watching entries. " +
        "Restoration merges library records. Original media and downloads are never deleted."

    /** No source credentials, download queue or authentication state is ever copied. */
    @Synchronized fun restore(candidate: PersonalMediaBackupCandidate, accountEmail: String): String {
        validate(candidate.sections)
        val before = snapshot(accountEmail)
        // The on-device undo snapshot is private and must be durable BEFORE the first mutation.
        val content = JSONObject().put("accountFingerprint", accountHash(accountEmail))
            .put("sections", before).toString()
        require(content.toByteArray().size <= BackupRestorePolicy.MAX_BYTES) { "Undo snapshot is too large" }
        // A new restore supersedes the previous undo; clear stale marker before
        // replacing its on-disk snapshot to avoid crossing two restore attempts.
        check(undoPreferences.edit().clear().commit()) { "Could not reset previous restore marker" }
        val temp = File(app.filesDir, "pm_str7d_restore_undo.tmp")
        temp.outputStream().use { it.write(content.toByteArray()); it.flush(); (it as java.io.FileOutputStream).fd.sync() }
        check(temp.renameTo(undoFile)) { "Could not safely prepare restore undo" }
        // Persist a recoverable pending marker before mutating any application data.
        // If the process dies mid-import, Settings can still offer Undo on next launch.
        check(undoPreferences.edit().clear().putBoolean("pending", true)
            .putString("account", accountHash(accountEmail)).commit()) { "Could not prepare restore recovery marker" }
        try {
            applySections(candidate.sections, accountEmail, merge = true)
        } catch (error: Exception) {
            if (runCatching { applySections(before, accountEmail, merge = false) }.isSuccess) {
                undoPreferences.edit().clear().commit()
                undoFile.delete()
            }
            throw IllegalStateException("Restore failed; previous settings were reapplied where possible", error)
        }
        val after = snapshot(accountEmail)
        check(undoPreferences.edit().putString("afterHash", digest(canonical(after)))
            .putBoolean("pending", false)
            .putString("account", accountHash(accountEmail)).commit()) { "Could not save undo marker" }
        return "Restored safely. Library entries were merged; saved folders may need reconnection. " +
            "Account credentials and download queues were not imported. Undo is available until backed-up settings change."
    }

    @Synchronized fun canUndo(accountEmail: String): Boolean = runCatching {
        undoFile.isFile && undoPreferences.getString("account", null) == accountHash(accountEmail) &&
            (undoPreferences.getBoolean("pending", false) ||
                undoPreferences.getString("afterHash", null) == digest(canonical(snapshot(accountEmail))))
    }.getOrDefault(false)

    @Synchronized fun undo(accountEmail: String): String {
        check(canUndo(accountEmail)) { "Backup undo is unavailable because the account or restored data changed" }
        val old = JSONObject(undoFile.readText())
        check(old.optString("accountFingerprint") == accountHash(accountEmail)) { "Backup belongs to a different account" }
        val original = old.getJSONObject("sections")
        validate(original)
        applySections(original, accountEmail, merge = false)
        undoPreferences.edit().clear().commit()
        undoFile.delete()
        return "Previous PersonalMedia library and preferences restored from the local undo snapshot."
    }

    private fun applySections(s: JSONObject, accountEmail: String, merge: Boolean) {
        val incoming = s.getJSONArray("library").objects(BackupRestorePolicy.MAX_MEDIA).map(::parseMedia)
        val present = if (merge) library.get() else emptyList()
        val originals = linkedMapOf<Pair<String, String>, MediaEntry>()
        for (item in present) originals[item.uri to item.type] = item
        for (item in incoming) {
            val key = item.uri to item.type
            val current = originals[key]
            originals[key] = if (current == null) item else current.copy(
                favourite = BackupRestorePolicy.mergeFlag(current.favourite, item.favourite),
                watched = BackupRestorePolicy.mergeFlag(current.watched, item.watched),
                positionMs = BackupRestorePolicy.mergePosition(current.positionMs, item.positionMs),
                art = current.art.ifBlank { item.art }, backdrop = current.backdrop.ifBlank { item.backdrop },
                plot = current.plot.ifBlank { item.plot }, genres = current.genres.ifBlank { item.genres })
        }
        check(originals.size <= BackupRestorePolicy.MAX_MEDIA) { "Merged catalog exceeds safe record limit" }
        library.save(originals.values.toList())
        val folderArray = s.getJSONArray("folders")
        val folderJson = JSONArray()
        val byUri = linkedMapOf<String, JSONObject>()
        if (merge) LibraryFolderManager(app, library).sources().forEach { f ->
            byUri[f.treeUri] = JSONObject().put("treeUri", f.treeUri).put("category", f.category)
                .put("label", f.label).put("lastScan", f.lastScan).put("scanTimeMs", f.scanTimeMs)
        }
        for (f in folderArray.objects(BackupRestorePolicy.MAX_FOLDERS)) {
            val id = f.optString("treeUri")
            byUri.putIfAbsent(id, JSONObject().put("treeUri", id)
                .put("category", f.optString("category"))
                .put("label", f.safeText("label", 160))
                .put("lastScan", if (merge) "Restored; reconnect permission if needed"
                    else f.safeText("lastScan", 300).ifBlank { "Restored from undo" })
                .put("scanTimeMs", if (merge) 0L else f.optLong("scanTimeMs")))
        }
        byUri.values.take(BackupRestorePolicy.MAX_FOLDERS).forEach { folderJson.put(it) }
        check(prefs("personalmedia_media_folders").edit().putString("sources", folderJson.toString())
            .remove("missingAudits").commit()) { "Could not save restored folders" }
        val restoredChannels = s.getJSONArray("freeview").objects(BackupRestorePolicy.MAX_CHANNELS)
        if (restoredChannels.isNotEmpty()) T405ChannelStore.save(app,
            if (merge) T405ChannelStore.merge(T405ChannelStore.load(app), restoredChannels)
            else restoredChannels)
        else if (!merge) prefs("personalmedia_t405_lineup_v1").edit().remove("services").commit()
        val receiver = s.getJSONObject("receiver")
        val receiverEdit = prefs("t405_receiver").edit()
        for (key in listOf("host", "control", "receive", "frequency", "subtitleMode")) {
            if (!merge || !prefs("t405_receiver").contains(key)) {
                val v = receiver.optString(key).take(100)
                if (v.isNotBlank()) receiverEdit.putString(key, v) else if (!merge) receiverEdit.remove(key)
            }
        }
        check(receiverEdit.commit()) { "Could not save tuner preferences" }
        val playback = s.getJSONObject("playback")
        val playbackSource = PlaybackPreferencesStore(app).read()
        check(PlaybackPreferencesStore(app).save(playbackSource.copy(
            keepScreenAwake = playback.optBoolean("keepScreenAwake", playbackSource.keepScreenAwake),
            audioLanguage = PlaybackPreferencesPolicy.language(playback.optString("audioLanguage")),
            subtitleLanguage = PlaybackPreferencesPolicy.language(playback.optString("subtitleLanguage")),
            subtitlesEnabled = playback.optBoolean("subtitlesEnabled"),
            preferredQuality = PlaybackPreferencesPolicy.quality(playback.optString("preferredQuality"))))) {
            "Could not save playback settings"
        }
        val a = s.getJSONObject("artwork")
        val currentArt = ArtworkPreferencesStore(app).read()
        check(ArtworkPreferencesStore(app).save(currentArt.copy(
            provider = a.optString("provider").takeIf { it in listOf("auto", "tmdb", "omdb", "free") } ?: "auto",
            language = a.optString("language").takeIf { it.matches(Regex("[a-z]{2}(?:-[A-Z]{2})?")) } ?: "en-US",
            fetchPosters = a.optBoolean("fetchPosters", true), fetchBackdrops = a.optBoolean("fetchBackdrops", true),
            automatic = a.optBoolean("automatic"), batchSize = if (a.optInt("batchSize") == 25) 25 else 2000))) {
            "Could not save artwork settings"
        }
        val trackPrefs = prefs("personalmedia_per_title_track_memory")
        val selectedTracks = s.getJSONObject("tracks")
        val trackEdit = trackPrefs.edit()
        if (!merge) trackEdit.clear()
        selectedTracks.keys().forEach { key ->
            if (!merge || !trackPrefs.contains(key)) trackEdit.putString(key, selectedTracks.getJSONObject(key).toString())
        }
        check(trackEdit.commit()) { "Could not save audio/subtitle memories" }
        val usPrefs = prefs("pm_us_tv")
        val us = s.getJSONObject("us")
        val usEdit = usPrefs.edit()
        if (!merge) usPrefs.all.keys.filter { it.startsWith("fav.") || it.startsWith("source.") }
            .forEach { usEdit.remove(it) }
        us.keys().forEach { key ->
            if (!merge || !usPrefs.contains(key)) {
                if (key.startsWith("fav.")) usEdit.putBoolean(key, us.optBoolean(key))
                else usEdit.putInt(key, us.optInt(key, 1).coerceIn(1,16))
            } else if (key.startsWith("fav.") && us.optBoolean(key)) usEdit.putBoolean(key, true)
        }
        check(usEdit.commit()) { "Could not save US channel preferences" }
        val cachedChannels = s.getJSONArray("usCatalog")
        val existingChannels = if (merge) UsTvRepository(app).cached() else emptyList()
        val channelMap = linkedMapOf<String, JSONObject>()
        existingChannels.forEach { channel ->
            channelMap[channel.id] = JSONObject().put("id", channel.id).put("name", channel.name)
        }
        for (row in cachedChannels.objects(2500)) channelMap.putIfAbsent(
            row.optString("id"), JSONObject().put("id", row.optString("id"))
                .put("name", row.safeText("name", 200)))
        val usCatalogEdit = usPrefs.edit().putString("catalog",
            JSONArray(channelMap.values.take(2500)).toString())
        if (!merge) usCatalogEdit.putLong("catalogFetched", 0L)
        check(usCatalogEdit.commit()) { "Could not save US channel cache" }
        val watch = s.getJSONObject("stremioWatch")
        val fingerprint = accountHash(accountEmail)
        if (fingerprint.isNotEmpty() && BackupRestorePolicy.canImportAccount(
                watch.optString("accountFingerprint"), fingerprint)) {
            val namespace = accountEmail.trim().lowercase(Locale.ROOT).hashCode().toUInt().toString(16)
            val p = prefs("pm_stremio_watch_$namespace")
            val imported = watch.getJSONArray("entries")
            val original = if (merge) runCatching { JSONArray(p.getString("watch", "[]") ?: "[]") }.getOrDefault(JSONArray())
                else JSONArray()
            val rows = linkedMapOf<String, JSONObject>()
            for (row in original.objects(BackupRestorePolicy.MAX_WATCH) + imported.objects(BackupRestorePolicy.MAX_WATCH)) {
                val id = row.safeText("id", 180)
                val type = row.optString("type")
                val video = row.safeText("videoId", 200)
                if (id.isBlank() || type !in setOf("movie", "series") || video.isBlank()) continue
                val key = "$type\u0000$id\u0000$video"
                val old = rows[key]
                rows[key] = JSONObject().put("id", id).put("type", type)
                    .put("videoId", video).put("title", row.safeText("title", 250))
                    .put("poster", row.safeText("poster", 2048))
                    .put("background", row.safeText("background", 2048))
                    .put("year", row.safeText("year", 8))
                    .put("positionMs", BackupRestorePolicy.mergePosition(
                        old?.optLong("positionMs") ?: 0L, row.optLong("positionMs")))
            }
            check(p.edit().putString("watch", JSONArray(rows.values.toList().takeLast(BackupRestorePolicy.MAX_WATCH)).toString())
                .commit()) { "Could not save continue-watching records" }
        }
    }
}
