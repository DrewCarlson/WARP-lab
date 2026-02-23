package dev.drews.warplab.cloudflare

import dev.drews.warplab.model.TunnelIngress
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class IngressManagerTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `sync skips update when ingress is unchanged`() = runTest {
        var putCalled = false
        val mockEngine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    content = json.encodeToString(CloudflareResponse.serializer(TunnelConfig.serializer()), CloudflareResponse(
                        success = true,
                        result = TunnelConfig(
                            config = TunnelConfigBody(
                                ingress = listOf(
                                    IngressRule(hostname = "*.your.homelab", service = "https://172.29.0.4:443"),
                                    IngressRule(hostname = "your.homelab", service = "https://172.29.0.4:443"),
                                    IngressRule(service = "http_status:404"),
                                )
                            )
                        )
                    )),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                HttpMethod.Put -> {
                    putCalled = true
                    respond(
                        content = """{"success":true,"result":null}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val cfClient = CloudflareClient(mockClient, "test-token", "test-account")
        val manager = IngressManager(cfClient, "test-tunnel")

        manager.sync(listOf(
            TunnelIngress(hostname = "*.your.homelab", service = "https://172.29.0.4:443"),
            TunnelIngress(hostname = "your.homelab", service = "https://172.29.0.4:443"),
        ))

        assertEquals(false, putCalled, "PUT should not be called when ingress is unchanged")
    }

    @Test
    fun `sync updates when ingress differs`() = runTest {
        var putCalled = false
        var putBodyBytes: ByteArray? = null
        val mockEngine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    content = json.encodeToString(CloudflareResponse.serializer(TunnelConfig.serializer()), CloudflareResponse(
                        success = true,
                        result = TunnelConfig(
                            config = TunnelConfigBody(
                                ingress = listOf(
                                    IngressRule(hostname = "*.your.homelab", service = "https://172.29.0.99:443"),
                                    IngressRule(service = "http_status:404"),
                                )
                            )
                        )
                    )),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                HttpMethod.Put -> {
                    putCalled = true
                    putBodyBytes = request.body.toByteArray()
                    respond(
                        content = """{"success":true,"result":null}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val cfClient = CloudflareClient(mockClient, "test-token", "test-account")
        val manager = IngressManager(cfClient, "test-tunnel")

        manager.sync(listOf(
            TunnelIngress(hostname = "*.your.homelab", service = "https://172.29.0.4:443"),
            TunnelIngress(hostname = "your.homelab", service = "https://172.29.0.4:443"),
        ))

        assertEquals(true, putCalled, "PUT should be called when ingress changed")
        val putBody = putBodyBytes?.decodeToString()
        assertEquals(true, putBody?.contains("172.29.0.4"), "PUT body should contain the new IP")
        assertEquals(true, putBody?.contains("http_status:404"), "PUT body should contain catch-all rule")
    }
}
