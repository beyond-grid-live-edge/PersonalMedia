package com.personalmedia.android

import android.content.Context
import org.json.JSONObject

/** Per-title choices only; keys are SHA-256 digests, never raw content URIs or torrent links. */
internal class MediaTrackMemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "personalmedia_per_title_track_memory", Context.MODE_PRIVATE)

    @Synchronized fun read(key: String?): RememberedTracks {
        if (key == null || !key.startsWith("track_")) return RememberedTracks()
        return runCatching {
            val row = JSONObject(prefs.getString(key, "{}") ?: "{}")
            RememberedTracks(decode(row.optJSONObject("audio"), false),
                decode(row.optJSONObject("subtitle"), true))
        }.getOrDefault(RememberedTracks())
    }

    @Synchronized fun saveAudio(key: String?, choice: RememberedTrack?) = update(key, audio = choice, isAudio = true)
    @Synchronized fun saveSubtitle(key: String?, choice: RememberedTrack?) = update(key, audio = choice, isAudio = false)

    private fun update(key: String?, audio: RememberedTrack?, isAudio: Boolean) {
        if (key == null || !key.startsWith("track_") || !TrackMemoryPolicy.valid(audio, !isAudio)) return
        val current = read(key)
        val next = if (isAudio) current.copy(audio = audio) else current.copy(subtitle = audio)
        val edit = prefs.edit()
        if (next == RememberedTracks()) edit.remove(key)
        else edit.putString(key, JSONObject().apply {
            next.audio?.let { put("audio", encode(it)) }
            next.subtitle?.let { put("subtitle", encode(it)) }
        }.toString())
        // Small synchronous write: user can close the player immediately after a selection.
        edit.commit()
    }

    private fun encode(value: RememberedTrack) = JSONObject()
        .put("mode", value.mode)
        .put("language", value.language)
        .put("title", value.title)
        .put("occurrence", value.occurrence)

    private fun decode(row: JSONObject?, subtitle: Boolean): RememberedTrack? {
        if (row == null) return null
        val result = RememberedTrack(row.optString("mode"),
            row.optString("language"), row.optString("title"), row.optInt("occurrence", 0))
        return result.takeIf { TrackMemoryPolicy.valid(it, subtitle) }
    }
}
