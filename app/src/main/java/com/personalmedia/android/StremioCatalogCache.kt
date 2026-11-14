package com.personalmedia.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Account-scoped stale-while-revalidate catalogue cache. It never persists
 * configured add-on transport URLs, session keys, or playable stream URLs.
 * Everything stored (including poster URLs) is encrypted using Android Keystore.
 * Only first-page poster metadata is cached; details and streams remain live.
 */
internal class StremioCatalogCache(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        val key = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext, "personalmedia_stremio_catalog_cache", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun key(account: String, cat: StremioCatalog, genre: String): String {
        val input = listOf("v1", account.lowercase(), cat.addonId, cat.type, cat.id, genre).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    suspend fun load(account: String, cat: StremioCatalog, genre: String = ""): List<StremioMeta> =
        withContext(Dispatchers.IO) {
            if (account.isBlank()) return@withContext emptyList()
            runCatching {
                val raw = prefs.getString(key(account, cat, genre), null) ?: return@runCatching emptyList()
                val doc = JSONObject(raw)
                if (doc.optInt("v") != 1 ||
                    System.currentTimeMillis() - doc.optLong("at") > 7L * 24 * 60 * 60 * 1000) return@runCatching emptyList()
                val entries = doc.optJSONArray("metas") ?: return@runCatching emptyList()
                (0 until minOf(entries.length(), 60)).mapNotNull { n ->
                    val row = entries.optJSONObject(n) ?: return@mapNotNull null
                    val type = row.optString("type")
                    val id = row.optString("id")
                    if (id.isBlank() || type !in listOf("movie", "series")) return@mapNotNull null
                    StremioMeta(
                        id = id, type = type, name = row.optString("name"),
                        poster = row.optString("poster"), description = row.optString("description"),
                        year = row.optString("year"), background = row.optString("background")
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun save(account: String, cat: StremioCatalog, genre: String = "", titles: List<StremioMeta>): Unit =
        withContext(Dispatchers.IO) {
            if (account.isBlank() || titles.isEmpty()) return@withContext
            // Cache only public catalogue fields. No provider URL, auth key, stream or add-on ID.
            runCatching {
                val entries = JSONArray()
                StremioBrowsePolicy.uniqueTitles(titles).take(60).forEach { item ->
                    entries.put(JSONObject()
                        .put("id", item.id.take(300))
                        .put("type", item.type)
                        .put("name", item.name.take(180))
                        .put("poster", item.poster.take(1000))
                        .put("background", item.background.take(1000))
                        .put("description", item.description.take(500))
                        .put("year", item.year.take(40)))
                }
                prefs.edit().putString(key(account, cat, genre),
                    JSONObject().put("v", 1).put("at", System.currentTimeMillis())
                        .put("metas", entries).toString()).apply()
            }
            Unit
        }
}
