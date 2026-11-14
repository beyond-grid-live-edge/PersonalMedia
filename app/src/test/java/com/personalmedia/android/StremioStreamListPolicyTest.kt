package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class StremioStreamListPolicyTest {
    private fun stream(release: String, badge: String = "Torrentio") = StremioSource(
        name = badge, release = release, url = "", hash = "", fileIndex = -1,
        trackers = emptyList(), kind = "Torrent",
        quality = StremioStreamListPolicy.quality(badge, release), addon = "Provider",
        subtitles = emptyList()
    )

    @Test fun qualityUsesReleaseBeforeProviderBadge() {
        assertEquals("1080p", StremioStreamListPolicy.quality("Torrentio 4K", "Film.1080p.BluRay.mkv"))
        assertEquals("4K", StremioStreamListPolicy.quality("Torrentio 4K", "Film.2160p.HEVC.mkv"))
        assertEquals("720p", StremioStreamListPolicy.quality("Torrentio", "Film 720p"))
        assertEquals("480p", StremioStreamListPolicy.quality("Torrentio", "Film 480p"))
        assertEquals("Other", StremioStreamListPolicy.quality("Provider", "Unlabelled release"))
    }

    @Test fun lowerQualityStreamsAfterFirstFortyStayDiscoverable() {
        val providerResults = (1..90).map { stream("Movie.2160p.release$it") } +
            (1..75).map { stream("Movie.1080p.release$it") } +
            (1..33).map { stream("Movie.720p.release$it") } +
            listOf(stream("Movie.SD.release"))
        assertEquals(199, providerResults.size)
        assertEquals(75, StremioStreamListPolicy.filter(providerResults, "1080p").size)
        assertEquals(33, StremioStreamListPolicy.filter(providerResults, "720p").size)
        assertEquals(199, StremioStreamListPolicy.filter(providerResults, "All").size)
        assertEquals(1, StremioStreamListPolicy.filter(providerResults, "Other").size)
        assertEquals(90, StremioStreamListPolicy.counts(providerResults)["4K"])
    }

    @Test fun batchVisibilityDoesNotDiscardUnderlyingResults() {
        val all = (1..230).map { stream("film.${if (it > 120) "1080p" else "4K"}.$it") }
        var displayed = all.take(StremioStreamListPolicy.PAGE_SIZE)
        assertEquals(48, displayed.size)
        assertEquals(230, all.size)
        var count = displayed.size
        while (count < all.size) {
            count = StremioStreamListPolicy.nextCount(count, all.size)
            displayed = all.take(count)
        }
        assertEquals(230, displayed.size)
        assertEquals("1080p", displayed.last().quality)
    }

    @Test fun duplicatesFromAddonAreNotMerged() {
        val sources = listOf(stream("Movie.1080p.release"), stream("Movie.1080p.release"))
        assertEquals(2, StremioStreamListPolicy.filter(sources, "All").size)
    }
}
