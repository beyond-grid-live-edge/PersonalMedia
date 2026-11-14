package com.personalmedia.android

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

/** Bounded JSON HTTP transport for Stremio API and manifest add-on resources.
 * Idempotent catalogue/meta/stream GET requests can follow safe temporary
 * redirects; credential-bearing account POST requests cannot.
 *
 * This file has no Android APIs so the redirect behaviour can be tested on
 * a regular JVM before building in Android Studio.
 */
internal object StremioJsonHttp {
    private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

    fun read(address: String, bodyJson: String?): String {
        var current = try { URI(address) } catch (_: Exception) {
            throw IllegalArgumentException("Invalid provider address")
        }
        require(current.scheme?.lowercase() in listOf("https", "http") &&
            !current.host.isNullOrBlank() && current.rawUserInfo == null) { "Unsupported provider address" }

        var hops = 0
        while (true) {
            val connection = try { current.toURL().openConnection() as HttpURLConnection }
            catch (_: Exception) { throw IllegalStateException("Network or provider unavailable") }
            connection.connectTimeout = 12000
            connection.readTimeout = 18000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (bodyJson != null) {
                    // Streaming mode prevents HttpURLConnection from transparently
                    // buffering/replaying credential-bearing POST data on redirects
                    // or authentication challenges. Only a single explicit POST is safe.
                    val payload = bodyJson.toByteArray(Charsets.UTF_8)
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(payload) }
                }
                val status = connection.responseCode
                if (StremioRedirectPolicy.isRedirect(status)) {
                    if (bodyJson != null)
                        throw IllegalStateException("Stremio API redirect blocked for account safety")
                    current = StremioRedirectPolicy.resolveGetRedirect(
                        current, connection.getHeaderField("Location"), hops)
                    hops++
                    continue
                }
                if (status !in 200..299) throw IllegalStateException("Provider returned HTTP $status")
                return connection.inputStream.use { stream ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > MAX_RESPONSE_BYTES)
                            throw IllegalStateException("Provider response too large")
                        out.write(buffer, 0, n)
                    }
                    out.toString("UTF-8")
                }
            } catch (e: IllegalStateException) { throw e }
            catch (_: Exception) { throw IllegalStateException("Network or provider unavailable") }
            finally { connection.disconnect() }
        }
    }
}
