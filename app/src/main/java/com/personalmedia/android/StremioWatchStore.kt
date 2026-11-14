package com.personalmedia.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** On-device Stremio Continue Watching rail. No credential, magnet, URL or tracker data is stored. */
data class StremioWatchEntry(
    val id: String, val type: String, val title: String, val poster: String, val background: String,
    val year: String, val videoId: String, val positionMs: Long
)

class StremioWatchStore(context: Context, accountEmail: String) {
    // Account-specific namespace; signing into a different Stremio account does not mix watch history.
    private val accountKey = accountEmail.trim().lowercase().hashCode().toUInt().toString(16)
    private val prefs = context.getSharedPreferences("pm_stremio_watch_$accountKey", Context.MODE_PRIVATE)
    fun get(): List<StremioWatchEntry> = runCatching {
        val data = JSONArray(prefs.getString("watch", "[]"))
        (0 until data.length()).map { data.getJSONObject(it) }.map { row ->
            StremioWatchEntry(row.optString("id"), row.optString("type"), row.optString("title"),
                row.optString("poster"), row.optString("background"), row.optString("year"),
                row.optString("videoId"), row.optLong("positionMs"))
        }.filter { it.id.isNotBlank() }
    }.getOrDefault(emptyList())

    fun mark(meta: StremioMeta, videoId: String) {
        val existing = get().firstOrNull { it.type == meta.type && it.id == meta.id && it.videoId == videoId }
        save(StremioWatchEntry(meta.id, meta.type, meta.name, meta.poster, meta.background,
            meta.year, videoId, existing?.positionMs ?: 0))
    }
    fun position(id: String, type: String, videoId: String): Long =
        get().firstOrNull { it.id == id && it.type == type && it.videoId == videoId }
            ?.positionMs?.coerceAtLeast(0) ?: 0L

    fun progress(id: String, type: String, videoId: String, ms: Long) {
        val entry = get().firstOrNull { it.id == id && it.type == type && it.videoId == videoId } ?: return
        if (ms > 0 && kotlin.math.abs(ms - entry.positionMs) >= 4000L) save(entry.copy(positionMs = ms))
    }
    private fun save(entry: StremioWatchEntry) {
        val others = get().filterNot { it.id == entry.id && it.type == entry.type && it.videoId == entry.videoId }
        val rows = JSONArray()
        (listOf(entry) + others).take(100).forEach { item ->
            rows.put(JSONObject().put("id", item.id).put("type", item.type).put("title", item.title)
                .put("poster", item.poster).put("background", item.background).put("year", item.year)
                .put("videoId", item.videoId).put("positionMs", item.positionMs))
        }
        prefs.edit().putString("watch", rows.toString()).apply()
    }
}
