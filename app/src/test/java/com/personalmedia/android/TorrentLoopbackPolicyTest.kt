package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class TorrentLoopbackPolicyTest {
    private val valid = "http://127.0.0.1:40777/" + "a".repeat(48) + "/video.mp4"

    @Test fun nativeRangePathAccepted() {
        assertTrue(TorrentLoopbackPolicy.isNativeTorrentUrl(valid))
        assertTrue(TorrentLoopbackPolicy.isNativeTorrentUrl(valid.replace("mp4", "mkv")))
    }

    @Test fun noOutsideOrUnprotectedEndpoints() {
        for (url in listOf(
            "https://127.0.0.1:40777/" + "a".repeat(48) + "/video.mp4",
            valid.replace("127.0.0.1", "example.com"),
            valid.replace("127.0.0.1", "192.168.1.1"),
            valid.replace("/video.mp4", "/video.mp4?token=secret"),
            valid.replace("a".repeat(48), "1234"),
            valid.replace("/video.mp4", "/video.exe"),
            valid.replace("/video.mp4", "/video.mp4#fragment"),
            "http://evil@127.0.0.1:40777/" + "a".repeat(48) + "/video.mp4"
        )) assertFalse(TorrentLoopbackPolicy.isNativeTorrentUrl(url))
    }
}
