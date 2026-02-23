package dev.drews.warplab.docker

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


/**
 * Watches Docker state via the Docker Engine API over unix socket.
 */
class DockerWatcher(
    private val client: DockerClient,
) {
    private val logger = KotlinLogging.logger {}

    data class NetworkInfo(
        val gateway: String,
        val containerIps: Map<String, String>,
    )

    data class ContainerInfo(
        val id: String,
        val labels: Map<String, String>,
    )

    suspend fun getNetworkInfo(networkName: String): NetworkInfo? {
        return try {
            val network = client.inspectNetwork(networkName)

            val gateway = network.ipam.config.firstOrNull()?.let { ipamConfig ->
                ipamConfig.gateway.ifEmpty {
                    if (ipamConfig.subnet.isNotEmpty()) deriveGateway(ipamConfig.subnet) else null
                }
            } ?: run {
                logger.warn { "No IPAM config found for network $networkName" }
                return null
            }

            val containerIps = network.containers.mapValues { (_, container) ->
                container.ipv4Address.substringBefore("/")
            }

            NetworkInfo(gateway = gateway, containerIps = containerIps)
        } catch (e: Exception) {
            logger.error(e) { "Failed to inspect network $networkName" }
            null
        }
    }

    suspend fun findContainerIpOnNetwork(containerId: String, networkName: String): String? {
        return try {
            val inspect = client.inspectContainer(containerId)
            val endpoint = inspect.networkSettings.networks[networkName]
            endpoint?.ipAddress?.ifEmpty { null }
        } catch (e: Exception) {
            logger.error(e) { "Failed to inspect container $containerId" }
            null
        }
    }

    suspend fun listRunningContainers(): List<ContainerInfo> {
        return try {
            val containers = client.listContainers(
                filters = mapOf("status" to listOf("running"))
            )
            containers.map { ContainerInfo(id = it.id, labels = it.labels) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to list running containers" }
            emptyList()
        }
    }

    fun watchEvents(): Flow<String> {
        val filters = mapOf(
            "type" to listOf("container"),
            "event" to listOf("start", "stop", "die", "destroy"),
        )
        return client.streamEvents(filters).map { event ->
            logger.debug { "Docker event: ${event.type} ${event.action} ${event.actor.id}" }
            event.action
        }
    }

    companion object {
        fun deriveGateway(subnet: String): String {
            val base = subnet.substringBefore("/")
            val parts = base.split(".")
            return "${parts[0]}.${parts[1]}.${parts[2]}.1"
        }
    }
}
