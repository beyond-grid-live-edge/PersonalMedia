package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class LibraryFolderPolicyTest {
    @Test fun moviesFolderIsAlwaysMoviesEvenForEpisodeNamedFile() {
        assertEquals("movie", LibraryFolderPolicy.kindForFile("Movies", "Show.S01E01.mkv"))
        assertEquals("movie", LibraryFolderPolicy.kindForFile("Movies", "Movie.2026.mp4"))
    }

    @Test fun tvFolderIsAlwaysTvEvenWhenEpisodeMarkerMissing() {
        assertEquals("tv", LibraryFolderPolicy.kindForFile("TV Shows", "Series Pilot 01.mkv"))
        assertEquals("tv", LibraryFolderPolicy.kindForFile("TV Shows", "S03E12.webm"))
    }

    @Test fun categoryCannotImportUnrelatedOrUnsafeFileTypes() {
        assertNull(LibraryFolderPolicy.kindForFile("Pictures", "movie.mkv"))
        assertNull(LibraryFolderPolicy.kindForFile("Music", "photo.jpg"))
        assertNull(LibraryFolderPolicy.kindForFile("Movies", "file.exe"))
        assertNull(LibraryFolderPolicy.kindForFile("TV Shows", "video.mp4.exe"))
        assertNull(LibraryFolderPolicy.kindForFile("Unknown", "video.mp4"))
    }

    @Test fun categoryExtensionsAreCaseInsensitive() {
        assertEquals("music", LibraryFolderPolicy.kindForFile("Music", "song.FLAC"))
        assertEquals("picture", LibraryFolderPolicy.kindForFile("Pictures", "photo.HEIC"))
        assertEquals("tv", LibraryFolderPolicy.kindForFile("TV Shows", "EPISODE.MP4"))
    }

    @Test fun filePickerFiltersMatchCategory() {
        assertArrayEquals(arrayOf("video/*"), LibraryFolderPolicy.mimeTypes("Movies"))
        assertArrayEquals(arrayOf("video/*"), LibraryFolderPolicy.mimeTypes("TV Shows"))
        assertArrayEquals(arrayOf("audio/*"), LibraryFolderPolicy.mimeTypes("Music"))
        assertArrayEquals(arrayOf("image/*"), LibraryFolderPolicy.mimeTypes("Pictures"))
        assertArrayEquals(arrayOf("video/*", "audio/*", "image/*"), LibraryFolderPolicy.mimeTypes(null))
    }

    @Test fun topLevelTabsMapToStableCategoriesOnly() {
        assertEquals("Movies", LibraryFolderPolicy.categoryForTab("Movies"))
        assertEquals("TV Shows", LibraryFolderPolicy.categoryForTab("TV Shows"))
        assertNull(LibraryFolderPolicy.categoryForTab("Home"))
        assertNull(LibraryFolderPolicy.categoryForTab("Stremio"))
    }
}
