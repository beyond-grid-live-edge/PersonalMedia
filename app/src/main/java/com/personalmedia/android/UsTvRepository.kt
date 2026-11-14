package com.personalmedia.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

internal data class UsChannel(val id: String, val name: String) {
    val pageUrl: String get() = UsTvPolicy.channelPage(id).orEmpty()
}

internal data class UsSource(val number: Int, val url: String)

/** A small, bounded read of user-facing Global Free TV public pages. No synthetic
 * IPTV-org URL substitutions. The full site directory may be JS-only; report that
 * explicitly rather than claiming to have imported all ~1,000 US channels. */
internal class UsTvRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pm_us_tv", Context.MODE_PRIVATE)

    fun cached(): List<UsChannel> {
        val rows = runCatching { JSONArray(prefs.getString("catalog", "[]").orEmpty().ifBlank { "[]" }) }.getOrNull() ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val obj = rows.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id")
            if (UsTvPolicy.channelPage(id) == null) null
            else UsChannel(id, obj.optString("name").ifBlank { UsTvPolicy.nameFromId(id) })
        }
    }

    fun refresh(): List<UsChannel> {
        val all = linkedMapOf<String, UsChannel>()
        val failures = mutableListOf<String>()
        for ((tag, url) in listOf("country" to UsTvPolicy.COUNTRY_URL, "featured" to UsTvPolicy.FEATURED_URL)) {
            try {
                val page = fetchSite(url)
                for ((id, name) in UsTvPolicy.parseUsChannelLinks(page)) {
                    all.putIfAbsent(id.lowercase(Locale.ROOT), UsChannel(id, name))
                }
            } catch (_: Exception) { failures.add(tag) }
        }
        if (all.isEmpty()) throw IllegalStateException(
            if (failures.size == 2) "Global Free TV is unreachable. Cached channels remain available."
            else "Global Free TV did not expose US channel links in these public pages. Its catalogue may be dynamically loaded."
        )
        val fresh = all.values.sortedBy { it.name.lowercase(Locale.ROOT) }
        val json = JSONArray().apply { fresh.forEach { channel ->
            put(JSONObject().put("id", channel.id).put("name", channel.name))
        } }
        prefs.edit().putString("catalog", json.toString())
            .putLong("catalogFetched", System.currentTimeMillis()).apply()
        return fresh
    }

    fun catalogAge(): Long = prefs.getLong("catalogFetched", 0L)

    fun sources(channel: UsChannel): List<UsSource> {
        val pageUrl = channel.pageUrl.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Not a Global Free TV United States channel")
        val page = fetchSite(pageUrl)
        // Don't accept URLs from a page that says it belongs to another country.
        if (Regex("(?is)Country\\s*:?\\s*(?:</[^>]*>\\s*)*United Kingdom").containsMatchIn(page))
            throw IllegalStateException("The channel page is not listed as a US channel")
        return UsTvPolicy.extractPageMedia(page).mapIndexed { index, url -> UsSource(index + 1, url) }
    }

    fun preferredSource(id: String): Int = prefs.getInt("source.$id", 1).coerceAtLeast(1)
    fun setPreferredSource(id: String, number: Int) {
        if (UsTvPolicy.channelPage(id) != null && number in 1..16)
            prefs.edit().putInt("source.$id", number).apply()
    }
    fun isFavourite(id: String): Boolean = prefs.getBoolean("fav.$id", false)
    fun toggleFavourite(id: String) { prefs.edit().putBoolean("fav.$id", !isFavourite(id)).apply() }
    var xmltvUrl: String
        get() = prefs.getString("epgUrl", "https://iptv-epg.org/files/epg-us.xml.gz").orEmpty()
        set(value) { prefs.edit().putString("epgUrl", value.trim()).apply() }
    fun epgMapping(id: String): String = prefs.getString("epg.$id", "").orEmpty()
    fun setEpgMapping(id: String, epgId: String) {
        if (UsTvPolicy.channelPage(id) != null)
            prefs.edit().putString("epg.$id", epgId.trim().take(180)).apply()
    }

    /** Site-only HTTPS fetch. Do not forward cookies or authorisation to redirects. */
    private fun fetchSite(url: String): String {
        var current = URI(url)
        repeat(3) {
            require(current.scheme.equals("https", true) && UsTvPolicy.isSiteHost(current.host) &&
                current.rawUserInfo == null && current.port == -1) { "Only Global Free TV HTTPS pages are allowed" }
            val connection = URL(current.toASCIIString()).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("User-Agent", "PersonalMedia-Android/US-LTV-1")
                if (connection.responseCode in setOf(301,302,303,307,308)) {
                    current = current.resolve(connection.getHeaderField("Location") ?: "")
                } else {
                    if (connection.responseCode != 200) throw IllegalStateException("Channel directory HTTP ${connection.responseCode}")
                    val length = connection.contentLengthLong
                    if (length > 4_000_000) throw IllegalStateException("Site page exceeds safety size limit")
                    connection.inputStream.use { stream ->
                        val data = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            if (data.size() + count > 4_000_000) throw IllegalStateException("Site page is too large")
                            data.write(buffer, 0, count)
                        }
                        return data.toString("UTF-8")
                    }
                }
            } finally { connection.disconnect() }
        }
        throw IllegalStateException("Too many Global Free TV redirects")
    }
}
