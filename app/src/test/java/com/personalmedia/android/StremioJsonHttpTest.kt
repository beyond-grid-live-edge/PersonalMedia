package com.personalmedia.android

import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/** JUnit JVM integration tests using only java.net, not JDK's optional jdk.httpserver module. */
class StremioJsonHttpTest {
    @Test fun follows307FromLocalCatalogAndReturnsJson() {
        LocalServer { path ->
            when (path) {
                "/catalog/movie/top.json" -> HttpReply(307, location = "/data.json")
                "/data.json" -> HttpReply(200, body = "{\"metas\":[{\"name\":\"Test Title\"}]}")
                else -> HttpReply(404)
            }
        }.use { server ->
            val result = StremioJsonHttp.read("${server.url}/catalog/movie/top.json", null)
            assertTrue(result.contains("Test Title"))
            assertEquals(listOf("/catalog/movie/top.json", "/data.json"), server.requests.toList())
        }
    }

    @Test fun rejectsRedirectedPostWithoutSendingBodyToNextEndpoint() {
        LocalServer { path ->
            when (path) {
                "/api/login" -> HttpReply(307, location = "/destination")
                "/destination" -> HttpReply(200, body = "{\"wrong\":true}")
                else -> HttpReply(404)
            }
        }.use { server ->
            val error = runCatching {
                StremioJsonHttp.read("${server.url}/api/login", "{\"authKey\":\"private-token\"}")
            }.exceptionOrNull()
            assertNotNull(error)
            assertEquals(listOf("/api/login"), server.requests.toList())
            assertEquals(listOf("/api/login" to "{\"authKey\":\"private-token\"}"), server.postBodies.toList())
            assertFalse(error.toString().contains("private-token"))
        }
    }

    private data class HttpReply(val status: Int, val body: String = "", val location: String? = null)

    private class LocalServer(private val responseFor: (String) -> HttpReply) : Closeable {
        private val socket = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 250 }
        private val failure = AtomicReference<Throwable?>(null)
        val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val postBodies: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())
        val url: String = "http://127.0.0.1:${socket.localPort}"
        private val worker = Thread({ serve() }, "StremioRedirectTestServer").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            try {
                while (!socket.isClosed) {
                    val client = try { socket.accept() }
                    catch (_: SocketTimeoutException) { continue }
                    catch (_: SocketException) { break }
                    client.use {
                        client.soTimeout = 3000
                        val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                        val firstLine = reader.readLine() ?: return@use
                        val method = firstLine.substringBefore(' ')
                        val path = firstLine.split(' ').getOrNull(1) ?: return@use
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", ignoreCase = true))
                                length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                        // Consume POST bytes before closing the connection. Otherwise
                        // the fixture can cause a TCP reset and a JVM-specific retry,
                        // which makes the test flaky without indicating a real redirect.
                        require(length in 0..65536) { "Unexpected test request size" }
                        if (method == "POST") {
                            val buffer = CharArray(length)
                            var received = 0
                            while (received < length) {
                                val count = reader.read(buffer, received, length - received)
                                if (count < 0) break
                                received += count
                            }
                            require(received == length) { "Truncated test POST" }
                            postBodies.add(path to String(buffer))
                        }
                        requests.add(path)
                        val reply = responseFor(path)
                        val body = reply.body.toByteArray(StandardCharsets.UTF_8)
                        val response = buildString {
                            append("HTTP/1.1 ${reply.status} ${if (reply.status == 307) "Temporary Redirect" else "OK"}\r\n")
                            reply.location?.let { append("Location: $it\r\n") }
                            append("Content-Type: application/json\r\n")
                            append("Content-Length: ${body.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(StandardCharsets.UTF_8)
                        client.getOutputStream().apply { write(response); write(body); flush() }
                    }
                }
            } catch (e: Exception) {
                if (!socket.isClosed) failure.set(e)
            }
        }

        override fun close() {
            socket.close()
            worker.join(2000)
            failure.get()?.let { throw AssertionError("Local HTTP fixture failed", it) }
        }
    }
}
