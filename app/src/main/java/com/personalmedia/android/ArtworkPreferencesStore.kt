package com.personalmedia.android

import android.content.Context

internal data class ArtworkPreferences(
    val provider: String = "auto", val language: String = "en-US",
    val fetchPosters: Boolean = true, val fetchBackdrops: Boolean = true,
    val automatic: Boolean = false, val batchSize: Int = 25
)

internal class ArtworkPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "personalmedia_artwork_preferences", Context.MODE_PRIVATE)
    fun read(): ArtworkPreferences = ArtworkPreferences(
        provider = prefs.getString("provider", "auto")?.takeIf { it in listOf("auto", "tmdb", "omdb", "free") } ?: "auto",
        language = prefs.getString("language", "en-US")?.takeIf { it.matches(Regex("[a-z]{2}(?:-[A-Z]{2})?")) } ?: "en-US",
        fetchPosters = prefs.getBoolean("posters", true),
        fetchBackdrops = prefs.getBoolean("backdrops", true),
        automatic = prefs.getBoolean("automatic", false),
        batchSize = if (prefs.getInt("batch_size", 25) == 25) 25 else 2000)
    fun save(value: ArtworkPreferences): Boolean = prefs.edit()
        .putString("provider", value.provider.takeIf { it in listOf("auto", "tmdb", "omdb", "free") } ?: "auto")
        .putString("language", value.language.takeIf { it.matches(Regex("[a-z]{2}(?:-[A-Z]{2})?")) } ?: "en-US")
        .putBoolean("posters", value.fetchPosters)
        .putBoolean("backdrops", value.fetchBackdrops)
        .putBoolean("automatic", value.automatic)
        .putInt("batch_size", if (value.batchSize == 25) 25 else 2000)
        .commit()
}
