package dev.drews.warplab.docker

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json


class DockerClient(
    client: HttpClient,
    private val json: Json,
    private val socketPath: String? = "/var/run/docker.sock",
) {
    private val logger = KotlinLogging.logger {}
    private val baseUrl = "http://localhost/v1.47"

    private val httpClient = client.config {
        defaultRequest {
            url("http://localhost/v1.47")
            if (socketPath != null) {
                unixSocket(socketPath)
            }
        }
    }

    suspend fun inspectNetwork(name: String): DockerNetwork {
        val response = httpClient.get("$baseUrl/networks/$name")
        return response.body()
    }

    suspend fun listContainers(filters: Map<String, List<String>>): List<DockerContainerSummary> {
        val filtersJson = json.encodeToString(filters)
        val response = httpClient.get("$baseUrl/containers/json") {
            parameter("filters", filtersJson)
        }
        return response.body()
    }

    suspend fun inspectContainer(id: String): DockerContainerInspect {
        val response = httpClient.get("$baseUrl/containers/$id/json")
        return response.body()
    }

    fun streamEvents(filters: Map<String, List<String>>): Flow<DockerEvent> = flow {
        val filtersJson = json.encodeToString(filters)
        httpClient.prepareGet("$baseUrl/events") {
            parameter("filters", filtersJson)
        }.execute { response ->
            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                if (line.isBlank()) continue
                try {
                    val event = json.decodeFromString<DockerEvent>(line)
                    emit(event)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to parse Docker event: $line" }
                }
            }
        }
    }
}
