package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class StremioSourceDiagnosticTest {
    @Test fun statusClassificationKeepsPrivateDetailsOut() {
        assertTrue(StremioSourceDiagnostic.response(403, "text/plain").contains("HTTP 403"))
        assertTrue(StremioSourceDiagnostic.response(404, "video/mp4").contains("no longer available"))
        assertTrue(StremioSourceDiagnostic.response(200, "text/html; charset=utf-8").contains("webpage"))
        assertTrue(StremioSourceDiagnostic.response(206, "video/mp4").contains("accepted"))
        assertTrue(StremioSourceDiagnostic.response(429, "application/json").contains("limiting"))
        for (code in listOf(200, 206, 302, 403, 404, 500)) {
            val message = StremioSourceDiagnostic.response(code, "video/mp4")
            assertFalse(message.contains("https://"))
            assertFalse(message.contains("Authorization"))
        }
    }

    @Test fun crossHostAndDowngradeAreNeverFollowed() {
        val from = URI("https://source.example/watch?token=SECRET")
        assertEquals("SAME_ORIGIN", StremioSourceDiagnostic.redirectMessage(from, "/video.mp4"))
        assertNotEquals("SAME_ORIGIN", StremioSourceDiagnostic.redirectMessage(from, "https://evil.example/copy"))
        assertNotEquals("SAME_ORIGIN", StremioSourceDiagnostic.redirectMessage(from, "http://source.example/video.mp4"))
        assertNotEquals("SAME_ORIGIN", StremioSourceDiagnostic.redirectMessage(from, "https://source.example:8443/video.mp4"))
        val redirection = StremioSourceDiagnostic.redirectMessage(from, "https://evil.example/copy?secret=SECRET")
        assertFalse(redirection.contains("SECRET"))
        assertFalse(redirection.contains("evil.example"))
    }


    @Test fun redirectTargetsRequireHttpsAndPublicDestinations() {
        val from = URI("https://source.example/media?token=SECRET")
        val cdn = StremioSourceDiagnostic.redirectDestination(from, "https://cdn.example/file.mp4?signature=SECRET")
        assertEquals("cdn.example", cdn?.host)
        assertEquals("/movie.mp4", StremioSourceDiagnostic.redirectDestination(from, "/movie.mp4")?.path)
        assertNull(StremioSourceDiagnostic.redirectDestination(from, "http://cdn.example/file.mp4"))
        assertNull(StremioSourceDiagnostic.redirectDestination(from, "https://127.0.0.1/secret"))
        assertNull(StremioSourceDiagnostic.redirectDestination(from, "https://192.168.1.1/media"))
        assertNull(StremioSourceDiagnostic.redirectDestination(from, "https://[::1]/media"))
        assertNull(StremioSourceDiagnostic.redirectDestination(from, "file:///etc/passwd"))
        assertNull(StremioSourceDiagnostic.redirectDestination(from, "https://evil@cdn.example/media"))
        assertNull(StremioSourceDiagnostic.redirectDestination(from, "https://cdn.example/media#fragment"))
        assertFalse(StremioSourceDiagnostic.redirectMessage(from, cdn.toString()).contains("SECRET"))
    }

    @Test fun onlyUsableHttpMediaResponsesMayOfferRetry() {
        assertTrue(StremioSourceDiagnostic.playableResponse(206, "video/mp4"))
        assertTrue(StremioSourceDiagnostic.playableResponse(200, "application/octet-stream"))
        assertFalse(StremioSourceDiagnostic.playableResponse(200, "text/html"))
        assertFalse(StremioSourceDiagnostic.playableResponse(200, "application/json"))
        assertFalse(StremioSourceDiagnostic.playableResponse(403, "video/mp4"))
    }
}
