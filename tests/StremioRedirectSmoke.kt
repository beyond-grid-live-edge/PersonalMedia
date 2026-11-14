// Standalone JVM smoke test: no Android SDK/Gradle required.
package com.personalmedia.android

import java.net.URI

fun main() {
    val from = URI("https://cinemeta-catalogs.strem.io/catalog/movie/top.json")
    check(StremioRedirectPolicy.resolveGetRedirect(from,
        "https://v3-cinemeta.strem.io/catalog/movie/top.json", 0).host == "v3-cinemeta.strem.io")
    check(StremioRedirectPolicy.resolveGetRedirect(
        URI("https://v3-cinemeta.strem.io/catalog/movie/top.json"),
        "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json", 0).host == "cinemeta-catalogs.strem.io")
    check(runCatching { StremioRedirectPolicy.resolveGetRedirect(
        URI("https://v3-cinemeta.strem.io/catalog/movie/top.json"),
        "https://cinemeta-catalogs.strem.io/fake/catalog/movie/top.json", 0) }.isFailure)
    check(StremioRedirectPolicy.resolveGetRedirect(
        URI("https://example.org/a/catalog/movie/top.json"), "../series/top.json", 0).path ==
        "/a/catalog/series/top.json")
    listOf(
        Triple(URI("https://example.org/x"), "http://example.org/x", 0),
        Triple(URI("https://example.org/key/x"), "https://evil.example/key/x", 0),
        Triple(URI("https://example.org/x"), "/x", StremioRedirectPolicy.MAX_HOPS)
    ).forEach { (uri, location, n) ->
        check(runCatching { StremioRedirectPolicy.resolveGetRedirect(uri, location, n) }.isFailure)
    }
    check(StremioRedirectPolicy.isRedirect(307))
    check(StremioRedirectPolicy.isRedirect(308))
    check(!StremioRedirectPolicy.isRedirect(401))
    println("STR-2C Cinemeta redirect policy: all smoke assertions passed")
}
