package com.personalmedia.android

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Service-owned transfers. Native torrent bytes ONLY enter the output after
 * native torrentReadVerified authenticates every backing BitTorrent piece. */
internal object DownloadTransfers {
    suspend fun run(ctx: Context, item: DownloadQueueItem,
        update: suspend (Long, Long, String, String) -> Unit) = withContext(Dispatchers.IO) {
        // A playlist is not a video file; offline HLS/DASH segment acquisition
        // is a separate feature and must never create a fake completed movie.
        if (item.kind == "Direct HTTP" && DownloadQueuePolicy.isStreamingManifest(item.sourceUrl))
            throw IOException("Source is a live playlist, not a single downloadable video file")
        val destination = DownloadStorage.prepare(ctx, item)
        update(item.done, item.total, "Document ready", destination.toString())
        if (item.kind == "Torrent") runTorrent(ctx, item, destination, update)
        else runHttp(ctx, item, destination, update)
    }

    private suspend fun runHttp(ctx: Context, item: DownloadQueueItem, document: Uri,
        update: suspend (Long, Long, String, String) -> Unit) {
        require(StremioPlaybackPolicy.safeTransport(item.sourceUrl, item.requestHeaders)) {
            "Source transport is no longer allowed"
        }
        var address = URI(item.sourceUrl)
        var headers = item.requestHeaders
        val visited = HashSet<String>()
        for (hop in 0..5) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(address.toASCIIString())) throw IOException("Provider redirected repeatedly")
            val connection = URL(address.toASCIIString()).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 12_000
                connection.readTimeout = 25_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (item.done > 0) {
                    connection.setRequestProperty("Range", "bytes=${item.done}-")
                    val validator = item.etag.ifBlank { item.lastModified }
                    if (validator.isNotBlank()) connection.setRequestProperty("If-Range", validator)
                }
                headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val next = StremioSourceDiagnostic.redirectDestination(address,
                        connection.getHeaderField("Location"))
                        ?: throw IOException("Provider redirected to an unsupported destination")
                    if (!address.scheme.equals(next.scheme, true) ||
                        !address.host.equals(next.host, true) || address.port != next.port)
                        headers = emptyMap() // Never forward original source credentials.
                    address = next
                    continue
                }
                if (status !in setOf(200, 206)) throw IOException("Media provider returned HTTP $status")
                if (item.done > 0 && status != 206) throw IOException(
                    "Provider cannot safely resume this file; select Restart from beginning")
                val contentType = connection.contentType.orEmpty().substringBefore(';').lowercase()
                if (contentType in setOf("text/html", "application/json", "application/xhtml+xml"))
                    throw IOException("Provider returned a webpage rather than downloadable media")
                if (DownloadQueuePolicy.isStreamingManifest(address.toASCIIString(), contentType))
                    throw IOException("Source is a live playlist, not a single downloadable video file")
                val total = if (status == 206)
                    DownloadQueuePolicy.validPartial(connection.getHeaderField("Content-Range"), item.done)
                        ?: throw IOException("Provider returned an incorrect byte range")
                else connection.contentLengthLong.coerceAtLeast(0L)
                val etag = connection.getHeaderField("ETag").orEmpty()
                val modified = connection.getHeaderField("Last-Modified").orEmpty()
                if (item.done > 0 && item.etag.isNotBlank() && etag.isNotBlank() && etag != item.etag)
                    throw IOException("Provider media changed; select Restart from beginning")
                if (item.done > 0 && item.lastModified.isNotBlank() && modified.isNotBlank() && modified != item.lastModified)
                    throw IOException("Provider media changed; select Restart from beginning")
                // The service saves validators before writing/resuming. Stored header values
                // stay in encrypted preferences; none are presented to the user.
                update(item.done, total, "HTTP connected", document.toString())
                DownloadQueueRepository.update(ctx, item.id) { current ->
                    current.copy(etag = etag.ifBlank { current.etag },
                        lastModified = modified.ifBlank { current.lastModified })
                }
                var progress = item.done
                var lastCheckpointMs = 0L
                DownloadStorage.openAt(ctx, document, item.done).use { out ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (n == 0) continue
                            out.output.write(buffer, 0, n)
                            progress += n
                            if (total > 0 && progress > total)
                                throw IOException("Provider sent more data than declared")
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastCheckpointMs >= 1100L || (total > 0 && progress == total)) {
                                // Persist progress only after the bytes have reached a durable
                                // SAF checkpoint. Interrupted excess is trimmed on next resume.
                                out.output.channel.force(false)
                                update(progress, total, "Downloading HTTPS media", document.toString())
                                lastCheckpointMs = now
                            }
                        }
                    }
                    out.output.channel.force(true)
                }
                if (total > 0 && progress != total) throw IOException("Download interrupted before file was complete")
                if (progress <= 0) throw IOException("Media provider returned no data")
                update(progress, progress, "File transferred", document.toString())
                return
            } finally { connection.disconnect() }
        }
        throw IOException("Media provider returned too many redirects")
    }

    private suspend fun runTorrent(ctx: Context, item: DownloadQueueItem, document: Uri,
        update: suspend (Long, Long, String, String) -> Unit) {
        if (!NativeBridge.isTorrentReady()) throw IOException("This APK has no native libtorrent engine")
        if (!ctx.getSharedPreferences("personalmedia_p2p_consent", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)) throw IOException("P2P consent disabled in Stremio Settings")
        if (!TorrentEngineLease.acquireDownload(item.id))
            throw IllegalStateException("A torrent is already playing. Pause playback and retry the download")
        try {
            update(item.done, item.total, "Starting verified torrent session", document.toString())
            val result = NativeBridge.torrentOpen(item.hash, item.fileIndex,
                item.trackers.joinToString("\n"), ctx.cacheDir.absolutePath)
            if (!result.startsWith("ready:")) throw IOException("Native torrent session could not start")
            var size = 0L
            for (attempt in 0 until 300) {
                currentCoroutineContext().ensureActive()
                val mediaUrl = NativeBridge.torrentRangeUrl(item.hash, item.fileIndex)
                val status = JSONObject(NativeBridge.torrentStatus())
                size = status.optLong("size").coerceAtLeast(0L)
                if (mediaUrl.isNotBlank() && size > 0 && TorrentLoopbackPolicy.isNativeTorrentUrl(mediaUrl)) break
                if (attempt % 5 == 0) update(item.done, item.total,
                    "Waiting for metadata · peers ${status.optInt("peers")} · DHT ${status.optInt("dhtNodes")}",
                    document.toString())
                delay(1000)
            }
            if (size <= 0) throw IOException("Torrent metadata unavailable; try a well-seeded source")
            if (item.done > size) throw IOException("The torrent file is smaller than saved progress")
            var position = item.done
            var lastCheckpointMs = 0L
            update(position, size, "Verified metadata received", document.toString())
            DownloadStorage.openAt(ctx, document, position).use { out ->
                val chunk = 256 * 1024
                while (position < size) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(chunk.toLong(), size - position).toInt()
                    NativeBridge.torrentDemand(position, count)
                    var bytes: ByteArray? = null
                    for (attempt in 0 until 300) {
                        currentCoroutineContext().ensureActive()
                        bytes = NativeBridge.torrentReadVerified(position, count)
                        if (bytes != null) break
                        delay(300)
                    }
                    val verified = bytes ?: throw IOException("Timed out waiting for hash-verified torrent pieces")
                    if (verified.isEmpty() || verified.size > count)
                        throw IOException("Native torrent returned an invalid verified byte range")
                    out.output.write(verified)
                    position += verified.size
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastCheckpointMs >= 1100L || position == size) {
                        out.output.channel.force(false)
                        update(position, size, "Downloading hash-verified torrent pieces", document.toString())
                        lastCheckpointMs = now
                    }
                }
                out.output.channel.force(true)
            }
            update(size, size, "All torrent pieces verified", document.toString())
        } finally {
            try {
                NativeBridge.torrentRequestCancel()
                // JNI detaches the old session; its network reaper runs asynchronously.
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { NativeBridge.torrentStop() }
            } finally { TorrentEngineLease.releaseDownload(item.id) }
        }
    }
}
