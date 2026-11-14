package com.personalmedia.android

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Real optional libmpv EGL surface frontend (never a WebView). Requires ABI-matched libmpv.so. */
@Composable
fun MpvPlayerScreen(uri: String, onFallback: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var paused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    DisposableEffect(uri) {
        onDispose { if (NativeBridge.loaded) NativeBridge.mpvStop() }
    }
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onClose) { Text("← Back") }
            Text("libmpv · EGL native", color = Color.LightGray)
            TextButton(onClick = { paused = !paused; NativeBridge.mpvPause(paused) }) {
                Text(if (paused) "Resume" else "Pause")
            }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        AndroidView(factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        scope.launch(Dispatchers.IO) {
                            val success = NativeBridge.mpvStart(holder.surface, uri)
                            if (!success) {
                                error = "MPV EGL renderer unavailable; switching to Android playback"
                                onFallback()
                            }
                        }
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) { NativeBridge.mpvStop() }
                })
            }
        }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
