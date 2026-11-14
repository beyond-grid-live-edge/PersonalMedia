package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class DownloadQueuePolicyTest {
    @Test fun partialResponsesMustMatchResumeOffset() {
        assertEquals(256L, DownloadQueuePolicy.validPartial("bytes 128-255/256",128L))
        assertEquals(1234L, DownloadQueuePolicy.validPartial("bytes 0-1233/1234",0L))
        assertNull(DownloadQueuePolicy.validPartial("bytes 0-10/100",11L))
        assertNull(DownloadQueuePolicy.validPartial("bytes 0-101/100",0L))
        assertNull(DownloadQueuePolicy.validPartial("bytes */100",0L))
        assertNull(DownloadQueuePolicy.validPartial("bytes 10-0/100",10L))
        assertNull(DownloadQueuePolicy.validPartial("bytes 1-2/*",1L))
    }
    @Test fun pauseResumeRestartStatePolicy() {
        assertTrue(DownloadQueuePolicy.canPause("queued"))
        assertTrue(DownloadQueuePolicy.canPause("running"))
        assertFalse(DownloadQueuePolicy.canPause("completed"))
        assertTrue(DownloadQueuePolicy.canResume("paused"))
        assertTrue(DownloadQueuePolicy.canResume("failed"))
        assertFalse(DownloadQueuePolicy.canResume("completed"))
        assertFalse(DownloadQueuePolicy.canRemove("running"))
        assertTrue(DownloadQueuePolicy.canRemove("completed"))
    }
    @Test fun progressClamped() {
        assertEquals(0, DownloadQueuePolicy.percent(1,0))
        assertEquals(50, DownloadQueuePolicy.percent(50,100))
        assertEquals(100, DownloadQueuePolicy.percent(300,100))
    }
    @Test fun crashTailIsTrimmedOnlyToSavedCheckpoint() {
        assertEquals(48L, DownloadQueuePolicy.uncommittedTail(1048L, 1000L))
        assertEquals(0L, DownloadQueuePolicy.uncommittedTail(1000L, 1000L))
        assertNull(DownloadQueuePolicy.uncommittedTail(900L, 1000L))
        assertNull(DownloadQueuePolicy.uncommittedTail(0L, -1L))
    }
    @Test fun completedFileMustMatchKnownPhysicalSize() {
        assertTrue(DownloadQueuePolicy.completeSizeMatches(4096, 4096, 4096))
        assertTrue(DownloadQueuePolicy.completeSizeMatches(4096, 4096, -1))
        assertFalse(DownloadQueuePolicy.completeSizeMatches(4095, 4096, 4095))
        assertFalse(DownloadQueuePolicy.completeSizeMatches(4096, 4096, 4095))
        assertFalse(DownloadQueuePolicy.completeSizeMatches(0, 0, 0))
    }
    @Test fun speedAndRemainingTimeAreGuarded() {
        assertEquals(1048576L, DownloadQueuePolicy.bytesPerSecond(1048576, 1000))
        assertEquals(0L, DownloadQueuePolicy.bytesPerSecond(1, 0))
        assertEquals(5L, DownloadQueuePolicy.remainingSeconds(0, 5_000_000, 1_000_000))
        assertNull(DownloadQueuePolicy.remainingSeconds(5_000_000, 5_000_000, 1_000_000))
        assertNull(DownloadQueuePolicy.remainingSeconds(0, 0, 1_000_000))
    }
    @Test fun rejectLiveManifestsAsWholeMediaDownloads() {
        assertTrue(DownloadQueuePolicy.isStreamingManifest("https://example.com/live/playlist.m3u8?token=123"))
        assertTrue(DownloadQueuePolicy.isStreamingManifest("https://example.com/manifest", "application/dash+xml"))
        assertFalse(DownloadQueuePolicy.isStreamingManifest("https://example.com/movie.mkv"))
        assertFalse(DownloadQueuePolicy.isStreamingManifest("https://example.com/video.mp4", "video/mp4"))
    }
    @Test fun torrentEngineLeasePreventsConcurrentSession() {
        TorrentEngineLease.releasePlayback()
        assertTrue(TorrentEngineLease.acquireDownload("a"))
        assertFalse(TorrentEngineLease.acquireDownload("b"))
        assertFalse(TorrentEngineLease.acquirePlayback())
        assertTrue(TorrentEngineLease.heldByDownload())
        TorrentEngineLease.releaseDownload("b")
        assertTrue(TorrentEngineLease.heldByDownload())
        TorrentEngineLease.releaseDownload("a")
        assertTrue(TorrentEngineLease.acquirePlayback())
        assertFalse(TorrentEngineLease.acquireDownload("c"))
        TorrentEngineLease.releasePlayback()
        assertTrue(TorrentEngineLease.acquireDownload("c"))
        TorrentEngineLease.releaseDownload("c")
    }
}
