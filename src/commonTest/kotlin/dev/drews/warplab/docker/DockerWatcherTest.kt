package dev.drews.warplab.docker

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DockerWatcherTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun mockWatcher(handler: MockRequestHandler): DockerWatcher {
        val engine = MockEngine(handler)
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val dockerClient = DockerClient(httpClient, json, socketPath = null)
        return DockerWatcher(dockerClient)
    }

    @Test
    fun `getNetworkInfo extracts gateway and container IPs`() = runTest {
        val watcher = mockWatcher { request ->
            respond(
                content = """
                {
                    "IPAM": {
                        "Config": [{"Subnet": "172.28.0.0/16", "Gateway": "172.28.0.1"}]
                    },
                    "Containers": {
                        "abc123": {"IPv4Address": "172.28.0.4/16"},
                        "def456": {"IPv4Address": "172.28.0.5/16"}
                    }
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val info = watcher.getNetworkInfo("cloudflare-warp")

        assertNotNull(info)
        assertEquals("172.28.0.1", info.gateway)
        assertEquals("172.28.0.4", info.containerIps["abc123"])
        assertEquals("172.28.0.5", info.containerIps["def456"])
    }

    @Test
    fun `getNetworkInfo derives gateway when IPAM gateway is empty`() = runTest {
        val watcher = mockWatcher { request ->
            respond(
                content = """
                {
                    "IPAM": {
                        "Config": [{"Subnet": "172.28.0.0/16", "Gateway": ""}]
                    },
                    "Containers": {}
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val info = watcher.getNetworkInfo("test-network")

        assertNotNull(info)
        assertEquals("172.28.0.1", info.gateway)
    }

    @Test
    fun `getNetworkInfo returns null on error`() = runTest {
        val watcher = mockWatcher { request ->
            respond(
                content = """{"message": "network not found"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val info = watcher.getNetworkInfo("nonexistent")

        assertNull(info)
    }

    @Test
    fun `findContainerIpOnNetwork returns IP`() = runTest {
        val watcher = mockWatcher { request ->
            respond(
                content = """
                {
                    "Id": "abc123",
                    "NetworkSettings": {
                        "Networks": {
                            "cloudflare-warp": {"IPAddress": "172.28.0.4"}
                        }
                    }
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ip = watcher.findContainerIpOnNetwork("abc123", "cloudflare-warp")

        assertEquals("172.28.0.4", ip)
    }

    @Test
    fun `findContainerIpOnNetwork returns null for unknown network`() = runTest {
        val watcher = mockWatcher { request ->
            respond(
                content = """
                {
                    "Id": "abc123",
                    "NetworkSettings": {
                        "Networks": {
                            "other-network": {"IPAddress": "10.0.0.1"}
                        }
                    }
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ip = watcher.findContainerIpOnNetwork("abc123", "cloudflare-warp")

        assertNull(ip)
    }

    @Test
    fun `listRunningContainers maps ids and labels`() = runTest {
        val watcher = mockWatcher { request ->
            respond(
                content = """
                [
                    {
                        "Id": "abc123",
                        "Names": ["/traefik"],
                        "Labels": {"com.docker.compose.service": "traefik"},
                        "NetworkSettings": {"Networks": {}}
                    },
                    {
                        "Id": "def456",
                        "Names": ["/dnsmasq"],
                        "Labels": {"com.docker.compose.service": "dnsmasq", "warplab.warp.dns": "dns.example.com"},
                        "NetworkSettings": {"Networks": {}}
                    }
                ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val containers = watcher.listRunningContainers()

        assertEquals(2, containers.size)
        assertEquals("abc123", containers[0].id)
        assertEquals("traefik", containers[0].labels["com.docker.compose.service"])
        assertEquals("def456", containers[1].id)
        assertEquals("dns.example.com", containers[1].labels["warplab.warp.dns"])
    }

    @Test
    fun `listRunningContainers returns empty list on error`() = runTest {
        val watcher = mockWatcher { request ->
            respond(
                content = "internal error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val containers = watcher.listRunningContainers()

        assertEquals(emptyList(), containers)
    }

    @Test
    fun `deriveGateway computes correct gateway`() {
        assertEquals("172.28.0.1", DockerWatcher.deriveGateway("172.28.0.0/16"))
        assertEquals("10.0.0.1", DockerWatcher.deriveGateway("10.0.0.0/24"))
        assertEquals("192.168.1.1", DockerWatcher.deriveGateway("192.168.1.0/24"))
    }
}
