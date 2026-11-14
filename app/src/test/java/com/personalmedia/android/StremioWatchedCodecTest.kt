package com.personalmedia.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StremioWatchedCodecTest {
    @Test fun decodesStremioOfficialExample() {
        val videos = (1..9).map { "tt2934286:1:$it" }
        val decoded = StremioWatchedCodec.decode("tt2934286:1:5:5:eJyTZwAAAEAAIA==", videos)
        assertEquals(videos.take(5).toSet(), decoded)
    }
    @Test fun roundTripAcrossByteBoundary() {
        val ids = (1..31).map { "tt0:1:$it" }
        val watched = setOf(ids[0], ids[15], ids[28])
        assertEquals(watched, StremioWatchedCodec.decode(StremioWatchedCodec.encode(ids, watched), ids))
    }
    @Test fun anchorMovesWhenEarlierEpisodeInserted() {
        val old = (1..6).map { "tt0:1:$it" }
        val encoded = StremioWatchedCodec.encode(old, setOf(old[3], old[4]))
        val new = listOf("tt0:0:1") + old
        assertEquals(setOf(old[3], old[4]), StremioWatchedCodec.decode(encoded, new))
    }
    @Test fun corruptAndMissingAnchorAreNotConsideredUnwatched() {
        val ids = listOf("tt0:1:1")
        assertNull(StremioWatchedCodec.decode("bad", ids))
        assertNull(StremioWatchedCodec.decode("tt0:9:99:1:eJwDAAAAAAE=", ids))
    }
    @Test fun videoSortingMatchesStremioCore() {
        val result = StremioWatchedCodec.orderedVideos(listOf(
            StremioVideo("s2e1", "", 2, 1), StremioVideo("s1e2", "", 1, 2),
            StremioVideo("s1e1", "", 1, 1)))
        assertEquals(listOf("s1e1", "s1e2", "s2e1"), result.map { it.id })
    }
}
