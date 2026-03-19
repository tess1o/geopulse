# Docker Deployment

> Canonical env var reference: [Environment Variables Reference](./environment-variables.md)

## Prerequisites

Before you begin, ensure you have the following installed:

- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)

## System Components Overview

GeoPulse consists of 3 mandatory services and one optional service:

1. GeoPulse Backend - mandatory
2. GeoPulse Frontend - mandatory
3. GeoPulse Postgres (Postgis) database - mandatory
4. MQTT Broker (Mosquitto) - optional

MQTT broker can be deployed if you want to use OwnTracks integration with MQTT protocol. If you don't use OwnTracks or
want to use OwnTracks with HTTP - you don't need the MQTT broker. This guide provides steps on how to install the system
with or without MQTT broker.

There is one-time service "geopulse-keygen" that creates JWT keys and AI encryption key in the "keys" folder. It starts
and stops automatically, no manual steps are required.

---

## Docker Compose Deployment

This section guides you through deploying GeoPulse using Docker Compose.

### 1. Quick Start

#### Option A: Regular install (without MQTT)

```bash
# 1) Download .env
curl -L -o .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example

# 2) Configure .env (optional for local evaluation, required for production/security)
# nano .env

# 3) Download docker-compose
curl -L -o docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.yml

# 4) Start
docker compose up -d
```

#### Option B: Install with MQTT

```bash
# 1) Download .env
curl -L -o .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example

# 2) Configure .env (optional for local evaluation, required for production/security)
# For MQTT usage, set GEOPULSE_MQTT_ENABLED=true
# nano .env

# 3) Download docker-compose with MQTT
curl -L -o docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose-complete.yml

# 4) Start
docker compose up -d
```

#### Optional: Add Apprise (Geofence External Notifications)

Apprise is optional and is **not bundled** into the main compose files.

Create an overlay file named `docker-compose.apprise.yml`:

```yaml
services:
  apprise-api:
    image: caronc/apprise:latest
    container_name: geopulse-apprise
    restart: unless-stopped
    ports:
      - "8000:8000"
```

Then start with the overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.apprise.yml up -d
```

After startup, configure Apprise in GeoPulse Admin UI:

- Open `Admin Dashboard > System Settings > Notifications`
- Set API URL:
  - `http://apprise-api:8000` when backend and Apprise run in the same docker-compose stack/network
  - `http://localhost:8000` only when backend runs on host and Apprise is exposed on host port `8000`
- Enable Apprise and run **Test Apprise**

For full admin/user configuration, templates, and in-app behavior, see:

- [Apprise Notifications](../../system-administration/configuration/apprise-notifications)
- [Geofences Guide](../../user-guide/core-features/geofences)

#### After Start

- Open GeoPulse UI: `http://localhost:5555`
- Check backend health: `curl http://localhost:8080/api/health`
- Follow logs if needed: `docker compose logs -f`

If these checks pass, your deployment is up and running. The sections below are optional tuning and production guidance.

> #### A Note on Docker Image Source
> By default, GeoPulse uses images from Docker Hub. We also publish images to GitHub Container Registry (GHCR).
>
> To switch to GHCR images, edit your `docker-compose.yml` file. For `geopulse-backend` and `geopulse-ui`, comment the default `image` and uncomment the `image` pointing to `ghcr.io`.
>
> **Example for `geopulse-backend`:**
> ```yaml
>   geopulse-backend:
>     # image: tess1o/geopulse-backend:${GEOPULSE_VERSION}-native
>     image: ghcr.io/tess1o/geopulse-backend:${GEOPULSE_VERSION}-native
> ```
>
> #### Multi-Architecture Support
> The native backend images support both AMD64 and ARM64 architectures, including Raspberry Pi. Docker will automatically pull the correct image for your platform.
>
> #### CPU Compatibility Issues
> If your backend container fails to start with an error about missing CPU features like:
> ```
> The current machine does not support all of the following CPU features that are required by the image:
> [AVX2, BMI1, BMI2, FMA, F16C, LZCNT] (AMD64) or [FP, ASIMD, CRC32, LSE] (ARM64)
> ```
>
> **This means you have an older CPU** that doesn't support the optimized instructions used by the default image.
>
> **Solution**: Use the compatible image variant instead:
>
> Edit your `docker-compose.yml` and replace the backend image:
> ```yaml
>   geopulse-backend:
>     # Comment out the default optimized image:
>     # image: tess1o/geopulse-backend:${GEOPULSE_VERSION}-native
>     # Use the compatible image instead:
>     image: tess1o/geopulse-backend:${GEOPULSE_VERSION}-native-compat
> ```
>
> **Who needs the compatible image?**
> - **Old x86 CPUs**: Intel pre-Haswell (before 2013), AMD pre-Excavator (before 2015)
>   - Examples: Intel Core i5-3470T (Ivy Bridge), Core i7-2600 (Sandy Bridge)
> - **Raspberry Pi 3/4**: ARM Cortex-A53/A72 processors
>
> **Performance Note**: The compatible image uses x86-64-v2 (AMD64) or armv8-a+nolse (ARM64) instruction sets, which sacrifice some performance for broader compatibility. Modern CPUs (2015+) should use the default optimized image for best performance.

### 2. Configure Environment (`.env`)

The `.env` file is the central place for configuring your GeoPulse instance.

#### Core Configuration

It is highly recommended to set strong, unique passwords for your services.

- `GEOPULSE_POSTGRES_PASSWORD`: The password for the PostgreSQL database.
  ```env
  GEOPULSE_POSTGRES_PASSWORD=your-secure-database-password
  ```
- `GEOPULSE_MQTT_PASSWORD`: The password for the MQTT broker. Only needed if you are using the MQTT deployment.
  ```env
  GEOPULSE_MQTT_PASSWORD=your-secure-mqtt-password
  ```

#### MQTT Configuration

- `GEOPULSE_MQTT_ENABLED`: Set to `true` to enable backend OwnTracks MQTT integration.
  Use this together with the MQTT compose file (`docker-compose-complete.yml`, Quick Start option B), which adds the Mosquitto service.
  ```env
  GEOPULSE_MQTT_ENABLED=true
  ```

#### Web Access & Security Configuration

These variables control how users access the GeoPulse frontend and how authentication cookies are handled.

- `GEOPULSE_CORS_ENABLED`: Enables/disables backend CORS handling.
- `GEOPULSE_CORS_ORIGINS`: Comma-separated list of allowed origins when CORS is enabled.
- `GEOPULSE_PUBLIC_BASE_URL`: Public base URL used for generated callback/link URLs (recommended for OIDC).
- `GEOPULSE_UI_URL`: Legacy fallback variable kept for backward compatibility (deprecated).
- `GEOPULSE_COOKIE_DOMAIN`: The domain for authentication cookies. **Keep empty for standard deployments**.
- `GEOPULSE_AUTH_SECURE_COOKIES`: Set to `true` to ensure cookies are only sent over HTTPS.

##### CORS Defaults

For standard docker-compose deployments, frontend and backend are served from the same origin through nginx (`/api` proxy).  
Because of this, `.env.example` sets:

```env
GEOPULSE_CORS_ENABLED=false
```

Only enable CORS if you intentionally deploy frontend and backend on different origins.

For cross-origin deployments, enable CORS explicitly and set the allowed origins:

```env
GEOPULSE_CORS_ENABLED=true
GEOPULSE_CORS_ORIGINS=https://app.yourdomain.com
```

`GEOPULSE_COOKIE_DOMAIN` is usually not needed in standard docker-compose deployments because nginx serves a same-origin app and API.
Set it only for advanced split-subdomain setups (for example `app.yourdomain.com` + `api.yourdomain.com`).
For full cookie-domain guidance, see [Authentication Configuration](../../system-administration/configuration/authentication.md).

#### Advanced Performance Tuning

<details>
<summary><strong>🚀 Advanced Performance Tuning (Click to expand)</strong></summary>

### Database Performance Tuning

GeoPulse includes optimized PostgreSQL/PostGIS configurations tailored for GPS tracking workloads. Different deployment
sizes use appropriate memory allocations and query optimization settings.

#### Deployment-Specific Database Settings

**Standard Deployment (docker-compose.yml and docker-compose-complete.yml)** - Minimal resource usage:

- `shared_buffers=256MB` - Conservative memory allocation for typical deployments
- `work_mem=8MB` - Low memory per connection, suitable for 1-10 concurrent users
- `effective_cache_size=1GB` - Assumes minimal system memory available
- `log_min_duration_statement=5000ms` - Only log very slow queries
- Both files use identical PostgreSQL settings; complete version adds MQTT support only

**Development (docker-compose-dev.yml)** - Enhanced debugging with minimal resources:

- `shared_buffers=128MB` - Very low memory usage for local development
- `work_mem=6MB` - Minimal memory per connection
- `log_min_duration_statement=1000ms` - Detailed query monitoring for optimization
- `track_functions=all` - Comprehensive function performance tracking

#### Environment Variable Configuration

All PostgreSQL settings can be customized using environment variables in your `.env` file:

```bash
# Memory Settings
GEOPULSE_POSTGRES_SHARED_BUFFERS=512MB          # Main PostgreSQL buffer cache
GEOPULSE_POSTGRES_WORK_MEM=16MB                 # Memory per connection for sorting/hashing
GEOPULSE_POSTGRES_MAINTENANCE_WORK_MEM=128MB    # Memory for maintenance operations
GEOPULSE_POSTGRES_EFFECTIVE_CACHE_SIZE=2GB      # Expected available OS cache

# WAL and Checkpoint Settings
GEOPULSE_POSTGRES_MAX_WAL_SIZE=1GB              # Maximum WAL size before checkpoint
GEOPULSE_POSTGRES_WAL_BUFFERS=32MB              # WAL buffer size in memory
GEOPULSE_POSTGRES_CHECKPOINT_TARGET=0.9         # Checkpoint completion target (0.0-1.0)

# Performance Settings
GEOPULSE_POSTGRES_RANDOM_PAGE_COST=1.1          # Cost of random page access (SSD optimized)
GEOPULSE_POSTGRES_IO_CONCURRENCY=100            # Expected concurrent I/O operations
GEOPULSE_POSTGRES_PARALLEL_WORKERS=2            # Max parallel workers per query

# Autovacuum Settings
GEOPULSE_POSTGRES_AUTOVACUUM_NAPTIME=60s        # Time between autovacuum runs
GEOPULSE_POSTGRES_VACUUM_SCALE_FACTOR=0.2       # Vacuum threshold as fraction of table size

# Logging Settings
GEOPULSE_POSTGRES_LOG_SLOW_QUERIES=2000         # Log queries slower than this (ms)
GEOPULSE_POSTGRES_LOG_CHECKPOINTS=off           # Log checkpoint activity
GEOPULSE_POSTGRES_LOG_STATEMENT=none            # Log SQL statements (none/ddl/mod/all)
GEOPULSE_POSTGRES_LOG_AUTOVACUUM=0              # Log autovacuum activity duration (ms)
GEOPULSE_POSTGRES_TRACK_FUNCTIONS=none          # Track function performance (none/pl/all)
GEOPULSE_POSTGRES_SYNC_COMMIT=on                # Synchronous commit mode
```

#### Example Custom Configuration for High-Performance Setup

```bash
# High-performance .env settings (requires 4GB+ container)
GEOPULSE_POSTGRES_SHARED_BUFFERS=1GB
GEOPULSE_POSTGRES_WORK_MEM=24MB
GEOPULSE_POSTGRES_MAINTENANCE_WORK_MEM=256MB
GEOPULSE_POSTGRES_EFFECTIVE_CACHE_SIZE=3GB
GEOPULSE_POSTGRES_MAX_WAL_SIZE=2GB
GEOPULSE_POSTGRES_WAL_BUFFERS=64MB
GEOPULSE_POSTGRES_PARALLEL_WORKERS=4
GEOPULSE_POSTGRES_IO_CONCURRENCY=200
GEOPULSE_POSTGRES_LOG_SLOW_QUERIES=1000
```

#### GPS Workload Optimizations

The database configurations include optimizations specific to GPS tracking applications:

- **Spatial Query Performance**: `random_page_cost=1.1` optimized for SSD storage
- **Bulk GPS Inserts**: Enhanced `wal_buffers` and checkpoint settings
- **Timeline Processing**: Optimized for large sequential scans and complex spatial joins
- **Concurrent Users**: `work_mem` tuned to prevent memory exhaustion with multiple users

#### Monitoring Database Performance

**Query Performance**:

```sql
-- Monitor slow queries
SELECT query, mean_exec_time, calls, total_exec_time
FROM pg_stat_statements
WHERE mean_exec_time > 1000
ORDER BY mean_exec_time DESC;
```

**Memory Usage**:

```sql
-- Check buffer hit ratio (should be >99%)
SELECT round(100.0 * blks_hit / (blks_hit + blks_read), 2) AS hit_ratio
FROM pg_stat_database
WHERE datname = current_database();
```

**Storage and Vacuum**:

```sql
-- Monitor autovacuum performance
SELECT schemaname, tablename, last_vacuum, last_autovacuum
FROM pg_stat_user_tables
WHERE tablename LIKE '%gps%'
   OR tablename LIKE '%timeline%';
```

</details>

## Production Deployment Example: Reverse Proxy

For a production deployment using a domain name, you must terminate HTTPS at a reverse proxy and forward traffic to the GeoPulse containers.

1. **Configure your `.env` file for production** (as shown in the "Web Access & Security Configuration" section).

2. **Set up your reverse proxy.** Here is a basic Nginx configuration example. You will also need to set up SSL certificates (e.g., using Let's Encrypt).

**Example Nginx Configuration**

```nginx
# /etc/nginx/sites-available/geopulse.conf

server {
    listen 80;
    server_name geopulse.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name geopulse.yourdomain.com;

    # SSL certs
    ssl_certificate /path/to/your/fullchain.pem;
    ssl_certificate_key /path/to/your/privkey.pem;

    # Frontend
    location / {
        proxy_pass http://localhost:5555;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## Running as Non-Root User (Optional)

GeoPulse containers support running as non-root users for enhanced security in production environments. This is **completely optional** - by default, containers work without any changes.

### Default Behavior (No Changes Required)

By default, GeoPulse containers run with these security settings:

- **Backend (JVM & Native)**: Already runs as non-root user (secure by default) ✅
- **Frontend (nginx)**: Runs as root user on port 80 (for backward compatibility)

**Existing deployments continue to work without modifications.**

### Optional: Running Frontend as Non-Root

For enhanced security, you can configure the frontend to run as a non-root user. This requires **two simple changes** in your `docker-compose.yml`:

1. Add the `user` directive with **any UID:GID** you prefer
2. Update the port mapping from `80` to `8080`

**Example for docker-compose.yml:**

```yaml
services:
  geopulse-ui:
    image: tess1o/geopulse-ui:${GEOPULSE_VERSION}
    container_name: geopulse-ui
    restart: unless-stopped
    user: "1000:1000"  # ← ADD THIS: Use your preferred UID:GID
    env_file:
      - .env
    ports:
      - 5555:8080      # ← CHANGE THIS: 80 → 8080
    depends_on:
      geopulse-backend:
        condition: service_healthy
```

**Flexibility**: The container works with **any UID:GID combination**:
- `user: "1000:1000"` - Your user UID and GID (most common)
- `user: "1001:1001"` - Different user
- `user: "1000:0"` - OpenShift pattern (UID with root group)
- Any other valid UID:GID pair

The container automatically detects the user and adjusts accordingly - no additional configuration needed!

### Optional: Running Backend with Custom User ID

Both backend images (JVM and native) already run as non-root by default, so **no changes are needed**. However, if you want to use a specific user ID (e.g., to match your host filesystem permissions), you can override it:

```yaml
services:
  geopulse-backend:
    image: tess1o/geopulse-backend:${GEOPULSE_VERSION}-native
    container_name: geopulse-backend
    user: "1000:1000"  # ← OPTIONAL: Override with any UID:GID
    # ... rest of configuration
```

**Backend flexibility**: Works with any UID:GID combination, including:
- `user: "1000:1000"` - Standard docker-compose usage
- `user: "1000:0"` - OpenShift pattern (recommended for Kubernetes)

### How It Works

The frontend container automatically detects whether it's running as root or non-root:

- **Running as root (UID 0)**: Uses port 80 (default, backward compatible)
- **Running as non-root (any other UID)**: Automatically switches to port 8080 (non-privileged)

This auto-detection ensures backward compatibility while enabling security-enhanced deployments.

### Benefits of Running as Non-Root

Running containers as non-root users provides several security advantages:

- **Principle of Least Privilege**: Containers run with minimal permissions required
- **Container Escape Mitigation**: Limits potential damage if a container is compromised
- **Compliance**: Meets security requirements for production environments and regulated industries
- **Defense in Depth**: Adds an additional security layer to your deployment

### Kubernetes/OpenShift Compatibility

All GeoPulse container images are designed to run in Kubernetes and OpenShift environments with flexible security contexts:

**Frontend Container:**
- Uses world-writable directories (777) for runtime files
- Works with **any UID:GID combination** - no special requirements
- Automatically adapts to root or non-root execution

**Backend Container:**
- Uses OpenShift pattern (user:group 0 with group permissions)
- Works best with **GID 0** for Kubernetes/OpenShift
- Also supports any UID:GID with proper volume permissions

**Example Kubernetes security contexts:**

```yaml
# Frontend - Simple (any UID:GID works)
frontend:
  securityContext:
    runAsNonRoot: true
    runAsUser: 101
    runAsGroup: 101

# Backend - OpenShift Pattern (recommended)
backend:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    fsGroup: 0  # Root group for OpenShift pattern
```

Both containers work seamlessly with Kubernetes `runAsUser` and OpenShift's automatic UID assignment. See the [Helm deployment guide](./helm-deployment.md) for complete Kubernetes/Helm configuration.

---

## Backward Compatibility

Existing `.env` files continue to work without mandatory changes.

- `GEOPULSE_UI_URL` is still supported as a legacy fallback for CORS/OIDC behavior.
- New deployments should use `GEOPULSE_CORS_ENABLED`, `GEOPULSE_CORS_ORIGINS`, and `GEOPULSE_PUBLIC_BASE_URL`.
- For OIDC callback URLs, prefer explicit `GEOPULSE_OIDC_CALLBACK_BASE_URL`.
- Detailed migration guidance is available in [Updating GeoPulse](../../system-administration/maintenance/updating.md).

---

## Troubleshooting

**Non-root user issues:**

If you've added the `user` directive to the frontend service but forgot to update the port mapping:

```bash
# Check container logs
docker compose logs geopulse-ui

# You'll see: "Running as non-root (UID 1000) - using port 8080"
# But the port mapping is still 5555:80 - this causes connection issues
```

**Solution**: Update the port mapping in docker-compose.yml from `5555:80` to `5555:8080`.

**Port conflicts:**

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

**Service issues:**

```bash
docker compose logs -f
curl http://localhost:8080/api/health
```

**Authentication issues:**

- Check domains match between frontend/backend
- For standard deployments (with nginx proxy): `GEOPULSE_COOKIE_DOMAIN=""` (keep empty)
- For HTTP deployments: `GEOPULSE_AUTH_SECURE_COOKIES=false`
- For HTTPS deployments: `GEOPULSE_AUTH_SECURE_COOKIES=true`
- Verify JWT keys exist in `keys/` directory
- For split-subdomain setups, see [Authentication Configuration](../../system-administration/configuration/authentication.md)

**Network/Proxy issues:**

- API requests failing: Check that frontend nginx proxy is configured correctly
- Cross-origin browser requests failing: Enable CORS with `GEOPULSE_CORS_ENABLED=true` and configure `GEOPULSE_CORS_ORIGINS`
- OIDC callback URL is wrong: Set `GEOPULSE_OIDC_CALLBACK_BASE_URL` (preferred) or `GEOPULSE_PUBLIC_BASE_URL`
- Development proxy issues: Ensure Vite dev server is running with proxy configuration

**Configuration issues:**

- Check `.env` file is properly formatted and readable by containers
- Verify `GEOPULSE_BACKEND_URL` uses correct container name (`http://geopulse-backend:8080` for Docker)
- For homelab/public deployment: Set `GEOPULSE_PUBLIC_BASE_URL` to your externally reachable URL

**Key generation issues:**

- JWT keys and AI encryption keys are automatically generated in the `keys/` directory on first startup
- If you see key-related errors, check that the `geopulse-keygen` service completed successfully:
  `docker compose logs geopulse-keygen`
- Keys are persistent - they won't be regenerated if they already exist
- To regenerate keys: remove the `keys/` directory and restart with `docker compose up -d`

**CPU compatibility issues:**

If the backend container crashes immediately with errors about missing CPU features:

```bash
docker compose logs geopulse-backend
```

Look for errors like:
- `[AVX2, BMI1, BMI2, FMA, F16C, LZCNT]` (AMD64 CPUs)
- `[FP, ASIMD, CRC32, LSE]` (ARM64/Raspberry Pi)

**Solution**: Switch to the compatible image tag as described in the "CPU Compatibility Issues" section above. Edit your `docker-compose.yml` to use `${GEOPULSE_VERSION}-native-compat` instead of `${GEOPULSE_VERSION}-native`.

**Check your CPU capabilities:**
```bash
# For x86/AMD64:
lscpu | grep -i "flags"
# Look for: avx2, bmi1, bmi2, fma

# For ARM64:
cat /proc/cpuinfo | grep "Features"
# Look for: asimd, crc32, atomics
```
