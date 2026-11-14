package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class StremioAddonManagePolicyTest {
    @Test fun acceptsPublicHttpsManifestOnly() {
        assertTrue(StremioAddonManagePolicy.acceptableManifestUrl("https://stremio.example/manifest.json"))
        assertTrue(StremioAddonManagePolicy.acceptableManifestUrl("https://configured.example/secret/manifest.json?token=PRIVATE"))
        listOf("http://stremio.example/manifest.json", "https://127.0.0.1/manifest.json",
            "https://192.168.1.1/manifest.json", "https://me:pass@stremio.example/manifest.json",
            "https://stremio.example:8443/manifest.json", "https://stremio.example/foo.json",
            "https://stremio.example/manifest.json#secret", "file:///etc/passwd").forEach {
            assertFalse(StremioAddonManagePolicy.acceptableManifestUrl(it))
        }
    }
    @Test fun contentFilteringMatchesReference() {
        assertTrue(StremioAddonManagePolicy.matchesType(listOf("movie", "series"), "Movies"))
        assertTrue(StremioAddonManagePolicy.matchesType(listOf("anime"), "Anime"))
        assertTrue(StremioAddonManagePolicy.matchesType(listOf("tv"), "Channels"))
        assertFalse(StremioAddonManagePolicy.matchesType(listOf("movie"), "Podcasts"))
        assertTrue(StremioAddonManagePolicy.matchesType(listOf("other"), "Others"))
    }
    @Test fun descriptionsDoNotExposeFullLinks() {
        assertFalse(StremioAddonManagePolicy.safeDescription("Secret: https://host.example/manifest.json?token=private")
            .contains("token=private"))
    }
}
