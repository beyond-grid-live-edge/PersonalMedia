package com.personalmedia.android

/** JNI has ONE native torrent session; never let a background download steal
 * the actively streaming libmpv torrent or vice versa. */
internal object TorrentEngineLease {
    private var owner: String = ""
    @Synchronized fun acquirePlayback(): Boolean {
        if (owner.isNotEmpty()) return owner == "playback"
        owner = "playback"
        return true
    }
    @Synchronized fun releasePlayback() { if (owner == "playback") owner = "" }
    @Synchronized fun acquireDownload(id: String): Boolean {
        if (owner.isNotEmpty()) return false
        owner = "download:$id"
        return true
    }
    @Synchronized fun releaseDownload(id: String) { if (owner == "download:$id") owner = "" }
    @Synchronized fun heldByDownload(): Boolean = owner.startsWith("download:")
    @Synchronized fun heldByPlayback(): Boolean = owner == "playback"
}
