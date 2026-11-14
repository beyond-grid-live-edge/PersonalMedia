package com.personalmedia.android

/** Pure string/percent handling for Android's file-export picker. */
internal object TorrentExportPolicy {
    fun suggestedName(title: String, release: String, hash: String): String {
        val name = title.ifBlank { "Stremio video" }
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim(' ', '.')
            .take(90).ifBlank { "Stremio video" }
        val ext = Regex("(?i)\\.(mkv|mp4|webm|avi|mov|m4v|ts)(?:\\b|$)")
            .find(release)?.groupValues?.get(1)?.lowercase() ?: "mkv"
        val tag = hash.take(8).takeIf { it.matches(Regex("[0-9a-fA-F]{8}")) } ?: "video"
        return "$name-$tag.$ext"
    }
    fun percent(done: Long, size: Long): Int = if(size<=0) 0
        else ((done.coerceIn(0L,size) * 100.0)/size).toInt().coerceIn(0,100)
}
