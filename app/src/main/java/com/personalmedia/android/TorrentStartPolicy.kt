package com.personalmedia.android

/** A torrent button must explain why it cannot start, not silently disable itself. */
internal object TorrentStartPolicy {
    fun issue(allowed: Boolean, nativeAvailable: Boolean, hash: String): String? = when {
        !allowed -> "Enable the P2P checkbox above Available streams before selecting a torrent."
        !nativeAvailable -> "Native libtorrent is unavailable in this installed APK. Check the ARM64 torrent-enabled build and reinstall it without uninstalling your data."
        !hash.matches(Regex("[0-9a-fA-F]{40}")) ->
            "This add-on did not provide a valid BitTorrent v1 information hash."
        else -> null
    }
}
