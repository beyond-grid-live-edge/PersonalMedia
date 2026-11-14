package com.personalmedia.android

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** STR-6 persistent download queue UI, embedded in the native Stremio Downloads tab.
 * This screen does not own transfer coroutines: closing it never cancels a job. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StremioDownloadQueueScreen(onChooseFolder: () -> Unit, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val uiScope = rememberCoroutineScope()
    remember(ctx) { DownloadQueueRepository.init(ctx); true }
    val tasks by DownloadQueueRepository.rows.collectAsState()
    val folder by DownloadQueueRepository.folder.collectAsState()
    val folderLabel = remember(folder) {
        if (folder.isEmpty()) "No downloads folder selected" else runCatching {
            DocumentFile.fromTreeUri(ctx, Uri.parse(folder))?.name ?: "Selected storage folder"
        }.getOrDefault("Selected storage folder")
    }
    var displayCount by remember { mutableIntStateOf(30) }
    var removeConfirmation by remember { mutableStateOf("") }
    var deleteConfirmation by remember { mutableStateOf("") }
    var noticeEnabled by remember { mutableStateOf(
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) }
    val askNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        noticeEnabled = allowed
        if (!allowed) onMessage("Notifications are disabled; download progress stays in this tab")
    }
    val blue = Color(0xFF79B9FF)
    val muted = Color(0xFFB6C9E7)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Persistent downloads", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Transfers continue while you leave this screen. Paused/interrupted tasks can resume after reopening the app.",
            color = muted, fontSize = 12.sp)
        Column(Modifier.fillMaxWidth().background(Color(0xFF121D2E), RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Download storage", color = Color.White, fontWeight = FontWeight.Bold)
            Text(folderLabel, color = blue, fontSize = 12.sp)
            Text("Choose a writable folder on the phone or an attached storage device. " +
                "Existing downloads keep their original folder when you change this default.",
                color = muted, fontSize = 11.sp)
            Button(onClick = onChooseFolder) { Text(if (folder.isEmpty()) "Choose downloads folder" else "Change default folder") }
            if (!noticeEnabled && Build.VERSION.SDK_INT >= 33)
                OutlinedButton(onClick = { askNotification.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Enable download notifications")
                }
            Text("${tasks.count { it.state == DownloadQueuePolicy.COMPLETED }} completed · " +
                "${tasks.count { it.state == DownloadQueuePolicy.RUNNING }} active · " +
                "${tasks.count { it.state == DownloadQueuePolicy.QUEUED }} queued · " +
                "${tasks.count { it.state == DownloadQueuePolicy.PAUSED }} paused",
                color = muted, fontSize = 11.sp)
            if (tasks.any { DownloadQueuePolicy.canPause(it.state) } ||
                tasks.any { DownloadQueuePolicy.canResume(it.state) }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tasks.any { DownloadQueuePolicy.canPause(it.state) })
                        OutlinedButton(onClick = {
                            if (!DownloadQueueService.pauseAll(ctx)) onMessage("Unable to pause download queue")
                        }) { Text("Pause all") }
                    if (tasks.any { DownloadQueuePolicy.canResume(it.state) })
                        OutlinedButton(onClick = {
                            if (!DownloadQueueService.resumeAll(ctx)) onMessage("Unable to resume download queue")
                        }) { Text("Resume all") }
                }
            }
        }
        if (tasks.isEmpty()) {
            Text("No downloads yet. Choose Play or Download on a Stremio stream card to add a saved download.",
                color = muted, fontSize = 12.sp)
        }
        tasks.asReversed().take(displayCount).forEach { job ->
            Column(Modifier.fillMaxWidth().background(Color(0xFF162841), RoundedCornerShape(11.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(job.title, color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 2)
                    Text(job.kind, color = blue, fontSize = 11.sp)
                }
                val stateLabel = job.state.replaceFirstChar { it.uppercase() }
                Text("$stateLabel · ${job.info}", color = when (job.state) {
                    DownloadQueuePolicy.FAILED -> Color(0xFFFFBE84)
                    DownloadQueuePolicy.COMPLETED -> Color(0xFF91E0B7)
                    else -> muted
                }, fontSize = 11.sp)
                if (job.total > 0) {
                    LinearProgressIndicator(
                        progress = { (job.done.toFloat() / job.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), color = blue)
                    Text("${DownloadQueuePolicy.percent(job.done, job.total)}% · " +
                        "${job.done / 1048576} / ${job.total / 1048576} MiB", color = muted, fontSize = 11.sp)
                } else if (job.done > 0)
                    Text("${job.done / 1048576} MiB received", color = muted, fontSize = 11.sp)
                if (job.state == DownloadQueuePolicy.RUNNING && job.speedBps > 0L) {
                    val mibPerSecond = job.speedBps.toDouble() / 1_048_576.0
                    val eta = DownloadQueuePolicy.remainingSeconds(job.done, job.total, job.speedBps)
                    val remaining = when {
                        eta == null -> "Size unknown"
                        eta >= 3600L -> "About ${eta / 3600}h ${(eta % 3600) / 60}m left"
                        eta >= 60L -> "About ${eta / 60}m ${eta % 60}s left"
                        else -> "About ${eta}s left"
                    }
                    Text("%.2f MiB/s · %s".format(mibPerSecond, remaining),
                        color = muted, fontSize = 11.sp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (DownloadQueuePolicy.canPause(job.state)) {
                        OutlinedButton(onClick = {
                            if (!DownloadQueueService.pause(ctx, job.id)) onMessage("Unable to pause download service")
                        }) { Text("Pause") }
                    }
                    if (DownloadQueuePolicy.canResume(job.state)) {
                        Button(onClick = {
                            if (!DownloadQueueService.resume(ctx, job.id)) onMessage("Unable to resume download service")
                        }) { Text("Resume") }
                        if (job.done > 0) OutlinedButton(onClick = {
                            if (!DownloadQueueService.restart(ctx, job.id)) onMessage("Could not restart transfer")
                        }) { Text("Restart") }
                    }
                    if (job.state == DownloadQueuePolicy.COMPLETED && job.destinationUri.isNotBlank()) {
                        OutlinedButton(onClick = {
                            val type = when (job.filename.substringAfterLast('.').lowercase()) {
                                "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"
                                "webm" -> "video/webm"; else -> "video/*"
                            }
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(job.destinationUri), type)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { ctx.startActivity(intent) }.onFailure { onMessage("No player found for the saved media") }
                        }) { Text("Open") }
                        OutlinedButton(onClick = {
                            uiScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val bytes = DownloadStorage.documentSize(ctx, Uri.parse(job.destinationUri))
                                        when {
                                            bytes < 0L -> "Storage is readable, but this provider cannot report file size"
                                            DownloadQueuePolicy.completeSizeMatches(job.done, job.total, bytes) ->
                                                "Saved file size verified: ${bytes / 1_048_576} MiB"
                                            else -> "Saved file differs from the completed download record; check storage"
                                        }
                                    }.getOrDefault("Saved file unavailable; reconnect the original storage")
                                }
                                onMessage(result)
                            }
                        }) { Text("Check file") }
                    }
                    if (job.state != DownloadQueuePolicy.RUNNING) {
                        OutlinedButton(onClick = { removeConfirmation = job.id }) { Text("Remove") }
                        if (job.state == DownloadQueuePolicy.COMPLETED && job.destinationUri.isNotBlank())
                            OutlinedButton(onClick = { deleteConfirmation = job.id }) { Text("Delete file") }
                    }
                }
                if (deleteConfirmation == job.id) {
                    Text("Permanently delete this saved media file from the chosen storage folder?", color = Color(0xFFFFC38D), fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val deleted = runCatching { ctx.contentResolver.delete(Uri.parse(job.destinationUri), null, null) > 0 }
                                .getOrDefault(false)
                            if (deleted) DownloadQueueRepository.remove(ctx, job.id)
                            else onMessage("Storage provider refused deletion; media and download record were preserved")
                            deleteConfirmation = ""
                        }) { Text("Delete permanently") }
                        TextButton(onClick = { deleteConfirmation = "" }) { Text("Keep file") }
                    }
                }
                if (removeConfirmation == job.id) {
                    Text("Remove from the queue? Files already saved on storage will NOT be deleted.", color = muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            DownloadQueueRepository.remove(ctx, job.id)
                            removeConfirmation = ""
                        }) { Text("Remove record") }
                        TextButton(onClick = { removeConfirmation = "" }) { Text("Keep") }
                    }
                }
            }
        }
        if (tasks.size > displayCount) Text("Show more downloads", color = blue,
            modifier = Modifier.clickable { displayCount += 30 }.padding(8.dp))
        Text("Android 15 limits background data-sync services to six hours per 24 hours. " +
            "If the system pauses a transfer, reopen PersonalMedia and select Resume. " +
            "Torrent downloads require P2P consent and do not run concurrently with torrent playback.",
            color = muted, fontSize = 11.sp)
    }
}
