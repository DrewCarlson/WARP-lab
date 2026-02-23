package dev.drews.warplab.cloudflare

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CloudflareResponse<T>(
    val success: Boolean,
    val result: T? = null,
    val errors: List<CloudflareError> = emptyList(),
)

@Serializable
data class CloudflareError(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
data class TunnelConfig(
    val config: TunnelConfigBody,
)

@Serializable
data class TunnelConfigBody(
    val ingress: List<IngressRule>,
)

@Serializable
data class IngressRule(
    val hostname: String? = null,
    val service: String,
    @SerialName("originRequest") val originRequest: OriginRequest? = null,
)

@Serializable
data class OriginRequest(
    val noTLSVerify: Boolean? = null,
)

@Serializable
data class FallbackDomain(
    val suffix: String,
    @SerialName("dns_server") val dnsServer: List<String> = emptyList(),
    val description: String = "",
)
