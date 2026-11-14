package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class UsTvPlaybackPolicyTest {
    @Test fun nativeLiveManifestTypes() {
        assertEquals(UsTvPlaybackPolicy.Format.HLS,
            UsTvPlaybackPolicy.format("https://cdn.example.com/abc/master.m3u8?t=1"))
        assertEquals(UsTvPlaybackPolicy.Format.DASH,
            UsTvPlaybackPolicy.format("https://cdn.example.com/abc/manifest.mpd"))
        assertEquals(UsTvPlaybackPolicy.Format.TRANSPORT_STREAM,
            UsTvPlaybackPolicy.format("https://cdn.example.com/abc/live.ts"))
        assertEquals(UsTvPlaybackPolicy.Format.PROGRESSIVE,
            UsTvPlaybackPolicy.format("https://cdn.example.com/abc/video.mp4"))
    }

    @Test fun preferredSourceThenOtherSourcesOnlyOnce() {
        val list = listOf(1 to "https://cdn.example.com/a.m3u8",
            2 to "https://cdn.example.com/b.m3u8",
            3 to "https://cdn.example.com/c.ts")
        val order = UsTvPlaybackPolicy.order(list, 2, false)
        assertEquals(listOf(2, 1, 3), order)
        assertEquals(1, UsTvPlaybackPolicy.next(order, setOf(2)))
        assertNull(UsTvPlaybackPolicy.next(order, setOf(1, 2, 3)))
    }

    @Test fun cleartextSourcesRequireUserApproval() {
        val list = listOf(1 to "http://cdn.example.com/live.m3u8",
            2 to "https://cdn.example.com/secure.m3u8")
        assertEquals(listOf(2), UsTvPlaybackPolicy.order(list, 1, false))
        assertEquals(listOf(1, 2), UsTvPlaybackPolicy.order(list, 1, true))
    }

    @Test fun neverUseLocalOrDirectoryUrlsAsStreams() {
        assertFalse(UsTvPlaybackPolicy.allowed("https://127.0.0.1/local.m3u8", true))
        assertFalse(UsTvPlaybackPolicy.allowed("https://192.168.1.1/live.m3u8", true))
        assertFalse(UsTvPlaybackPolicy.allowed("https://www.globalfreetv.com/countries/united-states", true))
        assertFalse(UsTvPlaybackPolicy.allowed("https://cdn.example.com/live.m3u8#fragment", true))
        assertFalse(UsTvPlaybackPolicy.allowed("file:///sdcard/video.mp4", true))
    }
}
