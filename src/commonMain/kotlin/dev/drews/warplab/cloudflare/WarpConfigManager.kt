package dev.drews.warplab.cloudflare

import dev.drews.warplab.model.FallbackDomainEntry
import io.github.oshai.kotlinlogging.KotlinLogging


class WarpConfigManager(
    private val client: CloudflareClient,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        const val MANAGED_PREFIX = "warplab-controller managed"
    }
    suspend fun sync(desired: List<FallbackDomainEntry>) {
        val currentResponse = client.getFallbackDomains()
        if (!currentResponse.success || currentResponse.result == null) {
            logger.error { "Failed to get fallback domains: ${currentResponse.errors}" }
            return
        }

        val currentList = currentResponse.result
        val unmanaged = currentList.filterNot { it.description.startsWith(MANAGED_PREFIX) }

        val desiredFallbacks = desired.map {
            FallbackDomain(
                suffix = it.suffix,
                dnsServer = it.dnsServers,
                description = it.description,
            )
        }

        val updatedList = unmanaged + desiredFallbacks

        if (currentList.size == updatedList.size &&
            currentList.toSet() == updatedList.toSet()
        ) {
            logger.info { "WARP fallback unchanged (${desired.size} managed entries), skipping update" }
            return
        }

        logger.info { "Updating WARP fallback: ${desired.size} managed entries (${desired.joinToString { it.suffix }})" }
        val response = client.putFallbackDomains(updatedList)
        if (response.success) {
            logger.info { "WARP fallback updated successfully" }
        } else {
            logger.error { "Failed to update WARP fallback: ${response.errors}" }
        }
    }
}
