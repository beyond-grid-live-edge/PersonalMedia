package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

/** Pure regression checks for HarmonyOS-inspired grouping. No Android provider/files touched. */
class LocalLibraryOrganizerTest {
    private fun film(name: String, path: String = name, source: String = "rootA") =
        MediaEntry("content://media/${name.hashCode()}/${path.hashCode()}", name, "movie",
            sourceUri = source, relativePath = path)
    private fun episode(name: String, path: String, source: String = "rootT") =
        MediaEntry("content://tv/${name.hashCode()}/${path.hashCode()}", name, "tv",
            sourceUri = source, relativePath = path)

    @Test fun movieCollectionsUseRealFoldersButNeverGenericCategoryFolders() {
        val movies = listOf(
            film("Back to the Future (1985)", "Back to the Future collection/Back to the Future (1985).mkv"),
            film("Back to the Future Part II (1989)", "Back to the Future collection/Back to the Future Part II (1989).mp4"),
            film("Random Film (2010)", "Movies/Random Film (2010).mkv"),
            film("Completely Different (2015)", "Movies/Completely Different (2015).mkv")
        )
        val collections = LocalLibraryOrganizer.movieCollections(movies)
        assertEquals(1, collections.size)
        assertEquals("Back to the Future collection", collections[0].title)
        assertEquals(2, collections[0].movies.size)
        assertEquals(2, LocalLibraryOrganizer.movieStandalones(movies).size)
    }

    @Test fun sameYearEditionsGroupButRemakesStaySeparate() {
        val movies = listOf(
            film("Halloween (1978) 2160p Remux", "Halloween 1978 UHD.mkv"),
            film("Halloween (1978) 1080p BluRay", "Halloween 1978 BD.mkv"),
            film("Halloween (2007) 1080p", "Halloween 2007.mkv")
        )
        val editions = LocalLibraryOrganizer.movieEditions(movies)
        assertEquals(2, editions.size)
        assertEquals(listOf(1, 2), editions.map { it.editions.size }.sorted())
        assertTrue(editions.any { it.year == "1978" && it.editions.size == 2 })
    }

    @Test fun nestedTvCollectionsKeepSeparateSeries() {
        val episodes = listOf(
            episode("S01E01", "V collection/V The Original Miniseries (1983)/S01E01.mkv"),
            episode("S01E02", "V collection/V The Original Miniseries (1983)/S01E02.mkv"),
            episode("S01E01", "V collection/V The Final Battle (1984)/S01E01.mkv"),
            episode("S01E01", "V collection/V The Series (1984)/S01E01.mkv")
        )
        val shows = LocalLibraryOrganizer.tvShows(episodes)
        assertEquals(3, shows.size)
        val group = LocalLibraryOrganizer.tvCollections(episodes)
        assertEquals(1, group.size)
        assertEquals(3, group.first().shows.size)
    }

    @Test fun seasonFolderAndMultipartAreParsedWithoutOverwritingUris() {
        val a = episode("Episode 02 Part 1", "The 100/Season 03/Episode 02 Part 1.mkv")
        val b = episode("Episode 02 Part 2", "The 100/Season 03/Episode 02 Part 2.mkv")
        val info = LocalLibraryOrganizer.tvEpisode(a)
        assertEquals("The 100", info.show)
        assertEquals(3, info.season)
        assertEquals(2, info.episode)
        assertEquals(1, info.part)
        assertEquals(2, LocalLibraryOrganizer.tvShows(listOf(a, b)).first().episodes.size)
    }

    @Test fun missingFileCleanupRequiresTwoMatchingCompletedScans() {
        assertEquals(emptyList<String>(), LocalLibraryOrganizer.confirmedMissing(emptyList(), listOf("a", "b")))
        assertEquals(listOf("b"), LocalLibraryOrganizer.confirmedMissing(listOf("a", "b"), listOf("b", "c")))
    }

    @Test fun exactRepairOnlyRemovesMatchingUriAndTypeAndPreservesWatchFlags() {
        val first = film("Example (2018)")
        val duplicate = first.copy(favourite = true, watched = true, positionMs = 98000)
        val edition = first.copy(uri = first.uri + "/otherVersion")
        val differentKind = first.copy(type = "tv")
        val input = listOf(first, duplicate, edition, differentKind)
        assertEquals(1, LocalLibraryOrganizer.exactDuplicateCount(input))
        val output = LocalLibraryOrganizer.repairExactDuplicates(input)
        assertEquals(3, output.size)
        assertTrue(output.first { it.uri == first.uri && it.type == "movie" }.watched)
        assertTrue(output.first { it.uri == first.uri && it.type == "movie" }.favourite)
        assertEquals(98000L, output.first { it.uri == first.uri && it.type == "movie" }.positionMs)
        assertTrue(output.any { it.uri == edition.uri })
        assertTrue(output.any { it.uri == differentKind.uri && it.type == "tv" })
    }
}
