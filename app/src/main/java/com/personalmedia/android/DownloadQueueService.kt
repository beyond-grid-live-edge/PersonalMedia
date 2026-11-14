package com.personalmedia.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** STR-6: user-started foreground dataSync service, one transfer at a time.
 * Queue and signed source details persist in encrypted app-private storage.
 * On Android 15+, dataSync FGS execution is time limited: interrupted entries
 * remain paused and can be explicitly resumed when the app is reopened.
 */
class DownloadQueueService : Service() {
    companion object {
        private const val CHANNEL = "personalmedia_downloads"
        private const val NOTIFICATION = 612
        private const val ACTION_RUN = "pm.DOWNLOAD.RUN"
        private const val ACTION_PAUSE = "pm.DOWNLOAD.PAUSE"
        private const val ACTION_RESUME = "pm.DOWNLOAD.RESUME"
        private const val ACTION_RESTART = "pm.DOWNLOAD.RESTART"
        private const val ACTION_PAUSE_ALL = "pm.DOWNLOAD.PAUSE_ALL"
        private const val ACTION_RESUME_ALL = "pm.DOWNLOAD.RESUME_ALL"
        private const val EXTRA_ID = "job"

        private fun send(ctx: Context, actionType: String, id: String = ""): Boolean {
            val intent = Intent(ctx, DownloadQueueService::class.java).apply {
                action = actionType
                if (id.isNotBlank()) putExtra(EXTRA_ID, id)
            }
            return runCatching {
                // Always entered through a user-started Activity, not BOOT_COMPLETED.
                ctx.startForegroundService(intent); true
            }.getOrDefault(false)
        }
        fun kickChecked(ctx: Context): Boolean {
            DownloadQueueRepository.init(ctx)
            return if (DownloadQueueRepository.rows.value.any { it.state == DownloadQueuePolicy.QUEUED })
                send(ctx, ACTION_RUN) else true
        }
        fun kick(ctx: Context) { kickChecked(ctx) }
        fun pause(ctx: Context, id: String) = send(ctx, ACTION_PAUSE, id)
        fun resume(ctx: Context, id: String) = send(ctx, ACTION_RESUME, id)
        fun restart(ctx: Context, id: String) = send(ctx, ACTION_RESTART, id)
        fun pauseAll(ctx: Context) = send(ctx, ACTION_PAUSE_ALL)
        fun resumeAll(ctx: Context) = send(ctx, ACTION_RESUME_ALL)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pump: Job? = null
    @Volatile private var transfer: Job? = null
    @Volatile private var activeId: String = ""
    private var wake: PowerManager.WakeLock? = null
    private var lastNotificationMs = 0L
    private var lastStoreMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        DownloadQueueRepository.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "PersonalMedia downloads",
            NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID).orEmpty()
        val action = intent?.action.orEmpty()
        // startForeground immediately, as required for an FGS started by the UI.
        val current = DownloadQueueRepository.find(this, id)
        try {
            foreground(current, "Preparing download queue")
        } catch (_: Exception) {
            if (id.isNotBlank()) DownloadQueueRepository.update(this, id) { row ->
                if (row.state != DownloadQueuePolicy.COMPLETED) row.copy(
                    state = DownloadQueuePolicy.PAUSED, speedBps = 0L,
                    info = "Android did not allow starting the foreground download service") else row
            }
            stopSelf()
            return START_NOT_STICKY
        }
        when (action) {
            ACTION_PAUSE -> {
                DownloadQueueRepository.update(this, id) { row ->
                    if (DownloadQueuePolicy.canPause(row.state)) row.copy(
                        state = DownloadQueuePolicy.PAUSED, speedBps = 0L,
                        info = "Paused; partial file kept") else row
                }
                if (id == activeId) {
                    if (current?.kind == "Torrent" && NativeBridge.loaded)
                        runCatching { NativeBridge.torrentRequestCancel() }
                    transfer?.cancel()
                }
            }
            ACTION_RESUME -> DownloadQueueRepository.update(this, id) { row ->
                if (DownloadQueuePolicy.canResume(row.state)) row.copy(
                    state = DownloadQueuePolicy.QUEUED, speedBps = 0L,
                    info = "Waiting to resume") else row
            }
            ACTION_RESTART -> DownloadQueueRepository.update(this, id) { row ->
                if (DownloadQueuePolicy.canResume(row.state)) row.copy(
                    state = DownloadQueuePolicy.QUEUED, done = 0L, total = 0L,
                    etag = "", lastModified = "", speedBps = 0L,
                    info = "Restarting from the beginning") else row
            }
            ACTION_PAUSE_ALL -> {
                DownloadQueueRepository.rows.value.forEach { job ->
                    if (DownloadQueuePolicy.canPause(job.state))
                        DownloadQueueRepository.update(this, job.id) { row ->
                            if (DownloadQueuePolicy.canPause(row.state)) row.copy(
                                state = DownloadQueuePolicy.PAUSED, speedBps = 0L,
                                info = "Paused; partial file kept") else row
                        }
                }
                val running = DownloadQueueRepository.find(this, activeId)
                if (running?.kind == "Torrent" && NativeBridge.loaded)
                    runCatching { NativeBridge.torrentRequestCancel() }
                transfer?.cancel()
            }
            ACTION_RESUME_ALL -> {
                DownloadQueueRepository.rows.value.forEach { job ->
                    if (DownloadQueuePolicy.canResume(job.state))
                        DownloadQueueRepository.update(this, job.id) { row ->
                            if (DownloadQueuePolicy.canResume(row.state)) row.copy(
                                state = DownloadQueuePolicy.QUEUED, speedBps = 0L,
                                info = "Waiting to resume") else row
                        }
                }
            }
        }
        if (pump?.isActive != true) pump = scope.launch { runQueue() }
        return START_NOT_STICKY
    }

    private suspend fun runQueue() {
        var waitingForPlayback = false
        while (scope.isActive) {
            val torrentInPlayer = TorrentEngineLease.heldByPlayback()
            val pending = DownloadQueueRepository.rows.value.filter {
                it.state == DownloadQueuePolicy.QUEUED
            }
            val next = pending.firstOrNull { !torrentInPlayer || it.kind != "Torrent" }
            if (next == null) {
                if (pending.isNotEmpty() && torrentInPlayer) {
                    pending.forEach { row -> DownloadQueueRepository.update(this, row.id) {
                        it.copy(info = "Waiting for current torrent playback to end")
                    } }
                    waitingForPlayback = true
                }
                break
            }
            // A process can die after the last durable checkpoint but before
            // setting COMPLETED. A fresh explicit Resume can finish that record
            // without asking the provider for an impossible end-of-file range.
            if (next.done > 0L && next.total > 0L && next.done == next.total &&
                next.destinationUri.isNotBlank()) {
                val physical = runCatching { DownloadStorage.documentSize(this,
                    android.net.Uri.parse(next.destinationUri)) }.getOrNull()
                if (physical != null && physical == next.done) {
                    DownloadQueueRepository.update(this, next.id) { row ->
                        if (row.state == DownloadQueuePolicy.QUEUED) row.copy(
                            state = DownloadQueuePolicy.COMPLETED, speedBps = 0L,
                            info = "Recovered completed file after interruption") else row
                    }
                    continue
                }
            }
            activeId = next.id
            transfer = scope.launch {
                val claimed = DownloadQueueRepository.update(this@DownloadQueueService, next.id) {
                    if (it.state == DownloadQueuePolicy.QUEUED)
                        it.copy(state = DownloadQueuePolicy.RUNNING, info = "Starting") else it
                }
                if (claimed?.state != DownloadQueuePolicy.RUNNING) return@launch
                var lastDone = next.done
                var lastTotal = next.total
                var lastDestination = next.destinationUri
                var sampleBytes = next.done
                var sampleMs = SystemClock.elapsedRealtime()
                var currentSpeed = 0L
                lastStoreMs = 0L
                wake = runCatching {
                    (getSystemService(Context.POWER_SERVICE) as PowerManager)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PersonalMedia:Download")
                        .apply { acquire(6 * 60 * 60 * 1000L) }
                }.getOrNull()
                try {
                    DownloadTransfers.run(this@DownloadQueueService, next) { done, total, detail, destination ->
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val now = SystemClock.elapsedRealtime()
                        if (now - sampleMs >= 1100L) {
                            currentSpeed = DownloadQueuePolicy.bytesPerSecond(
                                (done - sampleBytes).coerceAtLeast(0L), now - sampleMs)
                            sampleBytes = done
                            sampleMs = now
                        }
                        if (now - lastStoreMs >= 900 || destination != lastDestination || done == total && total > 0) {
                            DownloadQueueRepository.update(this@DownloadQueueService, next.id) { row ->
                                if (row.state != DownloadQueuePolicy.RUNNING) row
                                else row.copy(done = done, total = total, info = detail,
                                    speedBps = currentSpeed,
                                    destinationUri = destination.ifBlank { row.destinationUri })
                            }
                            lastStoreMs = now
                            lastDone = done; lastTotal = total; lastDestination = destination
                            if (now - lastNotificationMs > 1500L) foreground(
                                DownloadQueueRepository.find(this@DownloadQueueService, next.id), detail)
                        }
                    }
                    // The writer has closed and forced its file descriptor before
                    // returning. Never report completion if the storage provider now
                    // reports a different size or the document disappeared.
                    val document = DownloadQueueRepository.find(this@DownloadQueueService, next.id)
                        ?.destinationUri.orEmpty()
                    if (document.isBlank()) throw IOException("Saved download location is missing")
                    val physical = DownloadStorage.documentSize(this@DownloadQueueService,
                        android.net.Uri.parse(document))
                    if (!DownloadQueuePolicy.completeSizeMatches(lastDone, lastTotal, physical))
                        throw IOException("Saved file size differs from transfer progress; select Restart")
                    DownloadQueueRepository.update(this@DownloadQueueService, next.id) { row ->
                        if (row.state == DownloadQueuePolicy.RUNNING) row.copy(
                            state = DownloadQueuePolicy.COMPLETED, speedBps = 0L,
                            done = lastDone, total = if (lastTotal > 0) lastTotal else lastDone,
                            info = if (physical < 0L) "Saved; provider did not report file size"
                                else "Saved and file size checked") else row
                    }
                } catch (_: CancellationException) {
                    DownloadQueueRepository.update(this@DownloadQueueService, next.id) { row ->
                        if (row.state == DownloadQueuePolicy.RUNNING)
                            row.copy(state = DownloadQueuePolicy.PAUSED, speedBps = 0L,
                                info = "Interrupted; select Resume") else row
                    }
                } catch (failure: Exception) {
                    // Never leak source URLs, tracker URLs, signed query tokens or
                    // request headers via notifications or error UI.
                    val safeMessages = listOf("Provider", "Torrent", "Media", "P2P", "Native", "Downloads", "The ",
                        "Source ", "This ", "Invalid", "Partial", "Storage ", "Saved ", "A ",
                        "Interrupted", "Timed out")
                    val raw = failure.message.orEmpty()
                    val text = if (safeMessages.any { raw.startsWith(it) } &&
                        !raw.contains("https://") && !raw.contains("http://") && !raw.contains("content://")) raw.take(155)
                        else if (failure is IOException) "Network or storage failed; check connection and free space"
                        else "Transfer failed; select Retry or a different source"
                    DownloadQueueRepository.update(this@DownloadQueueService, next.id) { row ->
                        if (row.state == DownloadQueuePolicy.RUNNING)
                            row.copy(state = DownloadQueuePolicy.FAILED, speedBps = 0L,
                                info = text) else row
                    }
                } finally {
                    runCatching { wake?.release() }
                    wake = null
                }
            }
            transfer?.join()
            transfer = null
            activeId = ""
        }
        // Enqueue may race the final empty-queue check: don't silently strand it.
        // A new START command normally restarts the pump; handle the narrow exit race.
        if (!waitingForPlayback && DownloadQueueRepository.next(this) != null && scope.isActive) {
            pump = scope.launch { runQueue() }
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun foreground(item: DownloadQueueItem?, status: String) {
        val openApp = PendingIntent.getActivity(this, 10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.pm_mark)
            .setContentTitle(item?.title?.take(45) ?: "PersonalMedia downloads")
            .setContentText(status.take(90))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (item != null && item.total > 0)
            builder.setProgress(100, DownloadQueuePolicy.percent(item.done, item.total), false)
        if (item != null && item.state == DownloadQueuePolicy.RUNNING) {
            val intent = Intent(this, DownloadQueueService::class.java).apply {
                action = ACTION_PAUSE; putExtra(EXTRA_ID, item.id)
            }
            val pauseAction = PendingIntent.getService(this, 11, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Pause", pauseAction).build())
        }
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIFICATION, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION, builder.build())
        lastNotificationMs = SystemClock.elapsedRealtime()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 6-hour dataSync cap: persist a paused task, stop promptly.
        val id = activeId
        if (id.isNotBlank()) {
            DownloadQueueRepository.update(this, id) { it.copy(state = DownloadQueuePolicy.PAUSED,
                speedBps = 0L, info = "Android background time limit reached; resume in app") }
            transfer?.cancel()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        // A normally destroyed service can leave its process (and repository)
        // alive. Restore an interrupted task immediately, not only on restart.
        val interrupted = activeId
        if (interrupted.isNotBlank()) DownloadQueueRepository.update(this, interrupted) { row ->
            if (row.state == DownloadQueuePolicy.RUNNING)
                row.copy(state = DownloadQueuePolicy.PAUSED, speedBps = 0L,
                    info = "Download service stopped; select Resume") else row
        }
        transfer?.cancel()
        scope.cancel()
        runCatching { wake?.release() }
        wake = null
        super.onDestroy()
    }
}
