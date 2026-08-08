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
    private val shortsQueries = CopyOnWriteArrayList<String>()
    private val showsQueries = CopyOnWriteArrayList<String>()
    private val childrenQueries = CopyOnWriteArrayList<String>()
    private val paginateMovies = AtomicBoolean(false)
    private val continueQuery = AtomicReference<String>()
    private val nextUpQuery = AtomicReference<String>()
    private val recentlyAddedQuery = AtomicReference<String>()
    private val progressMethod = AtomicReference<String>()
    private val progressRequest = AtomicReference<String>()
    private val artworkRequests = CopyOnWriteArrayList<String>()
    private val playedRequests = CopyOnWriteArrayList<String>()
    private val searchQueries = CopyOnWriteArrayList<String>()
    private val collectionQueries = CopyOnWriteArrayList<String>()

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
        val nextUp = client.nextUp(address)
        val recentlyAdded = client.recentlyAdded(address)
        val item = client.item(address, 42)
        val playback = client.playback(address, 42)
        client.saveProgress(address, 42, positionMs = 15_000, durationMs = 120_000)

        assertEquals("Test Movie", movies.single().title)
        assertEquals("Continue Movie", continuing.single().title)
        // Next Up carries no series or season title; the repository resolves that
        // from the parent chain the way it does for Continue Watching.
        assertEquals("Next Episode", nextUp.single().title)
        assertEquals(51L, nextUp.single().parentId)
        assertEquals("Recent Movie", recentlyAdded.single().title)
        assertEquals(30_000L, item.progress?.resumePositionMs)
        assertEquals("/api/v1/media/7?tag=abc123", playback.streamPath)
        assertEquals("abc123", playback.tag)
        assertEquals(8_123_456_789L, playback.sizeBytes)
        assertEquals(
            listOf("library=movies&kind=movie&limit=200&offset=0"),
            moviesQueries,
        )
        assertEquals("limit=20", continueQuery.get())
        assertEquals("limit=20", nextUpQuery.get())
        assertEquals("limit=20", recentlyAddedQuery.get())
        assertEquals("PUT", progressMethod.get())
        assertEquals(
            "{\"position_ms\":15000,\"duration_ms\":120000}",
            progressRequest.get(),
        )
    }

    // Short films are their own library but carry item kind "movie", so the
    // request must pin both or Loom answers with the feature library.
    @Test
    fun `loads short films from their own library`() = runBlocking {
        val shorts = LoomClient().shorts(address)

        assertEquals("Test Short", shorts.single().title)
        assertEquals(
            listOf("library=shorts&kind=movie&limit=200&offset=0"),
            shortsQueries,
        )
        assertEquals(emptyList<String>(), moviesQueries)
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
        // Listings carry playback state, which is what lets a season draw watched
        // markers without a request per episode.
        assertEquals(true, episodes.single().progress?.played)
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

    // Marking and clearing share one endpoint and are told apart only by method,
    // so a swapped verb would silently discard history instead of recording it.
    @Test
    fun `writes watched state with the verb that matches`() = runBlocking {
        val client = LoomClient()

        client.setPlayed(address, 42, played = true)
        client.setPlayed(address, 51, played = false)

        assertEquals(
            listOf(
                "POST /api/v1/items/42/played",
                "DELETE /api/v1/items/51/played",
            ),
            playedRequests,
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

    // Loom serves every shelf resolved in one response, so asking for collections
    // must stay a single unpaged request no matter how many shelves come back.
    @Test
    fun `loads collections with their members in one request`() = runBlocking {
        val collections = LoomClient().collections(address)

        assertEquals("Star Wars", collections.single().title)
        assertEquals("star-wars", collections.single().slug)
        assertEquals(
            listOf("Star Wars", "The Empire Strikes Back"),
            collections.single().items.map { it.title },
        )
        assertEquals(listOf(""), collectionQueries)
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
            "/api/v1/collections" -> {
                collectionQueries += exchange.requestURI.query.orEmpty()
                """{"items":[{"slug":"star-wars","title":"Star Wars","items":[
                    {"id":11,"kind":"movie","title":"Star Wars","year":1977},
                    {"id":12,"kind":"movie","title":"The Empire Strikes Back","year":1980}
                ]}]}"""
            }
            "/api/v1/items" -> {
                if (exchange.requestURI.query.startsWith("library=tv")) {
                    showsQueries += exchange.requestURI.query
                    """{"items":[{"id":50,"kind":"show","title":"Test Show","year":2026}]}"""
                } else if (exchange.requestURI.query.startsWith("library=shorts")) {
                    shortsQueries += exchange.requestURI.query
                    """{"items":[{"id":60,"kind":"movie","title":"Test Short","year":2008}]}"""
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
                """{"items":[{"id":52,"kind":"episode","title":"Pilot","season_number":0,"episode_number":1,"progress":{"position_ms":120000,"duration_ms":125000,"played":true,"resume_position_ms":0}}]}"""
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
            "/api/v1/next-up" -> {
                nextUpQuery.set(exchange.requestURI.query)
                """{"items":[{"id":53,"kind":"episode","title":"Next Episode","parent_id":51,"season_number":1,"episode_number":2}]}"""
            }
            "/api/v1/recently-added" -> {
                recentlyAddedQuery.set(exchange.requestURI.query)
                """{"items":[{"id":44,"kind":"movie","title":"Recent Movie","year":2024}]}"""
            }
            "/api/v1/items/42" -> {
                """{"id":42,"kind":"movie","title":"Test Movie","progress":{"position_ms":30000,"duration_ms":120000,"played":false,"resume_position_ms":30000}}"""
            }
            "/api/v1/items/42/playback" -> {
                """{"item_id":42,"media":{"id":7,"filename":"Test Movie.mkv","size":8123456789,"tag":"abc123","duration_ms":120000,"container":"matroska"},"stream_url":"/api/v1/media/7?tag=abc123"}"""
            }
            "/api/v1/items/42/progress" -> {
                progressMethod.set(exchange.requestMethod)
                progressRequest.set(exchange.requestBody.bufferedReader().use { it.readText() })
                """{"position_ms":15000,"duration_ms":120000,"played":false,"resume_position_ms":15000}"""
            }
            "/api/v1/items/42/played", "/api/v1/items/51/played" -> {
                playedRequests += "${exchange.requestMethod} ${exchange.requestURI.path}"
                """{"updated":1}"""
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
