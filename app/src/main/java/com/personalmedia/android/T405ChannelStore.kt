package com.personalmedia.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Persistent, account-independent DVB service lineup and broadcast EIT snapshots.
 * Uses private SharedPreferences storage so app updates and network disconnects
 * cannot silently erase scanned services. No synthetic channel or EPG entries. */
object T405ChannelStore {
    private const val STORE = "personalmedia_t405_lineup_v1"
    private const val DATA = "services"
    private const val MAX_CHANNELS = 600
    private const val MAX_EVENTS = 96 // native receiver retains up to 96 EIT events per service
    private var lastSaved = ""

    fun load(context: Context): List<JSONObject> {
        val source = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(DATA, "[]").orEmpty()
        return runCatching { JSONArray(source).let { array ->
            val now = System.currentTimeMillis()
            (0 until minOf(array.length(), MAX_CHANNELS)).mapNotNull { array.optJSONObject(it) }
                .map { normalize(it, now) }
        } }.getOrDefault(emptyList())
    }

    private fun time(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(-1)

    private fun eventValid(event: JSONObject?, now: Long): Boolean {
        if (event == null || event.optString("title").isBlank()) return false
        val start = time(event.optString("start"))
        val end = time(event.optString("end"))
        return start > 0 && end > start && end >= now - 3_600_000L &&
            start <= now + 7L * 86_400_000L && end - start <= 36L * 3_600_000L
    }

    private fun normalize(row: JSONObject, now: Long): JSONObject {
        val item = JSONObject(row.toString())
        val events = linkedMapOf<Long, JSONObject>()
        fun add(event: JSONObject?) {
            if (!eventValid(event, now)) return
            val key = time(event!!.optString("start"))
            val old = events[key]
            if (old == null || (old.optString("description").isBlank() && event.optString("description").isNotBlank()))
                events[key] = event
        }
        val all = item.optJSONArray("events") ?: JSONArray()
        for (i in 0 until all.length()) add(all.optJSONObject(i))
        add(item.optJSONObject("now")); add(item.optJSONObject("next"))
        val timeline = events.values.sortedBy { time(it.optString("start")) }.take(MAX_EVENTS)
        item.put("events", JSONArray(timeline))
        val current = timeline.lastOrNull { e ->
            val t = time(e.optString("start")); t <= now && now < time(e.optString("end")) }
        val nextStart = current?.let { time(it.optString("end")) } ?: now
        val next = timeline.firstOrNull { time(it.optString("start")) >= nextStart }
        item.put("now", current ?: JSONObject())
        item.put("next", next ?: JSONObject())
        return item
    }

    /** Fresh native SI wins, but missing tables do not erase valid saved EPG. */
    fun merge(cached: List<JSONObject>, incoming: List<JSONObject>): List<JSONObject> {
        val now = System.currentTimeMillis()
        val map = linkedMapOf<String, JSONObject>()
        for (source in cached) {
            val key = source.optString("key")
            if (key.isNotBlank() && map.size < MAX_CHANNELS) map[key] = normalize(source, now)
        }
        for (source in incoming) {
            val key = source.optString("key")
            if (key.isBlank()) continue
            val fresh = normalize(source, now)
            val old = map[key]
            if (old != null) {
                for (part in listOf("now", "next"))
                    if (!eventValid(fresh.optJSONObject(part), now) && eventValid(old.optJSONObject(part), now))
                        fresh.put(part, old.optJSONObject(part))
                val events = linkedMapOf<String, JSONObject>()
                for (row in listOf(old, fresh)) {
                    val arr = row.optJSONArray("events") ?: continue
                    for (i in 0 until arr.length()) {
                        val event = arr.optJSONObject(i) ?: continue
                        if (eventValid(event, now)) events[event.optString("start")] = event
                    }
                }
                fresh.put("events", JSONArray(events.values.sortedBy { time(it.optString("start")) }.take(MAX_EVENTS)))
            }
            if (map.size < MAX_CHANNELS || map.containsKey(key)) map[key] = normalize(fresh, now)
        }
        return map.values.sortedWith(compareBy<JSONObject> { val n = it.optInt("number", 0); if (n > 0) n else 100_000 }
            .thenBy { it.optString("name") }).toList()
    }

    fun save(context: Context, list: List<JSONObject>) {
        if (list.isEmpty()) return // failed scans never destroy saved lineup
        val text = JSONArray(list.take(MAX_CHANNELS)).toString()
        if (text == lastSaved) return
        lastSaved = text
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putString(DATA, text).apply()
    }

    /** Tab/newline-free rows, strictly parsed and validated by the native receiver. */
    fun restoreNative(context: Context): Int {
        if (!NativeBridge.loaded) return 0
        val rows = load(context).mapNotNull { row ->
            val parts = row.optString("key").split(':')
            if (parts.size != 3 || parts.any { it.toIntOrNull() == null }) return@mapNotNull null
            val name = row.optString("name").replace(Regex("[\\t\\r\\n]"), " ").take(180)
            val provider = row.optString("provider").replace(Regex("[\\t\\r\\n]"), " ").take(180)
            if (name.isBlank()) return@mapNotNull null
            (parts + listOf(row.optLong("frequencyHz").toString(),
                row.optInt("bandwidthMHz", 8).toString(), row.optInt("number").toString(),
                if (row.optBoolean("encrypted")) "1" else "0", name, provider)).joinToString("\t")
        }
        return runCatching { NativeBridge.tvRestoreChannels(rows.joinToString("\n")) }.getOrDefault(0)
    }
}
