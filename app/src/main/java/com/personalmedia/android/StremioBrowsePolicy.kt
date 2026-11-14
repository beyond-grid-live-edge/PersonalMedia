package com.personalmedia.android

/** Pure browsing policy: dedupe provider results and merge only metadata for identical media IDs.
 * No network request or account write belongs here. */
internal object StremioBrowsePolicy {
    /** Prefer compatible popular catalogues for a fast meaningful first screen.
     * All installed providers remain selectable in Discover. */
    fun featuredCatalogs(catalogs: List<StremioCatalog>, type: String, limit: Int = 3): List<StremioCatalog> {
        val ranked = catalogs.filter { it.type == type && !it.requiresExtra }
            .sortedWith(compareBy<StremioCatalog> { cat ->
                val name = "${cat.name} ${cat.id}".lowercase()
                when {
                    "popular" in name || "top" in name -> 0
                    "trending" in name -> 1
                    "new" in name || "recent" in name -> 2
                    else -> 3
                }
            }.thenBy { it.addonName.lowercase() })
        // Take the best catalogue per add-on first: one provider shouldn't
        // crowd every other installed provider out of the initial view.
        return (ranked.distinctBy { it.addonId } + ranked)
            .distinctBy { "${it.addonId}:${it.type}:${it.id}" }
            .take(limit.coerceAtLeast(0))
    }

    fun uniqueTitles(items: List<StremioMeta>): List<StremioMeta> =
        items.filter { it.id.isNotBlank() && (it.type == "movie" || it.type == "series") }
            .distinctBy { "${it.type}:${it.id}" }

    private fun richer(a: String, b: String): String = if (b.isNotBlank() && b.length > a.length) b else a
    private fun episodeScore(v: StremioVideo): Int =
        (if (v.thumbnail.isNotBlank()) 8 else 0) +
        (if (v.overview.isNotBlank()) 5 else 0) +
        (if (v.released.isNotBlank()) 2 else 0) +
        (if (v.title.isNotBlank() && !v.title.matches(Regex("S\\d+ E\\d+"))) 1 else 0)

    fun mergeDetails(seed: StremioMeta, results: List<StremioMeta>): StremioMeta {
        val valid = results.filter { it.id == seed.id && it.type == seed.type }
        var combined = seed
        val videos = linkedMapOf<String, StremioVideo>()
        for (candidate in listOf(seed) + valid) {
            combined = combined.copy(
                name = richer(combined.name, candidate.name),
                poster = if (combined.poster.isBlank()) candidate.poster else combined.poster,
                background = if (combined.background.isBlank()) candidate.background else combined.background,
                description = richer(combined.description, candidate.description),
                year = if (combined.year.isBlank()) candidate.year else combined.year,
                addonId = if (combined.addonId.isBlank()) candidate.addonId else combined.addonId
            )
            for (v in candidate.videos) {
                if (v.id.isBlank()) continue
                val old = videos[v.id]
                if (old == null) videos[v.id] = v
                else {
                    val preferred = if (episodeScore(v) > episodeScore(old)) v else old
                    videos[v.id] = preferred.copy(
                        title = when {
                            old.title.matches(Regex("S\\d+ E\\d+")) &&
                                !v.title.matches(Regex("S\\d+ E\\d+")) -> v.title
                            else -> richer(old.title, v.title).ifBlank { preferred.title }
                        },
                        overview = richer(old.overview, v.overview),
                        thumbnail = old.thumbnail.ifBlank { v.thumbnail },
                        released = old.released.ifBlank { v.released }
                    )
                }
            }
        }
        return combined.copy(videos = videos.values.sortedWith(compareBy<StremioVideo> { it.season }
            .thenBy { it.episode }.thenBy { it.id }))
    }

    fun episodesInSeason(videos: List<StremioVideo>, season: Int): List<StremioVideo> =
        videos.filter { it.season == season }.distinctBy { it.id }
            .sortedWith(compareBy<StremioVideo> { it.episode }.thenBy { it.id })
}
