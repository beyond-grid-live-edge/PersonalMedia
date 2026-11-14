package com.personalmedia.android

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Siano C++ localhost MPEG-TS → native libmpv EGL surface.
 * One worker queue serializes start/stop on rapid channel changes. JNI owns the
 * separate "livetv" session so a late cleanup cannot stop fullscreen MPV.
 */
@Composable
fun T405MpvView(url: String, onStarted: () -> Unit, onFailure: (String) -> Unit,
                modifier: Modifier = Modifier) {
    val startedCallback by rememberUpdatedState(onStarted)
    val failureCallback by rememberUpdatedState(onFailure)
    val scope = rememberCoroutineScope()
    var surface by remember { mutableStateOf<Surface?>(null) }
    val serial = remember { Executors.newSingleThreadExecutor { r -> Thread(r, "T405-MPV").apply { isDaemon = true } } }
    val epoch = remember { AtomicLong(0) }
    LaunchedEffect(surface, url) {
        val token = epoch.incrementAndGet()
        val current = surface
        serial.execute {
            if (NativeBridge.loaded) NativeBridge.mpvStopForT405()
            if (url.isNotEmpty() && current != null && current.isValid && epoch.get() == token) {
                val result = runCatching { NativeBridge.mpvStartForT405(current, url) }
                if (epoch.get() == token) {
                    scope.launch {
                        if (result.getOrDefault(false)) startedCallback()
                        else failureCallback(result.exceptionOrNull()?.message ?: runCatching {
                            NativeBridge.mpvLastError().ifEmpty { "native MPV EGL initialization failed" }
                        }.getOrDefault("native MPV unavailable"))
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            epoch.incrementAndGet()
            serial.execute { if (NativeBridge.loaded) NativeBridge.mpvStopForT405() }
            serial.shutdown()
        }
    }
    Box(modifier) {
        AndroidView(factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) { surface = holder.surface }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        if (NativeBridge.loaded && width > 0 && height > 0) NativeBridge.mpvRequestRedrawForT405()
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) { surface = null }
                })
            }
        }, modifier = Modifier.matchParentSize())
    }
}
