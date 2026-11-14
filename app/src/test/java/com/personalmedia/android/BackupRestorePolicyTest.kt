package com.personalmedia.android

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRestorePolicyTest {
    @Test fun fileIdentityAndScope() {
        assertTrue(BackupRestorePolicy.validContentUri("content://com.example.documents/tree/primary:Movies"))
        assertFalse(BackupRestorePolicy.validContentUri("file:///storage/emulated/0/movie.mp4"))
        assertFalse(BackupRestorePolicy.validContentUri("https://example.com/secret?token=1"))
        assertFalse(BackupRestorePolicy.validContentUri("content://a\\nInjected"))
        assertFalse(BackupRestorePolicy.validContentUri("content://a\\\\b"))
    }
    @Test fun hashedTrackKeysOnly() {
        val key = "track_" + "a".repeat(64)
        assertTrue(BackupRestorePolicy.validTrackKey(key))
        assertFalse(BackupRestorePolicy.validTrackKey(key + "a"))
        assertFalse(BackupRestorePolicy.validTrackKey("track_https://token"))
        assertFalse(BackupRestorePolicy.validTrackKey("track_" + "A".repeat(64)))
    }
    @Test fun mergeDoesNotLoseWatchedOrProgress() {
        assertTrue(BackupRestorePolicy.mergeFlag(false, true))
        assertTrue(BackupRestorePolicy.mergeFlag(true, false))
        assertEquals(24000L, BackupRestorePolicy.mergePosition(24000L, 3000L))
        assertEquals(24000L, BackupRestorePolicy.mergePosition(500L, 24000L))
        assertEquals(0L, BackupRestorePolicy.mergePosition(-5L, -2L))
    }
    @Test fun accountWatchCannotCrossAccounts() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        assertTrue(BackupRestorePolicy.canImportAccount(a, a))
        assertFalse(BackupRestorePolicy.canImportAccount(a, b))
        assertFalse(BackupRestorePolicy.canImportAccount("", ""))
    }
    @Test fun recordBoundaries() {
        assertEquals(1, BackupRestorePolicy.VERSION)
        assertTrue(BackupRestorePolicy.MAX_MEDIA >= 5000)
        assertTrue(BackupRestorePolicy.MAX_BYTES <= 20 * 1024 * 1024)
        assertTrue(BackupRestorePolicy.validKind("tv"))
        assertFalse(BackupRestorePolicy.validKind("torrent"))
    }
}
