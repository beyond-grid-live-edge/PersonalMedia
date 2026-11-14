package com.personalmedia.android

/** Conservative, dependency-free title/year matching for remote movie/TV artwork. */
internal object ArtworkMatchPolicy {
    private val suffix = Regex("(?i)\\s*\\((?:(?:19|20)\\d{2}\\s+)?(?:film|movie|tv series|television series|miniseries)\\)\\s*$")
    private val yearSuffix = Regex("\\s*\\(((?:19|20)\\d{2})\\)\\s*$")
    private val yearWord = Regex("(?<!\\d)((?:19|20)\\d{2})(?!\\d)")
    private val noise = Regex("(?i)\\b(?:2160p|1080p|720p|4k|bluray|blu ray|web dl|webrip|brrip|x264|x265|h264|hevc|remux|extended|uncut|hdr|proper|repack)\\b")
    private fun canonical(text: String): String = text.lowercase()
        .replace('&', ' ').replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun title(raw: String, type: String): String {
        var name = raw.trim().replace(suffix, "").replace(yearSuffix, "")
        if (type == "movie") {
            val marker = yearWord.find(name)
            if (marker != null && marker.range.first > 0) name = name.substring(0, marker.range.first)
            val release = noise.find(name)
            if (release != null && release.range.first > 0) name = name.substring(0, release.range.first)
        }
        return name.replace(Regex("[._]+"), " ").trim(' ', '-', ':', '.')
    }

    fun year(raw: String): Int? = yearWord.find(raw)?.value?.toIntOrNull()

    /** Never apply art from a different remake year, or from a vaguely similar title. */
    fun matches(requestTitle: String, requestYear: Int?, resultTitle: String,
                resultYear: Int?, kind: String): Boolean {
        val a = canonical(title(requestTitle, kind))
        val b = canonical(title(resultTitle, kind))
        if (a.length < 2 || a != b) return false
        if (requestYear != null && resultYear != null && requestYear != resultYear) return false
        return true
    }

    fun providerOrder(preference: String, hasTmdb: Boolean, hasOmdb: Boolean,
                      kind: String): List<String> {
        val free = if (kind == "tv") "tvmaze" else "wikipedia"
        return when (preference) {
            "free" -> listOf(free)
            "tmdb" -> (if (hasTmdb) listOf("tmdb") else emptyList()) + free
            "omdb" -> (if (hasOmdb) listOf("omdb") else emptyList()) + free
            else -> (if (hasTmdb) listOf("tmdb") else emptyList()) +
                (if (hasOmdb) listOf("omdb") else emptyList()) + free
        }
    }

    fun safeImage(url: String): Boolean = url.length < 2048 &&
        url.startsWith("https://", ignoreCase = true) &&
        !url.contains('@') && !url.contains('\\') && !url.contains(' ') &&
        !url.contains("127.0.0.1") && !url.contains("localhost", ignoreCase = true)
}

internal data class ArtworkMatch(
    val title: String, val year: Int?, val poster: String, val backdrop: String,
    val plot: String, val genres: String, val source: String
)
