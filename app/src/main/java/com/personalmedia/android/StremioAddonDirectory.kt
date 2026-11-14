package com.personalmedia.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.InetAddress

/** Add-on descriptors, not APKs: transport URLs may contain account-specific secrets.
 * Never display, log, persist or transmit a configured URL outside Stremio's account API.
 */
internal data class StremioManageAddon(
    val transportUrl: String,
    val manifest: JSONObject,
    val flags: JSONObject = JSONObject(),
    val official: Boolean = false,
    val protected: Boolean = false
) {
    val id: String get() = manifest.optString("id")
    val name: String get() = manifest.optString("name", "Unnamed add-on").take(100)
    val version: String get() = manifest.optString("version").take(32)
    val description: String get() = StremioAddonManagePolicy.safeDescription(manifest.optString("description")).take(400)
    val logo: String get() = manifest.optString("logo").takeIf { it.startsWith("https://") } ?: ""
    val types: List<String> get() = manifest.optJSONArray("types")?.strings() ?: emptyList()
    val configurationRequired: Boolean get() = manifest.optJSONObject("behaviorHints")?.optBoolean("configurationRequired") == true
    val configurable: Boolean get() = manifest.optJSONObject("behaviorHints")?.optBoolean("configurable") == true
    val accountKey: String get() = transportUrl // Internal identity only, never render in UI.

    fun copyForAccount(): JSONObject = JSONObject()
        .put("transportUrl", transportUrl)
        .put("transportName", "http")
        .put("manifest", JSONObject(manifest.toString()))
        .put("flags", if (official) JSONObject(flags.toString()) else JSONObject())
}

/** Strict guard for addresses added to a user's Stremio account. */
internal object StremioAddonManagePolicy {
    fun acceptableManifestUrl(address: String): Boolean {
        return try {
        if (address.length !in 12..2048 || address.any { it.isISOControl() }) return false
        val uri = URI(address)
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.fragment != null || uri.port !in setOf(-1, 443) ||
            !uri.path.endsWith("/manifest.json", true)) return false
        val host = uri.host.lowercase()
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".lan")) return false
        val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").matchEntire(host)
        if (ipv4 != null) {
            val parts = ipv4.groupValues.drop(1).map { it.toInt() }
            if (parts.any { it !in 0..255 }) return false
            if (parts[0] == 0 || parts[0] == 10 || parts[0] == 127 || parts[0] >= 224 ||
                parts[0] == 169 && parts[1] == 254 || parts[0] == 192 && parts[1] == 168 ||
                parts[0] == 172 && parts[1] in 16..31 || parts[0] == 100 && parts[1] in 64..127) return false
        }
        if (host.contains(':')) return false // IPv6 literal URLs do not belong in the directory.
        true
        } catch (_: Exception) { false }
    }

    fun safeDescription(input: String): String = input
        .replace(Regex("(?i)(?:https?://|magnet:\\?)[^\\s]+"), "[link hidden]")
        .replace(Regex("(?i)(?:token|apikey|api_key|authkey|password)\\s*[:=]\\s*[^\\s,;]+"), "[private setting]")

    fun matchesType(types: List<String>, filter: String): Boolean = when (filter) {
        "All" -> true
        "Movies" -> "movie" in types
        "Series" -> "series" in types
        "Anime" -> "anime" in types
        "Channels" -> "channel" in types || "tv" in types
        "Podcasts" -> "podcast" in types || "Podcasts" in types
        "Others" -> types.isEmpty() || types.any { it !in setOf("movie", "series", "anime", "channel", "tv", "podcast", "Podcasts") }
        else -> false
    }
}

internal object StremioAddonDirectory {
    /** Stremio's official index and community catalogue; only the official index may assign protected flags. */
    private const val OFFICIAL = "https://raw.githubusercontent.com/Stremio/stremio-official-addons/master/index.json"
    private const val COMMUNITY = "https://api.strem.io/addonscollection.json"

    suspend fun browse(group: String): List<StremioManageAddon> = withContext(Dispatchers.IO) {
        require(group == "Official" || group == "Community")
        val raw = StremioJsonHttp.read(if (group == "Official") OFFICIAL else COMMUNITY, null)
        val json = JSONArray(raw)
        (0 until minOf(json.length(), 1800)).mapNotNull { index ->
            parse(json.optJSONObject(index), group == "Official")
        }.distinctBy { it.accountKey }
    }

    suspend fun previewCustom(url: String): StremioManageAddon = withContext(Dispatchers.IO) {
        require(StremioAddonManagePolicy.acceptableManifestUrl(url)) { "Enter an HTTPS add-on manifest URL" }
        // A custom manifest preview is a network request initiated by user input.
        // Reject local/private DNS destinations before requesting anything.
        val targetHost = URI(url).host ?: throw IllegalArgumentException("Invalid add-on hostname")
        val addresses = InetAddress.getAllByName(targetHost)
        require(addresses.isNotEmpty() && addresses.all {
            !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isSiteLocalAddress &&
                !it.isLinkLocalAddress && !it.isMulticastAddress
        }) { "Local network addresses are not supported for add-on installs" }
        val content = StremioJsonHttp.read(url, null)
        require(content.length <= 512_000) { "Add-on manifest is too large" }
        val manifest = JSONObject(content)
        require(manifest.optString("id").isNotBlank() && manifest.optString("name").isNotBlank()) {
            "This URL did not return a valid Stremio add-on manifest"
        }
        StremioManageAddon(url, manifest)
    }

    fun parse(row: JSONObject?, trustedOfficialIndex: Boolean): StremioManageAddon? {
        row ?: return null
        val url = row.optString("transportUrl")
        if (!StremioAddonManagePolicy.acceptableManifestUrl(url)) return null
        val manifest = row.optJSONObject("manifest") ?: return null
        if (manifest.optString("id").isBlank() || manifest.optString("name").isBlank()) return null
        val flags = if (trustedOfficialIndex) row.optJSONObject("flags") ?: JSONObject() else JSONObject()
        return StremioManageAddon(url, manifest, flags,
            official = trustedOfficialIndex, protected = trustedOfficialIndex && flags.optBoolean("protected"))
    }
}
