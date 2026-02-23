package dev.drews.warplab.cloudflare

import dev.drews.warplab.model.TunnelIngress
import io.github.oshai.kotlinlogging.KotlinLogging


class IngressManager(
    private val client: CloudflareClient,
    private val tunnelId: String,
) {
    private val logger = KotlinLogging.logger {}
    suspend fun sync(desiredRules: List<TunnelIngress>, noTlsVerify: Boolean = false) {
        val currentConfig = client.getTunnelConfig(tunnelId)
        if (!currentConfig.success || currentConfig.result == null) {
            logger.error { "Failed to get tunnel config: ${currentConfig.errors}" }
            return
        }

        val originRequest = if (noTlsVerify) OriginRequest(noTLSVerify = true) else null
        val desiredIngress = desiredRules.map { rule ->
            IngressRule(hostname = rule.hostname, service = rule.service, originRequest = originRequest)
        } + IngressRule(service = "http_status:404")

        val currentIngress = currentConfig.result.config.ingress

        if (currentIngress == desiredIngress) {
            logger.info { "Public tunnel ingress unchanged, skipping update" }
            return
        }

        logger.info { "Updating public tunnel ingress with ${desiredRules.size} rules" }
        val newConfig = TunnelConfig(config = TunnelConfigBody(ingress = desiredIngress))
        val response = client.putTunnelConfig(tunnelId, newConfig)
        if (response.success) {
            logger.info { "Public tunnel ingress updated successfully" }
        } else {
            logger.error { "Failed to update tunnel ingress: ${response.errors}" }
        }
    }
}
