package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class ArtworkMetadataPolicyTest {
    @Test fun exactFilmAndYearMatches() {
        assertTrue(ArtworkMatchPolicy.matches("Backrooms", 2026, "Backrooms (2026 film)", 2026, "movie"))
        assertFalse(ArtworkMatchPolicy.matches("The Thing", 1982, "The Thing", 2011, "movie"))
        assertFalse(ArtworkMatchPolicy.matches("Die Hard", 1988, "Die Hard 2", 1990, "movie"))
    }

    @Test fun exactSeriesMatching() {
        assertTrue(ArtworkMatchPolicy.matches("The X Files", null, "The X-Files", 1993, "tv"))
        assertFalse(ArtworkMatchPolicy.matches("V The Series", 1984, "V", 1984, "tv"))
    }

    @Test fun keylessModeDoesNotTouchKeyedProviders() {
        assertEquals(listOf("wikipedia"), ArtworkMatchPolicy.providerOrder("free", true, true, "movie"))
        assertEquals(listOf("tvmaze"), ArtworkMatchPolicy.providerOrder("free", true, true, "tv"))
    }

    @Test fun providerPriorityMatchesHarmonyOS() {
        assertEquals(listOf("tmdb", "omdb", "wikipedia"),
            ArtworkMatchPolicy.providerOrder("auto", true, true, "movie"))
        assertEquals(listOf("tvmaze"), ArtworkMatchPolicy.providerOrder("auto", false, false, "tv"))
    }

    @Test fun editionsShareLookupAndRemakesDoNot() {
        val records = listOf(
            MediaEntry("first", "Backrooms 2026 1080p Bluray", "movie"),
            MediaEntry("second", "Backrooms 2026 2160p Bluray", "movie"),
            MediaEntry("remake", "Backrooms 2009", "movie"))
        val targets = ArtworkBatchPlanner.plan(records, ArtworkPreferences())
        assertEquals(2, targets.size)
        assertTrue(targets.any { it.year == 2026 && it.uris == setOf("first", "second") })
    }

    @Test fun oneLookupPerSeries() {
        val records = listOf(
            MediaEntry("a", "Pilot S01E01", "tv", sourceUri="tvroot",
                relativePath="Curb Your Enthusiasm/Season 1/Pilot S01E01.mkv"),
            MediaEntry("b", "Episode S01E02", "tv", sourceUri="tvroot",
                relativePath="Curb Your Enthusiasm/Season 1/Episode S01E02.mkv"))
        val targets = ArtworkBatchPlanner.plan(records, ArtworkPreferences())
        assertEquals(1, targets.size)
        assertEquals(setOf("a", "b"), targets.single().uris)
    }
}
