package com.personalmedia.android

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** User-triggered metadata lookups. Keys are passed only to their respective HTTPS providers. */
internal class ArtworkLookupClient {
    private fun getJson(url: String, bearer: String = ""): JSONObject? {
        if (!url.startsWith("https://")) return null
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 9000
            connection.readTimeout = 9000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "PersonalMedia-Android/20.0 (https://github.com/beyond-grid-live-edge; user-requested artwork lookup)")
            if (bearer.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (connection.responseCode != 200) return null
            val maxBytes = 900_000
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) return null
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            return JSONObject(String(bytes, StandardCharsets.UTF_8))
        } finally { connection.disconnect() }
    }

    private fun url(host: String, path: String, values: Map<String, String>): String {
        val builder = Uri.Builder().scheme("https").authority(host).encodedPath(path)
        values.forEach { (k, v) -> if (v.isNotBlank()) builder.appendQueryParameter(k, v) }
        return builder.build().toString()
    }
    private fun valid(value: String, allowed: Set<String>): String {
        if (!ArtworkMatchPolicy.safeImage(value)) return ""
        val host = runCatching { URL(value).host.lowercase() }.getOrNull() ?: return ""
        return value.takeIf { host in allowed }.orEmpty()
    }
    private fun year(value: String) = ArtworkMatchPolicy.year(value)
    private fun genres(arr: JSONArray?): String = (0 until (arr?.length() ?: 0))
        .mapNotNull { arr?.optString(it)?.takeIf(String::isNotBlank) }.take(8).joinToString(", ")

    private fun tmdb(title: String, requestedYear: Int?, kind: String, lang: String,
                     key: String): ArtworkMatch? {
        val movie = kind == "movie"
        val bearer = if (key.startsWith("eyJ")) key else ""
        val params = linkedMapOf("query" to title, "language" to lang)
        if (requestedYear != null) params[if (movie) "year" else "first_air_date_year"] = requestedYear.toString()
        if (bearer.isEmpty()) params["api_key"] = key
        val reply = getJson(url("api.themoviedb.org", if (movie) "/3/search/movie" else "/3/search/tv", params), bearer)
            ?: return null
        val results = reply.optJSONArray("results") ?: return null
        for (i in 0 until minOf(results.length(), 10)) {
            val entry = results.optJSONObject(i) ?: continue
            val candidate = entry.optString(if (movie) "title" else "name")
            val foundYear = year(entry.optString(if (movie) "release_date" else "first_air_date"))
            if (!ArtworkMatchPolicy.matches(title, requestedYear, candidate, foundYear, kind)) continue
            fun path(field: String, width: String): String {
                val p = entry.optString(field)
                return if (p.startsWith("/") && !p.contains("//") && !p.contains(".."))
                    "https://image.tmdb.org/t/p/$width$p" else ""
            }
            val poster = path("poster_path", "w500")
            val backdrop = path("backdrop_path", "w1280")
            if (poster.isEmpty() && backdrop.isEmpty() && entry.optString("overview").isBlank()) continue
            return ArtworkMatch(candidate, foundYear, poster, backdrop,
                entry.optString("overview"), "", "TMDb")
        }
        return null
    }

    private fun omdb(title: String, requestedYear: Int?, kind: String, key: String): ArtworkMatch? {
        val params = linkedMapOf("apikey" to key, "t" to title,
            "type" to if (kind == "movie") "movie" else "series", "plot" to "full")
        if (requestedYear != null) params["y"] = requestedYear.toString()
        val result = getJson(url("www.omdbapi.com", "/", params)) ?: return null
        if (!result.optString("Response").equals("True", true)) return null
        val candidate = result.optString("Title")
        val yr = year(result.optString("Year"))
        if (!ArtworkMatchPolicy.matches(title, requestedYear, candidate, yr, kind)) return null
        return ArtworkMatch(candidate, yr, valid(result.optString("Poster"),
            setOf("m.media-amazon.com", "images-na.ssl-images-amazon.com", "image.tmdb.org")), "",
            result.optString("Plot").takeUnless { it == "N/A" }.orEmpty(),
            result.optString("Genre").takeUnless { it == "N/A" }.orEmpty(), "OMDb")
    }

    private fun tvmaze(title: String, requestedYear: Int?): ArtworkMatch? {
        val reply = getJson(url("api.tvmaze.com", "/singlesearch/shows", mapOf("q" to title)))
            ?: return null
        val candidate = reply.optString("name")
        val foundYear = year(reply.optString("premiered"))
        if (!ArtworkMatchPolicy.matches(title, requestedYear, candidate, foundYear, "tv")) return null
        val image = reply.optJSONObject("image")
        val poster = valid(image?.optString("original").orEmpty(), setOf("static.tvmaze.com"))
        val description = reply.optString("summary")
            .replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(1500)
        return ArtworkMatch(candidate, foundYear, poster, "", description,
            genres(reply.optJSONArray("genres")), "TVmaze")
    }

    /** Only film articles, and only when the article title and any known year agree. */
    private fun wikipedia(title: String, requestedYear: Int?): ArtworkMatch? {
        val query = if (requestedYear != null) "$title $requestedYear film" else "$title film"
        val reply = getJson(url("en.wikipedia.org", "/w/rest.php/v1/search/page",
            mapOf("q" to query, "limit" to "8"))) ?: return null
        val pages = reply.optJSONArray("pages") ?: return null
        for (i in 0 until pages.length()) {
            val page = pages.optJSONObject(i) ?: continue
            val name = page.optString("title")
            val description = page.optString("description")
            if (!name.contains("film", true) && !description.contains("film", true) &&
                !description.contains("movie", true)) continue
            val foundYear = year(name) ?: year(description)
            if (!ArtworkMatchPolicy.matches(title, requestedYear, name, foundYear, "movie")) continue
            val image = valid(page.optJSONObject("thumbnail")?.optString("url").orEmpty(),
                setOf("upload.wikimedia.org"))
            if (image.isEmpty()) continue
            return ArtworkMatch(name, foundYear, image, "", description, "", "Wikipedia / Wikimedia")
        }
        return null
    }

    suspend fun lookup(title: String, requestedYear: Int?, kind: String,
                       options: ArtworkPreferences, tmdbKey: String, omdbKey: String): ArtworkMatch? =
        withContext(Dispatchers.IO) {
            val steps = ArtworkMatchPolicy.providerOrder(options.provider,
                tmdbKey.isNotBlank(), omdbKey.isNotBlank(), kind)
            for (step in steps) {
                val result = runCatching {
                    when (step) {
                        "tmdb" -> tmdb(title, requestedYear, kind, options.language, tmdbKey)
                        "omdb" -> omdb(title, requestedYear, kind, omdbKey)
                        "tvmaze" -> tvmaze(title, requestedYear)
                        else -> wikipedia(title, requestedYear)
                    }
                }.getOrNull()
                if (result != null && ArtworkMatchPolicy.matches(title, requestedYear,
                        result.title, result.year, kind)) return@withContext result
            }
            null
        }
}
