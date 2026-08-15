package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.api.OFFLINE_MESSAGE
import java.io.IOException

class NetworkPolicyTest {

    @Test
    fun `a fresh install on the home network is not blocked`() {
        // Both switches default off, which must not read as "refuse everything":
        // onboarding itself runs through this gate before anything is saved.
        assertFalse(networkBlocked(NetworkFacts()))
    }

    @Test
    fun `cellular is blocked only while the setting is off`() {
        assertTrue(blocked(onCellular = true, allowCellular = false))
        assertFalse(blocked(onCellular = true, allowCellular = true))
    }

    @Test
    fun `the home subnet on wifi is never blocked`() {
        assertFalse(blocked())
    }

    @Test
    fun `another subnet is blocked until the tunnel is actually up`() {
        // The regression this replaced: a switch saying remote use was permitted
        // opened the gate on any strange Wi-Fi, tunnel or no tunnel, and every
        // request went to a LAN address nothing was listening at.
        assertTrue(blocked(onHomeSubnet = false))
        assertFalse(blocked(onHomeSubnet = false, onTailnet = true))
    }

    @Test
    fun `the tunnel does not also open the data plan`() {
        // Tailscale over cellular still spends the plan, so it needs the switch.
        assertTrue(blocked(onCellular = true, onHomeSubnet = false, onTailnet = true))
        assertFalse(
            blocked(
                onCellular = true,
                allowCellular = true,
                onHomeSubnet = false,
                onTailnet = true,
            ),
        )
    }

    @Test
    fun `a probe that answers is home only from Loom's own subnet`() {
        assertEquals(Reach.Home, reachFrom(answered = true, onHomeSubnet = true))
        assertEquals(Reach.Remote, reachFrom(answered = true, onHomeSubnet = false))
        assertEquals(Reach.Offline, reachFrom(answered = false, onHomeSubnet = true))
        assertEquals(Reach.Offline, reachFrom(answered = false, onHomeSubnet = false))
    }

    @Test
    fun `the reason names the gate that is closed, not the server`() {
        assertEquals(
            "Cellular data is off in Takeup.",
            offlineReason(NetworkFacts(onCellular = true)),
        )
        assertEquals(
            "You are away from home and Tailscale isn't connected.",
            offlineReason(NetworkFacts(onHomeSubnet = false)),
        )
        // Both gates open and still nothing: now it really is the server.
        assertEquals("Loom isn't answering.", offlineReason(NetworkFacts()))
    }

    @Test
    fun `a 24 bit prefix separates one home network from another`() {
        val loom = bytes(192, 168, 1, 20)
        assertTrue(inSameSubnet(loom, bytes(192, 168, 1, 57), 24))
        assertFalse(inSameSubnet(loom, bytes(192, 168, 4, 57), 24))
        // The friend's LAN that happens to be 192.168.1.x does match here; the
        // probe's response check is what keeps their device from reading as Loom.
        assertTrue(inSameSubnet(loom, bytes(192, 168, 1, 99), 24))
    }

    @Test
    fun `a prefix that is not a whole number of bytes still masks correctly`() {
        val loom = bytes(10, 0, 3, 5)
        assertTrue(inSameSubnet(loom, bytes(10, 0, 2, 9), 22))
        assertFalse(inSameSubnet(loom, bytes(10, 0, 4, 9), 22))
    }

    @Test
    fun `a tunnel's own host route never contains the server`() {
        // Tailscale hands out a 100.x address as a /32, so nothing shares it.
        assertFalse(inSameSubnet(bytes(192, 168, 1, 20), bytes(100, 84, 12, 3), 32))
    }

    @Test
    fun `addresses of different families are never on one subnet`() {
        assertFalse(inSameSubnet(bytes(192, 168, 1, 20), ByteArray(16), 24))
    }

    @Test
    fun `a nonsense prefix is not a match`() {
        assertFalse(inSameSubnet(bytes(192, 168, 1, 20), bytes(192, 168, 1, 20), 0))
        assertFalse(inSameSubnet(bytes(192, 168, 1, 20), bytes(192, 168, 1, 20), 33))
    }

    @Test
    fun `a blocked request reads as being offline`() {
        // What makes the app fall back to downloads rather than show an error.
        assertTrue(isOfflineError(IOException(OFFLINE_MESSAGE)))
    }

    @Test
    fun `the tailnet range is Tailscale's own address`() {
        // Captured from the Pixel with Tailscale connected: tun0, 100.73.96.93/32.
        assertTrue(isTailnetAddress(bytes(100, 73, 96, 93)))
        // The ends of 100.64.0.0/10.
        assertTrue(isTailnetAddress(bytes(100, 64, 0, 0)))
        assertTrue(isTailnetAddress(bytes(100, 127, 255, 255)))
        assertFalse(isTailnetAddress(bytes(100, 63, 255, 255)))
        assertFalse(isTailnetAddress(bytes(100, 128, 0, 0)))
    }

    @Test
    fun `the VPNs that are not Tailscale do not match`() {
        // Mullvad on the same Pixel, same tun0 slot: 10.159.206.81/32. Both are
        // tunnels on a device this check cannot tell apart by name, so the range
        // is what separates them.
        assertFalse(isTailnetAddress(bytes(10, 159, 206, 81)))
        // A 100.x first octet alone is not the range - 100.5.x.x is public.
        assertFalse(isTailnetAddress(bytes(100, 5, 1, 1)))
        assertFalse(isTailnetAddress(ByteArray(16)))
    }

    @Test
    fun `carrier CGNAT is in range, which is why the caller checks the device`() {
        // Carriers hand out 100.64/10 on the mobile interface, so this predicate
        // cannot be the whole test - a bare LTE connection would read as a
        // tunnel. NetworkPolicy only consults it for addresses on a tun device.
        assertTrue(isTailnetAddress(bytes(100, 96, 4, 7)))
    }

    private fun blocked(
        onCellular: Boolean = false,
        allowCellular: Boolean = false,
        onHomeSubnet: Boolean = true,
        onTailnet: Boolean = false,
    ) = networkBlocked(NetworkFacts(onCellular, allowCellular, onHomeSubnet, onTailnet))

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
