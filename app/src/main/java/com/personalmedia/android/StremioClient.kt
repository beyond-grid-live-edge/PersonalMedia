package com.personalmedia.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Port of the C20.0.7 ArkTS StremioClient protocol. All login and library writes
 * use Stremio's server, not an unrelated "local sync" pretend backend. */
data class StremioMeta(
    val id: String, val type: String, val name: String, val poster: String = "",
    val description: String = "", val year: String = "", val addonId: String = "",
    val videos: List<StremioVideo> = emptyList(), val background: String = ""
)
/** Video IDs come from the provider; enriched episode fields never replace playable IDs. */
data class StremioVideo(
    val id: String, val title: String, val season: Int, val episode: Int,
    val overview: String = "", val thumbnail: String = "", val released: String = ""
)
data class StremioCatalog(
    val addonId: String, val addonName: String, val id: String, val type: String,
    val name: String, val searchable: Boolean, val paginated: Boolean,
    val requiresExtra: Boolean, val genres: List<String>
)
data class StremioSource(
    val name: String, val release: String, val url: String, val hash: String,
    val fileIndex: Int, val trackers: List<String>, val kind: String,
    val quality: String, val addon: String, val subtitles: List<StremioSubtitle>,
    val requestHeaders: Map<String, String> = emptyMap()
)
data class StremioSubtitle(val url: String, val language: String)
/** Aggregated stream request results. Never expose configured add-on URLs or tokens. */
data class StremioStreamLoadReport(
    val streams: List<StremioSource>, val queriedAddons: Int, val failedAddons: Int
)
/** Public, credential-free metadata for the Stremio add-on status screen.
 * Configured transport URLs are intentionally never presented or logged. */
data class StremioAddonInfo(
    val name: String, val version: String, val description: String,
    val contentTypes: List<String>, val resources: List<String>, val catalogCount: Int,
    val searchableCatalogCount: Int
)
private data class StremioAddon(
    val id: String, val name: String, val manifest: JSONObject,
    val resources: JSONArray, val types: List<String>, val prefixes: List<String>
)

class StremioClient(private val context: Context) {
    private val prefs by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "personalmedia_stremio_secret", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    private var token: String = ""
    var email: String = ""; private set
    val loggedIn get() = token.isNotBlank()
    private var addons: List<StremioAddon> = emptyList()
    /** Full account collection, including unsupported transports. Never overwrite with a filtered list. */
    private var installedDescriptors: List<JSONObject> = emptyList()
    private val addonWriteLock = Mutex()
    internal val installedManageAddons: List<StremioManageAddon>
        get() = installedDescriptors.mapNotNull { descriptor ->
            val url = descriptor.optString("transportUrl")
            val manifest = descriptor.optJSONObject("manifest") ?: return@mapNotNull null
            if (url.isBlank()) return@mapNotNull null
            val flags = descriptor.optJSONObject("flags") ?: JSONObject()
            StremioManageAddon(url, manifest, flags,
                official = flags.optBoolean("official"), protected = flags.optBoolean("protected"))
        }
    fun hasAddon(address: String): Boolean = installedDescriptors.any {
        it.optString("transportUrl") == address
    }

    var catalogs: List<StremioCatalog> = emptyList(); private set
    /** No configured URLs (which may contain API keys) leave this getter. */
    val addonInfo: List<StremioAddonInfo>
        get() = addons.map { addon ->
            val ownCatalogs = catalogs.filter { it.addonId == addon.id }
            StremioAddonInfo(
                name = addon.name,
                version = addon.manifest.optString("version").take(40),
                description = redact(addon.manifest.optString("description")).take(240),
                contentTypes = addon.types.take(12),
                resources = addon.resources.items().mapNotNull { entry ->
                    when (entry) {
                        is String -> entry
                        is JSONObject -> entry.optString("name").takeIf { it.isNotBlank() }
                        else -> null
                    }
                }.distinct().take(20),
                catalogCount = ownCatalogs.size,
                searchableCatalogCount = ownCatalogs.count { it.searchable }
            )
        }

    suspend fun restore(): Boolean {
        val saved = withContext(Dispatchers.IO) { prefs.getString("auth_key", "").orEmpty() }
        if (saved.isEmpty()) return false
        return try { signInWithKey(saved); true }
        catch (_: Exception) { false }
    }
    suspend fun signIn(email: String, password: String) {
        require(email.isNotBlank() && password.isNotBlank()) { "Enter email and password" }
        val result = api("login", JSONObject().put("type", 1).put("email", email.trim())
            .put("password", password).put("authKey", JSONObject.NULL)).getJSONObject("result")
        val key = result.optString("authKey")
        require(key.isNotEmpty()) { "Stremio did not return a session" }
        token = key
        this.email = result.optJSONObject("user")?.optString("email") ?: email.trim()
        saveToken()
    }
    suspend fun signInWithKey(key: String) {
        require(key.isNotBlank() && key.length <= 4096)
        val user = api("getUser", JSONObject().put("authKey", key)).getJSONObject("result")
        require(user.optString("_id").isNotEmpty()) { "Session rejected by Stremio" }
        token = key
        email = user.optString("email", "Stremio account")
        saveToken()
    }
    private suspend fun saveToken() = withContext(Dispatchers.IO) {
        prefs.edit().putString("auth_key", token).apply()
    }
    fun signOut() {
        token = ""; email = ""; addons = emptyList(); catalogs = emptyList(); installedDescriptors = emptyList()
        prefs.edit().remove("auth_key").apply()
    }
    suspend fun refreshAddons() {
        require(loggedIn) { "Sign in to Stremio" }
        val session = token
        val result = api("addonCollectionGet", JSONObject().put("authKey", session)
            .put("update", true).put("addFromURL", JSONArray())).optJSONObject("result") ?: JSONObject()
        val found = mutableListOf<StremioAddon>()
        val cats = mutableListOf<StremioCatalog>()
        result.optJSONArray("addons").orEmpty().forEachObject { entry ->
            val url = entry.optString("transportUrl")
            val manifest = entry.optJSONObject("manifest") ?: return@forEachObject
            if (!url.matches(Regex("^https?://.+/manifest\\.json(?:\\?.*)?$", RegexOption.IGNORE_CASE))) return@forEachObject
            val addon = StremioAddon(url, manifest.optString("name", "Add-on"), manifest,
                manifest.optJSONArray("resources") ?: JSONArray(),
                manifest.optJSONArray("types").strings(), manifest.optJSONArray("idPrefixes").strings())
            found += addon
            manifest.optJSONArray("catalogs").orEmpty().forEachObject { cat ->
                val type = cat.optString("type")
                val id = cat.optString("id")
                if (type !in listOf("movie", "series") || id.isBlank()) return@forEachObject
                val extras = cat.optJSONArray("extra") ?: JSONArray()
                val names = extras.objects().map { it.optString("name") }
                val genre = extras.objects().firstOrNull { it.optString("name") == "genre" }
                cats += StremioCatalog(url, addon.name, id, type, cat.optString("name", id),
                    "search" in names, "skip" in names,
                    extras.objects().any { it.optBoolean("isRequired") }, genre?.optJSONArray("options").strings())
            }
        }
        // Refresh may finish after logout/account switch. Never show another account's add-ons.
        if (token != session || !loggedIn) return
        installedDescriptors = result.optJSONArray("addons").orEmpty().objects().map { JSONObject(it.toString()) }
        addons = found.distinctBy { it.id }
        catalogs = cats.distinctBy { "${it.addonId}:${it.type}:${it.id}" }
    }
    /** Account-wide add-on install/remove, not a local toggle. Fetch fresh before writing
     * so supported/unsupported and configured add-ons remain in their original order.
     * Verify the server's saved collection before reporting success.
     */
    internal suspend fun setAddonInstalled(addon: StremioManageAddon, install: Boolean) = addonWriteLock.withLock {
        require(loggedIn) { "Sign in before managing add-ons" }
        require(addon.transportUrl.isNotBlank())
        if (install) require(StremioAddonManagePolicy.acceptableManifestUrl(addon.transportUrl)) {
            "Only HTTPS manifest add-ons can be installed"
        }
        val owner = token
        val server = api("addonCollectionGet", JSONObject().put("authKey", owner)
            .put("update", true).put("addFromURL", JSONArray())).optJSONObject("result")
            ?: throw IllegalStateException("Cannot read installed Stremio add-ons")
        val previous = server.optJSONArray("addons")?.objects()
            ?: throw IllegalStateException("Account did not return an add-on collection")
        check(token == owner && loggedIn) { "Stremio account changed" }
        val existing = previous.any { it.optString("transportUrl") == addon.transportUrl }
        if (install && existing || !install && !existing) {
            refreshAddons()
            return@withLock
        }
        if (!install) {
            val target = previous.first { it.optString("transportUrl") == addon.transportUrl }
            val protected = target.optJSONObject("flags")?.optBoolean("protected") == true || addon.protected
            require(!protected) { "This official add-on is protected and cannot be removed" }
        }
        val next = JSONArray()
        previous.forEach { descriptor ->
            if (install || descriptor.optString("transportUrl") != addon.transportUrl)
                next.put(JSONObject(descriptor.toString()))
        }
        if (install) next.put(addon.copyForAccount())
        api("addonCollectionSet", JSONObject().put("type", "AddonCollectionSet")
            .put("authKey", owner).put("addons", next))
        check(token == owner && loggedIn) { "Stremio account changed" }
        refreshAddons()
        check(hasAddon(addon.transportUrl) == install) {
            "Stremio did not confirm the add-on change; refresh and try again"
        }
    }

    suspend fun catalog(cat: StremioCatalog, genre: String = "", skip: Int = 0): List<StremioMeta> {
        require(skip in 0..10000)
        val addon = addons.firstOrNull { it.id == cat.addonId } ?: return emptyList()
        val params = buildList {
            if (genre.isNotBlank()) add("genre=${encode(genre)}")
            if (skip > 0) add("skip=$skip")
        }.joinToString("&")
        return resource(addon, "catalog", cat.type, cat.id, params)
            .optJSONArray("metas").orEmpty().objects().take(100).map { parseMeta(it, addon.id, cat.type) }
            .filter { it.id.isNotEmpty() }
    }
    suspend fun search(query: String): List<StremioMeta> {
        require(query.trim().length >= 2) { "Enter at least two characters" }
        return catalogs.filter { it.searchable }.take(12).flatMap { cat ->
            runCatching {
                val addon = addons.first { it.id == cat.addonId }
                resource(addon, "catalog", cat.type, cat.id, "search=${encode(query.trim())}")
                    .optJSONArray("metas").orEmpty().objects().take(40)
                    .map { parseMeta(it, addon.id, cat.type) }
            }.getOrDefault(emptyList())
        }.distinctBy { "${it.type}:${it.id}" }.filter { it.id.isNotBlank() }.take(100)
    }
    /** Up to four compatible meta providers can complement missing episode artwork/synopses.
     * Only exact same type+ID data is merged; the episode IDs remain provider-supplied. */
    suspend fun detail(item: StremioMeta): StremioMeta = coroutineScope {
        val candidates = addons.filter { supports(it, "meta", item.type, item.id) }
            .sortedBy { if (it.id == item.addonId) 0 else 1 }.take(4)
        val details = candidates.map { addon ->
            async(Dispatchers.IO) {
                runCatching {
                    resource(addon, "meta", item.type, item.id).optJSONObject("meta")
                        ?.takeIf { it.optString("id", it.optString("_id")) == item.id }
                        ?.let { parseMeta(it, addon.id, item.type) }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
        StremioBrowsePolicy.mergeDetails(item, details)
    }
    /** The stream protocol returns the whole provider-supplied array, usually ordered
     * high-quality first. Do not take(40): that silently removes 1080p/720p sources
     * when a provider puts dozens of 4K releases at the top of its response.
     * Requests run independently so one slow add-on does not serially stall others.
     * StremioJsonHttp already bounds each JSON response to 8 MiB.
     */
    suspend fun sourcesReport(meta: StremioMeta, videoId: String): StremioStreamLoadReport = coroutineScope {
        val providers = addons.filter { supports(it, "stream", meta.type, videoId) }
        val outcomes = providers.map { addon ->
            async(Dispatchers.IO) {
                runCatching {
                    resource(addon, "stream", meta.type, videoId)
                        .optJSONArray("streams").orEmpty().objects().map { row ->
                            val url = row.optString("url")
                            val magnetHash = Regex("[?&]xt=urn:btih:([0-9a-fA-F]{40})(?:&|$)")
                                .find(url)?.groupValues?.getOrNull(1).orEmpty()
                            val hash = row.optString("infoHash").ifEmpty { magnetHash }
                            val kind = when {
                                url.startsWith("https://") || url.startsWith("http://") -> "Direct HTTP"
                                hash.matches(Regex("[0-9a-fA-F]{40}")) -> "Torrent"
                                else -> "Unsupported"
                            }
                            val hints = row.optJSONArray("sources").strings().filter {
                                it.matches(Regex("^(?:tracker:)?(?:udp|https?)://.*|^dht:.*", RegexOption.IGNORE_CASE))
                            }.take(64)
                            val title = redact(row.optString("title"))
                            val description = redact(row.optString("description"))
                            val label = row.optString("name", "Stream")
                            val quality = StremioStreamListPolicy.quality(label, title, description)
                            StremioSource(label, title.take(650), url, hash,
                                row.optInt("fileIdx", -1).coerceAtLeast(-1), hints, kind, quality, addon.name,
                                row.optJSONArray("subtitles").orEmpty().objects().take(40).mapNotNull { sub ->
                                    val subUrl = sub.optString("url")
                                    if (!subUrl.startsWith("https://")) null
                                    else StremioSubtitle(subUrl, sub.optString("lang"))
                                }, StremioPlaybackPolicy.extractRequestHeaders(row))
                        }
                }
            }
        }.awaitAll()
        StremioStreamLoadReport(outcomes.flatMap { it.getOrDefault(emptyList()) },
            providers.size, outcomes.count { it.isFailure })
    }

    suspend fun sources(meta: StremioMeta, videoId: String): List<StremioSource> =
        sourcesReport(meta, videoId).streams

    suspend fun library(): List<StremioMeta> {
        require(loggedIn) { "Sign in first" }
        val result = api("datastoreMeta", JSONObject().put("collection", "libraryItem")
            .put("authKey", token)).optJSONArray("result").orEmpty()
        val ids = result.arrays().mapNotNull { it.optString(0).takeIf(String::isNotBlank) }.distinct()
        val items = mutableListOf<StremioMeta>()
        for (batch in ids.chunked(100)) {
            val json = api("datastoreGet", JSONObject().put("collection", "libraryItem")
                .put("ids", JSONArray(batch)).put("all", false).put("authKey", token))
                .optJSONArray("result").orEmpty()
            json.forEachObject { entry ->
                if (entry.optBoolean("removed") || entry.optBoolean("temp") ||
                    entry.optString("type") !in listOf("movie", "series")) return@forEachObject
                val meta = parseMeta(entry, "", entry.optString("type"))
                val id = entry.optString("_id", meta.id)
                if (id.isNotEmpty()) items += meta.copy(id = id)
            }
        }
        return items
    }
    suspend fun setLibraryMembership(meta: StremioMeta, add: Boolean) {
        require(loggedIn && meta.id.isNotBlank() && meta.type in listOf("movie", "series"))
        val fetched = api("datastoreGet", JSONObject().put("collection", "libraryItem")
            .put("ids", JSONArray().put(meta.id)).put("all", false).put("authKey", token))
            .optJSONArray("result").orEmpty()
        val now = java.time.Instant.now().toString()
        val existing = fetched.objects().firstOrNull { it.optString("_id", it.optString("id")) == meta.id }
        val row = existing ?: JSONObject().apply {
            put("_id", meta.id); put("type", meta.type); put("name", meta.name)
            put("poster", meta.poster); put("posterShape", "poster")
            put("_ctime", now); put("state", JSONObject()); put("behaviorHints", JSONObject())
        }
        row.put("_id", meta.id).put("type", meta.type).put("removed", !add)
            .put("temp", false).put("_mtime", now)
        api("datastorePut", JSONObject().put("collection", "libraryItem")
            .put("changes", JSONArray().put(row)).put("authKey", token))
    }

    /** Official Stremio datastore watched field, including temporary and removed library rows.
     * Read-only import; does not upload anything to the account. */
    suspend fun accountWatchedRaw(): Map<String, String> {
        require(loggedIn) { "Sign in to Stremio first" }
        val owner = token
        val ids = api("datastoreMeta", JSONObject().put("collection", "libraryItem")
            .put("authKey", owner)).optJSONArray("result").orEmpty()
            .arrays().mapNotNull { it.optString(0).takeIf(String::isNotBlank) }.distinct()
        val result = linkedMapOf<String, String>()
        for (batch in ids.chunked(100)) {
            check(token == owner && loggedIn) { "Account changed during history import" }
            val rows = api("datastoreGet", JSONObject().put("collection", "libraryItem")
                .put("ids", JSONArray(batch)).put("all", false).put("authKey", owner))
                .optJSONArray("result").orEmpty()
            rows.forEachObject { row ->
                val id = row.optString("_id", row.optString("id"))
                if (id.isNotBlank() && row.optString("type") == "series") {
                    val state = row.optJSONObject("state")
                    result[id] = state?.optString("watched")?.takeUnless { it == "null" }.orEmpty()
                }
            }
        }
        check(token == owner && loggedIn) { "Account changed during history import" }
        return result
    }

    /** Write only explicitly queued episode flags, preserving every unrelated Stremio field.
     * The previous encoded bitfield is never discarded if it cannot be interpreted safely. */
    internal suspend fun putAccountWatchedChanges(meta: StremioMeta,
        changes: List<StremioPendingWatch>): String {
        require(loggedIn && meta.type == "series" && meta.id.isNotBlank())
        require(changes.isNotEmpty() && changes.all { it.seriesId == meta.id })
        val owner = token
        val orderedIds = StremioWatchedCodec.orderedVideos(meta.videos).map { it.id }
        check(orderedIds.isNotEmpty() && orderedIds.containsAll(changes.map { it.videoId })) {
            "Complete matching episode metadata required before watched sync"
        }
        val current = api("datastoreGet", JSONObject().put("collection", "libraryItem")
            .put("ids", JSONArray().put(meta.id)).put("all", false).put("authKey", owner))
            .optJSONArray("result").orEmpty().objects()
            .firstOrNull { it.optString("_id", it.optString("id")) == meta.id }
        check(token == owner && loggedIn) { "Account changed during history sync" }
        val row = if (current != null) JSONObject(current.toString()) else JSONObject().apply {
            put("_id", meta.id); put("type", "series"); put("name", meta.name)
            put("poster", meta.poster); put("posterShape", "poster")
            put("_ctime", java.time.Instant.now().toString())
            put("removed", true); put("temp", true)
            put("state", JSONObject()); put("behaviorHints", JSONObject())
        }
        val state = row.optJSONObject("state") ?: JSONObject()
        val original = state.optString("watched").takeUnless { it == "null" }.orEmpty()
        val watched = StremioWatchedCodec.decode(original, orderedIds)
            ?: throw IllegalStateException("Stremio watched data uses an unknown episode order; no changes uploaded")
        val amended = watched.toMutableSet()
        changes.forEach { change ->
            if (change.watched) amended.add(change.videoId) else amended.remove(change.videoId)
        }
        val encoded = StremioWatchedCodec.encode(orderedIds, amended)
        state.put("watched", encoded)
        row.put("state", state).put("_mtime", java.time.Instant.now().toString())
        check(token == owner && loggedIn) { "Account changed before watched sync" }
        api("datastorePut", JSONObject().put("collection", "libraryItem")
            .put("changes", JSONArray().put(row)).put("authKey", owner))
        check(token == owner && loggedIn) { "Account changed during watched sync" }
        return encoded
    }

    private fun supports(addon: StremioAddon, resource: String, type: String, id: String): Boolean {
        for (entry in addon.resources.items()) {
            val rule = entry as? JSONObject
            val name = rule?.optString("name") ?: (entry as? String ?: "")
            if (name != resource) continue
            val types = rule?.optJSONArray("types").strings().ifEmpty { addon.types }
            if (types.isNotEmpty() && type !in types) continue
            val prefixes = rule?.optJSONArray("idPrefixes").strings().ifEmpty {
                if (resource == "catalog") emptyList() else addon.prefixes
            }
            if (prefixes.isNotEmpty() && prefixes.none { id.startsWith(it) }) continue
            return true
        }
        return false
    }
    private suspend fun resource(addon: StremioAddon, res: String, type: String, id: String, extra: String = ""): JSONObject {
        val split = addon.id.lastIndexOf("manifest.json")
        require(split >= 0)
        val tail = addon.id.substring(split + "manifest.json".length)
        val route = "${encode(res)}/${encode(type)}/${encode(id)}" +
            if (extra.isBlank()) ".json" else "/$extra.json"
        return request(addon.id.substring(0, split) + route + tail, null)
    }
    private suspend fun api(name: String, payload: JSONObject): JSONObject {
        val response = request("https://api.strem.io/api/$name", payload)
        if (response.has("error") && !response.isNull("error"))
            throw IllegalStateException("Stremio rejected $name (check session and account)")
        if (!response.has("result")) throw IllegalStateException("Empty Stremio $name response")
        return response
    }
    private suspend fun request(address: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        // HTTP redirects are handled by the tested native Android/JVM transport,
        // not by following credential-bearing Stremio account POST requests.
        // Never include configured provider URLs in error text or diagnostics.
        val response = StremioJsonHttp.read(address, body?.toString())
        try { JSONObject(response) }
        catch (_: Exception) { throw IllegalStateException("Provider returned invalid JSON") }
    }
    private fun parseMeta(row: JSONObject, addon: String, type: String): StremioMeta {
        val videos = row.optJSONArray("videos").orEmpty().objects().take(600).map { v ->
            val number = v.optInt("episode")
            val season = v.optInt("season")
            val suppliedTitle = v.optString("title").ifBlank { v.optString("name") }
            StremioVideo(
                id = v.optString("id"),
                title = suppliedTitle.ifBlank { "S$season E$number" },
                season = season, episode = number,
                overview = v.optString("overview").ifBlank { v.optString("description") }.take(1800),
                thumbnail = v.optString("thumbnail").ifBlank { v.optString("image") }.take(2048),
                released = v.optString("released").take(64)
            )
        }.filter { it.id.isNotBlank() }
        return StremioMeta(row.optString("id", row.optString("_id")), row.optString("type", type),
            row.optString("name", row.optString("title")), row.optString("poster"),
            row.optString("description").ifBlank { row.optString("overview") },
            row.optString("year", row.optString("releaseInfo")), addon, videos,
            row.optString("background"))
    }
    private fun redact(input: String): String = input.replace(Regex("(?i)(?:https?://|magnet:\\?)[^\\s]+"), "[link hidden]")
    private fun encode(input: String): String = java.net.URLEncoder.encode(input, "UTF-8").replace("+", "%20")
}

internal fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()
internal fun JSONArray.items(): List<Any> = (0 until length()).mapNotNull { opt(it) }
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONArray.arrays(): List<JSONArray> = (0 until length()).mapNotNull { optJSONArray(it) }
internal fun JSONArray?.strings(): List<String> = this?.let { arr -> (0 until arr.length()).mapNotNull {
    arr.optString(it).takeIf(String::isNotBlank)
} } ?: emptyList()
internal inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) { objects().forEach(block) }
