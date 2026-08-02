package xyz.five82.takeup.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class LoomClientTest {
    private lateinit var server: HttpServer
    private var address = ServerAddress.parse("http://127.0.0.1")
    private val moviesQuery = AtomicReference<String>()
    private val progressMethod = AtomicReference<String>()
    private val progressRequest = AtomicReference<String>()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        address = ServerAddress.parse("http://127.0.0.1:${server.address.port}")
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `uses Loom API paths and parses responses`() = runBlocking {
        val client = LoomClient()

        client.checkHealth(address)
        val movies = client.movies(address)
        val item = client.item(address, 42)
        val playback = client.playback(address, 42)
        client.saveProgress(address, 42, positionMs = 15_000, durationMs = 120_000)

        assertEquals("Test Movie", movies.single().title)
        assertEquals(30_000L, item.progress?.resumePositionMs)
        assertEquals("/api/v1/media/7", playback.streamPath)
        assertEquals("library=movies&kind=movie&limit=50&offset=0", moviesQuery.get())
        assertEquals("PUT", progressMethod.get())
        assertEquals(
            "{\"position_ms\":15000,\"duration_ms\":120000}",
            progressRequest.get(),
        )
    }

    private fun handle(exchange: HttpExchange) {
        val response = when (exchange.requestURI.path) {
            "/api/v1/health" -> """{"status":"ok"}"""
            "/api/v1/items" -> {
                moviesQuery.set(exchange.requestURI.query)
                """{"items":[{"id":42,"title":"Test Movie","year":2026}]}"""
            }
            "/api/v1/items/42" -> {
                """{"id":42,"title":"Test Movie","progress":{"position_ms":30000,"duration_ms":120000,"played":false,"resume_position_ms":30000}}"""
            }
            "/api/v1/items/42/playback" -> {
                """{"item_id":42,"media":{"duration_ms":120000,"container":"matroska"},"stream_url":"/api/v1/media/7"}"""
            }
            "/api/v1/items/42/progress" -> {
                progressMethod.set(exchange.requestMethod)
                progressRequest.set(exchange.requestBody.bufferedReader().use { it.readText() })
                """{"position_ms":15000,"duration_ms":120000,"played":false,"resume_position_ms":15000}"""
            }
            else -> {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return
            }
        }
        val bytes = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
