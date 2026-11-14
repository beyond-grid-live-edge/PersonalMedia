package com.personalmedia.android

import java.net.URI

/**
 * Stremio add-on JSON requests can be temporarily redirected (notably Cinemeta's
 * catalog host). This policy is deliberately limited to idempotent GET requests.
 * Authenticated Stremio API POST requests never follow a redirect, because their
 * JSON bodies contain an auth key or password.
 *
 * Do not include the provider's URL or Location in errors: configured add-on
 * paths and queries may contain secret tokens.
 */
internal object StremioRedirectPolicy {
    const val MAX_HOPS = 5

    fun isRedirect(status: Int): Boolean = status in listOf(301, 302, 303, 307, 308)

    fun resolveGetRedirect(current: URI, location: String?, hops: Int): URI {
        if (hops >= MAX_HOPS) throw IllegalStateException("Provider redirected too many times")
        if (location.isNullOrBlank()) throw IllegalStateException("Provider redirected without a destination")
        val target = try { current.resolve(URI(location.trim())).normalize() }
        catch (_: Exception) { throw IllegalStateException("Provider sent an invalid redirect") }
        val oldScheme = current.scheme?.lowercase()
        val scheme = target.scheme?.lowercase()
        if (scheme !in listOf("http", "https") || target.host.isNullOrBlank() || target.rawUserInfo != null)
            throw IllegalStateException("Provider redirect destination is not allowed")
        if (oldScheme == "https" && scheme != "https")
            throw IllegalStateException("Provider redirect to insecure connection blocked")
        if (target.host.equals(current.host, ignoreCase = true) &&
            (effectivePort(target) == effectivePort(current) ||
                (oldScheme == "http" && scheme == "https" &&
                    effectivePort(current) == 80 && effectivePort(target) == 443))) return target

        // Cinemeta has two public HTTPS origins. Its current catalogue route
        // is v3-cinemeta.strem.io/catalog/{type}/{id}[/{extras}].json
        //   -> cinemeta-catalogs.strem.io/{id}/catalog/{type}/{id}[/{extras}].json.
        // Older accounts can encounter the reverse origin migration too.
        // Never permit a generic cross-host redirect: only the exact known
        // origin pair, expected catalogue path, and unchanged query are valid.
        // All redirected GET requests send *only* Accept: application/json;
        // authKey POST bodies, cookies and add-on headers are not forwarded.
        if (oldScheme == "https" && scheme == "https" &&
            effectivePort(current) == 443 && effectivePort(target) == 443 &&
            current.rawUserInfo == null && target.rawUserInfo == null &&
            target.rawFragment == null && current.rawQuery == target.rawQuery &&
            isCinemetaCatalogueRedirect(current, target)) return target

        throw IllegalStateException("Provider redirected to a different host; blocked for account safety")
    }

    private fun isCinemetaCatalogueRedirect(current: URI, target: URI): Boolean {
        val from = current.host ?: return false
        val to = target.host ?: return false
        if (from.equals("v3-cinemeta.strem.io", true) &&
            to.equals("cinemeta-catalogs.strem.io", true)) {
            val path = current.rawPath ?: return false
            val segments = path.split('/')
            if (segments.size !in 4..5 || segments[0].isNotEmpty() ||
                segments[1] != "catalog" || segments[2] !in listOf("movie", "series")) return false
            // A catalogue id is the last segment ("top.json") or the
            // penultimate segment ("top/genre=...json"). It is never a URL.
            val id = segments[3].removeSuffix(".json")
            if (!id.matches(Regex("[A-Za-z0-9_-]{1,80}")) ||
                !path.endsWith(".json") || segments.last().isEmpty()) return false
            return target.rawPath == "/$id$path"
        }
        // Retain the compatibility path allowed by STR-2B, but restrict it to
        // the official origin pair and a public catalogue/metadata JSON path.
        if (from.equals("cinemeta-catalogs.strem.io", true) &&
            to.equals("v3-cinemeta.strem.io", true)) {
            val sourcePath = current.rawPath.orEmpty()
            val targetPath = target.rawPath.orEmpty()
            return targetPath.startsWith("/catalog/") && targetPath.endsWith(".json") &&
                (sourcePath == targetPath || sourcePath.endsWith(targetPath))
        }
        return false
    }

    private fun effectivePort(uri: URI): Int = uri.port.takeIf { it >= 0 } ?: when (uri.scheme?.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }
}
