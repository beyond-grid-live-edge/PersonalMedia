package com.personalmedia.android

import android.content.Context

internal class PlaybackPreferencesStore(context: Context) {
    private val store = context.applicationContext.getSharedPreferences(
        "personalmedia_playback_preferences", Context.MODE_PRIVATE)

    fun read(): PlaybackPreferences = PlaybackPreferences(
        keepScreenAwake = store.getBoolean("keep_screen_awake", true),
        audioLanguage = PlaybackPreferencesPolicy.language(store.getString("audio_language", "auto")),
        subtitleLanguage = PlaybackPreferencesPolicy.language(store.getString("subtitle_language", "auto")),
        subtitlesEnabled = store.getBoolean("show_subtitles", false),
        preferredQuality = PlaybackPreferencesPolicy.quality(store.getString("preferred_quality", "auto"))
    )

    fun save(value: PlaybackPreferences): Boolean = store.edit()
        .putBoolean("keep_screen_awake", value.keepScreenAwake)
        .putString("audio_language", PlaybackPreferencesPolicy.language(value.audioLanguage))
        .putString("subtitle_language", PlaybackPreferencesPolicy.language(value.subtitleLanguage))
        .putBoolean("show_subtitles", value.subtitlesEnabled)
        .putString("preferred_quality", PlaybackPreferencesPolicy.quality(value.preferredQuality))
        .commit()
}
