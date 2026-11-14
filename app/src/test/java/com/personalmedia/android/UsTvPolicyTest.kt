package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class UsTvPolicyTest {
    @Test fun onlyUsChannelsFromTheOriginalSite() {
        val html = """<a href="/channels/MiamiTV.us">Miami TV</a>
            <a href="/channels/BBCNews.uk">BBC News</a>
            <a href="https://example.invalid/channels/CNN.us">Not our site</a>
            <a href="/channels/ABCNewsLive.us"><img alt="ABC News Live"></a>"""
        val channels = UsTvPolicy.parseUsChannelLinks(html)
        assertEquals(2, channels.size)
        assertEquals("MiamiTV.us", channels[0].first)
        assertEquals("ABC News Live", channels[1].second)
    }

    @Test fun handlesPageEmbeddedUsCatalogWithoutOtherCountries() {
        val html = """<script>{"items":[{"id":"News24.us","name":"News 24"},
            {"id":"BBCOne.uk","name":"BBC One"}]}</script>"""
        assertEquals(listOf("News24.us" to "News 24"), UsTvPolicy.parseUsChannelLinks(html))
    }

    @Test fun siteSourceExtractionCannotImportUnrelatedOrLocalStreams() {
        val html = """{"url":"https://cdn.example/hls/live.m3u8?token=private"}
            {"url":"https://cdn.example/hls/live.m3u8?token=private"}
            {"url":"http://backup.example/stream.m3u8"}
            {"url":"http://192.168.1.1/stream.m3u8"}
            {"url":"https://example.invalid/unrelated.json"}"""
        val sources = UsTvPolicy.extractPageMedia(html)
        assertEquals(2, sources.size)
        assertTrue(sources[0].startsWith("https://"))
        assertFalse(sources.any { it.contains("192.168.1.1") })
    }

    @Test fun xmltvMappingMustBeExactAndUnambiguous() {
        val candidates = listOf("a.us" to "ABC News Live", "b.us" to "NBC News")
        assertEquals("a.us", UsTvPolicy.epgUniqueNameMatch("ABC News Live", candidates))
        assertNull(UsTvPolicy.epgUniqueNameMatch("NBC", candidates))
        assertNull(UsTvPolicy.epgUniqueNameMatch("ABC News Live", candidates + ("c.us" to "ABC News Live")))
        assertEquals(UsTvEpg.parseXmltvTime("20261112190000 +0000"),
            UsTvEpg.parseXmltvTime("20261112140000 -0500"))
    }
}
