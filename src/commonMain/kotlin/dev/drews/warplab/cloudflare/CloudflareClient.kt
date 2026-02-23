package dev.drews.warplab.cloudflare

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*


class CloudflareClient(
    private val httpClient: HttpClient,
    private val apiToken: String,
    private val accountId: String,
) {
    private val logger = KotlinLogging.logger {}
    private val baseUrl = "https://api.cloudflare.com/client/v4"

    private fun HttpRequestBuilder.auth() {
        header("Authorization", "Bearer $apiToken")
    }

    suspend fun getTunnelConfig(tunnelId: String): CloudflareResponse<TunnelConfig> {
        val response = httpClient.get("$baseUrl/accounts/$accountId/cfd_tunnel/$tunnelId/configurations") {
            auth()
        }
        return response.body()
    }

    suspend fun putTunnelConfig(tunnelId: String, config: TunnelConfig): CloudflareResponse<TunnelConfig> {
        val response = httpClient.put("$baseUrl/accounts/$accountId/cfd_tunnel/$tunnelId/configurations") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(config)
        }
        return response.body()
    }

    suspend fun getFallbackDomains(): CloudflareResponse<List<FallbackDomain>> {
        val response = httpClient.get("$baseUrl/accounts/$accountId/devices/policy/fallback_domains") {
            auth()
        }
        return response.body()
    }

    suspend fun putFallbackDomains(domains: List<FallbackDomain>): CloudflareResponse<List<FallbackDomain>> {
        val response = httpClient.put("$baseUrl/accounts/$accountId/devices/policy/fallback_domains") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(domains)
        }
        logger.debug { "PUT fallback domains response status: ${response.status}" }
        return response.body()
    }
}
