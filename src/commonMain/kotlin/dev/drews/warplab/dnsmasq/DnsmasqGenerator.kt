package dev.drews.warplab.dnsmasq

import dev.drews.warplab.model.DnsEntry
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString


fun readFileText(path: String): String? {
    val filePath = Path(path)
    if (!SystemFileSystem.exists(filePath)) return null
    return SystemFileSystem.source(filePath).buffered().use { it.readString() }
}

fun writeFileText(path: String, content: String) {
    SystemFileSystem.sink(Path(path)).buffered().use { it.writeString(content) }
}

class DnsmasqGenerator(
    private val configPath: String = "/etc/dnsmasq.d/warplab.conf",
    private val httpClient: HttpClient,
    private val dnsmasqRestartUrl: String = "http://dnsmasq:8080/restart",
) {
    private val logger = KotlinLogging.logger {}
    fun generateConfig(entries: List<DnsEntry>, dnsmasqIp: String): String {
        val lines = buildList {
            add("listen-address=$dnsmasqIp")
            add("server=1.1.1.1")
            add("server=1.0.0.1")
            entries
                .sortedWith(compareBy({ !it.hostname.startsWith("*.") }, { it.hostname }))
                .forEach { add("address=/${it.hostname}/${it.ip}") }
        }
        return lines.joinToString("\n").plus("\n")
    }

    suspend fun applyConfig(entries: List<DnsEntry>, dnsmasqIp: String): Boolean {
        val newContent = generateConfig(entries, dnsmasqIp)
        val currentContent = readFileText(configPath)

        if (newContent == currentContent) {
            logger.info { "dnsmasq config unchanged, skipping reload" }
            return false
        }

        writeFileText(configPath, newContent)
        logger.info { "Wrote dnsmasq config with ${entries.size} entries" }

        restartDnsmasq()
        return true
    }

    private suspend fun restartDnsmasq() {
        try {
            val response = httpClient.put(dnsmasqRestartUrl)
            if (response.status.isSuccess()) {
                logger.info { "dnsmasq restarted successfully" }
            } else {
                logger.warn { "dnsmasq restart returned ${response.status}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to restart dnsmasq via HTTP API" }
        }
    }
}
