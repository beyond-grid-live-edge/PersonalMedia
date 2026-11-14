package com.personalmedia.android

import java.net.URI

/** Only a private HTTP endpoint returned by native libtorrent can be handed to MPV. */
internal object TorrentLoopbackPolicy {
    fun isNativeTorrentUrl(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme == "http" && uri.host == "127.0.0.1" &&
            uri.port in 1..65535 && uri.rawUserInfo == null && uri.fragment == null &&
            uri.rawQuery == null &&
            Regex("^/[0-9a-f]{48}/video\\.(mkv|mp4|webm|avi|ts|mpeg|mov|bin)$")
                .matches(uri.rawPath ?: "")
    } catch (_: Exception) { false }
}
