package com.personalmedia.android

/** One network lookup per distinct film or TV series, not per file/episode. */
internal data class ArtworkBatchTarget(
    val key: String, val title: String, val year: Int?, val kind: String,
    val uris: Set<String>
)
internal object ArtworkBatchPlanner {
    fun plan(items: List<MediaEntry>, options: ArtworkPreferences): List<ArtworkBatchTarget> {
        val movies = LocalLibraryOrganizer.movieEditions(items).map { film ->
            ArtworkBatchTarget("movie:${film.key}", film.title, film.year.toIntOrNull(), "movie",
                film.editions.map(MediaEntry::uri).toSet())
        }
        val shows = LocalLibraryOrganizer.tvShows(items).map { show ->
            ArtworkBatchTarget("tv:${show.key}", ArtworkMatchPolicy.title(show.title, "tv"),
                ArtworkMatchPolicy.year(show.title), "tv", show.episodes.map(MediaEntry::uri).toSet())
        }
        val byUri = items.associateBy(MediaEntry::uri)
        return (movies + shows).filter { target ->
            target.title.isNotBlank() && target.uris.any { uri ->
                val entry = byUri[uri] ?: return@any false
                (options.fetchPosters && entry.art.isBlank()) ||
                    (options.fetchBackdrops && entry.backdrop.isBlank()) ||
                    entry.plot.isBlank()
            }
        }
    }
}
