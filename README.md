# warplab-controller

A Kotlin/Native sidecar that watches Docker containers and automatically keeps dnsmasq, Cloudflare Tunnel ingress, and WARP local domain fallback in sync. Designed for homelabs that use Cloudflare Tunnels for public access and Cloudflare WARP for private VPN access to the same services.

## Why

In a Docker homelab with Cloudflare Tunnels, container IPs change on every recreate. This controller eliminates hardcoded IPs by discovering containers at runtime and reconciling three systems automatically:

- **dnsmasq** DNS records so WARP clients resolve `*.yourdomain` to Traefik's current IP
- **Cloudflare Tunnel ingress** rules so public traffic routes to Traefik's current IP
- **WARP fallback domains** so WARP clients know which DNS server handles your private domain

## Architecture

```
Internet                         WARP Clients
   |                                  |
cloudflare-public tunnel       cloudflare-warp tunnel
   |                                  |
   +-- cloudflare-public net --+  +--- cloudflare-warp net ----------+
       (172.29.0.0/16)         |  |    (172.28.0.0/16)               |
                               |  |        |            |            |
                              traefik   dnsmasq   warplab-controller |
                                 |                                   |
                         traefik net (172.24.0.0/16)                 |
                                 |                                   |
                          app containers ────────────────────────────+
                   (with warplab.warp.dns label)   (if warplab.warp.direct=true)
```

The controller reads the Docker socket (read-only), discovers all container IPs and labels, then reconciles dnsmasq config, tunnel ingress, and WARP fallback on every container start/stop event.

## Quick Start

1. Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

2. Start the stack:

```bash
docker compose up -d
```

3. Add `warplab.warp.dns` labels to any service you want accessible over WARP:

```yaml
services:
  my-app:
    image: my-app:latest
    labels:
      warplab.warp.dns: "my-app.yourdomain.com"
    networks:
      - traefik
```

The controller will detect the new container and update DNS/ingress automatically.

## Configuration

All configuration is via environment variables:

| Variable                | Required | Default | Description                                                                 |
|-------------------------|----------|---------|-----------------------------------------------------------------------------|
| `CF_API_TOKEN`          | Yes      |         | Cloudflare API token with Tunnel and Zero Trust edit permissions            |
| `CF_ACCOUNT_ID`         | Yes      |         | Cloudflare account ID                                                       |
| `CF_PUBLIC_TUNNEL_ID`   | Yes      |         | ID of the public-facing cloudflared tunnel                                  |
| `CF_WARP_TUNNEL_ID`     | Yes      |         | ID of the WARP private network tunnel                                       |
| `WARPLAB_DOMAIN`        | Yes      |         | Base domain (e.g. `your.homelab`)                                          |
| `WARPLAB_SSH_SUBDOMAIN` | No       | `ssh`   | Subdomain for SSH access to the Docker host. Set to empty string to disable |
| `WARPLAB_TRAEFIK_HTTPS` | No       | `true`  | Use HTTPS for tunnel ingress to Traefik. When enabled, sets `noTLSVerify` on the tunnel origin request |
| `WARPLAB_DRY_RUN`       | No       | `false` | Enable dry-run mode: performs discovery and logs computed state, but writes nothing |

## Docker Labels

Apply these labels to containers to register them with the controller:

| Label                 | Description                                                                                                                              |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `warplab.warp.dns`    | Hostname to resolve for this service (e.g. `app.your.homelab`). Required for the controller to manage the service.                      |
| `warplab.warp.direct` | Set to `true` to resolve DNS directly to this container's IP on `cloudflare-warp` instead of Traefik's IP. Useful for non-HTTP services. |

### Examples

**Standard web service** (routed through Traefik):
```yaml
my-app:
  labels:
    warplab.warp.dns: "my-app.your.homelab"
  networks:
    - traefik
```

**Direct-access service** (bypasses Traefik, e.g. for SSH or custom protocols):
```yaml
code-server:
  labels:
    warplab.warp.dns: "code.your.homelab"
    warplab.warp.direct: "true"
  networks:
    - traefik
    - cloudflare-warp
```

## Dry-Run Mode

Dry-run mode lets you verify the controller's behavior without making any changes. It performs full Docker discovery, computes the DNS entries, ingress rules, and WARP fallback config, then logs everything and exits.

```bash
# Via environment variable
WARPLAB_DRY_RUN=true ./warplab-controller

# Via docker compose override
docker compose -f docker-compose.yml -f docker-compose.dryrun.yml up warplab-controller
```

In dry-run mode:
- Docker discovery runs normally (read-only socket access)
- All computed state is logged with `[DRY-RUN]` prefix
- No dnsmasq config is written, no Cloudflare API calls are made
- The process exits after a single reconciliation pass
- Cloudflare credentials (`CF_API_TOKEN`, etc.) are optional

## How Reconciliation Works

On startup and on every Docker container event (debounced to 2 seconds):

1. **Discover** — Query Docker for the `cloudflare-warp` network info, all running containers, and their IPs on each network
2. **Build DNS entries** — Wildcard + apex records pointing to Traefik, per-service records from labels, SSH record pointing to the network gateway (Docker host)
3. **Apply dnsmasq config** — Write `/etc/dnsmasq.d/warplab.conf` and reload dnsmasq (skipped if unchanged)
4. **Sync tunnel ingress** — PUT updated ingress rules to Cloudflare (skipped if unchanged)
5. **Sync WARP fallback** — PUT updated fallback domain config to Cloudflare (skipped if unchanged)

Each step is independent — a failure in one step does not block the others.

## Building

Requires a JDK 21+ for Gradle (the output binary is native, no JVM at runtime).

```bash
# Run tests
./gradlew linuxX64Test

# Build debug binary
./gradlew linkDebugExecutableLinuxX64

# Build release binaries (both architectures)
./gradlew linkReleaseExecutableLinuxX64 linkReleaseExecutableLinuxArm64

# Build Docker image
docker build -t warplab-controller .
```

The Docker image expects pre-built binaries in `build/bin/` — run the Gradle release build first.
