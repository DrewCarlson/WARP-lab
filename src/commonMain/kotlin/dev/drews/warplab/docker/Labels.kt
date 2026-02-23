package dev.drews.warplab.docker

object Labels {
    const val WARP_DNS = "warplab.warp.dns"
    const val WARP_DIRECT = "warplab.warp.direct"
    const val COMPOSE_SERVICE = "com.docker.compose.service"
}

data class WarpLabServiceInfo(
    val hostname: String,
    val direct: Boolean,
)

fun parseLabels(labels: Map<String, String>): WarpLabServiceInfo? {
    val hostname = labels[Labels.WARP_DNS] ?: return null
    val direct = labels[Labels.WARP_DIRECT]?.equals("true", ignoreCase = true) == true
    return WarpLabServiceInfo(hostname = hostname, direct = direct)
}
