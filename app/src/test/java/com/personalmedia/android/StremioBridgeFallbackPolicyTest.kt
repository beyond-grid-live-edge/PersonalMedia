package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test

class StremioBridgeFallbackPolicyTest {
    private val media = StremioSourceInspection(
        "A verified media response", retryUrl = "https://cdn.example/v.mp4?sig=PRIVATE",
        bridgeUrl = "https://cdn.example/v.mp4?sig=PRIVATE", httpStatus = 206)

    @Test fun onlyRealLoadFailuresCanTriggerFallback() {
        assertTrue(StremioBridgeFallbackPolicy.eligible("failed", -13, false, media))
        assertFalse(StremioBridgeFallbackPolicy.eligible("loading", -13, false, media))
        assertFalse(StremioBridgeFallbackPolicy.eligible("failed", -17, false, media))
        assertFalse(StremioBridgeFallbackPolicy.eligible("failed", -13, true, media))
    }

    @Test fun onlyVerifiedHttp206AndEligibleHttpsCanAutoBridge() {
        assertFalse(StremioBridgeFallbackPolicy.eligible("failed", -13, false,
            media.copy(httpStatus = 200)))
        assertFalse(StremioBridgeFallbackPolicy.eligible("failed", -13, false,
            media.copy(bridgeUrl = null)))
        for (unsafe in listOf("http://cdn.example/v.mp4", "https://me:pw@cdn.example/v.mp4",
                "https://cdn.example:8443/v.mp4", "https://cdn.example/v.mp4#frag", "https://127.0.0.1/media", "https://192.168.1.1/media", "not a url")) {
            assertFalse(StremioBridgeFallbackPolicy.eligible("failed", -13, false,
                media.copy(bridgeUrl = unsafe)))
        }
    }

    @Test fun noPrivateSourceUrlsLeakIntoUserFacingMessage() {
        assertFalse(media.message.contains("PRIVATE"))
        assertFalse(media.message.contains("cdn.example"))
    }
}
