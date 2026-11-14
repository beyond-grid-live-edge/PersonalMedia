package com.personalmedia.android

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Ephemeral diagnostic outcome. Neither URL may be displayed, logged or persisted:
 * both can contain time-limited signed access tokens. bridgeUrl is populated only
 * when an HTTPS media endpoint accepts a byte-range request WITHOUT headers from
 * another origin. httpStatus records the probe response, not MPV's result. */
internal data class StremioSourceInspection(
    val message: String,
    val retryUrl: String? = null,
    val bridgeUrl: String? = null,
    val httpStatus: Int = 0
)

/** STR-4c: inspect the redirect chain *without* reading media bytes.
 * Origin-specific headers are never sent past their original HTTPS origin.
 * A usable cross-origin HTTPS endpoint can be offered as an explicit, header-free MPV retry.
 */
internal object StremioSourceDiagnostic {
    private val redirects = setOf(301, 302, 303, 307, 308)

    fun response(status: Int, contentType: String?, redirected: Boolean = false): String {
        val type = contentType.orEmpty().substringBefore(';').trim().lowercase()
        return when {
            status == 401 -> "Source check: HTTP 401 — the provider requires authentication."
            status == 403 -> "Source check: HTTP 403 — the provider denied this request; the stream may require a different session or request headers."
            status == 404 || status == 410 -> "Source check: HTTP $status — this particular stream is no longer available. Choose another source."
            status == 429 -> "Source check: HTTP 429 — the provider is limiting requests."
            status in 500..599 -> "Source check: provider server error (HTTP $status)."
            status in redirects -> "Source check: the provider redirects the media request; its final destination requires further verification."
            status in 200..299 && (type == "text/html" || type == "application/xhtml+xml") ->
                "Source check: HTTP $status returned a webpage, not a playable direct media file."
            status in 200..299 && (type == "application/json" || type == "text/plain") ->
                "Source check: HTTP $status returned non-media content ($type). The add-on may be providing a webpage or error response."
            status in 200..299 ->
                "Source check: provider accepted the request (HTTP $status${if (redirected) ", after redirect" else ""}). " +
                    "MPV still could not open the source; its network backend, server requirements or media format may differ."
            else -> "Source check: provider returned HTTP $status. Try another source from the list."
        }
    }

    private fun port(uri: URI): Int = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
    private fun sameOrigin(a: URI, b: URI): Boolean =
        a.scheme.equals("https", true) && b.scheme.equals("https", true) &&
            a.host != null && a.host.equals(b.host, true) && port(a) == port(b) && b.rawUserInfo == null

    // Don't allow provider-controlled redirects to arbitrary local network services.
    private fun privateAddress(host: String?): Boolean {
        if (host.isNullOrBlank()) return true
        val lower = host.trim('[', ']').lowercase()
        if (lower == "localhost" || lower.endsWith(".localhost") || lower.endsWith(".local") ||
            lower.endsWith(".internal") || lower.endsWith(".lan")) return true
        // Check numeric literals, including bracketed IPv6, without looking up arbitrary DNS names.
        val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
            .matchEntire(lower)
        if (ipv4 != null) {
            val parts = ipv4.groupValues.drop(1).map { it.toInt() }
            if (parts.any { it !in 0..255 }) return true
            return parts[0] == 0 || parts[0] == 10 || parts[0] == 127 || parts[0] >= 224 ||
                (parts[0] == 169 && parts[1] == 254) || (parts[0] == 192 && parts[1] == 168) ||
                (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 100 && parts[1] in 64..127)
        }
        if (lower.all { it.isDigit() }) return true
        if (lower.contains(':')) {
            val address = runCatching { InetAddress.getByName(lower) }.getOrNull() ?: return true
            return address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress ||
                lower.startsWith("fc") || lower.startsWith("fd")
        }
        return false
    }

    internal fun redirectDestination(current: URI, location: String?): URI? {
        if (location.isNullOrBlank()) return null
        val next = runCatching { current.resolve(location) }.getOrNull() ?: return null
        return next.takeIf {
            it.isAbsolute && it.scheme.equals("https", true) && !it.host.isNullOrBlank() &&
                it.rawUserInfo == null && it.fragment == null && (it.port == -1 || it.port in 1..65535) &&
                !privateAddress(it.host)
        }
    }

    /** Compatibility API for existing diagnostics/tests, intentionally no URL in the message. */
    fun redirectMessage(start: URI, location: String?): String {
        val target = redirectDestination(start, location)
            ?: return "Source check: provider redirect is missing, insecure or unsupported."
        return if (sameOrigin(start, target)) "SAME_ORIGIN"
        else "Source check: provider redirects to another HTTPS host; checking it without private source headers."
    }

    internal fun playableResponse(status: Int, contentType: String?): Boolean {
        if (status !in 200..299) return false
        val type = contentType.orEmpty().substringBefore(';').trim().lowercase()
        return type !in setOf("text/html", "application/xhtml+xml", "application/json", "text/plain", "application/xml", "text/xml")
    }

    /** IO-thread only. Never includes a stream URL or header value in message. */
    fun inspectDetailed(url: String, headers: Map<String, String>): StremioSourceInspection {
        if (!StremioPlaybackPolicy.safeTransport(url, headers))
            return StremioSourceInspection("Source check: unsupported stream URL or request headers.")
        var current = runCatching { URI(url) }.getOrNull()
            ?: return StremioSourceInspection("Source check: invalid stream URL.")
        val visited = mutableSetOf<String>()
        var allowedHeaders = headers
        var crossHost = false
        var redirected = false
        repeat(6) { hop ->
            if (!visited.add(current.toASCIIString()))
                return StremioSourceInspection("Source check: provider returned a redirect loop.")
            var connection: HttpURLConnection? = null
            try {
                connection = URL(current.toASCIIString()).openConnection() as? HttpURLConnection
                    ?: return StremioSourceInspection("Source check: unsupported provider transport.")
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5500
                connection.readTimeout = 5500
                connection.requestMethod = "GET"
                connection.setRequestProperty("Range", "bytes=0-0")
                connection.setRequestProperty("Accept-Encoding", "identity")
                allowedHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                val status = connection.responseCode
                if (status in redirects) {
                    if (hop == 5) return StremioSourceInspection("Source check: too many redirects.")
                    val next = redirectDestination(current, connection.getHeaderField("Location"))
                        ?: return StremioSourceInspection("Source check: provider redirects to an insecure or unsupported destination. No private headers were forwarded.")
                    // Even an otherwise safe cross-host redirect must NOT receive cookies,
                    // Authorization, Referer or provider-provided User-Agent.
                    if (!sameOrigin(current, next)) {
                        allowedHeaders = emptyMap()
                        crossHost = true
                    }
                    current = next
                    redirected = true
                    return@repeat
                }
                val description = response(status, connection.contentType, redirected)
                if (crossHost && playableResponse(status, connection.contentType)) {
                    return StremioSourceInspection(
                        "Source check: the redirect reaches an HTTPS media endpoint (HTTP $status). " +
                            "A header-free retry is available. This confirms HTTP access, not video decoding.",
                        retryUrl = current.toASCIIString(),
                        bridgeUrl = current.takeIf { status == 206 && it.port in setOf(-1, 443) }?.toASCIIString(),
                        httpStatus = status
                    )
                }
                // A public, header-free source returning 206 is also eligible for
                // Android HTTPS bridge fallback even when no redirect occurred.
                // Never proxy a source that still requires private request headers.
                val bridgeCandidate = if (status == 206 &&
                    playableResponse(status, connection.contentType) &&
                    allowedHeaders.isEmpty() && current.scheme.equals("https", true) &&
                    current.port in setOf(-1, 443)) current.toASCIIString() else null
                return StremioSourceInspection(description,
                    bridgeUrl = bridgeCandidate, httpStatus = status)
            } catch (_: UnknownHostException) {
                return StremioSourceInspection("Source check: the destination host could not be resolved (DNS).")
            } catch (_: SocketTimeoutException) {
                return StremioSourceInspection("Source check: the destination did not answer before the network timeout.")
            } catch (_: SSLException) {
                return StremioSourceInspection("Source check: Android could not establish secure HTTPS to this destination.")
            } catch (_: IOException) {
                return StremioSourceInspection("Source check: Android could not complete the provider request.")
            } catch (_: Exception) {
                return StremioSourceInspection("Source check: unable to inspect this provider safely.")
            } finally {
                connection?.disconnect()
            }
        }
        return StremioSourceInspection("Source check: redirect limit reached.")
    }

    /** Retained for old callers. */
    fun inspect(url: String, headers: Map<String, String>): String = inspectDetailed(url, headers).message
}
