package com.personalmedia.android

import android.net.Uri
import android.app.Activity
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private data class Media3TrackOption(
    val group: Tracks.Group, val index: Int, val language: String,
    val title: String, val selected: Boolean
)

private fun media3Options(player: ExoPlayer, type: Int): List<Media3TrackOption> = buildList {
    player.currentTracks.groups.filter { it.type == type }.forEach { group ->
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            add(Media3TrackOption(group, index, format.language.orEmpty(),
                format.label.orEmpty(), group.isTrackSelected(index)))
        }
    }
}

/** Native Media3 player for local SAF files and direct URLs.
 * Unlike transient player-session track IDs, saved choices use semantic track metadata.
 */
@Composable
fun PlayerScreen(uri: String, subtitleTracks: List<StremioSubtitle> = emptyList(), initialMs: Long = 0,
    preferences: PlaybackPreferences = PlaybackPreferences(), mediaKey: String? = null,
    onProgress: (Long) -> Unit = {}, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val memoryStore = remember(context) { MediaTrackMemoryStore(context.applicationContext) }
    var rememberedTracks by remember(mediaKey) { mutableStateOf(memoryStore.read(mediaKey)) }
    val player = remember(uri, subtitleTracks, preferences.audioLanguage,
        preferences.subtitleLanguage, preferences.subtitlesEnabled) {
        ExoPlayer.Builder(context).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(preferences.audioLanguage.takeUnless { it == "auto" })
                .setPreferredTextLanguage(preferences.subtitleLanguage.takeUnless { it == "auto" })
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !preferences.subtitlesEnabled)
                .build()
            val builder = MediaItem.Builder().setUri(Uri.parse(uri))
            if (uri.contains("/live.ts") || uri.endsWith(".ts")) builder.setMimeType(MimeTypes.VIDEO_MP2T)
            if (subtitleTracks.isNotEmpty()) builder.setSubtitleConfigurations(
                subtitleTracks.filter { it.url.startsWith("https://") }.map {
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                        .setMimeType(MimeTypes.TEXT_VTT).setLanguage(it.language)
                        .setSelectionFlags(0).build()
                })
            setMediaItem(builder.build())
            prepare()
            if (initialMs > 0) seekTo(initialMs)
            playWhenReady = true
        }
    }
    var mediaPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var audioMenuOpen by remember { mutableStateOf(false) }
    var subtitleMenuOpen by remember { mutableStateOf(false) }
    var tracksRevision by remember(player) { mutableIntStateOf(0) }
    var audioApplied by remember(player, mediaKey) { mutableStateOf(false) }
    var subtitleApplied by remember(player, mediaKey) { mutableStateOf(false) }
    var initialSelectionReady by remember(player) { mutableStateOf(false) }
    // Suppress callbacks from our own settings changes; the built-in Media3 selector
    // remains usable and its user-initiated choices are also remembered.
    val ignoredParameters = remember(player) { mutableListOf<TrackSelectionParameters>() }
    var priorParameters by remember(player) { mutableStateOf(player.trackSelectionParameters) }

    fun setParameters(parameters: TrackSelectionParameters) {
        if (parameters != player.trackSelectionParameters) {
            ignoredParameters.add(parameters)
            if (ignoredParameters.size > 12) ignoredParameters.removeAt(0)
        }
        priorParameters = parameters
        player.trackSelectionParameters = parameters
    }
    fun applyChoice(type: Int, choice: RememberedTrack?, options: List<Media3TrackOption>): Boolean {
        if (choice == null) return true
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        when (choice.mode) {
            "off" -> if (type == C.TRACK_TYPE_TEXT) builder.setTrackTypeDisabled(type, true)
                else return true
            "auto" -> {
                builder.setTrackTypeDisabled(type, false)
                if (type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(null)
                else builder.setPreferredTextLanguage(null)
            }
            "track" -> {
                val found = TrackMemoryPolicy.find(choice, options.map { AvailableTrack(it.language, it.title) })
                    ?: return false
                val chosen = options[found]
                builder.setTrackTypeDisabled(type, false)
                builder.setOverrideForType(TrackSelectionOverride(chosen.group.mediaTrackGroup, listOf(chosen.index)))
            }
        }
        setParameters(builder.build())
        return true
    }
    fun applyGlobal(type: Int) {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(
            preferences.audioLanguage.takeUnless { it == "auto" })
        else {
            builder.setPreferredTextLanguage(preferences.subtitleLanguage.takeUnless { it == "auto" })
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !preferences.subtitlesEnabled)
        }
        setParameters(builder.build())
    }
    fun persistAudio(choice: RememberedTrack?) {
        memoryStore.saveAudio(mediaKey, choice)
        rememberedTracks = rememberedTracks.copy(audio = choice)
        audioApplied = false
    }
    fun persistSubtitle(choice: RememberedTrack?) {
        memoryStore.saveSubtitle(mediaKey, choice)
        rememberedTracks = rememberedTracks.copy(subtitle = choice)
        subtitleApplied = false
    }
    DisposableEffect(player, mediaKey) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { mediaPlaying = isPlaying }
            override fun onTracksChanged(tracks: Tracks) { tracksRevision++ }
            override fun onPlaybackStateChanged(playbackState: Int) { tracksRevision++ }
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                val previous = priorParameters
                priorParameters = parameters
                if (ignoredParameters.remove(parameters)) return
                if (!initialSelectionReady || mediaKey == null) return
                fun overrideFor(type: Int, p: TrackSelectionParameters) =
                    p.overrides.values.filter { it.type == type }
                val audioChanged = overrideFor(C.TRACK_TYPE_AUDIO, previous) !=
                    overrideFor(C.TRACK_TYPE_AUDIO, parameters) ||
                    previous.preferredAudioLanguages != parameters.preferredAudioLanguages
                val subtitleChanged = overrideFor(C.TRACK_TYPE_TEXT, previous) !=
                    overrideFor(C.TRACK_TYPE_TEXT, parameters) ||
                    previous.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) !=
                        parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
                    previous.preferredTextLanguages != parameters.preferredTextLanguages
                fun selectedChoice(type: Int): RememberedTrack {
                    if (type == C.TRACK_TYPE_TEXT && parameters.disabledTrackTypes.contains(type))
                        return RememberedTrack("off")
                    val options = media3Options(player, type)
                    val selected = options.indexOfFirst { option ->
                        parameters.overrides[option.group.mediaTrackGroup]?.trackIndices?.contains(option.index) == true
                    }
                    return TrackMemoryPolicy.makeChoice(selected,
                        options.map { AvailableTrack(it.language, it.title) }) ?: RememberedTrack("auto")
                }
                if (audioChanged) persistAudio(selectedChoice(C.TRACK_TYPE_AUDIO))
                if (subtitleChanged) persistSubtitle(selectedChoice(C.TRACK_TYPE_TEXT))
            }
        }
        player.addListener(listener)
        tracksRevision++ // Read already-prepared player tracks even if event happened before listener registration.
        onDispose { player.removeListener(listener) }
    }
    val audioOptions = remember(player, tracksRevision) { media3Options(player, C.TRACK_TYPE_AUDIO) }
    val subtitleOptions = remember(player, tracksRevision) { media3Options(player, C.TRACK_TYPE_TEXT) }
    // Initial restore is never persisted as a manual selection. Missing tracks keep
    // global defaults, rather than choosing an unrelated dub or commentary track.
    LaunchedEffect(player, tracksRevision, rememberedTracks, mediaKey) {
        if (player.playbackState == Player.STATE_IDLE) return@LaunchedEffect
        val metadataComplete = player.playbackState == Player.STATE_READY ||
            player.playbackState == Player.STATE_ENDED
        if (!audioApplied && (audioOptions.isNotEmpty() || metadataComplete ||
                rememberedTracks.audio?.mode != "track")) {
            val choice = rememberedTracks.audio
            if (choice != null && !applyChoice(C.TRACK_TYPE_AUDIO, choice, audioOptions)) applyGlobal(C.TRACK_TYPE_AUDIO)
            audioApplied = true
        }
        if (!subtitleApplied && (subtitleOptions.isNotEmpty() || metadataComplete ||
                rememberedTracks.subtitle?.mode != "track")) {
            val choice = rememberedTracks.subtitle
            if (choice != null && !applyChoice(C.TRACK_TYPE_TEXT, choice, subtitleOptions)) applyGlobal(C.TRACK_TYPE_TEXT)
            subtitleApplied = true
        }
        if (audioApplied && subtitleApplied) initialSelectionReady = true
    }
    DisposableEffect(activity, preferences.keepScreenAwake, mediaPlaying) {
        if (preferences.keepScreenAwake && mediaPlaying)
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(player) {
        onDispose { onProgress(player.currentPosition.coerceAtLeast(0)); player.release() }
    }
    LaunchedEffect(player) {
        while (true) { delay(5000); onProgress(player.currentPosition.coerceAtLeast(0)) }
    }
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onClose) { Text("← Back", color = Color.White) }
            Row {
                Box {
                    TextButton(onClick = { audioMenuOpen = true; subtitleMenuOpen = false }) {
                        Text("Audio ▾", color = Color.White)
                    }
                    DropdownMenu(expanded = audioMenuOpen, onDismissRequest = { audioMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Use global audio default") }, onClick = {
                            persistAudio(null); applyGlobal(C.TRACK_TYPE_AUDIO); audioApplied = true
                            audioMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("Auto for this title") }, onClick = {
                            val choice = RememberedTrack("auto")
                            applyChoice(C.TRACK_TYPE_AUDIO, choice, audioOptions)
                            persistAudio(choice); audioApplied = true; audioMenuOpen = false
                        })
                        audioOptions.forEachIndexed { idx, option ->
                            DropdownMenuItem(text = { Text((if (option.selected) "✓ " else "") +
                                listOf(option.language, option.title).filter { it.isNotBlank() }
                                    .joinToString(" · ").ifBlank { "Audio ${idx + 1}" }) }, onClick = {
                                val choice = TrackMemoryPolicy.makeChoice(idx,
                                    audioOptions.map { AvailableTrack(it.language, it.title) })
                                if (choice != null && applyChoice(C.TRACK_TYPE_AUDIO, choice, audioOptions)) {
                                    persistAudio(choice); audioApplied = true
                                }
                                audioMenuOpen = false
                            })
                        }
                    }
                }
                Box {
                    TextButton(onClick = { subtitleMenuOpen = true; audioMenuOpen = false }) {
                        Text("CC ▾", color = Color.White)
                    }
                    DropdownMenu(expanded = subtitleMenuOpen, onDismissRequest = { subtitleMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Use global subtitle default") }, onClick = {
                            persistSubtitle(null); applyGlobal(C.TRACK_TYPE_TEXT); subtitleApplied = true
                            subtitleMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("Off for this title") }, onClick = {
                            val choice = RememberedTrack("off")
                            applyChoice(C.TRACK_TYPE_TEXT, choice, subtitleOptions)
                            persistSubtitle(choice); subtitleApplied = true; subtitleMenuOpen = false
                        })
                        DropdownMenuItem(text = { Text("Auto for this title") }, onClick = {
                            val choice = RememberedTrack("auto")
                            applyChoice(C.TRACK_TYPE_TEXT, choice, subtitleOptions)
                            persistSubtitle(choice); subtitleApplied = true; subtitleMenuOpen = false
                        })
                        subtitleOptions.forEachIndexed { idx, option ->
                            DropdownMenuItem(text = { Text((if (option.selected) "✓ " else "") +
                                listOf(option.language, option.title).filter { it.isNotBlank() }
                                    .joinToString(" · ").ifBlank { "Subtitle ${idx + 1}" }) }, onClick = {
                                val choice = TrackMemoryPolicy.makeChoice(idx,
                                    subtitleOptions.map { AvailableTrack(it.language, it.title) })
                                if (choice != null && applyChoice(C.TRACK_TYPE_TEXT, choice, subtitleOptions)) {
                                    persistSubtitle(choice); subtitleApplied = true
                                }
                                subtitleMenuOpen = false
                            })
                        }
                    }
                }
            }
        }
        AndroidView(factory = {
            PlayerView(it).apply {
                this.player = player
                useController = true
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
