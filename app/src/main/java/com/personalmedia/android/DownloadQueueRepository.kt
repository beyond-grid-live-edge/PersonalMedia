package com.personalmedia.android

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** URLs, tracker addresses, signed query tokens and provider headers are stored only
 * in encrypted app-private preferences, never in a Logcat message or backup. */
internal data class DownloadQueueItem(
    val id: String, val title: String, val filename: String, val kind: String,
    val sourceUrl: String, val hash: String, val fileIndex: Int, val trackers: List<String>,
    val requestHeaders: Map<String, String>, val treeUri: String, val destinationUri: String = "",
    val state: String = DownloadQueuePolicy.QUEUED, val done: Long = 0, val total: Long = 0,
    val info: String = "Waiting", val etag: String = "", val lastModified: String = "",
    val createdMs: Long = System.currentTimeMillis(),
    val speedBps: Long = 0L
)

internal object DownloadQueueRepository {
    private val sync = Any()
    private const val FILE = "pm_str6_encrypted_download_queue"
    private const val DATA = "queue_v1"
    private const val FOLDER = "folder_v1"
    private var context: Context? = null
    private var ready = false
    private var preferences: android.content.SharedPreferences? = null
    private val _rows = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
    val rows: StateFlow<List<DownloadQueueItem>> = _rows
    private val _folder = MutableStateFlow("")
    val folder: StateFlow<String> = _folder

    fun init(ctx: Context) = synchronized(sync) {
        if (ready) return@synchronized
        val app = ctx.applicationContext
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(app, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        preferences = prefs
        context = app
        val saved = prefs.getString(DATA, "[]").orEmpty()
        val decoded = runCatching {
            val a = JSONArray(saved)
            (0 until minOf(a.length(), 200)).mapNotNull { i ->
                runCatching { fromJson(a.getJSONObject(i)) }.getOrNull()
            }
        }.getOrElse { throw IllegalStateException("Encrypted download queue is unreadable; no records were overwritten") }
        // Process death may interrupt a transfer. Never present it as completed,
        // or silently resume dataSync work from a boot receiver.
        _rows.value = decoded.map { item ->
            if (item.state == DownloadQueuePolicy.RUNNING)
                item.copy(state = DownloadQueuePolicy.PAUSED, speedBps = 0L,
                    info = "Interrupted; select Resume to continue")
            else item.copy(speedBps = 0L)
        }
        _folder.value = prefs.getString(FOLDER, "").orEmpty()
        ready = true
        saveLocked()
    }

    fun chosenFolder(ctx: Context): String { init(ctx); return _folder.value }
    fun setFolder(ctx: Context, uri: Uri) = synchronized(sync) {
        init(ctx)
        val permission = ctx.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        require(permission) { "Android has not granted persistent write access to this folder" }
        _folder.value = uri.toString()
        check(preferences!!.edit().putString(FOLDER, uri.toString()).commit()) {
            "Could not persist the downloads location"
        }
    }
    fun enqueue(ctx: Context, source: StremioSource, title: String, suggestedName: String): DownloadQueueItem = synchronized(sync) {
        init(ctx)
        val tree = _folder.value
        require(tree.isNotBlank()) { "Choose a downloads folder first" }
        require(source.kind in setOf("Torrent", "Direct HTTP")) { "Unsupported download source" }
        if (source.kind == "Direct HTTP") require(StremioPlaybackPolicy.safeTransport(source.url, source.requestHeaders)) {
            "Unsupported media address or provider headers"
        } else require(source.hash.matches(Regex("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}"))) {
            "Invalid torrent identifier"
        }
        check(_rows.value.size < 200) { "Queue full: remove old items before adding new downloads" }
        val id = UUID.randomUUID().toString()
        val filename = suggestedName.substringBeforeLast('.', suggestedName).take(110) + "-" + id.take(6) +
            if ('.' in suggestedName) "." + suggestedName.substringAfterLast('.').take(7) else ".mkv"
        val item = DownloadQueueItem(id, title.take(130), filename, source.kind,
            source.url, source.hash, source.fileIndex, source.trackers, source.requestHeaders, treeUri = tree)
        _rows.value = _rows.value + item
        saveLocked()
        item
    }
    fun find(ctx: Context, id: String): DownloadQueueItem? { init(ctx); return _rows.value.firstOrNull { it.id == id } }
    fun next(ctx: Context): DownloadQueueItem? { init(ctx); return _rows.value.firstOrNull { it.state == DownloadQueuePolicy.QUEUED } }
    fun update(ctx: Context, id: String, block: (DownloadQueueItem) -> DownloadQueueItem): DownloadQueueItem? = synchronized(sync) {
        init(ctx)
        val previous = _rows.value
        val found = previous.firstOrNull { it.id == id } ?: return@synchronized null
        val changed = block(found)
        _rows.value = previous.map { if (it.id == id) changed else it }
        saveLocked()
        changed
    }
    fun remove(ctx: Context, id: String): Boolean = synchronized(sync) {
        init(ctx)
        val before = _rows.value
        val old = before.firstOrNull { it.id == id } ?: return@synchronized false
        if (!DownloadQueuePolicy.canRemove(old.state)) return@synchronized false
        _rows.value = before.filterNot { it.id == id }
        saveLocked()
        true
    }
    private fun saveLocked() {
        val array = JSONArray()
        _rows.value.forEach { array.put(toJson(it)) }
        check(preferences!!.edit().putString(DATA, array.toString()).commit()) {
            "Download progress could not be saved securely"
        }
    }
    private fun toJson(x: DownloadQueueItem): JSONObject = JSONObject().apply {
        put("id", x.id); put("title", x.title); put("filename", x.filename); put("kind", x.kind)
        put("url", x.sourceUrl); put("hash", x.hash); put("fileIndex", x.fileIndex)
        put("trackers", JSONArray(x.trackers)); put("headers", JSONObject(x.requestHeaders))
        put("tree", x.treeUri); put("destination", x.destinationUri); put("state", x.state)
        put("done", x.done); put("total", x.total); put("info", x.info)
        put("etag", x.etag); put("modified", x.lastModified); put("created", x.createdMs)
    }
    private fun fromJson(o: JSONObject): DownloadQueueItem {
        val t = o.optJSONArray("trackers") ?: JSONArray()
        val h = o.optJSONObject("headers") ?: JSONObject()
        return DownloadQueueItem(o.getString("id"), o.optString("title"), o.optString("filename"),
            o.optString("kind"), o.optString("url"), o.optString("hash"), o.optInt("fileIndex", -1),
            (0 until t.length()).map { t.optString(it) }, h.keys().asSequence().associateWith { h.optString(it) },
            o.optString("tree"), o.optString("destination"), o.optString("state", DownloadQueuePolicy.PAUSED),
            o.optLong("done"), o.optLong("total"), o.optString("info"), o.optString("etag"),
            o.optString("modified"), o.optLong("created"))
    }
}
