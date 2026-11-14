package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class TorrentExportPolicyTest {
    @Test fun safeDocumentNames() {
        val a = TorrentExportPolicy.suggestedName("Halloween: 1978 / Blu-ray", "H.264.mkv", "12345678abcdef")
        assertEquals("Halloween_ 1978 _ Blu-ray-12345678.mkv", a)
        assertEquals("Stremio video-video.mkv", TorrentExportPolicy.suggestedName("", "", ""))
        assertTrue(TorrentExportPolicy.suggestedName("File", "/media/title.mp4?token=SECRET", "").endsWith(".mp4"))
        assertFalse(TorrentExportPolicy.suggestedName("private", "/media/movie.mkv?token=SECRET", "").contains("SECRET"))
    }
    @Test fun progressDoesNotOverflow() {
        assertEquals(0, TorrentExportPolicy.percent(1, 0))
        assertEquals(0, TorrentExportPolicy.percent(0, 10))
        assertEquals(50, TorrentExportPolicy.percent(5, 10))
        assertEquals(100, TorrentExportPolicy.percent(9999, 10))
        assertEquals(100, TorrentExportPolicy.percent(Long.MAX_VALUE, Long.MAX_VALUE))
    }
}
