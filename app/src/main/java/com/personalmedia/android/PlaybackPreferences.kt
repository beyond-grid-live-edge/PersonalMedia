package com.personalmedia.android

/** Native playback preferences, independent of the HarmonyOS installation or Stremio login.
 * No source URLs, filenames, credentials or playback history are stored here. */
data class PlaybackPreferences(
    val keepScreenAwake: Boolean = true,
    val audioLanguage: String = "auto",
    val subtitleLanguage: String = "auto",
    val subtitlesEnabled: Boolean = false,
    val preferredQuality: String = "auto"
)

/** Pure policy: safe defaults and exact language matches, never a guessed track. */
internal object PlaybackPreferencesPolicy {
    val languages = listOf("auto", "en", "fr", "de")
    val qualities = listOf("auto", "4K", "1080p", "720p")

    fun language(value: String?): String = value?.takeIf { it in languages } ?: "auto"
    fun quality(value: String?): String = value?.takeIf { it in qualities } ?: "auto"

    fun streamFilter(preferred: String, availableQualities: List<String>): String {
        val quality = quality(preferred)
        return if (quality != "auto" && availableQualities.any { it == quality })
            quality else "All"
    }

    /** Match mpv's actual track language metadata, never guess from a release title. */
    fun trackForLanguage(preferred: String, tracks: List<Pair<String, String>>): String? {
        val language = language(preferred)
        if (language == "auto") return null
        val aliases = when (language) {
            "en" -> setOf("en", "eng", "english")
            "fr" -> setOf("fr", "fra", "fre", "french")
            "de" -> setOf("de", "deu", "ger", "german")
            else -> return null
        }
        return tracks.firstOrNull { (_, label) ->
            val prefix = label.substringBefore('·').trim().lowercase()
            prefix in aliases || prefix.substringBefore('-') in aliases
        }?.first
    }
}
