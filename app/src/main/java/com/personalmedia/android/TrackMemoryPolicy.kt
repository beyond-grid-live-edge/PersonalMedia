package com.personalmedia.android

import java.security.MessageDigest
import java.util.Locale

/** Only semantic track choices survive: never cache transient MPV track IDs or stream URLs. */
internal data class RememberedTrack(
    val mode: String, // "auto", "off" (subtitles only), or "track"
    val language: String = "",
    val title: String = "",
    val occurrence: Int = 0
)

internal data class RememberedTracks(
    val audio: RememberedTrack? = null,
    val subtitle: RememberedTrack? = null
)

internal data class AvailableTrack(val language: String, val title: String)

/** No Android dependency: tested independently of an emulator. */
internal object TrackMemoryPolicy {
    fun mediaKey(identity: String): String? {
        if (identity.isBlank() || identity.length > 4096) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        return "track_" + digest.joinToString("") { "%02x".format(it) }
    }

    fun localKey(uri: String): String? = if (uri.startsWith("content://"))
        mediaKey("local\u0000$uri") else null

    fun stremioKey(account: String, type: String, id: String, videoId: String): String? =
        if (type.isNotBlank() && id.isNotBlank() && videoId.isNotBlank())
            mediaKey("stremio\u0000${account.trim().lowercase(Locale.ROOT)}\u0000$type\u0000$id\u0000$videoId")
        else null

    private fun normalizedLanguage(value: String): String {
        val s = value.trim().lowercase(Locale.ROOT).replace('_', '-').substringBefore('-')
        return when (s) {
            "eng", "english" -> "en"
            "fra", "fre", "french" -> "fr"
            "deu", "ger", "german" -> "de"
            "spa", "esl", "spanish" -> "es"
            "jpn", "japanese" -> "ja"
            else -> s.take(24)
        }
    }
    private fun normalizedTitle(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(120)

    fun makeChoice(selectedIndex: Int, available: List<AvailableTrack>): RememberedTrack? {
        if (selectedIndex !in available.indices) return null
        val track = available[selectedIndex]
        val lang = normalizedLanguage(track.language)
        val label = normalizedTitle(track.title)
        val ordinal = available.take(selectedIndex).count {
            normalizedLanguage(it.language) == lang && normalizedTitle(it.title) == label
        }
        return RememberedTrack("track", lang, label, ordinal)
    }

    /** Conservative: never choose a different dub/commentary if the remembered track vanished. */
    fun find(choice: RememberedTrack?, available: List<AvailableTrack>): Int? {
        if (choice?.mode != "track") return null
        val lang = normalizedLanguage(choice.language)
        val title = normalizedTitle(choice.title)
        if (lang.isEmpty() && title.isEmpty()) return null
        val exact = available.indices.filter { idx ->
            (lang.isEmpty() || normalizedLanguage(available[idx].language) == lang) &&
                normalizedTitle(available[idx].title) == title
        }
        if (exact.isNotEmpty()) return exact.getOrNull(choice.occurrence.coerceAtLeast(0))
        // Only fall back by language when the stored track had no distinguishing title.
        // Never replace a missing "Main" dub with the only remaining "Commentary" dub.
        if (lang.isNotEmpty() && title.isEmpty()) {
            val sameLanguage = available.indices.filter { idx ->
                normalizedLanguage(available[idx].language) == lang
            }
            if (sameLanguage.size == 1) return sameLanguage.first()
        }
        return null
    }

    /** External subtitle URLs are never saved; match only currently offered safe URLs. */
    fun externalSubtitleIndex(choice: RememberedTrack?, languages: List<String>): Int? {
        if (choice?.mode != "external" || choice.language.isBlank()) return null
        val lang = normalizedLanguage(choice.language)
        val savedIndex = choice.occurrence
        if (savedIndex in languages.indices && normalizedLanguage(languages[savedIndex]) == lang)
            return savedIndex
        val matches = languages.indices.filter { normalizedLanguage(languages[it]) == lang }
        return matches.singleOrNull()
    }

    fun valid(choice: RememberedTrack?, subtitle: Boolean): Boolean {
        if (choice == null) return true
        if (choice.mode == "auto") return true
        if (subtitle && choice.mode == "off") return true
        if (subtitle && choice.mode == "external") return choice.occurrence in 0..63 &&
            choice.language.isNotBlank() && choice.language.length <= 24 && choice.title.isEmpty()
        return choice.mode == "track" && choice.occurrence in 0..63 &&
            choice.language.length <= 24 && choice.title.length <= 120 &&
            (choice.language.isNotBlank() || choice.title.isNotBlank())
    }
}
