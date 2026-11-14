package com.personalmedia.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPreferencesPolicyTest {
    @Test fun languageNeverTrustsArbitraryValues() {
        assertEquals("auto", PlaybackPreferencesPolicy.language("unknown"))
        assertEquals("en", PlaybackPreferencesPolicy.language("en"))
    }
    @Test fun qualityFallsBackWhenSourceResolutionAbsent() {
        assertEquals("All", PlaybackPreferencesPolicy.streamFilter("1080p", listOf("4K", "720p")))
        assertEquals("1080p", PlaybackPreferencesPolicy.streamFilter("1080p", listOf("4K", "1080p")))
        assertEquals("All", PlaybackPreferencesPolicy.streamFilter("auto", listOf("4K")))
    }
    @Test fun matchesTrackLanguageButNotTitleAlone() {
        val tracks = listOf("1" to "eng · English commentary", "2" to "deu · Deutsch", "3" to "spa · French dub")
        assertEquals("1", PlaybackPreferencesPolicy.trackForLanguage("en", tracks))
        assertEquals("2", PlaybackPreferencesPolicy.trackForLanguage("de", tracks))
        assertNull(PlaybackPreferencesPolicy.trackForLanguage("fr", tracks))
    }
    @Test fun matchesBcp47Languages() {
        assertEquals("5", PlaybackPreferencesPolicy.trackForLanguage("en", listOf("5" to "en-US · Main")))
        assertNull(PlaybackPreferencesPolicy.trackForLanguage("auto", listOf("1" to "eng")))
    }
    @Test fun unknownAudioDoesNotForceWrongTrack() {
        assertNull(PlaybackPreferencesPolicy.trackForLanguage("fr", listOf("1" to "eng")))
    }
}
