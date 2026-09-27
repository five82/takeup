package xyz.five82.takeup.data

import android.content.Context
import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files

class OfflineArtworkTest {
    private lateinit var directory: File
    private lateinit var server: HttpServer
    private lateinit var artwork: OfflineArtwork
    private val paths = mutableListOf<String>()
    private var fail = false

    @Before fun setUp() {
        directory = Files.createTempDirectory("takeup-artwork").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.toString()
            val bytes = if (fail) "unavailable".toByteArray() else exchange.requestURI.path.toByteArray()
            exchange.sendResponseHeaders(if (fail) 503 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        artwork = OfflineArtwork(context, LoomApi("http://127.0.0.1:${server.address.port}"))
    }

    @After fun tearDown() {
        server.stop(0)
        directory.deleteRecursively()
    }

    @Test fun `saves available artwork in the expected buckets and deletes all copies`() = runBlocking {
        val item = Item(id = 42, posterImageId = 1, posterImageTag = "p", backdropImageId = 2,
            thumbImageId = 3, logoImageId = 4)
        assertNull(artwork.posterPath(item.id))
        artwork.save(item)
        assertEquals(listOf(
            "/api/v1/images/1?width=480&tag=p",
            "/api/v1/images/2?width=1440",
            "/api/v1/images/3?width=480",
            "/api/v1/images/4?width=480",
        ), paths)
        val poster = File(requireNotNull(artwork.posterPath(42)).removePrefix("file://"))
        assertArrayEquals("/api/v1/images/1".toByteArray(), poster.readBytes())
        assertEquals("42-poster", poster.name)
        assertEquals("42-backdrop", File(requireNotNull(artwork.backdropPath(42)).removePrefix("file://")).name)
        assertEquals("42-thumb", File(requireNotNull(artwork.thumbPath(42)).removePrefix("file://")).name)
        assertEquals("42-logo", File(requireNotNull(artwork.logoPath(42)).removePrefix("file://")).name)
        artwork.delete(42)
        assertNull(artwork.posterPath(42))
        assertNull(artwork.backdropPath(42))
        assertNull(artwork.thumbPath(42))
        assertNull(artwork.logoPath(42))
    }

    @Test fun `missing images are skipped and failed refresh preserves existing copies`() = runBlocking {
        artwork.save(Item(id = 7, posterImageId = 8))
        assertEquals(listOf("/api/v1/images/8?width=480"), paths)
        assertNull(artwork.backdropPath(7))
        val poster = File(requireNotNull(artwork.posterPath(7)).removePrefix("file://"))
        val original = poster.readBytes()
        fail = true
        artwork.save(Item(id = 7, posterImageId = 8, logoImageId = 9))
        assertArrayEquals(original, poster.readBytes())
        assertNull(artwork.logoPath(7))
    }
}
