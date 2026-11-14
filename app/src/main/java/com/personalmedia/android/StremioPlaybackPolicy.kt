package com.personalmedia.android

import org.json.JSONObject
import java.net.URI

/** STR-4: ephemeral transport policy. Never persist URL/header values or put them in diagnostics. */
internal object StremioPlaybackPolicy {
    private val headerName = Regex("[A-Za-z][A-Za-z0-9-]{0,63}")
    private val forbidden = setOf("host", "connection", "content-length", "transfer-encoding",
        "proxy-authorization", "proxy-connection", "upgrade", "te", "trailer")

    /** Stremio add-ons may supply stream-level proxyHeaders or behaviorHints.proxyHeaders. */
    fun extractRequestHeaders(stream: JSONObject): Map<String, String> {
        val hints = stream.optJSONObject("behaviorHints")
        val headers = hints?.optJSONObject("proxyHeaders")?.optJSONObject("request")
            ?: stream.optJSONObject("proxyHeaders")?.optJSONObject("request")
            ?: return emptyMap()
        return buildMap {
            val it = headers.keys()
            while (it.hasNext()) {
                require(size < 16) { "Too many stream request headers" }
                val name = it.next()
                val value = headers.optString(name)
                require(validHeader(name, value)) { "Invalid stream request header" }
                put(name, value)
            }
        }
    }

    fun validHeader(name: String, value: String): Boolean =
        headerName.matches(name) && name.lowercase() !in forbidden &&
            value.length in 1..2048 && value.none { it == '\r' || it == '\n' || it == '\u0000' } &&
            value.all { it.code >= 32 && it.code != 127 }

    fun safeTransport(url: String, headers: Map<String, String>): Boolean = try {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase()
        (scheme == "https" || (scheme == "http" && headers.isEmpty())) &&
            !uri.host.isNullOrEmpty() && uri.rawUserInfo == null && uri.fragment == null &&
            headers.size <= 16 && headers.all { (k,v) -> validHeader(k,v) }
    } catch (_: Exception) { false }

    /** Per-player HTTP fields may also apply to MPV subtitle fetches.
     * Don't send a stream's private headers to an unrelated subtitle host. */
    fun externalSubtitleAllowed(streamUrl: String, subtitleUrl: String,
        headers: Map<String, String>): Boolean = try {
        val subtitle = URI(subtitleUrl)
        if (subtitle.scheme?.lowercase() != "https" || subtitle.host.isNullOrBlank() ||
            subtitle.rawUserInfo != null || subtitle.fragment != null) false
        else if (headers.isEmpty()) true
        else {
            val source = URI(streamUrl)
            source.scheme?.lowercase() == "https" &&
                source.host.equals(subtitle.host, ignoreCase = true) &&
                (if (source.port < 0) 443 else source.port) ==
                    (if (subtitle.port < 0) 443 else subtitle.port)
        }
    } catch (_: Exception) { false }

    /** LF is a delimiter only after CR/LF injection has been rejected above. */
    fun serializeHeaders(headers: Map<String,String>): String {
        require(headers.size <= 16 && headers.all { validHeader(it.key,it.value) }) { "Invalid stream request headers" }
        return headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }.also {
            require(it.toByteArray(Charsets.UTF_8).size <= 8192) { "Stream request headers too large" }
        }
    }

    fun resumePosition(positionMs: Long): Long = positionMs.coerceIn(0, 24L * 60 * 60 * 1000)
}
