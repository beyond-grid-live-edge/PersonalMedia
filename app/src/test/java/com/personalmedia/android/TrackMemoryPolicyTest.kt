package com.personalmedia.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMemoryPolicyTest {
    @Test fun stableIdentityWithoutStoringUris() {
        val local = TrackMemoryPolicy.localKey("content://media/external/video/media/42")!!
        assertEquals(local, TrackMemoryPolicy.localKey("content://media/external/video/media/42"))
        assertTrue(local.startsWith("track_"))
        assertFalse(local.contains("content:"))
        assertNull(TrackMemoryPolicy.localKey("https://provider/stream.m3u8"))
    }
    @Test fun episodesAccountsAndMoviesAreSeparate() {
        val first = TrackMemoryPolicy.stremioKey("USER@EXAMPLE.COM", "series", "tt1", "episode1")
        assertEquals(first, TrackMemoryPolicy.stremioKey("user@example.com", "series", "tt1", "episode1"))
        assertFalse(first == TrackMemoryPolicy.stremioKey("user@example.com", "series", "tt1", "episode2"))
        assertFalse(first == TrackMemoryPolicy.stremioKey("other@example.com", "series", "tt1", "episode1"))
        assertFalse(first == TrackMemoryPolicy.stremioKey("user@example.com", "movie", "tt1", "episode1"))
    }
    @Test fun tracksWithSameLanguageRemainDistinct() {
        val tracks = listOf(AvailableTrack("eng", "Main"), AvailableTrack("eng", "Director commentary"),
            AvailableTrack("deu", "Main"))
        val selected = TrackMemoryPolicy.makeChoice(1, tracks)!!
        assertEquals(1, TrackMemoryPolicy.find(selected, tracks))
        assertNull(TrackMemoryPolicy.find(selected, listOf(tracks[0], tracks[2])))
        assertEquals(2, TrackMemoryPolicy.find(TrackMemoryPolicy.makeChoice(2, tracks), tracks))
    }
    @Test fun exactTrackCanMoveBetweenIds() {
        val original = listOf(AvailableTrack("de", "Deutsch"), AvailableTrack("en-US", "Main"))
        val remapped = listOf(AvailableTrack("es", "Main"), AvailableTrack("eng", "Main"))
        assertEquals(1, TrackMemoryPolicy.find(TrackMemoryPolicy.makeChoice(1, original), remapped))
    }
    @Test fun repeatedLanguageAndTitleUsesOccurrence() {
        val tracks = listOf(AvailableTrack("en", ""), AvailableTrack("en", ""))
        val selected = TrackMemoryPolicy.makeChoice(1, tracks)
        assertEquals(1, TrackMemoryPolicy.find(selected, tracks))
        assertNull(TrackMemoryPolicy.find(selected, tracks.take(1)))
    }
    @Test fun unmatchedOrInvalidNeverSelectsWrongTrack() {
        assertNull(TrackMemoryPolicy.find(RememberedTrack("off"), listOf(AvailableTrack("en", "Main"))))
        assertNull(TrackMemoryPolicy.find(RememberedTrack("track", "ja", "Main"), listOf(AvailableTrack("de", "Main"))))
        assertFalse(TrackMemoryPolicy.valid(RememberedTrack("off"), subtitle = false))
        assertTrue(TrackMemoryPolicy.valid(RememberedTrack("off"), subtitle = true))
        assertFalse(TrackMemoryPolicy.valid(RememberedTrack("track", "en", "", 90), subtitle = false))
    }
    @Test fun externalSubtitleMemoryNeverContainsUrl() {
        val choice = RememberedTrack("external", "en", "", 1)
        assertTrue(TrackMemoryPolicy.valid(choice, subtitle = true))
        assertEquals(1, TrackMemoryPolicy.externalSubtitleIndex(choice, listOf("de", "eng")))
        assertEquals(0, TrackMemoryPolicy.externalSubtitleIndex(choice, listOf("eng")))
        assertNull(TrackMemoryPolicy.externalSubtitleIndex(choice, listOf("de", "fr")))
    }
    @Test fun remembersAutoWithoutConfusingItWithNoPreference() {
        val state = RememberedTracks(audio = RememberedTrack("auto"), subtitle = RememberedTrack("off"))
        assertTrue(state.audio != null)
        assertTrue(state.subtitle != null)
        assertEquals(RememberedTracks(), state.copy(audio = null, subtitle = null))
    }
}
