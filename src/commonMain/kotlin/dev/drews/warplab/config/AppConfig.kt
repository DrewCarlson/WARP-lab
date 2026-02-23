package dev.drews.warplab.config

data class AppConfig(
    val cfApiToken: String,
    val cfAccountId: String,
    val cfPublicTunnelId: String,
    val cfWarpTunnelId: String,
    val domain: String,
    val sshSubdomain: String,
    val dryRun: Boolean,
    val traefikHttps: Boolean = true,
) {
    val sshEnabled: Boolean get() = sshSubdomain.isNotBlank()
    val sshHostname: String get() = "$sshSubdomain.$domain"
    val wildcardHostname: String get() = "*.$domain"
    val traefikScheme: String get() = if (traefikHttps) "https" else "http"
    val traefikPort: Int get() = if (traefikHttps) 443 else 80

    companion object {
        fun fromEnvironment(): AppConfig {
            fun env(name: String): String =
                getenv(name) ?: error("Required environment variable $name is not set")

            val dryRun = getenv("WARPLAB_DRY_RUN")?.lowercase() in listOf("true", "1", "yes")

            return AppConfig(
                cfApiToken = if (dryRun) getenv("CF_API_TOKEN").orEmpty() else env("CF_API_TOKEN"),
                cfAccountId = if (dryRun) getenv("CF_ACCOUNT_ID").orEmpty() else env("CF_ACCOUNT_ID"),
                cfPublicTunnelId = if (dryRun) getenv("CF_PUBLIC_TUNNEL_ID").orEmpty() else env("CF_PUBLIC_TUNNEL_ID"),
                cfWarpTunnelId = if (dryRun) getenv("CF_WARP_TUNNEL_ID").orEmpty() else env("CF_WARP_TUNNEL_ID"),
                domain = env("WARPLAB_DOMAIN"),
                sshSubdomain = getenv("WARPLAB_SSH_SUBDOMAIN") ?: "ssh",
                dryRun = dryRun,
                traefikHttps = (getenv("WARPLAB_TRAEFIK_HTTPS")?.lowercase() ?: "true") in listOf("true", "1", "yes"),
            )
        }
    }
}

internal expect fun getenv(name: String): String?
