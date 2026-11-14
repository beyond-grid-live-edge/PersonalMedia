import com.personalmedia.android.cleanTitle
import com.personalmedia.android.mediaType

fun main() {
    check(mediaType("Film.2025.mkv") == "movie")
    check(mediaType("Series.S02E04.mkv") == "tv")
    check(mediaType("Season_1_episode.mp4") == "tv")
    check(mediaType("Track.FLAC") == "music")
    check(mediaType("Photo.HEIC") == "picture")
    check(mediaType("Archive.zip") == "")
    check(cleanTitle("My.Film.2024.mkv") == "My Film 2024")
    println("Native Kotlin media classification: PASS")
}
