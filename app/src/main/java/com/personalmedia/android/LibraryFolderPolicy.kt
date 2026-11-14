package com.personalmedia.android

/** Stable library categories; independent of vendor document providers. */
internal object LibraryFolderPolicy {
    val categories = listOf("Movies", "TV Shows", "Music", "Pictures")
    private val videoExtensions = setOf("mp4", "m4v", "mkv", "avi", "mov", "webm", "wmv", "mpeg", "mpg", "mts", "m2ts", "ts")
    private val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma", "alac")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "tif", "tiff")

    fun kindForFile(category: String, filename: String): String? {
        val extension = filename.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        return when (category) {
            "Movies" -> if (extension in videoExtensions) "movie" else null
            "TV Shows" -> if (extension in videoExtensions) "tv" else null
            "Music" -> if (extension in audioExtensions) "music" else null
            "Pictures" -> if (extension in imageExtensions) "picture" else null
            else -> null
        }
    }

    fun mimeTypes(category: String?): Array<String> = when (category) {
        "Movies", "TV Shows" -> arrayOf("video/*")
        "Music" -> arrayOf("audio/*")
        "Pictures" -> arrayOf("image/*")
        else -> arrayOf("video/*", "audio/*", "image/*")
    }

    fun categoryForTab(tab: String): String? = tab.takeIf { it in categories }
}
