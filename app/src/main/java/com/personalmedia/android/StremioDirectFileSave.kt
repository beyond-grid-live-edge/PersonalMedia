package com.personalmedia.android

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Foreground direct-source file export via Android's networking stack.
 * Redirects never forward an add-on's private headers across origins.
 * Unlike torrent exports, HTTP source bytes are not BitTorrent piece-verified.
 */
internal object StremioDirectFileSave {
    suspend fun export(context: Context, source: StremioSource, destination: Uri,
        onProgress: suspend (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        require(StremioPlaybackPolicy.safeTransport(source.url, source.requestHeaders)) {
            "Unsupported direct media source" }
        var destinationUrl = URI(source.url)
        var headers = source.requestHeaders
        val visited = mutableSetOf<String>()
        var done = false
        try {
            repeat(6) {
                currentCoroutineContext().ensureActive()
                check(visited.add(destinationUrl.toASCIIString())) { "Media provider redirected in a loop" }
                val connection = URL(destinationUrl.toASCIIString()).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 30_000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                    val code = connection.responseCode
                    if (code in setOf(301, 302, 303, 307, 308)) {
                        val next = StremioSourceDiagnostic.redirectDestination(
                            destinationUrl, connection.getHeaderField("Location"))
                            ?: throw IOException("Provider redirect is insecure or unsupported")
                        if (!destinationUrl.scheme.equals(next.scheme, true) ||
                            !destinationUrl.host.equals(next.host, true) ||
                            destinationUrl.port != next.port) headers = emptyMap()
                        destinationUrl = next
                        return@repeat
                    }
                    check(code in 200..299) { "Provider rejected the media download (HTTP $code)" }
                    val type = connection.contentType.orEmpty().substringBefore(';').lowercase()
                    check(type !in setOf("text/html", "application/xhtml+xml", "application/json", "text/xml")) {
                        "Provider returned a webpage or data instead of media" }
                    val total = connection.contentLengthLong.coerceAtLeast(0L)
                    val out = context.contentResolver.openOutputStream(destination, "wt")
                        ?: error("The selected destination cannot be opened")
                    out.use { output ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var count = 0L
                            var updatedAt = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val got = input.read(buffer)
                                if (got < 0) break
                                output.write(buffer, 0, got)
                                count += got
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - updatedAt > 900) {
                                    onProgress(count, total)
                                    updatedAt = now
                                }
                            }
                            if (total > 0 && total != count)
                                throw IOException("Media transfer ended before its declared length")
                            onProgress(count, total)
                        }
                        output.flush()
                    }
                    done = true
                    return@withContext
                } finally { connection.disconnect() }
            }
            throw IOException("Media provider redirected too many times")
        } finally {
            if (!done) runCatching { context.contentResolver.delete(destination, null, null) }
        }
    }
}
