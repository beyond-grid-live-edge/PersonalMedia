package com.personalmedia.android

/** Display-only organisation of user-authorised media files. No filename, URI or file is modified. */
internal data class MovieEditionGroup(
    val key: String, val title: String, val year: String, val editions: List<MediaEntry>
)
internal data class MovieCollectionGroup(
    val key: String, val title: String, val movies: List<MovieEditionGroup>
)
internal data class TvEpisodeInfo(
    val show: String, val season: Int, val episode: Int, val part: Int, val label: String
)
internal data class TvShowGroup(val key: String, val title: String, val episodes: List<MediaEntry>)
internal data class TvCollectionGroup(val key: String, val title: String, val shows: List<TvShowGroup>)

/** Cautious grouping rules based on HarmonyOS's LibraryOrganizer and movieCollections implementations.
 * Release metadata is stripped only for *display and grouping*, never persisted to source records.
 */
internal object LocalLibraryOrganizer {
    private val yearExpr = Regex("(?<!\\d)((?:19|20)\\d{2})(?!\\d)")
    private val seasonEpisode = Regex("(?i)(?:^|[ ._\\-])S(\\d{1,2})[ ._\\-]*E(\\d{1,3})(?:[ ._\\-]*E\\d{1,3})?")
    private val xEpisode = Regex("(?i)(?:^|[ ._\\-])(\\d{1,2})x(\\d{1,3})(?:[ ._\\-]|$)")
    private val seasonFolder = Regex("(?i)^(?:season[ ._\\-]*|s)(\\d{1,2})$")
    private val partExpr = Regex("(?i)(?:^|[ ._\\-])(?:part|pt)[ ._\\-]*(\\d{1,2})(?:$|[ ._\\-])")
    private val releaseToken = Regex("(?i)\\b(?:2160p|1080p|720p|480p|4k|bluray|blu[ ._\\-]*ray|bdrip|brrip|webrip|web[ ._\\-]*dl|remux|x264|x265|h264|h265|hevc|av1|hdr10?|dv|dts|aac|atmos|proper|repack|extended|uncut|directors?[ ._\\-]*cut|special[ ._\\-]*edition|theatrical)\\b")
    private val genericFolders = setOf("movies", "films", "video", "videos", "library", "downloads", "1080p", "720p", "4k", "action", "drama", "comedy", "horror", "thriller", "scifi", "sci fi", "documentaries")

    private fun tidy(value: String): String = value.replace(Regex("[._]+"), " ")
        .replace(Regex("\\s+"), " ").trim(' ', '-', ':', '.', '_')
    private fun canonical(value: String): String = tidy(value).lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun pathParts(item: MediaEntry): List<String> = item.relativePath.replace('\\', '/')
        .split('/').filter { it.isNotBlank() }
    private fun cleanedStem(item: MediaEntry): String = tidy(item.title)

    /** The year is used as an identity boundary: remakes with distinct years must not merge. */
    fun movieIdentity(item: MediaEntry): Pair<String, String> {
        val stem = cleanedStem(item)
        val year = yearExpr.find(stem)?.value.orEmpty()
        var name = if (year.isNotEmpty()) stem.substringBefore(year) else stem
        // Release formats trailing the title often produce near-identical edition names.
        val marker = releaseToken.find(name)
        if (marker != null && marker.range.first > 0) name = name.substring(0, marker.range.first)
        name = tidy(name.replace(Regex("[\\[(]$"), ""))
        if (name.isBlank()) name = stem
        // Without a year, don't coalesce names with different release-token suffixes.
        val safeName = canonical(name).ifBlank { canonical(stem) }
        val unknownYearGuard = if (year.isBlank()) "u:" + canonical(stem) else ""
        return (safeName + "|" + year + "|" + unknownYearGuard) to name
    }

    fun movieEditions(entries: List<MediaEntry>): List<MovieEditionGroup> = entries
        .filter { it.type == "movie" }
        .groupBy { movieIdentity(it).first }
        .map { (key, matches) ->
            val best = matches.firstOrNull { it.art.isNotBlank() } ?: matches.first()
            val name = movieIdentity(best).second
            MovieEditionGroup(key, name, yearExpr.find(cleanedStem(best))?.value.orEmpty(),
                matches.distinctBy { it.uri }.sortedBy { it.title.lowercase() })
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    private fun namedFranchise(name: String): String? {
        val n = canonical(name)
        return when {
            n.startsWith("back to the future") -> "Back to the Future collection"
            n.startsWith("die hard") -> "Die Hard collection"
            n.startsWith("mission impossible") -> "Mission: Impossible collection"
            n.startsWith("the matrix") -> "The Matrix collection"
            n.startsWith("a nightmare on elm street") || n.startsWith("freddys dead") -> "A Nightmare on Elm Street collection"
            n.startsWith("alien ") || n == "alien" || n.startsWith("aliens ") || n.startsWith("prometheus") -> "Alien collection"
            n.startsWith("28 days later") || n.startsWith("28 weeks later") || n.startsWith("28 years later") -> "28 Days & Weeks & Years Later collection"
            else -> null
        }
    }

    /** Prefer top-level source folders. A known-franchise fallback is only for individually imported files. */
    fun movieCollections(entries: List<MediaEntry>): List<MovieCollectionGroup> {
        val groups = movieEditions(entries)
        val byCollection = linkedMapOf<String, MutableList<MovieEditionGroup>>()
        val labels = linkedMapOf<String, String>()
        groups.forEach { movie ->
            val entry = movie.editions.first()
            val components = pathParts(entry)
            val folder = components.firstOrNull()?.let(::tidy).orEmpty()
            val folderCanonical = canonical(folder)
            val key: String
            val label: String
            if (entry.sourceUri.isNotBlank() && components.size > 1 &&
                folderCanonical !in genericFolders && folderCanonical.isNotBlank()) {
                key = "folder:${entry.sourceUri}:$folderCanonical"
                label = folder
            } else if (entry.sourceUri.isBlank()) {
                label = namedFranchise(movie.title) ?: return@forEach
                key = "named:${canonical(label)}"
            } else return@forEach
            byCollection.getOrPut(key) { mutableListOf() }.add(movie)
            labels[key] = label
        }
        return byCollection.filterValues { it.distinctBy(MovieEditionGroup::key).size >= 2 }
            .map { (key, members) -> MovieCollectionGroup(key, labels[key].orEmpty(),
                members.distinctBy(MovieEditionGroup::key).sortedWith(
                    compareBy<MovieEditionGroup> { it.year.toIntOrNull() ?: 9999 }.thenBy { it.title.lowercase() })) }
            .sortedBy { it.title.lowercase() }
    }

    fun movieStandalones(entries: List<MediaEntry>): List<MovieEditionGroup> {
        val collections = movieCollections(entries)
        val hidden = collections.flatMap { it.movies }.map { it.key }.toSet()
        return movieEditions(entries).filterNot { it.key in hidden }
    }

    private fun webPrefix(value: String): String = tidy(value.replace(
        Regex("(?i)^[a-z0-9_-]{2,25}\\.(?:com|net|org)[ ._\\-]+"), ""))

    fun tvEpisode(item: MediaEntry): TvEpisodeInfo {
        val parts = pathParts(item)
        val name = cleanedStem(item)
        val match = seasonEpisode.find(name) ?: xEpisode.find(name)
        val parent = parts.dropLast(1)
        val afterCollection = if (parent.firstOrNull()?.contains("collection", true) == true && parent.size > 1)
            parent.drop(1) else parent
        val showFolder = afterCollection.firstOrNull()?.takeIf { !seasonFolder.matches(tidy(it)) }
        val prefix = match?.let { name.substring(0, it.range.first).trim() }.orEmpty()
        val rawShow = if (showFolder != null) showFolder else prefix.ifBlank { parent.lastOrNull().orEmpty() }
        val show = webPrefix(rawShow).ifBlank { prefix.ifBlank { name } }
        val season = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: afterCollection.firstNotNullOfOrNull { seasonFolder.matchEntire(tidy(it))?.groupValues?.get(1)?.toIntOrNull() }
            ?: 1
        val episode = match?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: Regex("(?i)(?:^|[ ._\\-])(?:episode|ep)[ ._\\-]*(\\d{1,3})").find(name)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0
        val part = partExpr.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val title = if (match != null) tidy(name.substring(match.range.last + 1)).ifBlank { "Episode $episode" }
            else name
        return TvEpisodeInfo(show, season, episode, part, title)
    }

    fun tvShows(entries: List<MediaEntry>): List<TvShowGroup> = entries.filter { it.type == "tv" }
        .groupBy { canonical(tvEpisode(it).show) }
        .map { (key, files) -> TvShowGroup(key, tvEpisode(files.first()).show,
            files.distinctBy { it.uri }.sortedWith(compareBy<MediaEntry> { tvEpisode(it).season }
                .thenBy { tvEpisode(it).episode }.thenBy { tvEpisode(it).part }.thenBy { it.title.lowercase() })) }
        .sortedBy { it.title.lowercase() }

    /** A source folder such as `V collection/Show Name/Season 1/file.mkv` groups related shows
     * but preserves their different series and episode identities. */
    fun tvCollections(entries: List<MediaEntry>): List<TvCollectionGroup> {
        val groups = linkedMapOf<String, MutableList<TvShowGroup>>()
        val labels = linkedMapOf<String, String>()
        val all = tvShows(entries)
        all.forEach { series ->
            val first = series.episodes.firstOrNull() ?: return@forEach
            val parts = pathParts(first)
            val firstFolder = parts.firstOrNull()?.let(::tidy).orEmpty()
            if (first.sourceUri.isBlank() || parts.size < 3 || !firstFolder.contains("collection", true)) return@forEach
            val key = "folder:${first.sourceUri}:${canonical(firstFolder)}"
            labels[key] = firstFolder
            groups.getOrPut(key) { mutableListOf() }.add(series)
        }
        return groups.filterValues { it.distinctBy(TvShowGroup::key).size >= 2 }
            .map { (key, shows) -> TvCollectionGroup(key, labels[key].orEmpty(), shows.distinctBy(TvShowGroup::key)) }
            .sortedBy { it.title.lowercase() }
    }

    /** Only candidates absent on *two* completed scans can enter a missing-record cleanup. */
    fun confirmedMissing(previous: List<String>, current: List<String>): List<String> =
        current.distinct().filter { it in previous.toSet() }

    /** Do not infer duplicates from title alone: only identical URI *and media type* qualify. */
    fun exactDuplicateCount(entries: List<MediaEntry>): Int = entries.size -
        entries.distinctBy { it.uri to it.type }.size

    fun repairExactDuplicates(entries: List<MediaEntry>): List<MediaEntry> = entries
        .groupBy { it.uri to it.type }.values.map { group ->
            val first = group.first()
            first.copy(favourite = group.any { it.favourite }, watched = group.any { it.watched },
                positionMs = group.maxOf { it.positionMs },
                art = group.firstOrNull { it.art.isNotBlank() }?.art.orEmpty(),
                backdrop = group.firstOrNull { it.backdrop.isNotBlank() }?.backdrop.orEmpty(),
                plot = group.firstOrNull { it.plot.isNotBlank() }?.plot.orEmpty(),
                genres = group.firstOrNull { it.genres.isNotBlank() }?.genres.orEmpty(),
                metadataSource = group.firstOrNull { it.metadataSource.isNotBlank() }?.metadataSource.orEmpty())
        }
}
