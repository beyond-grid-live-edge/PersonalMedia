package com.personalmedia.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioBrowsePolicyTest {
    @Test fun featuredCatalogsPreferPopularAndProviderDiversity() {
        fun cat(provider: String, id: String, name: String, type: String = "movie",
            required: Boolean = false) = StremioCatalog(provider, provider, id, type,
            name, searchable = false, paginated = true, requiresExtra = required,
            genres = emptyList())
        val catalogs = listOf(
            cat("cinemeta", "top", "Popular"),
            cat("cinemeta", "new", "New"),
            cat("provider2", "top", "Popular"),
            cat("provider3", "required", "Popular", required = true),
            cat("provider4", "shows", "Popular", type = "series")
        )
        val featured = StremioBrowsePolicy.featuredCatalogs(catalogs, "movie", 3)
        assertEquals(3, featured.size)
        assertEquals(listOf("cinemeta", "provider2", "cinemeta"), featured.map { it.addonId })
        assertTrue(featured.none { it.requiresExtra || it.type != "movie" })
        assertEquals(1, StremioBrowsePolicy.featuredCatalogs(catalogs, "series").size)
    }

    @Test fun dedupeUsesTypeAndId() {
        val series = StremioMeta("same-id", "series", "Series")
        val movie = StremioMeta("same-id", "movie", "Movie")
        assertEquals(2, StremioBrowsePolicy.uniqueTitles(listOf(series, series, movie)).size)
    }

    @Test fun episodeEnrichmentPreservesPlayableId() {
        val seed = StremioMeta("tt-example", "series", "Test Series")
        val first = seed.copy(videos = listOf(
            StremioVideo("tt-example:1:2", "Second", 1, 2),
            StremioVideo("tt-example:1:1", "S1 E1", 1, 1)))
        val richer = seed.copy(videos = listOf(StremioVideo(
            "tt-example:1:1", "Pilot", 1, 1,
            overview = "Actual synopsis", thumbnail = "https://example.org/still.jpg", released = "2026-11-02")))
        val unrelated = StremioMeta("other", "series", "Other", videos = listOf(
            StremioVideo("not-our-video", "Unrelated", 1, 3)))
        val merged = StremioBrowsePolicy.mergeDetails(seed, listOf(first, richer, unrelated))
        assertEquals(listOf("tt-example:1:1", "tt-example:1:2"), merged.videos.map { it.id })
        assertEquals("Pilot", merged.videos[0].title)
        assertEquals("Actual synopsis", merged.videos[0].overview)
        assertTrue(merged.videos.none { it.id == "not-our-video" })
        assertEquals(2, StremioBrowsePolicy.episodesInSeason(merged.videos, 1).size)
        assertTrue(StremioBrowsePolicy.episodesInSeason(merged.videos, 2).isEmpty())
    }
}
