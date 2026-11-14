package com.personalmedia.android

/** Pure validation for STR-7D's portable, user-selected backup format. */
internal object BackupRestorePolicy {
    const val FORMAT = "personalmedia-android-backup"
    const val VERSION = 1
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_MEDIA = 25000
    const val MAX_FOLDERS = 150
    const val MAX_TRACK_KEYS = 15000
    const val MAX_WATCH = 100
    const val MAX_CHANNELS = 600

    fun validContentUri(value: String): Boolean =
        value.length in 11..4096 && value.startsWith("content://", ignoreCase = true) &&
            !value.contains('\\') && !value.contains('\n') && !value.contains('\r')

    fun validTrackKey(value: String): Boolean =
        value.length == 70 && value.startsWith("track_") &&
            value.drop(6).all { it in '0'..'9' || it in 'a'..'f' }

    fun validKind(value: String): Boolean = value in setOf("movie", "tv", "music", "picture")

    fun mergePosition(now: Long, imported: Long): Long = maxOf(now, imported).coerceAtLeast(0)
    fun mergeFlag(now: Boolean, imported: Boolean): Boolean = now || imported
    fun canImportAccount(storedFingerprint: String, currentFingerprint: String): Boolean =
        storedFingerprint.length == 64 && storedFingerprint == currentFingerprint

    fun safeDiagnosticValue(value: Int): Int = value.coerceAtLeast(0)
}
