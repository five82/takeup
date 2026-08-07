package xyz.five82.takeup.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LoomClientTest {
    private lateinit var server: HttpServer
    private var address = ServerAddress.parse("http://127.0.0.1")
    private val moviesQueries = CopyOnWriteArrayList<String>()
    private val showsQueries = CopyOnWriteArrayList<String>()
    private val childrenQueries = CopyOnWriteArrayList<String>()
    private val paginateMovies = AtomicBoolean(false)
    private val continueQuery = AtomicReference<String>()
    private val recentlyAddedQuery = AtomicReference<String>()
    private val progressMethod = AtomicReference<String>()
    private val progressRequest = AtomicReference<String>()
    private val artworkRequests = CopyOnWriteArrayList<String>()
    private val searchQueries = CopyOnWriteArrayList<String>()

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
        val continuing = client.continueWatching(address)
        val recentlyAdded = client.recentlyAdded(address)
        val item = client.item(address, 42)
        val playback = client.playback(address, 42)
        client.saveProgress(address, 42, positionMs = 15_000, durationMs = 120_000)

        assertEquals("Test Movie", movies.single().title)
        assertEquals("Continue Movie", continuing.single().title)
        assertEquals("Recent Movie", recentlyAdded.single().title)
        assertEquals(30_000L, item.progress?.resumePositionMs)
        assertEquals("/api/v1/media/7", playback.streamPath)
        assertEquals(
            listOf("library=movies&kind=movie&limit=200&offset=0"),
            moviesQueries,
        )
        assertEquals("limit=20", continueQuery.get())
        assertEquals("limit=20", recentlyAddedQuery.get())
        assertEquals("PUT", progressMethod.get())
        assertEquals(
            "{\"position_ms\":15000,\"duration_ms\":120000}",
            progressRequest.get(),
        )
    }

    @Test
    fun `loads show and episode hierarchy`() = runBlocking {
        val client = LoomClient()

        val shows = client.shows(address)
        val seasons = client.children(address, shows.single().id)
        val episodes = client.children(address, seasons.single().id)

        assertEquals("Test Show", shows.single().title)
        assertEquals("Specials", seasons.single().title)
        assertEquals("Pilot", episodes.single().title)
        assertEquals(
            listOf("library=tv&kind=show&limit=200&offset=0"),
            showsQueries,
        )
        assertEquals(
            listOf(
                "50:limit=200&offset=0",
                "51:limit=200&offset=0",
            ),
            childrenQueries,
        )
    }

    @Test
    fun `loads selects and resets artwork`() = runBlocking {
        val client = LoomClient()
        val options = client.artworkOptions(address, 42, ArtworkKind.POSTER)

        client.selectArtwork(address, 42, ArtworkKind.POSTER, options.single())
        client.resetArtwork(address, 42, ArtworkKind.POSTER)

        assertEquals("/alternate.jpg", options.single().providerPath)
        assertEquals(
            listOf(
                "GET /api/v1/items/42/images/poster/options ",
                "PUT /api/v1/items/42/images/poster {\"provider\":\"tmdb\",\"provider_path\":\"/alternate.jpg\"}",
                "POST /api/v1/items/42/images/poster/reset ",
            ),
            artworkRequests,
        )
    }

    @Test
    fun `loads thumb artwork options`() = runBlocking {
        val options = LoomClient().artworkOptions(address, 42, ArtworkKind.THUMB)

        assertEquals("/titled.jpg", options.single().providerPath)
        assertEquals(
            listOf("GET /api/v1/items/42/images/thumb/options "),
            artworkRequests,
        )
    }

    @Test
    fun `loads every movie page`() = runBlocking {
        paginateMovies.set(true)

        val movies = LoomClient().movies(address)

        assertEquals(201, movies.size)
        assertEquals(201L, movies.last().id)
        assertEquals(
            listOf(
                "library=movies&kind=movie&limit=200&offset=0",
                "library=movies&kind=movie&limit=200&offset=200",
            ),
            moviesQueries,
        )
    }

    @Test
    fun `searches items and parses episode context`() = runBlocking {
        val results = LoomClient().search(address, "test show")

        assertEquals("Test Movie", results[0].title)
        assertEquals("Pilot", results[1].title)
        assertEquals("Test Show", results[1].seriesTitle)
        assertEquals("Season 1", results[1].seasonTitle)
        assertEquals("Test Show \u00B7 Season 1 \u00B7 S01E01", results[1].episodeContext())
        assertEquals(listOf("q=test+show&limit=200&offset=0"), searchQueries)
    }

    @Test
    fun `loads movie genres`() = runBlocking {
        val genres = LoomClient().genres(address)

        assertEquals(
            listOf(
                GenreSummary(28, "Action", 12),
                GenreSummary(878, "Science Fiction", 4),
            ),
            genres,
        )
    }

    @Test
    fun `filters movies by genre`() = runBlocking {
        LoomClient().movies(address, genreId = 878)

        assertEquals(
            listOf("library=movies&kind=movie&genre_id=878&limit=200&offset=0"),
            moviesQueries,
        )
    }

    private fun handle(exchange: HttpExchange) {
        val response = when (exchange.requestURI.path) {
            "/api/v1/health" -> """{"status":"ok"}"""
            "/api/v1/genres" -> """{"items":[{"id":28,"name":"Action","item_count":12},{"id":878,"name":"Science Fiction","item_count":4}]}"""
            "/api/v1/items" -> {
                if (exchange.requestURI.query.startsWith("library=tv")) {
                    showsQueries += exchange.requestURI.query
                    """{"items":[{"id":50,"kind":"show","title":"Test Show","year":2026}]}"""
                } else {
                    moviesQueries += exchange.requestURI.query
                    if (paginateMovies.get()) {
                        paginatedMovies(exchange.requestURI.query)
                    } else {
                        """{"items":[{"id":42,"kind":"movie","title":"Test Movie","year":2026}]}"""
                    }
                }
            }
            "/api/v1/items/50/children" -> {
                childrenQueries += "50:${exchange.requestURI.query}"
                """{"items":[{"id":51,"kind":"season","title":"Specials","season_number":0}]}"""
            }
            "/api/v1/items/51/children" -> {
                childrenQueries += "51:${exchange.requestURI.query}"
                """{"items":[{"id":52,"kind":"episode","title":"Pilot","season_number":0,"episode_number":1}]}"""
            }
            "/api/v1/search" -> {
                searchQueries += exchange.requestURI.rawQuery
                """{"items":[
                    {"id":42,"kind":"movie","title":"Test Movie","year":2026},
                    {"id":52,"kind":"episode","title":"Pilot","season_number":1,"episode_number":1,
                    "series_title":"Test Show","season_title":"Season 1"}
                ]}"""
            }
            "/api/v1/continue-watching" -> {
                continueQuery.set(exchange.requestURI.query)
                """{"items":[{"id":43,"kind":"movie","title":"Continue Movie","year":2025}]}"""
            }
            "/api/v1/recently-added" -> {
                recentlyAddedQuery.set(exchange.requestURI.query)
                """{"items":[{"id":44,"kind":"movie","title":"Recent Movie","year":2024}]}"""
            }
            "/api/v1/items/42" -> {
                """{"id":42,"kind":"movie","title":"Test Movie","progress":{"position_ms":30000,"duration_ms":120000,"played":false,"resume_position_ms":30000}}"""
            }
            "/api/v1/items/42/playback" -> {
                """{"item_id":42,"media":{"duration_ms":120000,"container":"matroska"},"stream_url":"/api/v1/media/7"}"""
            }
            "/api/v1/items/42/progress" -> {
                progressMethod.set(exchange.requestMethod)
                progressRequest.set(exchange.requestBody.bufferedReader().use { it.readText() })
                """{"position_ms":15000,"duration_ms":120000,"played":false,"resume_position_ms":15000}"""
            }
            "/api/v1/items/42/images/poster/options" -> {
                artworkRequests += "${exchange.requestMethod} ${exchange.requestURI.path} " +
                    exchange.requestBody.bufferedReader().use { it.readText() }
                """{"items":[{"provider":"tmdb","provider_path":"/alternate.jpg","width":1000,"height":1500,"thumbnail_url":"https://image.tmdb.org/poster.jpg","selected":false}]}"""
            }
            "/api/v1/items/42/images/thumb/options" -> {
                artworkRequests += "${exchange.requestMethod} ${exchange.requestURI.path} " +
                    exchange.requestBody.bufferedReader().use { it.readText() }
                """{"items":[{"provider":"tmdb","provider_path":"/titled.jpg","width":1920,"height":1080,"language":"en","thumbnail_url":"https://image.tmdb.org/titled.jpg","selected":true}]}"""
            }
            "/api/v1/items/42/images/poster" -> {
                artworkRequests += "${exchange.requestMethod} ${exchange.requestURI.path} " +
                    exchange.requestBody.bufferedReader().use { it.readText() }
                """{"id":7,"item_id":42,"kind":"poster","provider":"tmdb","provider_path":"/alternate.jpg","tag":"tag"}"""
            }
            "/api/v1/items/42/images/poster/reset" -> {
                artworkRequests += "${exchange.requestMethod} ${exchange.requestURI.path} " +
                    exchange.requestBody.bufferedReader().use { it.readText() }
                """{"id":7,"item_id":42,"kind":"poster","provider":"tmdb","provider_path":"/default.jpg","tag":"tag"}"""
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

    private fun paginatedMovies(query: String): String {
        val offset = query.substringAfter("offset=").toInt()
        val range = when (offset) {
            0 -> 1..200
            200 -> 201..201
            else -> IntRange.EMPTY
        }
        val items = range.joinToString(",") { id ->
            """{"id":$id,"kind":"movie","title":"Movie $id"}"""
        }
        return """{"items":[$items]}"""
    }
}
