package dev.drews.warplab

import dev.drews.warplab.cloudflare.CloudflareClient
import dev.drews.warplab.cloudflare.IngressManager
import dev.drews.warplab.cloudflare.WarpConfigManager
import dev.drews.warplab.config.AppConfig
import dev.drews.warplab.dnsmasq.DnsmasqGenerator
import dev.drews.warplab.docker.DockerClient
import dev.drews.warplab.docker.DockerWatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger("warplab-controller")

fun runController() = runBlocking {
    logger.info { "warplab-controller starting" }

    val config = AppConfig.fromEnvironment()
    logger.info { "Domain: ${config.domain}, SSH: ${if (config.sshEnabled) config.sshHostname else "disabled"}" }
    if (config.dryRun) {
        logger.info { "Dry-run mode enabled — will perform discovery and log computed state, then exit" }
    }

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    val dockerClient = DockerClient(client = httpClient, json = json)
    val docker = DockerWatcher(dockerClient)
    val dnsmasq = DnsmasqGenerator(httpClient = httpClient)
    val cfClient = CloudflareClient(httpClient, config.cfApiToken, config.cfAccountId)
    val ingressManager = IngressManager(cfClient, config.cfPublicTunnelId)
    val warpConfigManager = WarpConfigManager(cfClient)

    val reconciler = Reconciler(config, docker, dnsmasq, ingressManager, warpConfigManager)

    // Initial reconciliation with retry/backoff
    var attempt = 0
    while (true) {
        try {
            reconciler.reconcile()
            break
        } catch (e: Exception) {
            attempt++
            val backoff = 1.seconds * minOf(attempt, 5)
            logger.warn(e) { "Initial reconciliation failed (attempt $attempt), retrying in $backoff" }
            delay(backoff)
        }
    }

    if (config.dryRun) {
        logger.info { "Dry-run complete, exiting" }
        return@runBlocking
    }

    // Watch Docker events and reconcile on changes
    while (true) {
        try {
            logger.info { "Watching Docker events" }
            @OptIn(FlowPreview::class)
            docker.watchEvents()
                .debounce(2.seconds)
                .collect { event ->
                    logger.info { "Docker event: $event" }
                    try {
                        reconciler.reconcile()
                    } catch (e: Exception) {
                        logger.error(e) { "Reconciliation failed after Docker event" }
                    }
                }
        } catch (e: Exception) {
            logger.warn(e) { "Docker event stream disconnected, reconnecting in 5s" }
            delay(5.seconds)
        }
    }
}
