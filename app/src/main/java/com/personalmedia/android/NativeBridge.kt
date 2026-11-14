package com.personalmedia.android

/** Ported native code is in-process. Torrent streaming intentionally fails closed unless
 * libtorrent 2.x was supplied to CMake; missing pieces are never presented as verified. */
object NativeBridge {
    val loaded: Boolean = runCatching { System.loadLibrary("personalmedia"); true }.getOrDefault(false)
    private fun requireLoaded() { check(loaded) { "PersonalMedia native library could not be loaded" } }

    @JvmStatic external fun engineAvailable(): Boolean
    @JvmStatic external fun torrentOpen(hash: String, index: Int, trackers: String, cacheDir: String): String
    @JvmStatic external fun torrentStatus(): String
    @JvmStatic external fun torrentRangeUrl(hash: String, index: Int): String
    @JvmStatic external fun torrentRequestCancel()
    @JvmStatic external fun torrentDemand(offset: Long, length: Int)
    /** Returns bytes only when every piece in the requested range is hash-verified. */
    @JvmStatic external fun torrentReadVerified(offset: Long, length: Int): ByteArray?
    @JvmStatic external fun torrentStop()
    @JvmStatic external fun mpvAvailable(): Boolean
    @JvmStatic external fun mpvLastError(): String
    @JvmStatic external fun mpvDiagnostics(): String
    @JvmStatic external fun mpvStart(surface: android.view.Surface, url: String): Boolean
    @JvmStatic external fun mpvStartForT405(surface: android.view.Surface, url: String): Boolean
    @JvmStatic external fun mpvStopForT405()
    @JvmStatic external fun mpvStop()
    // Owner-isolated Stremio direct-stream controls. No file URLs/header values in status.
    @JvmStatic external fun mpvStartStremio(surface: android.view.Surface, url: String,
        requestHeaderLines: String, resumeMs: Long, softwareDecoder: Boolean): Boolean
    @JvmStatic external fun mpvStopStremio()
    @JvmStatic external fun mpvStatusStremio(): String
    @JvmStatic external fun mpvPauseStremio(paused: Boolean): Boolean
    @JvmStatic external fun mpvSeekStremio(positionMs: Long): Boolean
    @JvmStatic external fun mpvSetTrackStremio(property: String, value: String): Boolean
    @JvmStatic external fun mpvAddSubtitleStremio(url: String): Boolean
    @JvmStatic external fun mpvPause(paused: Boolean)
    @JvmStatic external fun mpvPauseForT405(paused: Boolean): Boolean
    @JvmStatic external fun mpvSetTrackForT405(property: String, value: String): Boolean
    @JvmStatic external fun mpvTracksForT405(): String
    @JvmStatic external fun mpvRequestRedrawForT405()
    @JvmStatic external fun tvConnect(host: String, controlPort: Int, receivePort: Int): Boolean
    @JvmStatic external fun tvControlSocketFd(): Int
    @JvmStatic external fun tvDisconnect()
    @JvmStatic external fun tvProbe(): Boolean
    @JvmStatic external fun tvTune(frequency: Int, bandwidth: Int): Boolean
    @JvmStatic external fun tvScan(profile: String): Boolean
    @JvmStatic external fun tvCancelScan()
    @JvmStatic external fun tvStatus(): String
    @JvmStatic external fun tvChannels(): String
    @JvmStatic external fun tvRestoreChannels(rows: String): Int
    @JvmStatic external fun tvPlay(key: String): String
    @JvmStatic external fun tvRecoverStream(): Boolean
    @JvmStatic external fun tvStop()
    fun isTorrentReady(): Boolean = loaded && engineAvailable()
}
