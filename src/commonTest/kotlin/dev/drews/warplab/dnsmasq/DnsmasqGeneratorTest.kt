package dev.drews.warplab.dnsmasq

import dev.drews.warplab.model.DnsEntry
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DnsmasqGeneratorTest {
    private val generator = DnsmasqGenerator(configPath = "/tmp/test-warplab.conf", httpClient = HttpClient(CIO))
    private val dnsmasqIp = "172.28.0.3"

    @Test
    fun `generateConfig produces correct address lines`() {
        val entries = listOf(
            DnsEntry("app.your.homelab", "172.28.0.4"),
            DnsEntry("ssh.your.homelab", "172.28.0.1"),
        )
        val config = generator.generateConfig(entries, dnsmasqIp)
        assertTrue(config.contains("address=/app.your.homelab/172.28.0.4"))
        assertTrue(config.contains("address=/ssh.your.homelab/172.28.0.1"))
    }

    @Test
    fun `generateConfig includes listen-address and upstream servers`() {
        val entries = listOf(DnsEntry("app.your.homelab", "172.28.0.4"))
        val config = generator.generateConfig(entries, dnsmasqIp)
        val lines = config.trimEnd().lines()
        assertEquals("listen-address=$dnsmasqIp", lines[0])
        assertEquals("server=1.1.1.1", lines[1])
        assertEquals("server=1.0.0.1", lines[2])
    }

    @Test
    fun `generateConfig places wildcard entries first after server lines`() {
        val entries = listOf(
            DnsEntry("ssh.your.homelab", "172.28.0.1"),
            DnsEntry("*.your.homelab", "172.28.0.4"),
            DnsEntry("app.your.homelab", "172.28.0.4"),
        )
        val config = generator.generateConfig(entries, dnsmasqIp)
        val lines = config.trimEnd().lines()
        assertEquals("address=/*.your.homelab/172.28.0.4", lines[3])
    }

    @Test
    fun `generateConfig sorts non-wildcard entries alphabetically`() {
        val entries = listOf(
            DnsEntry("zebra.your.homelab", "172.28.0.10"),
            DnsEntry("alpha.your.homelab", "172.28.0.11"),
        )
        val config = generator.generateConfig(entries, dnsmasqIp)
        val lines = config.trimEnd().lines()
        // First 3 lines are listen-address, server, server
        assertEquals("address=/alpha.your.homelab/172.28.0.11", lines[3])
        assertEquals("address=/zebra.your.homelab/172.28.0.10", lines[4])
    }

    @Test
    fun `generateConfig handles empty list`() {
        val config = generator.generateConfig(emptyList(), dnsmasqIp)
        val lines = config.trimEnd().lines()
        assertEquals(3, lines.size)
        assertEquals("listen-address=$dnsmasqIp", lines[0])
    }

    @Test
    fun `generateConfig ends with newline`() {
        val entries = listOf(DnsEntry("test.your.homelab", "172.28.0.5"))
        val config = generator.generateConfig(entries, dnsmasqIp)
        assertTrue(config.endsWith("\n"))
    }
}
