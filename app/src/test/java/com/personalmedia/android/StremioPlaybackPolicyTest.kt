package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class StremioPlaybackPolicyTest {
    @Test fun directHttpsWithHeadersAndHttpWithoutHeadersAreAllowed() {
        assertTrue(StremioPlaybackPolicy.safeTransport("https://video.example/media.m3u8",
            mapOf("Referer" to "https://provider.example/", "Authorization" to "Bearer demo")))
        assertTrue(StremioPlaybackPolicy.safeTransport("http://media.example/video.mp4", emptyMap()))
        assertFalse(StremioPlaybackPolicy.safeTransport("http://media.example/video.mp4",
            mapOf("Cookie" to "session=private")))
    }
    @Test fun urlAndHeaderInjectionAreRejected() {
        assertFalse(StremioPlaybackPolicy.safeTransport("https://user:pass@media.example/video.mp4", emptyMap()))
        assertFalse(StremioPlaybackPolicy.safeTransport("file:///storage/movie.mp4", emptyMap()))
        assertFalse(StremioPlaybackPolicy.safeTransport("https://media.example/film#fragment", emptyMap()))
        assertFalse(StremioPlaybackPolicy.validHeader("Authorization", "Bearer abc\\r\\nHost: steal.example".replace("\\r", "\r").replace("\\n", "\n")))
        assertFalse(StremioPlaybackPolicy.validHeader("Host", "another.example"))
        assertFalse(StremioPlaybackPolicy.validHeader("Header\\nBad", "anything"))
    }
    @Test fun preservesRequestHeadersAndClampsResume() {
        assertEquals("Referer: https://provider.example/\nX-Token: demo",
            StremioPlaybackPolicy.serializeHeaders(linkedMapOf(
                "Referer" to "https://provider.example/", "X-Token" to "demo")))
        assertEquals(0L, StremioPlaybackPolicy.resumePosition(-200))
        assertEquals(93_000L, StremioPlaybackPolicy.resumePosition(93_000))
        assertEquals(86_400_000L, StremioPlaybackPolicy.resumePosition(Long.MAX_VALUE))
    }
    @Test fun subtitleHeadersAreNotSentToDifferentHost() {
        assertTrue(StremioPlaybackPolicy.externalSubtitleAllowed(
            "https://media.example/film.m3u8", "https://media.example/caption.vtt",
            mapOf("Authorization" to "Bearer demo")))
        assertFalse(StremioPlaybackPolicy.externalSubtitleAllowed(
            "https://media.example/film.m3u8", "https://other.example/caption.vtt",
            mapOf("Authorization" to "Bearer demo")))
        assertTrue(StremioPlaybackPolicy.externalSubtitleAllowed(
            "https://media.example/film.m3u8", "https://subtitles.example/caption.vtt", emptyMap()))
    }
    @Test fun rejectsNewlineSerializedHeaders() {
        try {
            StremioPlaybackPolicy.serializeHeaders(mapOf("Cookie" to "foo\\r\\nSecret: bar".replace("\\r", "\r").replace("\\n", "\n")))
            fail("CRLF injection should fail")
        } catch (_: IllegalArgumentException) { }
    }
}
