package com.personalmedia.android

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

fun main() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    var forwardedLoginBodies = 0
    server.createContext("/catalog/movie/top.json") { exchange ->
        exchange.responseHeaders.add("Location", "/catalog-result.json")
        exchange.sendResponseHeaders(307, -1)
        exchange.close()
    }
    server.createContext("/catalog-result.json") { exchange ->
        val bytes = "{\"metas\":[{\"name\":\"Test Film\"}]}".toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    server.createContext("/api/login") { exchange ->
        exchange.responseHeaders.add("Location", "/bad")
        exchange.sendResponseHeaders(307, -1)
        exchange.close()
    }
    server.createContext("/bad") { exchange ->
        forwardedLoginBodies++
        exchange.sendResponseHeaders(200, -1)
        exchange.close()
    }
    server.start()
    try {
        val base = "http://127.0.0.1:${server.address.port}"
        val result = StremioJsonHttp.read("$base/catalog/movie/top.json", null)
        check("Test Film" in result)
        check(runCatching { StremioJsonHttp.read("$base/api/login", "{\"authKey\":\"secret\"}") }.isFailure)
        check(forwardedLoginBodies == 0)
        println("STR-2 local HTTP 307 integration: GET follows, account POST stays blocked")
    } finally { server.stop(0) }
}
