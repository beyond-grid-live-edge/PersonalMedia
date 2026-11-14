package com.personalmedia.android

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Foreground, SAF-selected export. Bytes are read ONLY through JNI's verified-piece API.
 * This is not the resumable, persistent download queue planned for STR-6.
 * The Activity must stay open; an interrupted/failed export is deleted where the
 * document provider permits. An incomplete file must never be reported as done.
 */
internal object TorrentVerifiedFileSave {
    private const val CHUNK = 256 * 1024
    private const val TIMEOUT_MS = 90_000L

    suspend fun export(context: Context, destination: Uri, size: Long,
        onProgress: suspend (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        require(size > 0) { "Verified torrent file size is not yet available" }
        var complete = false
        try {
            val stream = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IllegalStateException("The selected location could not be opened for writing")
            stream.use { out ->
                var offset = 0L
                var updatedAt = 0L
                while (offset < size) {
                    currentCoroutineContext().ensureActive()
                    val length = minOf(CHUNK.toLong(), size - offset).toInt()
                    NativeBridge.torrentDemand(offset, length)
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    var data: ByteArray? = null
                    while (data == null) {
                        currentCoroutineContext().ensureActive()
                        data = NativeBridge.torrentReadVerified(offset, length)
                        if (data != null) break
                        if (android.os.SystemClock.elapsedRealtime() - startedAt >= TIMEOUT_MS)
                            throw IllegalStateException("Timed out waiting for verified torrent pieces at this file position")
                        delay(350)
                    }
                    // Only bytes whose torrent pieces were authenticated in native
                    // libtorrent can arrive at this point.
                    val verified = data ?: error("Verified bytes unavailable")
                    out.write(verified)
                    offset += verified.size
                    if (android.os.SystemClock.elapsedRealtime() - updatedAt > 900 || offset == size) {
                        onProgress(offset, size)
                        updatedAt = android.os.SystemClock.elapsedRealtime()
                    }
                }
                out.flush()
            }
            complete = true
        } finally {
            if (!complete) {
                // ACTION_CREATE_DOCUMENT created a new document specifically for
                // this export. No existing user document is targeted for deletion.
                runCatching { context.contentResolver.delete(destination, null, null) }
            }
        }
    }
}
