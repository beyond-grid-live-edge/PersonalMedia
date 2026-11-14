package com.personalmedia.android

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** Per-account watched flags and queued writes. Separate from Continue Watching positions.
 * Imports never silently replace pending local changes; writes need explicit opt-in. */
internal data class StremioPendingWatch(
    val seriesId: String, val videoId: String, val watched: Boolean, val revision: Long
)

internal class StremioWatchedStore(context: Context, accountEmail: String) {
    private val account = accountEmail.trim().lowercase()
    private val namespace = MessageDigest.getInstance("SHA-256")
        .digest(account.toByteArray(Charsets.UTF_8)).take(16)
        .joinToString("") { "%02x".format(it) }
    private val prefs = context.getSharedPreferences("pm_stremio_watched_$namespace", Context.MODE_PRIVATE)

    var twoWayEnabled: Boolean
        get() = prefs.getBoolean("twoWayEnabled", false)
        set(value) { prefs.edit().putBoolean("twoWayEnabled", value).apply() }

    val imported: Boolean get() = prefs.getBoolean("imported", false)
    private fun remote(): JSONObject = runCatching {
        JSONObject(prefs.getString("remote", "{}") ?: "{}")
    }.getOrElse { JSONObject() }
    private fun queue(): JSONObject = runCatching {
        JSONObject(prefs.getString("pending", "{}") ?: "{}")
    }.getOrElse { JSONObject() }

    /** Keep only non-secret episode metadata needed to retry queued edits after restart. */
    @Synchronized fun rememberSeries(meta: StremioMeta) {
        if (meta.type != "series" || meta.id.isBlank() || meta.videos.isEmpty()) return
        val saved = runCatching { JSONObject(prefs.getString("seriesMetadata", "{}") ?: "{}") }
            .getOrElse { JSONObject() }
        val row = JSONObject().put("id", meta.id).put("name", meta.name)
            .put("poster", meta.poster).put("year", meta.year)
        val videos = org.json.JSONArray()
        StremioWatchedCodec.orderedVideos(meta.videos).take(800).forEach { video ->
            videos.put(JSONObject().put("id", video.id).put("title", video.title)
                .put("season", video.season).put("episode", video.episode)
                .put("released", video.released))
        }
        row.put("videos", videos)
        saved.put(meta.id, row)
        prefs.edit().putString("seriesMetadata", saved.toString()).commit()
    }

    @Synchronized fun seriesMetadata(id: String): StremioMeta? {
        val row = runCatching { JSONObject(prefs.getString("seriesMetadata", "{}") ?: "{}") }
            .getOrElse { JSONObject() }.optJSONObject(id) ?: return null
        val videos = row.optJSONArray("videos") ?: return null
        val list = (0 until videos.length()).mapNotNull { videos.optJSONObject(it) }.map { video ->
            StremioVideo(video.optString("id"), video.optString("title"),
                video.optInt("season"), video.optInt("episode"), released = video.optString("released"))
        }.filter { it.id.isNotBlank() }
        return StremioMeta(id = id, type = "series", name = row.optString("name"),
            poster = row.optString("poster"), year = row.optString("year"), videos = list)
    }

    @Synchronized fun pending(): List<StremioPendingWatch> {
        val entries = queue()
        return entries.keys().asSequence().mapNotNull { series ->
            entries.optJSONObject(series)?.let { entry ->
                entry.keys().asSequence().mapNotNull { video ->
                    entry.optJSONObject(video)?.let { value ->
                        StremioPendingWatch(series, video, value.optBoolean("watched"), value.optLong("rev"))
                    }
                }.toList()
            }
        }.flatten().toList().sortedBy { it.revision }
    }

    @Synchronized fun setLocal(series: String, video: String, watched: Boolean) {
        require(series.isNotBlank() && video.isNotBlank())
        val entries = queue()
        val row = entries.optJSONObject(series) ?: JSONObject()
        val revision = maxOf(System.currentTimeMillis(), (pending().maxOfOrNull { it.revision } ?: 0L) + 1L)
        row.put(video, JSONObject().put("watched", watched).put("rev", revision))
        entries.put(series, row)
        prefs.edit().putString("pending", entries.toString()).commit()
    }

    @Synchronized fun importRemote(remoteFields: Map<String, String>) {
        val obj = JSONObject()
        remoteFields.forEach { (id, raw) -> if (id.isNotBlank()) obj.put(id, raw) }
        // Never touch queued edits when importing Stremio history.
        prefs.edit().putString("remote", obj.toString()).putBoolean("imported", true).commit()
    }

    @Synchronized fun updateAfterUpload(series: String, raw: String, sent: List<StremioPendingWatch>) {
        val fields = remote(); fields.put(series, raw)
        val entries = queue()
        val row = entries.optJSONObject(series)
        if (row != null) {
            sent.forEach { change ->
                if (row.optJSONObject(change.videoId)?.optLong("rev") == change.revision)
                    row.remove(change.videoId)
            }
            if (!row.keys().hasNext()) entries.remove(series) else entries.put(series, row)
        }
        prefs.edit().putString("remote", fields.toString()).putString("pending", entries.toString())
            .putBoolean("imported", true).commit()
    }

    /** Decode once per series rather than decompressing the bitfield for every episode row. */
    @Synchronized fun statuses(series: String, videos: List<StremioVideo>): Map<String, Boolean?> {
        val ids = StremioWatchedCodec.orderedVideos(videos).map { it.id }
        val remoteWatched = if (imported) StremioWatchedCodec.decode(remote().optString(series, ""), ids) else null
        val overlay = pending().filter { it.seriesId == series }.associateBy { it.videoId }
        return ids.associateWith { id -> overlay[id]?.watched ?: remoteWatched?.contains(id) }
    }

    @Synchronized fun remoteCount(): Int = remote().length()
}
