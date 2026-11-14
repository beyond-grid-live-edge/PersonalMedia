package com.personalmedia.android

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Stremio-core-compatible watched field: {anchor video id}:{anchor index + 1}:{zlib/base64 bitmap}.
 * Video order is the original provider's chronological season/episode/release order.
 * The anchor moves saved flags when a provider inserts earlier episodes.
 * No lossy default is returned for corrupted/unknown remote data. */
internal object StremioWatchedCodec {
    private const val MAX_EPISODES = 10000
    private const val MAX_BYTES = (MAX_EPISODES + 7) / 8

    fun orderedVideos(videos: List<StremioVideo>): List<StremioVideo> = videos
        .distinctBy { it.id }
        .sortedWith(compareBy<StremioVideo>({ it.season }, { it.episode }, { it.released }))

    fun decode(raw: String, videoIds: List<String>): Set<String>? {
        if (videoIds.size > MAX_EPISODES) return null
        if (raw.isBlank()) return emptySet()
        val last = raw.lastIndexOf(':')
        if (last < 1) return null
        val before = raw.substring(0, last)
        val anchorSplit = before.lastIndexOf(':')
        if (anchorSplit < 1) return null
        val anchor = before.substring(0, anchorSplit)
        val anchorLength = before.substring(anchorSplit + 1).toIntOrNull() ?: return null
        if (anchorLength !in 1..MAX_EPISODES) return null
        val anchorIndex = videoIds.indexOf(anchor)
        if (anchorIndex < 0) return null // never guess an offset for a missing anchor
        val compressed = try { Base64.getDecoder().decode(raw.substring(last + 1)) }
            catch (_: IllegalArgumentException) { return null }
        if (compressed.size > 8192) return null
        val data = inflateBounded(compressed) ?: return null
        val offset = anchorLength - anchorIndex - 1
        return videoIds.indices.filter { index ->
            val old = index + offset
            old >= 0 && old / 8 < data.size && ((data[old / 8].toInt() ushr (old % 8)) and 1) != 0
        }.mapTo(linkedSetOf()) { videoIds[it] }
    }

    /** Encode a full watched snapshot. Do not call after a failed decode of an existing field. */
    fun encode(videoIds: List<String>, watched: Set<String>): String {
        require(videoIds.size <= MAX_EPISODES && videoIds.distinct().size == videoIds.size)
        val bits = ByteArray((videoIds.size + 7) / 8)
        videoIds.forEachIndexed { index, id ->
            if (id in watched) bits[index / 8] = (bits[index / 8].toInt() or (1 shl (index % 8))).toByte()
        }
        val last = videoIds.indexOfLast { it in watched }.coerceAtLeast(0)
        val anchor = videoIds.getOrNull(last) ?: "undefined"
        val zipper = Deflater(6)
        return try {
            zipper.setInput(bits); zipper.finish()
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(256)
            while (!zipper.finished()) {
                val n = zipper.deflate(chunk)
                check(n > 0) { "Unable to compress watched state" }
                output.write(chunk, 0, n)
            }
            "$anchor:${last + 1}:${Base64.getEncoder().encodeToString(output.toByteArray())}"
        } finally { zipper.end() }
    }

    private fun inflateBounded(zipped: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(zipped)
            val output = ByteArrayOutputStream()
            val buf = ByteArray(256)
            while (!inflater.finished()) {
                if (output.size() > MAX_BYTES) return null
                val n = inflater.inflate(buf)
                if (n > 0) output.write(buf, 0, n)
                else if (inflater.needsInput() || inflater.needsDictionary()) return null
                else return null
            }
            if (output.size() > MAX_BYTES) null else output.toByteArray()
        } catch (_: Exception) { null }
        finally { inflater.end() }
    }
}
