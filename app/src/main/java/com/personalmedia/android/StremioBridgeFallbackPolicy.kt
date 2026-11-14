package com.personalmedia.android

import java.net.URI

/** STR-4e: one-shot automatic HTTPS bridge selection after native libmpv fails
 * to open media. This does not guess from UI text or attempt a provider bypass.
 * The IO-thread bridge will independently resolve and reject private addresses.
 * The caller MUST strip all provider headers before passing this URL to MPV.
 */
internal object StremioBridgeFallbackPolicy {
    fun eligible(phase: String, failureCode: Int, alreadyAttempted: Boolean,
        inspection: StremioSourceInspection): Boolean {
        if (phase != "failed" || failureCode != -13 || alreadyAttempted ||
            inspection.httpStatus != 206) return false
        val candidate = inspection.bridgeUrl ?: return false
        return try {
            val url = URI(candidate)
            url.scheme.equals("https", true) && !url.host.isNullOrBlank() &&
                url.rawUserInfo == null && url.fragment == null &&
                url.port in setOf(-1, 443) &&
                StremioSourceDiagnostic.redirectDestination(URI("https://public.example"), candidate) != null
        } catch (_: Exception) { false }
    }
}
