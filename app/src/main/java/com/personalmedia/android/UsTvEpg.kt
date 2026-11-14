package com.personalmedia.android

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FilterInputStream
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

internal data class UsProgramme(
    val channelId: String, val title: String, val startMs: Long, val stopMs: Long,
    val description: String = ""
)

internal class UsTvEpg {
    private class CappedInput(input: java.io.InputStream, private val limit: Long) : FilterInputStream(input) {
        private var size: Long = 0L
        override fun read(): Int {
            val value = super.read()
            if (value >= 0 && ++size > limit) throw java.io.IOException("Guide exceeds safety size limit")
            return value
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            // Read directly from the wrapped stream; FilterInputStream's own read may call read().
            val count = `in`.read(b, off, len)
            if (count > 0) { size += count; if (size > limit) throw java.io.IOException("Guide exceeds safety size limit") }
            return count
        }
    }

    fun refresh(url: String, channels: List<UsChannel>, mappings: Map<String, String>): List<UsProgramme> {
        require(url.startsWith("https://", true)) { "Only HTTPS XMLTV guides are accepted" }
        var location = URI(url.trim())
        var connection: HttpURLConnection? = null
        try {
            var ready = false
            repeat(3) {
                require(location.scheme.equals("https", true) && location.host != null &&
                    location.rawUserInfo == null && location.port in setOf(-1, 443) &&
                    !location.host.equals("localhost", true) && !location.host.endsWith(".local")) {
                    "Only public HTTPS guide addresses are accepted"
                }
                connection = URL(location.toASCIIString()).openConnection() as HttpURLConnection
                connection!!.instanceFollowRedirects = false
                connection!!.connectTimeout = 12_000
                connection!!.readTimeout = 22_000
                val status = connection!!.responseCode
                if (status in listOf(301,302,303,307,308)) {
                    location = location.resolve(connection!!.getHeaderField("Location") ?: "")
                    connection!!.disconnect(); connection = null
                } else {
                    if (status != 200) throw IllegalStateException("Guide returned HTTP $status")
                    ready = true
                    return loadGuide(connection!!, channels, mappings)
                }
            }
            if (!ready) throw IllegalStateException("Too many guide redirects")
            return emptyList()
        } finally { connection?.disconnect() }
    }

    private fun loadGuide(c: HttpURLConnection, channels: List<UsChannel>, mappings: Map<String, String>): List<UsProgramme> {
        val results = ArrayList<UsProgramme>()
        val matching = mutableMapOf<String, String>()
        val names = channels.map { it.id to it.name }
        // Explicit ID map always takes precedence over fuzzy matching.
        for ((key, value) in mappings) if (value.isNotBlank() && channels.any { it.id == key })
            matching[value] = key
        val windowStart = System.currentTimeMillis() - 6L * 3600_000
        val windowStop = windowStart + 42L * 3600_000
        c.inputStream.use { raw ->
            val limited = CappedInput(raw, 38_000_000)
            val buffered = BufferedInputStream(limited)
            buffered.mark(4)
            val b0 = buffered.read(); val b1 = buffered.read()
            buffered.reset()
            val zip = b0 == 0x1f && b1 == 0x8b
            val uncompressed = CappedInput(if (zip) GZIPInputStream(buffered) else buffered, 240_000_000)
            uncompressed.use { input ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false) }
                parser.setInput(input, "UTF-8")
                var currentId = ""
                var displayName = ""
                var programmeId = ""
                var start = 0L; var stop = 0L; var title = ""; var desc = ""
                var textTarget = ""
                var count = 0
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "channel" -> { currentId = parser.getAttributeValue(null, "id").orEmpty(); displayName = "" }
                            "display-name" -> if (currentId.isNotEmpty()) textTarget = "display-name"
                            "programme" -> {
                                programmeId = parser.getAttributeValue(null, "channel").orEmpty()
                                start = parseXmltvTime(parser.getAttributeValue(null, "start"))
                                stop = parseXmltvTime(parser.getAttributeValue(null, "stop"))
                                title = ""; desc = ""
                            }
                            "title" -> if (programmeId.isNotEmpty()) textTarget = "title"
                            "desc" -> if (programmeId.isNotEmpty()) textTarget = "desc"
                        }
                        XmlPullParser.TEXT -> when (textTarget) {
                            "display-name" -> if (displayName.length < 150) displayName += parser.text
                            "title" -> if (title.length < 180) title += parser.text
                            "desc" -> if (desc.length < 300) desc += parser.text
                        }
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "display-name", "title", "desc" -> textTarget = ""
                            "channel" -> {
                                if (!matching.containsKey(currentId)) {
                                    val exact = channels.singleOrNull { it.id.equals(currentId, true) }?.id
                                    val viaName = UsTvPolicy.epgUniqueNameMatch(displayName, names)
                                    val selected = exact ?: viaName
                                    if (selected != null && mappings[selected].isNullOrBlank()) matching[currentId] = selected
                                }
                                currentId = ""; displayName = ""
                            }
                            "programme" -> {
                                val usId = matching[programmeId]
                                if (usId != null && start in windowStart..windowStop && stop > start && results.size < 5000)
                                    results.add(UsProgramme(usId, title.ifBlank { "Programme" }.take(180), start, stop, desc.take(300)))
                                programmeId = ""; textTarget = ""
                                if (++count >= 1_600_000) break // protect the user's phone from unexpectedly huge XMLTV files
                            }
                        }
                    }
                    parser.next()
                }
            }
        }
        return results.sortedWith(compareBy({ it.channelId }, { it.startMs }))
    }

    companion object {
        fun parseXmltvTime(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            val tokens = raw.trim().split(Regex("\\s+"))
            if (!Regex("\\d{14}").matches(tokens[0])) return 0L
            val suffix = tokens.getOrNull(1) ?: "+0000"
            if (!Regex("[+-]\\d{4}").matches(suffix)) return 0L
            return runCatching {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
                    isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
                }.parse("${tokens[0]} $suffix")?.time ?: 0L
            }.getOrDefault(0L)
        }
    }
}
