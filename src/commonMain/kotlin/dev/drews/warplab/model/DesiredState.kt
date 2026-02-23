package dev.drews.warplab.model

data class DnsEntry(
    val hostname: String,
    val ip: String,
)

data class TunnelIngress(
    val hostname: String?,
    val service: String,
)

data class FallbackDomainEntry(
    val suffix: String,
    val dnsServers: List<String>,
    val description: String = "",
)

data class DesiredState(
    val dnsEntries: List<DnsEntry>,
    val publicIngressRules: List<TunnelIngress>,
    val warpFallback: FallbackDomainEntry?,
)
