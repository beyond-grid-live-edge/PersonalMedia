package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class StremioMpvStateTest {
    @Test fun pendingLoadIsNotReportedAsPlaying() {
        assertTrue(StremioMpvState.notice("loading", 0, 1000, 0, 0, false).contains("Opening"))
        assertTrue(StremioMpvState.notice("loading", 0, 17000, 0, 0, false).contains("Still opening"))
    }
    @Test fun mpvErrorsBecomeSafeActionableMessages() {
        assertTrue(StremioMpvState.notice("failed", -13, 18000, 0, 0, false).contains("failed to open"))
        assertTrue(StremioMpvState.notice("failed", -17, 18000, 0, 0, false).contains("media format"))
    }
    @Test fun loadedWithoutVideoIsIdentified() {
        assertTrue(StremioMpvState.notice("ready", 0, 15000, 0, 0, false).contains("without a video track"))
        assertTrue(StremioMpvState.notice("ready", 0, 15000, 1, 0, false).contains("no decoded frame"))
    }
    @Test fun codecAndNetworkHintsNeverExposePrivateUrlsOrHeaders() {
        val text = StremioMpvState.notice("failed", -13, 60000, 0, 0, false)
        assertFalse(text.contains("Authorization", true))
        assertFalse(text.contains("https://"))
        assertTrue(StremioMpvState.notice("ready", 0, 20000, 1, 1080, true).contains("Buffering"))
    }
}
