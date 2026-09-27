package xyz.five82.takeup

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.LoomException
import xyz.five82.takeup.api.OFFLINE_MESSAGE
import java.io.IOException
import java.net.InetSocketAddress

class LoomApiHttpTest {
    private lateinit var server: HttpServer
    private lateinit var api: LoomApi
    private val requests = mutableListOf<RecordedRequest>()
    private var response: (RecordedRequest) -> Pair<Int, ByteArray> = { 200 to "{}".toByteArray() }

    @Before fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = RecordedRequest(exchange.requestMethod, exchange.requestURI.toString(), exchange.requestBody.readBytes().decodeToString())
            synchronized(requests) { requests += request }
            val (code, body) = response(request)
            exchange.sendResponseHeaders(code, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        api = LoomApi("http://127.0.0.1:${server.address.port}")
    }

    @After fun stopServer() = server.stop(0)

    @Test fun `browse decodes lists and encodes query parameters`() = runBlocking {
        response = { request ->
            val body = when (request.path.substringBefore('?')) {
                "/api/v1/libraries" -> """{"items":[{"id":3,"kind":"movies"}]}"""
                "/api/v1/genres" -> """{"items":[{"id":4,"name":"Comedy"}]}"""
                "/api/v1/collections" -> """{"items":null}"""
                "/api/v1/items" -> """{"items":[{"id":7,"title":"A Film"}]}"""
                "/api/v1/items/7/children" -> """{"items":[]}"""
                "/api/v1/search" -> """{"items":[{"id":7}],"fuzzy":true}"""
                "/api/v1/items/7" -> """{"id":7,"title":"A Film"}"""
                else -> "{}"
            }
            200 to body.toByteArray()
        }
        api.health()
        assertEquals(3L, api.libraries().single().id)
        assertEquals("Comedy", api.genres().single().name)
        assertTrue(api.collections().isEmpty())
        assertEquals(7L, api.item(7).id)
        assertTrue(api.children(7).isEmpty())
        assertEquals("A Film", api.items("movies & shorts", 4, 5, 10).single().title)
        assertTrue(api.search("A & B", 3).fuzzy)
        assertEquals("/api/v1/health", requests[0].path)
        assertEquals("/api/v1/items/7/children?limit=200&offset=0", requests[5].path)
        val itemsUrl = (api.baseUrl + requests[6].path).toHttpUrl()
        assertEquals("movies & shorts", itemsUrl.queryParameter("library"))
        assertEquals("4", itemsUrl.queryParameter("genre_id"))
        assertEquals("5", itemsUrl.queryParameter("limit"))
        assertEquals("10", itemsUrl.queryParameter("offset"))
        assertEquals("A & B", (api.baseUrl + requests[7].path).toHttpUrl().queryParameter("q"))
    }

    @Test fun `top level and child lists walk full pages then stop at a short page`() = runBlocking {
        response = { request ->
            val offset = (api.baseUrl + request.path).toHttpUrl().queryParameter("offset")
            val count = if (offset == "0") LoomApi.PAGE_LIMIT else 1
            val items = (1..count).joinToString(",") { """{"id":${it + (offset?.toInt() ?: 0)}}""" }
            200 to """{"items":[$items]}""".toByteArray()
        }
        assertEquals(201, api.allItems("movies").size)
        assertEquals(201L, api.allItems("movies", 9).last().id)
        assertEquals(201, api.children(4).size)
        assertEquals(listOf("0", "200"), requests.take(2).map {
            (api.baseUrl + it.path).toHttpUrl().queryParameter("offset")
        })
        assertEquals("9", (api.baseUrl + requests[2].path).toHttpUrl().queryParameter("genre_id"))
        assertEquals("/api/v1/items/4/children", requests[4].path.substringBefore('?'))
    }

    @Test fun `home playback images and scan status decode server responses`() = runBlocking {
        response = { request ->
            val body = when (request.path) {
                "/api/v1/home" -> """{"featured":{"id":7},"next_up":[]}"""
                "/api/v1/items/7/playback" -> """{"item_id":7,"stream_url":"/stream/7"}"""
                "/api/v1/items/7/images/poster/options" -> """{"items":[{"provider":"tmdb","selected":true}]}"""
                "/api/v1/scan" -> """{"running":true,"library":"movies"}"""
                else -> "{}"
            }
            200 to body.toByteArray()
        }
        assertEquals(7L, api.home().featured?.id)
        assertEquals("/stream/7", api.playback(7).streamUrl)
        assertTrue(api.imageOptions(7, "poster").single().selected)
        assertTrue(api.scanStatus().running)
        assertEquals(listOf("/api/v1/home", "/api/v1/items/7/playback",
            "/api/v1/items/7/images/poster/options", "/api/v1/scan"), requests.map { it.path })
    }

    @Test fun `writes use the expected verbs paths and JSON bodies`() = runBlocking {
        response = { request ->
            200 to if (request.path.endsWith("/progress")) """{"position_ms":12,"duration_ms":90}""".toByteArray() else "{}".toByteArray()
        }
        assertEquals(12L, api.saveProgress(7, 12, 90).positionMs)
        api.markPlayed(7)
        api.clearPlayed(7)
        api.selectImage(7, "poster", "tmdb", "/art/1")
        api.resetImage(7, "poster")
        api.triggerScan()
        assertEquals(listOf("PUT", "POST", "DELETE", "PUT", "POST", "POST"), requests.map { it.method })
        assertEquals(listOf("/api/v1/items/7/progress", "/api/v1/items/7/played", "/api/v1/items/7/played", "/api/v1/items/7/images/poster", "/api/v1/items/7/images/poster/reset", "/api/v1/scan"), requests.map { it.path })
        assertEquals(12L, JsonParser.parseString(requests[0].body).asJsonObject["position_ms"].asLong)
        assertEquals(90L, JsonParser.parseString(requests[0].body).asJsonObject["duration_ms"].asLong)
        assertEquals("tmdb", JsonParser.parseString(requests[3].body).asJsonObject["provider"].asString)
        assertEquals("/art/1", JsonParser.parseString(requests[3].body).asJsonObject["provider_path"].asString)
        assertTrue(requests[1].body.isEmpty())
        assertTrue(requests[2].body.isEmpty())
    }

    @Test fun `HTTP errors preserve Loom messages and fall back for malformed bodies`() {
        response = { 404 to """{"error":"Not found"}""".toByteArray() }
        val missing = assertThrows(LoomException::class.java) { runBlocking { api.item(3) } }
        assertEquals(404, missing.code)
        assertEquals("Not found", missing.message)
        response = { 503 to "unavailable".toByteArray() }
        assertEquals("Loom returned HTTP 503", assertThrows(LoomException::class.java) {
            runBlocking { api.health() }
        }.message)
        assertEquals("Loom returned HTTP 503", assertThrows(LoomException::class.java) {
            runBlocking { api.fetchBytes(api.baseUrl + "/image") }
        }.message)
    }

    @Test fun `requests without a server fail before connecting and the gate protects bytes too`() {
        api.baseUrl = null
        assertEquals("No server configured", assertThrows(LoomException::class.java) {
            runBlocking { api.health() }
        }.message)
        api.baseUrl = "not a server"
        assertEquals("Invalid server address", assertThrows(LoomException::class.java) {
            runBlocking { api.health() }
        }.message)
        val gated = LoomApi(api.baseUrl, blocked = { true })
        gated.baseUrl = "http://127.0.0.1:${server.address.port}"
        assertEquals(OFFLINE_MESSAGE, assertThrows(IOException::class.java) {
            runBlocking { gated.health() }
        }.message)
        assertEquals(OFFLINE_MESSAGE, assertThrows(IOException::class.java) {
            runBlocking { gated.fetchBytes(gated.baseUrl + "/image") }
        }.message)
        assertTrue(requests.isEmpty())
    }

    @Test fun `binary artwork fetch returns exact bytes`() = runBlocking {
        val bytes = byteArrayOf(0, 1, -1, 42)
        response = { 200 to bytes }
        assertArrayEquals(bytes, api.fetchBytes(api.baseUrl + "/image"))
        assertEquals("GET", requests.single().method)
    }

    private data class RecordedRequest(val method: String, val path: String, val body: String)
}
