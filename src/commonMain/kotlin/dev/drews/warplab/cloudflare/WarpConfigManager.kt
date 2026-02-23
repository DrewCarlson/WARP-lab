package dev.drews.warplab.cloudflare

import dev.drews.warplab.model.FallbackDomainEntry
import io.github.oshai.kotlinlogging.KotlinLogging


class WarpConfigManager(
    private val client: CloudflareClient,
) {
    private val logger = KotlinLogging.logger {}
    suspend fun sync(desired: FallbackDomainEntry) {
        val currentResponse = client.getFallbackDomains()
        if (!currentResponse.success || currentResponse.result == null) {
            logger.error { "Failed to get fallback domains: ${currentResponse.errors}" }
            return
        }

        val currentList = currentResponse.result
        val existingEntry = currentList.find { it.suffix == desired.suffix }

        if (existingEntry != null &&
            existingEntry.dnsServer == desired.dnsServers &&
            existingEntry.description == desired.description
        ) {
            logger.info { "WARP fallback for ${desired.suffix} unchanged (${desired.dnsServers}), skipping update" }
            return
        }

        val newEntry = FallbackDomain(
            suffix = desired.suffix,
            dnsServer = desired.dnsServers,
            description = desired.description,
        )

        val updatedList = if (existingEntry != null) {
            currentList.map { if (it.suffix == desired.suffix) newEntry else it }
        } else {
            currentList + newEntry
        }

        logger.info { "Updating WARP fallback: ${desired.suffix} → ${desired.dnsServers}" }
        val response = client.putFallbackDomains(updatedList)
        if (response.success) {
            logger.info { "WARP fallback updated successfully" }
        } else {
            logger.error { "Failed to update WARP fallback: ${response.errors}" }
        }
    }
}
