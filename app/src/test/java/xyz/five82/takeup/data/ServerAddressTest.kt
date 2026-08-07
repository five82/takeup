package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerAddressTest {
    @Test
    fun `adds HTTP scheme and normalizes trailing slash`() {
        val address = ServerAddress.parse(" 192.168.1.20:8097 ")

        assertEquals("http://192.168.1.20:8097", address.toString())
        assertEquals(
            "http://192.168.1.20:8097/api/v1/health",
            address.api("api/v1/health").toString(),
        )
    }

    @Test
    fun `accepts HTTPS`() {
        assertEquals(
            "https://loom.example.test",
            ServerAddress.parse("https://loom.example.test/").toString(),
        )
    }

    @Test
    fun `rejects a server path`() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerAddress.parse("http://loom.example.test/something")
        }
    }

    @Test
    fun `resolves a Loom stream path on the same server`() {
        val address = ServerAddress.parse("http://192.168.1.20:8097")

        assertEquals(
            "http://192.168.1.20:8097/api/v1/media/42?tag=abc123",
            address.stream("/api/v1/media/42?tag=abc123").toString(),
        )
    }

    @Test
    fun `rejects an absolute stream URL`() {
        val address = ServerAddress.parse("http://192.168.1.20:8097")

        assertThrows(IllegalArgumentException::class.java) {
            address.stream("http://other.example.test/video.mkv")
        }
    }
}
