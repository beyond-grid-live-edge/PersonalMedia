package com.personalmedia.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import java.net.Inet4Address
import java.net.InetAddress

/** Route the native T405 control socket without changing process-wide routing.
 * Stremio, local video HTTP (127.0.0.1) and other PersonalMedia traffic must
 * continue to use their normal networks. No Wi-Fi joining or hidden API calls.
 * If no matching Android Wi-Fi route is visible, keep system routing and verify actual tuner replies. */
object T405NetworkRoute {
    @Volatile var lastReport: String = "Not checked"
        private set

    private fun findWifiRoute(context: Context, host: String): Pair<Network, String>? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() as? Inet4Address ?: return null
        return cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@mapNotNull null
            val links = cm.getLinkProperties(network) ?: return@mapNotNull null
            // Require a directly connected route, not a 0/0 internet default
            // route that could lead to an unrelated Wi-Fi access point.
            val best = links.routes.filter {
                it.destination.prefixLength > 0 && it.destination.contains(address)
            }
                .maxByOrNull { it.destination.prefixLength } ?: return@mapNotNull null
            Triple(network, links.interfaceName ?: "Wi-Fi", best.destination.prefixLength)
        }.maxByOrNull { it.third }?.let { Pair(it.first, it.second) }
    }

    fun inspect(context: Context, host: String): String = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiCount = cm?.allNetworks?.count {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: 0
        val match = findWifiRoute(context, host)
        when {
            match != null -> "Android Wi-Fi route to $host is available on ${match.second}."
            wifiCount > 0 -> "Android sees Wi-Fi but no route to $host. Check which Wi-Fi network is connected."
            else -> "No Android Wi-Fi route visible. Connect this device to the T405 Wi-Fi, then check tuner replies; an open socket alone is not proof."
        }
    }.getOrElse { "Unable to inspect Android network routing: ${it.javaClass.simpleName}" }

    /** Only the send UDP socket is bound. Duplicating its descriptor is required
     * because Java must not own/close the C++ descriptor. */
    fun bindNativeControlSocket(context: Context, host: String): String {
        val match = runCatching { findWifiRoute(context, host) }.getOrNull()
        if (match == null) {
            lastReport = "T405 control UDP: default Android routing (no visible matching Wi-Fi route)."
            return lastReport
        }
        val fd = if (NativeBridge.loaded) NativeBridge.tvControlSocketFd() else -1
        if (fd < 0) {
            lastReport = "T405 receiver socket is not open."
            return lastReport
        }
        lastReport = runCatching {
            ParcelFileDescriptor.fromFd(fd).use { copy ->
                match.first.bindSocket(copy.fileDescriptor)
            }
            "T405 control UDP bound to Wi-Fi (${match.second}); other PersonalMedia traffic unchanged."
        }.getOrElse { "Wi-Fi socket binding unavailable (${it.javaClass.simpleName}); using Android routing." }
        return lastReport
    }
}
