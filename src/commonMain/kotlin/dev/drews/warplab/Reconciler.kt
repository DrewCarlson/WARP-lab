package dev.drews.warplab

import dev.drews.warplab.cloudflare.IngressManager
import dev.drews.warplab.cloudflare.WarpConfigManager
import dev.drews.warplab.config.AppConfig
import dev.drews.warplab.dnsmasq.DnsmasqGenerator
import dev.drews.warplab.docker.DockerWatcher
import dev.drews.warplab.docker.Labels
import dev.drews.warplab.docker.parseLabels
import dev.drews.warplab.model.DnsEntry
import dev.drews.warplab.model.FallbackDomainEntry
import dev.drews.warplab.model.TunnelIngress
import io.github.oshai.kotlinlogging.KotlinLogging


class Reconciler(
    private val config: AppConfig,
    private val docker: DockerWatcher,
    private val dnsmasq: DnsmasqGenerator,
    private val ingressManager: IngressManager,
    private val warpConfigManager: WarpConfigManager,
) {
    private val logger = KotlinLogging.logger {}
    suspend fun reconcile() {
        val dryRun = config.dryRun
        if (dryRun) {
            logger.info { "[DRY-RUN] Reconciliation starting (no writes will be performed)" }
        } else {
            logger.info { "Starting reconciliation" }
        }

        // Step 1: Discover IPs
        val warpNetwork = docker.getNetworkInfo("cloudflare-warp")

        if (warpNetwork == null) {
            logger.error { "Could not find cloudflare-warp network" }
            return
        }

        val containers = docker.listRunningContainers()

        val traefikContainer = containers.find {
            it.labels[Labels.COMPOSE_SERVICE] == "traefik"
        }
        val dnsmasqContainer = containers.find {
            it.labels[Labels.COMPOSE_SERVICE] == "dnsmasq"
        }

        val traefikWarpIp = traefikContainer?.id?.let {
            docker.findContainerIpOnNetwork(it, "cloudflare-warp")
        }
        val traefikPublicIp = traefikContainer?.id?.let {
            docker.findContainerIpOnNetwork(it, "cloudflare")
        }
        val dnsmasqWarpIp = dnsmasqContainer?.id?.let {
            docker.findContainerIpOnNetwork(it, "cloudflare-warp")
        }

        if (dryRun) {
            logger.info { "[DRY-RUN] === Discovery Results ===" }
            logger.info { "[DRY-RUN] Network gateway: ${warpNetwork.gateway}" }
            logger.info { "[DRY-RUN] Containers on cloudflare-warp:\n${warpNetwork.containerIps.entries.joinToString("\n")}" }
            logger.info { "[DRY-RUN] Running containers (${containers.size}):" }
            for (container in containers) {
                val service = container.labels[Labels.COMPOSE_SERVICE] ?: "unknown"
                val warpLabels = container.labels.filterKeys { it.startsWith("warplab.") }
                logger.info { "[DRY-RUN]   - service=$service (id=${container.id.take(12)}) warplab labels=$warpLabels" }
            }
            logger.info { "[DRY-RUN] Key IPs: traefikWarpIp=$traefikWarpIp, traefikPublicIp=$traefikPublicIp, dnsmasqWarpIp=$dnsmasqWarpIp" }
        }

        // Step 2: Build DNS entries
        val dnsEntries = mutableListOf<DnsEntry>()

        if (traefikWarpIp != null) {
            dnsEntries.add(DnsEntry(hostname = config.wildcardHostname, ip = traefikWarpIp))
            dnsEntries.add(DnsEntry(hostname = config.domain, ip = traefikWarpIp))
        } else {
            logger.warn { "Traefik not found on cloudflare-warp network" }
        }

        if (config.sshEnabled) {
            val gateway = warpNetwork.gateway
            dnsEntries.add(DnsEntry(hostname = config.sshHostname, ip = gateway))
        }

        // Parse labels from all containers
        for (container in containers) {
            val serviceInfo = parseLabels(container.labels) ?: continue

            val ip = if (serviceInfo.direct) {
                docker.findContainerIpOnNetwork(container.id, "cloudflare-warp")
            } else {
                traefikWarpIp
            }

            if (ip != null) {
                dnsEntries.add(DnsEntry(hostname = serviceInfo.hostname, ip = ip))
            } else {
                logger.warn { "No IP for service ${serviceInfo.hostname}" }
            }
        }

        if (dryRun) {
            logger.info { "[DRY-RUN] === Computed DNS Entries (${dnsEntries.size}) ===" }
            for (entry in dnsEntries) {
                logger.info { "[DRY-RUN]   ${entry.hostname} -> ${entry.ip}" }
            }
        }

        // Step 3: Apply dnsmasq config
        if (dnsmasqWarpIp == null) {
            logger.warn { "dnsmasq not found on cloudflare-warp network, skipping dnsmasq config" }
        } else {
            if (dryRun) {
                val configText = dnsmasq.generateConfig(dnsEntries, dnsmasqWarpIp)
                logger.info { "[DRY-RUN] === Step 3: dnsmasq config (would write) ===" }
                for (line in configText.trimEnd().lines()) {
                    logger.info { "[DRY-RUN]   $line" }
                }
            } else {
                try {
                    dnsmasq.applyConfig(dnsEntries, dnsmasqWarpIp)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to apply dnsmasq config" }
                }
            }
        }

        // Step 4: Sync Cloudflare public tunnel ingress
        if (traefikPublicIp == null) {
            logger.warn { "Traefik not found on cloudflare-public network, skipping ingress sync" }
        } else {
            val scheme = config.traefikScheme
            val port = config.traefikPort
            val ingressRules = listOf(
                TunnelIngress(hostname = config.wildcardHostname, service = "$scheme://$traefikPublicIp:$port"),
                TunnelIngress(hostname = config.domain, service = "$scheme://$traefikPublicIp:$port"),
            )
            if (dryRun) {
                logger.info { "[DRY-RUN] === Step 4: Cloudflare ingress rules (would PUT) ===" }
                for (rule in ingressRules) {
                    logger.info { "[DRY-RUN]   ${rule.hostname} -> ${rule.service}" }
                }
                if (config.traefikHttps) {
                    logger.info { "[DRY-RUN]   originRequest.noTLSVerify=true" }
                }
            } else {
                try {
                    ingressManager.sync(ingressRules, noTlsVerify = config.traefikHttps)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to sync Cloudflare ingress" }
                }
            }
        }

        // Step 5: Sync WARP fallback
        if (dnsmasqWarpIp == null) {
            logger.warn { "dnsmasq not found on cloudflare-warp network, skipping WARP fallback sync" }
        } else {
            val fallback = FallbackDomainEntry(
                suffix = config.domain,
                dnsServers = listOf(dnsmasqWarpIp),
                description = "warplab-controller managed",
            )
            if (dryRun) {
                logger.info { "[DRY-RUN] === Step 5: WARP fallback domain (would PUT) ===" }
                logger.info { "[DRY-RUN]   suffix=${fallback.suffix}, dnsServers=${fallback.dnsServers}" }
            } else {
                try {
                    warpConfigManager.sync(fallback)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to sync WARP fallback" }
                }
            }
        }

        if (dryRun) {
            logger.info { "[DRY-RUN] Reconciliation complete (no writes were performed)" }
        } else {
            logger.info { "Reconciliation complete" }
        }
    }
}
