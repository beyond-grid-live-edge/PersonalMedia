package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class StremioRedirectPolicyTest {
    @Test fun followsCinemeta307() {
        val from = URI("https://cinemeta-catalogs.strem.io/catalog/movie/top.json")
        val result = StremioRedirectPolicy.resolveGetRedirect(from,
            "https://v3-cinemeta.strem.io/catalog/movie/top.json", 0)
        assertEquals("v3-cinemeta.strem.io", result.host)
        assertEquals("/catalog/movie/top.json", result.path)
    }

    @Test fun followsCinemetaOfficialCatalogue307() {
        val from = URI("https://v3-cinemeta.strem.io/catalog/movie/top.json")
        val result = StremioRedirectPolicy.resolveGetRedirect(from,
            "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json", 0)
        assertEquals("cinemeta-catalogs.strem.io", result.host)
        assertEquals("/top/catalog/movie/top.json", result.path)
    }

    @Test fun followsCinemetaSeriesAndGenreRoute() {
        val from = URI("https://v3-cinemeta.strem.io/catalog/series/top/genre=Action.json")
        val result = StremioRedirectPolicy.resolveGetRedirect(from,
            "https://cinemeta-catalogs.strem.io/top/catalog/series/top/genre=Action.json", 0)
        assertEquals("/top/catalog/series/top/genre=Action.json", result.path)
    }

    @Test fun blocksCinemetaWrongCatalogTarget() = expectError {
        StremioRedirectPolicy.resolveGetRedirect(
            URI("https://v3-cinemeta.strem.io/catalog/movie/top.json"),
            "https://cinemeta-catalogs.strem.io/imdbRating/catalog/movie/top.json", 0)
    }

    @Test fun blocksCinemetaCrossHostQueryTampering() = expectError {
        StremioRedirectPolicy.resolveGetRedirect(
            URI("https://v3-cinemeta.strem.io/catalog/movie/top.json?search=abc"),
            "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json?search=changed", 0)
    }

    @Test fun blocksNonCinemetaRedirectToCatalogueHost() = expectError {
        StremioRedirectPolicy.resolveGetRedirect(
            URI("https://otheraddon.example/catalog/movie/top.json"),
            "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json", 0)
    }

    @Test fun handlesRelativeRedirectAndEncodings() {
        val original = URI("https://example.org/a/catalog/movie/top.json")
        val target = StremioRedirectPolicy.resolveGetRedirect(original, "../series/top.json", 1)
        assertEquals("https://example.org/a/catalog/series/top.json", target.toASCIIString())
    }

    @Test fun blocksHttpsDowngrade() = expectError {
        StremioRedirectPolicy.resolveGetRedirect(URI("https://example.org/catalog/movie/top.json"),
            "http://example.org/catalog/movie/top.json", 0)
    }

    @Test fun blocksUnexpectedHostAndTokenDisclosure() = expectError {
        StremioRedirectPolicy.resolveGetRedirect(URI("https://example.org/private-key/catalog/movie/top.json"),
            "https://untrusted.example/private-key/catalog/movie/top.json", 0)
    }

    @Test fun blocksRedirectLoops() = expectError {
        StremioRedirectPolicy.resolveGetRedirect(URI("https://example.org/start"), "/start", 5)
    }

    @Test fun blocksMissingLocation() = expectError {
        StremioRedirectPolicy.resolveGetRedirect(URI("https://example.org/start"), null, 0)
    }

    @Test fun recognisesRedirectCodes() {
        assertTrue(StremioRedirectPolicy.isRedirect(307))
        assertTrue(StremioRedirectPolicy.isRedirect(308))
        assertFalse(StremioRedirectPolicy.isRedirect(200))
        assertFalse(StremioRedirectPolicy.isRedirect(401))
    }

    private inline fun expectError(block: () -> Unit) {
        try { block(); fail("Expected policy to reject redirect") }
        catch (_: IllegalStateException) { /* expected */ }
    }
}
