package com.personalmedia.android

/** Pure state and HTTP-range policy for STR-6 persistent downloads. */
internal object DownloadQueuePolicy {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val FAILED = "failed"
    const val COMPLETED = "completed"
    private val active = setOf(QUEUED, RUNNING)
    fun canResume(state: String) = state == PAUSED || state == FAILED
    fun canPause(state: String) = state in active
    fun canRemove(state: String) = state != RUNNING
    /** Bytes written after the last secure checkpoint must be discarded on
     * resume. A missing/shortened document cannot safely be appended to. */
    fun uncommittedTail(physicalSize: Long, savedOffset: Long): Long? =
        if (physicalSize < 0L || savedOffset < 0L || physicalSize < savedOffset) null
        else physicalSize - savedOffset

    /** Guard against false completion after a truncated response. */
    fun completeSizeMatches(done: Long, total: Long, actualSize: Long): Boolean =
        done > 0L && (total <= 0L || done == total) &&
            (actualSize < 0L || actualSize == done)

    /** Integer-only throughput avoids overflow when a large transfer is resumed. */
    fun bytesPerSecond(deltaBytes: Long, elapsedMs: Long): Long =
        if (deltaBytes <= 0L || elapsedMs <= 0L) 0L
        else (deltaBytes.toDouble() * 1000.0 / elapsedMs).toLong().coerceAtLeast(0L)

    fun remainingSeconds(done: Long, total: Long, speedBps: Long): Long? =
        if (total <= 0L || done >= total || speedBps <= 0L) null
        else (total - done).toDouble().div(speedBps).toLong().coerceAtLeast(0L)

    fun isStreamingManifest(sourceUrl: String, contentType: String = ""): Boolean {
        val path = runCatching { java.net.URI(sourceUrl).path.orEmpty().lowercase() }.getOrDefault("")
        val mime = contentType.substringBefore(';').trim().lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".mpd") ||
            mime in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl",
                "application/dash+xml", "audio/mpegurl")
    }
    fun percent(done: Long, total: Long): Int = if (total <= 0) 0
        else ((done.coerceIn(0, total) * 100.0) / total).toInt().coerceIn(0, 100)
    /** Parse a single-range upstream 206. A resumed file must never append wrong offsets. */
    fun validPartial(contentRange: String?, expectedOffset: Long): Long? {
        val row = Regex("^bytes ([0-9]+)-([0-9]+)/([0-9]+)$").matchEntire(contentRange.orEmpty()) ?: return null
        val start = row.groupValues[1].toLongOrNull() ?: return null
        val end = row.groupValues[2].toLongOrNull() ?: return null
        val total = row.groupValues[3].toLongOrNull() ?: return null
        return total.takeIf { start == expectedOffset && end >= start && end < total }
    }
}
