package com.personalmedia.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android-only August T405 receiver guardian.
 *
 * The legacy August app's private 32-bit EyeTV/Siano libraries are not bundled.
 * This service keeps the existing PersonalMedia native UDP receiver in its owning
 * process, and keeps Android's radio/CPU awake while the user explicitly requests
 * a tuner session. It never silently changes Wi-Fi networks or claims to prevent
 * Android from switching Wi-Fi networks or changing its connection policy.
 *
 * Foreground service starts only after a user taps Connect and stops on Disconnect
 * or the notification's Disconnect action. Native control/probe and playback are
 * still handled by NativeBridge. START_NOT_STICKY avoids restoring stale sessions.
 */
class T405GuardianService : Service() {
    companion object {
        private const val ACTION_START = "com.personalmedia.android.T405_GUARDIAN_START"
        private const val ACTION_STOP = "com.personalmedia.android.T405_GUARDIAN_STOP"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_CONTROL = "control"
        private const val EXTRA_RECEIVE = "receive"
        private const val CHANNEL_ID = "personalmedia_t405_receiver"
        private const val NOTIFICATION_ID = 405
        @Volatile var active: Boolean = false
            private set
        @Volatile var networkStatus: String = "Not monitoring"
            private set

        fun start(context: Context, host: String, control: Int, receive: Int): Boolean {
            if (host.isBlank() || control !in 1..65535 || receive !in 1..65535) return false
            val intent = Intent(context, T405GuardianService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_CONTROL, control)
                putExtra(EXTRA_RECEIVE, receive)
            }
            return runCatching { context.startForegroundService(intent); true }.getOrDefault(false)
        }

        fun stop(context: Context) {
            // A started service is stopped even if the on-screen UI has been disposed.
            context.stopService(Intent(context, T405GuardianService::class.java))
            active = false
            networkStatus = "Not monitoring"
        }
    }

    // All status parsing, JNI probes and stream recovery run off the UI thread.
    private val workers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private var connectivity: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var host: String = ""
    private var controlPort: Int = 5002
    private var receivePort: Int = 5001
    private var lastRetryMs = 0L
    private var lastProbeMs = 0L
    private var consecutiveRetries = 0
    private var wifiWasReported = false

    private suspend fun inspectReceiverLoop() {
        delay(5_000L)
        while (active && workers.isActive) {
            val state = runCatching {
                if (NativeBridge.loaded) JSONObject(NativeBridge.tvStatus()) else JSONObject()
            }.getOrDefault(JSONObject())
            // The T405 firmware can deliver transport without Siano acknowledgements.
            // Fresh TS is the real liveness signal; missing replies alone are not a stall.
            val freshTs = state.optLong("lastTsAgeMs", -1L) in 0L..6_000L
            val nowMs = SystemClock.elapsedRealtime()
            if (freshTs || state.optBoolean("deviceReplied")) consecutiveRetries = 0
            val hasWifiRoute = T405NetworkRoute.inspect(this@T405GuardianService, host)
                .contains("is available on")
            if (!state.optBoolean("connected") && host.isNotBlank() && NativeBridge.loaded) {
                // An explicitly disconnected session stops the service; only unexpected
                // socket failures get limited reconnect attempts on verified Wi-Fi.
                if (active && hasWifiRoute && nowMs - lastRetryMs >= 20_000L && consecutiveRetries < 3) {
                    lastRetryMs = nowMs
                    consecutiveRetries++
                    val ok = runCatching { NativeBridge.tvConnect(host, controlPort, receivePort) }
                        .getOrDefault(false)
                    if (ok) {
                        T405NetworkRoute.bindNativeControlSocket(this@T405GuardianService, host)
                        T405ChannelStore.restoreNative(this@T405GuardianService)
                        NativeBridge.tvProbe()
                        lastProbeMs = nowMs
                    }
                    networkStatus = if (ok) "Tuner socket reopened; saved lineup restored" else
                        "Tuner socket unavailable; check receiver Wi-Fi"
                } else if (!hasWifiRoute) {
                    networkStatus = "Tuner Wi-Fi route unavailable; waiting for reconnection"
                }
            } else if (state.optBoolean("connected")) {
                val replyAgeMs = state.optLong("lastSianoReplyAgeMs", -1L)
                // Read-only version probe for firmware that supports it; no retune
                // solely because it never answers probes.
                if ((!state.optBoolean("deviceReplied") || replyAgeMs > 45_000L) &&
                    nowMs - lastProbeMs >= 30_000L && hasWifiRoute) {
                    lastProbeMs = nowMs
                    runCatching { NativeBridge.tvProbe() }
                }
                val selected = state.optString("selected")
                val mediaAgeMs = state.optLong("selectedMediaAgeMs", -1L)
                val mediaSeen = state.optLong("selectedMediaPackets") > 0L
                if (selected.isNotEmpty() && !state.optBoolean("scanning") &&
                    mediaSeen && mediaAgeMs >= 12_000L) {
                    if (hasWifiRoute) {
                        // Native throttling reissues filters first, retunes only
                        // after a prolonged stall, and never drops MPV's HTTP client.
                        val recovered = runCatching { NativeBridge.tvRecoverStream() }
                            .getOrDefault(false)
                        if (recovered) networkStatus = "Recovery request sent for stalled live transport"
                    } else networkStatus = "Live transport stalled; waiting for tuner Wi-Fi"
                } else if (freshTs) {
                    networkStatus = "Live transport healthy"
                }
            }
            delay(8_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = runCatching {
            wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PersonalMedia:T405")?.apply {
                setReferenceCounted(false); acquire()
            }
        }.getOrNull()
        // T405 control and MPEG-TS reception are unicast. A multicast lock
        // is unnecessary and would waste power during long TV sessions.
        val power = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        cpuLock = runCatching {
            power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PersonalMedia:T405Receiver")?.apply {
                setReferenceCounted(false); acquire()
            }
        }.getOrNull()
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiWasReported = true
                networkStatus = "Android reports Wi-Fi; verifying tuner route"
            }
            override fun onLost(network: Network) {
                networkStatus = "Wi-Fi network lost; waiting for system reconnection"
            }
        }
        val cb = callback
        if (cb != null) runCatching {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb
            )
        }.onFailure { networkStatus = "Network callback unavailable in this Android runtime" }
        networkStatus = if (wifiWasReported) networkStatus else "Monitoring network; waiting for Wi-Fi status"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (NativeBridge.loaded) runCatching { NativeBridge.tvDisconnect() }
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        controlPort = intent.getIntExtra(EXTRA_CONTROL, 5002)
        receivePort = intent.getIntExtra(EXTRA_RECEIVE, 5001)
        if (host.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "August T405 receiver", NotificationManager.IMPORTANCE_LOW)
        )
        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 1,
            Intent(this, T405GuardianService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.personalmedia.android.R.drawable.pm_mark)
            .setContentTitle("PersonalMedia · August T405")
            .setContentText("Receiver guardian active · tap to return to Live TV")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", stopIntent).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        } catch (error: RuntimeException) {
            // Android may deny foreground-service creation due to permissions or device state.
            // Never crash the already-working TV activity over this enhancement.
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        consecutiveRetries = 0
        lastProbeMs = SystemClock.elapsedRealtime()
        monitorJob?.cancel()
        monitorJob = workers.launch { inspectReceiverLoop() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        active = false
        monitorJob?.cancel()
        monitorJob = null
        workers.cancel()
        val cb = callback
        if (cb != null) runCatching { connectivity?.unregisterNetworkCallback(cb) }
        callback = null
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        runCatching { cpuLock?.takeIf { it.isHeld }?.release() }
        networkStatus = "Not monitoring"
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
