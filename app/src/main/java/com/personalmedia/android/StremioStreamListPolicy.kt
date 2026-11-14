package com.personalmedia.android

/** Independent of Compose and provider networking: preserve complete stream
 * arrays and let the user filter/paginate only the visible list. Never apply a
 * quality limit to the provider response itself. */
internal object StremioStreamListPolicy {
    const val PAGE_SIZE = 48
    val categories = listOf("4K", "1440p", "1080p", "720p", "480p", "Other")
    private val resolution = Regex(
        "(?i)(?<![a-z0-9])(?:4320p|8k|2160p|4k|uhd|1440p|1080p|720p|480p|360p|240p|sd)(?![a-z0-9])"
    )

    fun quality(name: String, release: String, description: String = ""): String {
        // Stream titles normally contain the actual filename/quality; a provider
        // badge may contain a different quality, so prefer the release title.
        val token = sequenceOf(release, name, description)
            .mapNotNull { resolution.find(it)?.value?.lowercase() }.firstOrNull() ?: return "Other"
        return when (token) {
            "4320p", "8k" -> "8K"
            "2160p", "4k", "uhd" -> "4K"
            "1440p" -> "1440p"
            "1080p" -> "1080p"
            "720p" -> "720p"
            "480p" -> "480p"
            "360p" -> "360p"
            "240p" -> "240p"
            else -> "Other"
        }
    }

    fun category(quality: String): String = if (quality in categories) quality else "Other"
    fun filter(streams: List<StremioSource>, selected: String): List<StremioSource> =
        if (selected == "All") streams else streams.filter { category(it.quality) == selected }
    fun counts(streams: List<StremioSource>): Map<String, Int> =
        streams.groupingBy { category(it.quality) }.eachCount()
    fun nextCount(current: Int, total: Int): Int = (current + PAGE_SIZE).coerceAtMost(total)
}
