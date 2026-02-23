package dev.drews.warplab.docker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// GET /networks/{id}
@Serializable
data class DockerNetwork(
    @SerialName("IPAM") val ipam: IPAM = IPAM(),
    @SerialName("Containers") val containers: Map<String, NetworkContainer> = emptyMap(),
)

@Serializable
data class IPAM(
    @SerialName("Config") val config: List<IPAMConfig> = emptyList(),
)

@Serializable
data class IPAMConfig(
    @SerialName("Subnet") val subnet: String = "",
    @SerialName("Gateway") val gateway: String = "",
)

@Serializable
data class NetworkContainer(
    @SerialName("IPv4Address") val ipv4Address: String = "",
)

// GET /containers/json
@Serializable
data class DockerContainerSummary(
    @SerialName("Id") val id: String,
    @SerialName("Names") val names: List<String> = emptyList(),
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
    @SerialName("NetworkSettings") val networkSettings: ContainerNetworkSettings = ContainerNetworkSettings(),
)

@Serializable
data class ContainerNetworkSettings(
    @SerialName("Networks") val networks: Map<String, NetworkEndpoint> = emptyMap(),
)

@Serializable
data class NetworkEndpoint(
    @SerialName("IPAddress") val ipAddress: String = "",
)

// GET /containers/{id}/json
@Serializable
data class DockerContainerInspect(
    @SerialName("Id") val id: String = "",
    @SerialName("NetworkSettings") val networkSettings: InspectNetworkSettings = InspectNetworkSettings(),
)

@Serializable
data class InspectNetworkSettings(
    @SerialName("Networks") val networks: Map<String, NetworkEndpoint> = emptyMap(),
)

// GET /events
@Serializable
data class DockerEvent(
    @SerialName("Type") val type: String = "",
    @SerialName("Action") val action: String = "",
    @SerialName("Actor") val actor: DockerEventActor = DockerEventActor(),
)

@Serializable
data class DockerEventActor(
    @SerialName("ID") val id: String = "",
)
