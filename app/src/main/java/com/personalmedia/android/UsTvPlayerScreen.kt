package com.personalmedia.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Source-aware live US internet television player.
 * Unlike VOD's MPV Range bridge, Media3 handles HLS master/variant playlists,
 * segment requests, MPEG-DASH, live timing and network buffering itself.
 * Only stream addresses obtained from the selected Global Free TV channel page
 * can enter this player; XMLTV supplies programme text, not video links. */
@Composable
internal fun UsTvPlayerScreen(
    channel: UsChannel,
    sources: List<UsSource>,
    initialSource: Int,
    allowHttpSource: Boolean,
    onWorkingSource: (Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val allowedOrder = remember(channel.id, sources, initialSource, allowHttpSource) {
        UsTvPlaybackPolicy.order(sources.map { it.number to it.url }, initialSource, allowHttpSource)
    }
    var sourceNumber by remember(channel.id) { mutableIntStateOf(allowedOrder.firstOrNull() ?: -1) }
    var attempted by remember(channel.id) {
        mutableStateOf(allowedOrder.firstOrNull()?.let { setOf(it) } ?: emptySet())
    }
    var playbackAttempt by remember(channel.id) { mutableIntStateOf(0) }
    var ready by remember(channel.id) { mutableStateOf(false) }
    var mediaPlaying by remember(channel.id) { mutableStateOf(false) }
    var errorText by remember(channel.id) { mutableStateOf("") }
    var notice by remember(channel.id) { mutableStateOf("Opening live channel…") }
    val latestWorking by rememberUpdatedState(onWorkingSource)
    val latestClose by rememberUpdatedState(onClose)
    val source = sources.firstOrNull { it.number == sourceNumber &&
        UsTvPlaybackPolicy.allowed(it.url, allowHttpSource) }

    fun switchTo(number: Int, note: String) {
        if (number !in allowedOrder) return
        attempted = attempted + number
        errorText = ""
        ready = false
        notice = note
        sourceNumber = number
        playbackAttempt++ // Force a new player even when retrying the same source.
    }
    fun failed(reason: String) {
        if (errorText.isNotEmpty()) return // Avoid repeated callbacks from a disposed player.
        val next = UsTvPlaybackPolicy.next(allowedOrder, attempted)
        if (next != null) {
            switchTo(next, "Source $sourceNumber unavailable ($reason). Trying Source $next…")
        } else {
            ready = false
            errorText = "Unable to play this channel ($reason). No more permitted sources are available."
            notice = "Return to the channel list or retry a source. Some providers restrict regions or devices."
        }
    }

    BackHandler { latestClose() }
    val keepAwake = remember { PlaybackPreferencesStore(context).read().keepScreenAwake }
    DisposableEffect(activity, keepAwake, mediaPlaying) {
        val window = activity?.window
        if (keepAwake && mediaPlaying) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val player = remember(channel.id, source?.url, playbackAttempt) {
        source?.let { item ->
            ExoPlayer.Builder(context).build().apply {
                val format = UsTvPlaybackPolicy.format(item.url)
                val media = MediaItem.Builder().setUri(Uri.parse(item.url))
                when (format) {
                    UsTvPlaybackPolicy.Format.HLS -> media.setMimeType(MimeTypes.APPLICATION_M3U8)
                    UsTvPlaybackPolicy.Format.DASH -> media.setMimeType(MimeTypes.APPLICATION_MPD)
                    UsTvPlaybackPolicy.Format.TRANSPORT_STREAM -> media.setMimeType(MimeTypes.VIDEO_MP2T)
                    else -> Unit
                }
                setMediaItem(media.build())
                playWhenReady = true
                prepare()
            }
        }
    }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { mediaPlaying = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && source?.number == sourceNumber) {
                    ready = true
                    errorText = ""
                    notice = "Live · Source $sourceNumber"
                    source?.number?.let { latestWorking(it) }
                } else if (state == Player.STATE_ENDED && source?.number == sourceNumber) {
                    failed("source ended")
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                // Never display signed source URLs or exception strings.
                if (source?.number == sourceNumber) failed(error.errorCodeName)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        if (player == null) return@DisposableEffect onDispose { }
        var resumeAfterBackground = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                resumeAfterBackground = player.playWhenReady
                player.playWhenReady = false
            } else if (event == Lifecycle.Event.ON_START && resumeAfterBackground) {
                player.playWhenReady = true
                resumeAfterBackground = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Some providers hang during playlist/segment resolution without reporting an error.
    // Give each source a bounded opportunity, then try the next exposed source.
    LaunchedEffect(player, sourceNumber) {
        if (player != null) {
            delay(30_000)
            if (!ready && errorText.isEmpty()) failed("startup timed out")
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (player != null) {
            AndroidView(factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            }, update = { view -> if (view.player !== player) view.player = player },
                modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(Color(0xD208111F)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = latestClose) { Text("← US channels") }
                Text(channel.name, color = Color.White, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall)
                Text("US Live TV · Media3", color = Color(0xFF8BC5FF),
                    style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                allowedOrder.forEach { number ->
                    FilterChip(selected = number == sourceNumber,
                        onClick = {
                            attempted = setOf(number)
                            switchTo(number, "Opening Source $number…")
                            // Explicit Retry of same source must also reconstruct the player.
                        }, label = { Text("Source $number") })
                }
                val next = UsTvPlaybackPolicy.next(allowedOrder, attempted)
                if (next != null) TextButton(onClick = { switchTo(next, "Trying Source $next…") }) {
                    Text("Next source")
                }
                TextButton(onClick = {
                    attempted = setOf(sourceNumber)
                    switchTo(sourceNumber, "Retrying Source $sourceNumber…")
                }, enabled = source != null) { Text("Retry") }
            }
            if (errorText.isNotEmpty()) Text(errorText, color = Color(0xFFFFADA9),
                style = MaterialTheme.typography.bodySmall)
            else if (!ready) Text(notice, color = Color(0xFFFFD591),
                style = MaterialTheme.typography.bodySmall)
        }
        if (errorText.isNotEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No playable source is currently available", color = Color.White)
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channel.pageUrl)))
                }) { Text("Open on Global Free TV") }
                TextButton(onClick = latestClose) { Text("Return to US channels") }
            }
        }
        if (source == null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No permitted playable source for this channel.", color = Color.White)
                TextButton(onClick = latestClose) { Text("Return to US channels") }
            }
        }
    }
}
