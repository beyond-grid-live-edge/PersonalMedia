package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class TorrentStartPolicyTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test fun p2pPermissionNeverFailsSilently() {
        assertTrue(TorrentStartPolicy.issue(false, true, hash)!!.contains("P2P"))
    }

    @Test fun missingNativeEngineExplainsTheBuild() {
        assertTrue(TorrentStartPolicy.issue(true, false, hash)!!.contains("libtorrent"))
    }

    @Test fun badHashShowsReasonAndValidSourceProceeds() {
        assertTrue(TorrentStartPolicy.issue(true, true, "bad")!!.contains("hash"))
        assertNull(TorrentStartPolicy.issue(true, true, hash))
    }
}
