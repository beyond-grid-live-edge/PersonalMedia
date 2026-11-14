package com.personalmedia.android

import java.net.URI
import java.util.Locale

/** Only Global Free TV US channel identities; no third-party stream directory fallback. */
internal object UsTvPolicy {
    const val COUNTRY_URL = "https://www.globalfreetv.com/countries/united-states"
    const val FEATURED_URL = "https://www.globalfreetv.com/us-tv"
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,110}\\.us$", RegexOption.IGNORE_CASE)
    private val channelHref = Regex("(?is)<a\\b[^>]*?href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>")
    private val altText = Regex("(?is)<img\\b[^>]*?alt\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']")
    private val tag = Regex("(?is)<[^>]+>")
    private val richUrl = Regex("(?i)https?:(?:\\/|\\\\/){2}[^\\\"'<>\\s]{8,1900}")
    private val mediaSuffix = Regex("(?i)\\.(?:m3u8|mpd|mp4|m4v|ts)(?:[?#]|$)")

    fun siteChannelId(href: String): String? = try {
        val url = URI(COUNTRY_URL).resolve(unescape(href))
        if (!url.scheme.equals("https", true) || !isSiteHost(url.host) ||
            url.rawUserInfo != null || url.port != -1) null
        else url.path.removeSuffix("/").takeIf { it.startsWith("/channels/") }
            ?.removePrefix("/channels/")?.takeIf { idPattern.matches(it) }
    } catch (_: Exception) { null }

    fun isSiteHost(host: String?): Boolean =
        host.equals("globalfreetv.com", true) || host.equals("www.globalfreetv.com", true)

    fun channelPage(id: String): String? =
        id.takeIf { idPattern.matches(it) }?.let { "https://www.globalfreetv.com/channels/$it" }

    fun nameFromId(id: String): String = id.removeSuffix(".us")
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replace('_', ' ').trim().ifBlank { id }

    fun cleanName(text: String): String = unescape(text.replace(tag, " "))
        .replace(Regex("\\s+"), " ").trim().take(90)

    internal fun unescape(value: String): String = value
        .replace("\\\\/", "/").replace("\\u002F", "/", true)
        .replace("\\u003A", ":", true).replace("\\u0026", "&", true)
        .replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    /** The page must come from the US country directory or the site's US hub. */
    fun parseUsChannelLinks(page: String): List<Pair<String, String>> {
        val records = linkedMapOf<String, Pair<String, String>>()
        for (match in channelHref.findAll(page)) {
            val id = siteChannelId(match.groupValues[1]) ?: continue
            val html = match.groupValues[2]
            val imgLabel = altText.find(html)?.groupValues?.get(1)
            val name = cleanName(imgLabel ?: html).ifBlank { nameFromId(id) }
                .trim().ifBlank { nameFromId(id) }
            records.putIfAbsent(id.lowercase(Locale.ROOT), id to name)
        }
        // Some revisions of the site embed a US channel catalogue in JSON
        // instead of link elements. Only accept well-formed .us IDs from these
        // already US-scoped pages; do not import another site's catalogue.
        val renderedJson = unescape(page).replace("\\\"", "\"")
        val objectPattern = Regex("(?s)\\{[^{}]{1,1000}\\}")
        val idField = Regex("\"id\"\\s*:\\s*\"([^\"]+\\.us)\"", RegexOption.IGNORE_CASE)
        val nameField = Regex("\"name\"\\s*:\\s*\"([^\"]{1,130})\"", RegexOption.IGNORE_CASE)
        for (obj in objectPattern.findAll(renderedJson)) {
            val id = idField.find(obj.value)?.groupValues?.get(1) ?: continue
            if (channelPage(id) == null) continue
            val name = cleanName(nameField.find(obj.value)?.groupValues?.get(1).orEmpty())
                .ifBlank { nameFromId(id) }
            records.putIfAbsent(id.lowercase(Locale.ROOT), id to name)
        }
        return records.values.toList()
    }

    /** Only media URLs visible on the *same channel's* public page are candidates.
     * Never synthesize URLs from EPG or other IPTV databases. */
    fun isPublicMediaUrl(candidate: String): Boolean = try {
        val uri = URI(candidate)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val quad = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
            .matchEntire(host)
        val internalIp = if (quad == null) false else {
            val parts = quad.groupValues.drop(1).map { it.toInt() }
            parts.any { it !in 0..255 } || parts[0] in listOf(0, 10, 127) || parts[0] >= 224 ||
                (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 169 && parts[1] == 254) || (parts[0] == 100 && parts[1] in 64..127)
        }
        uri.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") && host.isNotBlank() &&
            !internalIp && !host.contains(':') && host != "localhost" &&
            !host.endsWith(".local") && !host.endsWith(".internal") &&
            uri.rawUserInfo == null && uri.fragment == null &&
            uri.port in setOf(-1, 80, 443) &&
            !isSiteHost(host) && mediaSuffix.containsMatchIn(uri.path + (uri.rawQuery?.let { "?$it" } ?: ""))
    } catch (_: Exception) { false }

    fun extractPageMedia(html: String): List<String> {
        val decoded = unescape(html).replace("\\u003d", "=", true)
            .replace("\\u003f", "?", true)
        val found = linkedSetOf<String>()
        for (match in richUrl.findAll(decoded)) {
            val raw = match.value.replace("\\/", "/")
                .trimEnd(')', ',', ';', '}', '\\')
            val url = runCatching { URI(raw) }.getOrNull() ?: continue
            if (!isPublicMediaUrl(url.toASCIIString())) continue
            if (url.toASCIIString().length <= 1900) found.add(url.toASCIIString())
            if (found.size >= 16) break
        }
        return found.toList()
    }

    fun epgKey(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "")

    fun epgUniqueNameMatch(channelName: String, names: List<Pair<String, String>>): String? {
        val key = epgKey(channelName)
        if (key.length < 3) return null
        val candidates = names.filter { epgKey(it.second) == key }
        return candidates.singleOrNull()?.first
    }
}
