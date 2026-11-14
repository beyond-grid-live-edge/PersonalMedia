package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class TorrentAttemptPolicyTest {
    @Test fun cancelledAttemptNeverOwnsLaterSession() {
        val started = 41L
        assertTrue(TorrentAttemptPolicy.isCurrent(started, started))
        val cancelled = started + 1
        assertFalse(TorrentAttemptPolicy.isCurrent(started, cancelled))
        assertTrue(TorrentAttemptPolicy.isCurrent(cancelled, cancelled))
        val next = cancelled + 1
        assertFalse(TorrentAttemptPolicy.isCurrent(started, next))
        assertFalse(TorrentAttemptPolicy.isCurrent(cancelled, next))
        assertTrue(TorrentAttemptPolicy.isCurrent(next, next))
    }
}
