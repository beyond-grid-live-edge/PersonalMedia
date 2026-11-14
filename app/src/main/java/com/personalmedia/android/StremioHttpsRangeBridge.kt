package com.personalmedia.android

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * STR-4d compatibility bridge: Android's HTTPS implementation fetches video byte ranges;
 * libmpv continues to decode/play/seek via a private HTTP loopback endpoint.
 *
 * Only enable for the explicitly selected HTTPS media source. This is deliberately NOT a
 * proxy for arbitrary URLs and never logs or persists the signed source address.
 * Remote provider-specific headers are NOT forwarded (use the verified header-free URL).
 */
internal class StremioHttpsRangeBridge(
    private val httpsSource: String,
    // Allows deterministic unit tests; normal production calls always use Android HTTPS.
    private val openUpstream: (URI) -> HttpURLConnection = { URL(it.toASCIIString()).openConnection() as HttpURLConnection }
) : AutoCloseable {
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private val server = ServerSocket()
    private val clients = java.util.Collections.synchronizedSet(mutableSetOf<Socket>())
    private val workers = ThreadPoolExecutor(2, 4, 15, TimeUnit.SECONDS,
        ArrayBlockingQueue(8), { task -> Thread(task, "PM-Stremio-Range").apply { isDaemon = true } })
    private var acceptThread: Thread? = null
    @Volatile private var running = false
    private val failure = AtomicReference("")

    val lastFailure: String get() = failure.get() // No URL, hostname or credentials.

    @Synchronized fun start(): String {
        check(!running) { "Media bridge is already running" }
        val source = URI(httpsSource)
        require(allowedTarget(source)) { "The selected HTTPS media address is not eligible for the bridge" }
        server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8)
        running = true
        acceptThread = Thread({ acceptLoop() }, "PM-Stremio-Range-Accept").apply {
            isDaemon = true
            start()
        }
        return "http://127.0.0.1:${server.localPort}/$token/media"
    }

    private fun acceptLoop() {
        while (running) {
            val client = try { server.accept() } catch (_: IOException) { break }
            client.soTimeout = 15000
            clients.add(client)
            try { workers.execute { serve(client) } }
            catch (_: RejectedExecutionException) {
                clients.remove(client)
                runCatching { client.close() }
            }
        }
    }

    private fun readLine(input: BufferedInputStream, max: Int = 8192): String? {
        val line = StringBuilder()
        while (line.length <= max) {
            val value = input.read()
            if (value == -1) return null
            if (value == 10) return line.toString().removeSuffix("\r")
            if (value < 32 && value != 13 && value != 9) return null
            line.append(value.toChar())
        }
        return null
    }

    private fun serve(socket: Socket) {
        try {
            socket.use {
                val input = BufferedInputStream(it.getInputStream())
                val output = BufferedOutputStream(it.getOutputStream())
                val first = readLine(input) ?: return
                val parts = first.split(' ')
                if (parts.size != 3 || parts[0] !in setOf("GET", "HEAD") ||
                    parts[1] != "/$token/media" || !parts[2].startsWith("HTTP/1.")) {
                    respond(output, 403, "Forbidden")
                    return
                }
                var range: String? = null
                for (lineNo in 0 until 64) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    if (lineNo == 63) return
                    if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                }
                if (range != null && !validRange(range)) {
                    respond(output, 416, "Invalid Range")
                    return
                }
                fetch(output, parts[0], range)
            }
        } catch (_: Exception) {
            failure.set("Local playback bridge was interrupted")
        } finally { clients.remove(socket) }
    }

    private fun fetch(output: BufferedOutputStream, method: String, range: String?) {
        var target = URI(httpsSource)
        val visited = mutableSetOf<String>()
        for (hop in 0..5) {
            if (!allowedTarget(target) || !visited.add(target.toASCIIString())) {
                failure.set("Media host or redirect was rejected by the secure bridge")
                respond(output, 502, "Upstream rejected")
                return
            }
            var connection: HttpURLConnection? = null
            var responseStarted = false
            try {
                connection = openUpstream(target)
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 12000
                connection.readTimeout = 25000
                connection.requestMethod = method
                connection.setRequestProperty("Accept-Encoding", "identity")
                // Match the default Android networking identity used by the
                // successful STR-4c source check; do not invent provider headers.
                if (range != null) connection.setRequestProperty("Range", range)
                val code = connection.responseCode
                if (code in setOf(301,302,303,307,308)) {
                    if (hop == 5) {
                        failure.set("The media host redirected too many times")
                        respond(output, 502, "Upstream redirect limit")
                        return
                    }
                    val location = connection.getHeaderField("Location")
                    val next = runCatching { target.resolve(location) }.getOrNull()
                    if (next == null || !allowedTarget(next)) {
                        failure.set("The media host redirected to an unsupported destination")
                        respond(output, 502, "Upstream redirect rejected")
                        return
                    }
                    target = next
                    continue
                }
                if (code == 416) {
                    respond(output, 416, "Range Not Satisfiable",
                        connection.getHeaderField("Content-Range")?.takeIf { validContentRange416(it) }?.let { mapOf("Content-Range" to it) } ?: emptyMap())
                    return
                }
                if (code != 200 && code != 206) {
                    failure.set("Media host returned HTTP $code")
                    respond(output, 502, "Upstream HTTP error")
                    return
                }
                if (range != null && code != 206) {
                    failure.set("Media host does not support reliable byte-range requests")
                    respond(output, 502, "Upstream Range unsupported")
                    return
                }
                val contentRange = connection.getHeaderField("Content-Range")
                if (code == 206 && (contentRange == null || !validContentRange(contentRange, range))) {
                    failure.set("Media host returned inconsistent byte-range metadata")
                    respond(output, 502, "Upstream Range invalid")
                    return
                }
                val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
                if (type in setOf("text/html", "application/xhtml+xml", "application/json")) {
                    failure.set("Media host returned a webpage instead of video data")
                    respond(output, 502, "Upstream content was not media")
                    return
                }
                val length = connection.contentLengthLong
                val safeType = connection.contentType?.take(120)?.takeIf { value ->
                    value.isNotBlank() && value.all { it.code in 32..126 && it != '\r' && it != '\n' }
                } ?: "application/octet-stream"
                val headers = linkedMapOf("Content-Type" to safeType,
                    "Accept-Ranges" to "bytes", "Cache-Control" to "no-store")
                if (contentRange != null) headers["Content-Range"] = contentRange
                if (length >= 0) headers["Content-Length"] = length.toString()
                writeStatus(output, code, if (code == 206) "Partial Content" else "OK", headers)
                responseStarted = true
                if (method != "HEAD") {
                    connection.inputStream.use { remote ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesLeft = if (length >= 0) length else Long.MAX_VALUE
                        while (running && bytesLeft > 0) {
                            val count = remote.read(buffer, 0, minOf(buffer.size.toLong(), bytesLeft).toInt())
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            bytesLeft -= count
                        }
                    }
                }
                output.flush()
                return
            } catch (_: Exception) {
                failure.set("Android HTTPS media transfer failed")
                if (!responseStarted) respond(output, 502, "Upstream transfer failed")
                return
            } finally { connection?.disconnect() }
        }
    }

    private fun writeStatus(out: BufferedOutputStream, code: Int, phrase: String, headers: Map<String,String> = emptyMap()) {
        val text = buildString {
            append("HTTP/1.1 $code $phrase\r\nConnection: close\r\n")
            for ((name, value) in headers) append("$name: $value\r\n")
            append("\r\n")
        }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }
    private fun respond(out: BufferedOutputStream, code: Int, phrase: String, headers: Map<String,String> = emptyMap()) {
        writeStatus(out, code, phrase, headers + ("Content-Length" to "0"))
    }

    @Synchronized override fun close() {
        running = false
        runCatching { server.close() }
        synchronized(clients) { clients.toList().forEach { runCatching { it.close() } }; clients.clear() }
        workers.shutdownNow()
    }

    companion object {
        private val rangePattern = Regex("bytes=(?:[0-9]+-[0-9]*|-[1-9][0-9]*)")
        private val contentRangePattern = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+|\\*)")
        internal fun validRange(range: String): Boolean = range.length <= 96 && rangePattern.matches(range)
        internal fun validContentRange(value: String, requestRange: String?): Boolean {
            val groups = contentRangePattern.matchEntire(value)?.groupValues ?: return false
            val begin = groups[1].toLongOrNull() ?: return false
            val end = groups[2].toLongOrNull() ?: return false
            val total = groups[3].takeIf { it != "*" }?.toLongOrNull()
            if (begin > end || (total != null && end >= total)) return false
            if (requestRange != null && requestRange.startsWith("bytes=") && !requestRange.startsWith("bytes=-")) {
                val beginRequest = requestRange.substringAfter('=').substringBefore('-').toLongOrNull() ?: return false
                if (begin != beginRequest) return false
            }
            return true
        }
        private fun validContentRange416(value: String): Boolean = Regex("bytes \\*/[0-9]+").matches(value)
        private fun allowedTarget(uri: URI): Boolean {
            return try {
            if (!uri.scheme.equals("https", true) || uri.host.isNullOrEmpty() ||
                uri.rawUserInfo != null || uri.fragment != null || uri.port !in listOf(-1,443)) return false
            val host = uri.host.lowercase()
            if (host == "localhost" || host.endsWith(".localhost") ||
                host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan")) return false
            val ips = InetAddress.getAllByName(host)
            ips.isNotEmpty() && ips.all {
                !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress &&
                    !it.isSiteLocalAddress && !it.isMulticastAddress
            }
        } catch (_: Exception) { false }
        }
    }
}
