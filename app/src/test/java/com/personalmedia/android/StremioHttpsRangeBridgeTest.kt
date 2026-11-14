package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.net.URL

class StremioHttpsRangeBridgeTest {
    @Test fun rangeValidation() {
        assertTrue(StremioHttpsRangeBridge.validRange("bytes=0-0"))
        assertTrue(StremioHttpsRangeBridge.validRange("bytes=500-"))
        assertTrue(StremioHttpsRangeBridge.validRange("bytes=-500"))
        assertFalse(StremioHttpsRangeBridge.validRange("bytes=0-2,4-6"))
        assertFalse(StremioHttpsRangeBridge.validRange("bytes=0-\r\nX: bad"))
        assertFalse(StremioHttpsRangeBridge.validRange("bytes=-0"))
        assertTrue(StremioHttpsRangeBridge.validContentRange("bytes 20-25/100", "bytes=20-25"))
        assertFalse(StremioHttpsRangeBridge.validContentRange("bytes 19-25/100", "bytes=20-25"))
        assertFalse(StremioHttpsRangeBridge.validContentRange("bytes 20-101/100", "bytes=20-"))
    }

    @Test fun loopbackRangeAndAuth() {
        var requestCount = 0
        val bridge = StremioHttpsRangeBridge("https://1.1.1.1/video.mp4") { url ->
            requestCount++
            FakeConnection(URL(url.toASCIIString()))
        }
        val local = bridge.start()
        try {
            val parsed = URI(local)
            assertEquals("127.0.0.1", parsed.host)
            val forbidden = rawRequest(parsed.port, "/wrong/media", "bytes=5-9")
            assertTrue(forbidden.startsWith("HTTP/1.1 403"))
            assertEquals(0, requestCount)
            val body = rawRequest(parsed.port, parsed.rawPath, "bytes=5-9")
            assertTrue(body.startsWith("HTTP/1.1 206"))
            assertTrue(body.contains("Content-Range: bytes 5-9/100"))
            assertTrue(body.endsWith("56789"))
            assertEquals(1, requestCount)
            val bad = rawRequest(parsed.port, parsed.rawPath, "bytes=1-3,5-7")
            assertTrue(bad.startsWith("HTTP/1.1 416"))
            assertEquals(1, requestCount)
        } finally { bridge.close() }
    }

    private fun rawRequest(port: Int, path: String, range: String): String = Socket("127.0.0.1", port).use { sock ->
        sock.soTimeout = 5000
        sock.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: $range\r\n\r\n".toByteArray())
        sock.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
    }

    private class FakeConnection(url: URL) : HttpURLConnection(url) {
        override fun connect() { connected = true }
        override fun disconnect() { connected = false }
        override fun usingProxy() = false
        override fun getResponseCode() = 206
        override fun getContentType() = "video/mp4"
        override fun getContentLengthLong() = 5L
        override fun getHeaderField(name: String): String? = when(name.lowercase()) {
            "content-range" -> "bytes 5-9/100"
            else -> null
        }
        override fun getInputStream() = ByteArrayInputStream("56789".toByteArray())
    }
}
