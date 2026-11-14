package com.personalmedia.android

import android.content.Context

/** Explicit, user-initiated native T405 session. Never silently joins a Wi-Fi AP. */
object T405SessionController {
    fun connect(context: Context, host: String, control: Int, receive: Int): String {
        if (!NativeBridge.loaded) return "Native T405 receiver could not be loaded."
        if (host.isBlank() || control !in 1..65535 || receive !in 1..65535) return "Invalid T405 IP address or UDP ports."
        if (!runCatching { NativeBridge.tvConnect(host, control, receive) }.getOrDefault(false))
            return "Unable to open T405 UDP receiver. Check tuner IP and ports."
        // Restore saved DVB identities before the first channel selection.
        val restored = T405ChannelStore.restoreNative(context)
        val binding = T405NetworkRoute.bindNativeControlSocket(context, host)
        val probed = NativeBridge.tvProbe()
        val guarded = T405GuardianService.start(context, host, control, receive)
        return "UDP receiver open; ${if (probed) "probe sent" else "probe failed"}. " +
            "${if (guarded) "Guardian requested" else "Guardian unavailable"}. $restored saved services restored. $binding"
    }

    fun disconnect(context: Context) {
        if (NativeBridge.loaded) NativeBridge.tvDisconnect()
        T405GuardianService.stop(context)
    }
}
