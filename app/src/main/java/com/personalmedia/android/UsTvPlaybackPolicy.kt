package com.personalmedia.android

import java.net.URI
import java.util.Locale

/** US-LTV-2 playback choices; streaming addresses must come from the selected
 * Global Free TV US channel page. An EPG is never a media source. */
internal object UsTvPlaybackPolicy {
    enum class Format { HLS, DASH, TRANSPORT_STREAM, PROGRESSIVE }

    fun format(url: String): Format? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
        return when {
            path.endsWith(".m3u8") -> Format.HLS
            path.endsWith(".mpd") -> Format.DASH
            path.endsWith(".ts") -> Format.TRANSPORT_STREAM
            path.endsWith(".mp4") || path.endsWith(".m4v") -> Format.PROGRESSIVE
            else -> null
        }
    }

    fun allowed(url: String, httpOptIn: Boolean): Boolean =
        format(url) != null && UsTvPolicy.isPublicMediaUrl(url) &&
            (url.startsWith("https://", true) ||
                (httpOptIn && url.startsWith("http://", true)))

    /** Prefer the user-selected source, then attempt other allowed sources once.
     * Never convert a link from the EPG or an unrelated IPTV directory. */
    fun order(sources: List<Pair<Int, String>>, preferred: Int, httpOptIn: Boolean): List<Int> {
        val valid = sources.filter { allowed(it.second, httpOptIn) }.map { it.first }.distinct()
        return if (preferred in valid) listOf(preferred) + valid.filterNot { it == preferred } else valid
    }

    fun next(ordered: List<Int>, attempted: Set<Int>): Int? = ordered.firstOrNull { it !in attempted }
}
