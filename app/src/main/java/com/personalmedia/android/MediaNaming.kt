package com.personalmedia.android

/** Pure filename classification used by the local media scanner, also host-tested. */
fun mediaType(filename: String): String {
    val lower = filename.substringAfterLast('.').lowercase()
    return when (lower) {
        "mp4", "m4v", "mkv", "avi", "mov", "webm", "wmv", "mpeg", "mpg", "mts", "m2ts", "ts" ->
            if (Regex("(?i)(?:s\\d{1,2}e\\d{1,2}|season[ ._-]*\\d+)").containsMatchIn(filename)) "tv" else "movie"
        "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma", "alac" -> "music"
        "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "tif", "tiff" -> "picture"
        else -> ""
    }
}
fun cleanTitle(name: String): String = name.substringBeforeLast('.', name)
    .replace(Regex("[._]+"), " ").trim()
