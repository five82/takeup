package xyz.five82.takeup.data

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoomDiscoveryTest {
    @Test
    fun `prefers a non-loopback IPv4 address`() {
        val addresses = listOf(
            InetAddress.getByName("::1"),
            InetAddress.getByName("fd00::20"),
            InetAddress.getByName("192.168.1.20"),
        )

        assertEquals("192.168.1.20:8097", loomAddress(addresses, 8097))
    }

    @Test
    fun `brackets an IPv6 address`() {
        val addresses = listOf(InetAddress.getByName("fd00::20"))

        assertEquals("[fd00:0:0:0:0:0:0:20]:8097", loomAddress(addresses, 8097))
    }

    @Test
    fun `rejects loopback-only results and invalid ports`() {
        assertNull(loomAddress(listOf(InetAddress.getLoopbackAddress()), 8097))
        assertNull(loomAddress(listOf(InetAddress.getByName("192.168.1.20")), 0))
    }
}
