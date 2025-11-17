# Docker Deployment

## Prerequisites

Before you begin, ensure you have the following installed:

- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)

## Overview about the system components

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

### 1. Download Configuration Files

First, download the necessary configuration files.

#### Step 1.1: Download `.env` configuration and `generate-keys.sh` script

**Using wget:**

```bash
wget -O .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
wget https://raw.githubusercontent.com/tess1o/GeoPulse/main/generate-keys.sh
chmod +x generate-keys.sh
```

**Using curl:**

```bash
curl -L -o .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
curl -L -o generate-keys.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/generate-keys.sh
chmod +x generate-keys.sh
```

#### Step 1.2: Choose your deployment type

**Option A: Basic deployment (without MQTT broker)**

Download the basic docker-compose.yml:

Using wget:

```bash
wget -O docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.yml
```

Using curl:

```bash
curl -L -o docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.yml
```

**Option B: Full deployment (with MQTT broker for OwnTracks)**

Download the complete docker-compose.yml and mosquitto entrypoint script:

Using wget:

```bash
wget -O docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose-complete.yml
wget -O mosquitto_entrypoint.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/mosquitto_entrypoint.sh
chmod +x mosquitto_entrypoint.sh
```

Using curl:

```bash
curl -L -o docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose-complete.yml
curl -L -o mosquitto_entrypoint.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/mosquitto_entrypoint.sh
chmod +x mosquitto_entrypoint.sh
```

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

> #### A note on Rasberry Pi support
> If you are deploying on a Raspberry Pi, you can use the `tess1o/geopulse-backend:1.4.1-native-raspi` image.
> 
> Change `docker-compose.yml` to use this image.
>
> ```yaml
>    geopulse-backend:
>      image: tess1o/geopulse-backend:${GEOPULSE_VERSION}-native-raspi
> ```

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

- `GEOPULSE_MQTT_ENABLED`: Set to `true` to enable the OwnTracks MQTT integration. This requires using the full deployment `docker-compose.yml`.
  ```env
  GEOPULSE_MQTT_ENABLED=true
  ```

#### Web Access & Security Configuration

These variables control how users access the GeoPulse frontend and how authentication cookies are handled.

- `GEOPULSE_UI_URL`: A comma-separated list of URLs that can be used to access the frontend. This is crucial for CORS (Cross-Origin Resource Sharing) to work correctly.
- `GEOPULSE_COOKIE_DOMAIN`: The domain for authentication cookies. **Keep empty for standard deployments** (see details below).
- `GEOPULSE_AUTH_SECURE_COOKIES`: Set to `true` to ensure cookies are only sent over HTTPS.

##### Understanding GEOPULSE_COOKIE_DOMAIN

**In GeoPulse's architecture**, nginx acts as a reverse proxy that serves both the frontend and proxies API requests to the backend. From the browser's perspective, all requests (frontend assets and API calls) come from the same origin (e.g., `geopulse.yourdomain.com`). This is called **same-origin**, and authentication cookies work automatically without setting a cookie domain.

**When to keep GEOPULSE_COOKIE_DOMAIN empty (recommended):**
- ✅ All standard Docker deployments using docker-compose
- ✅ Localhost access: `http://localhost:5555`
- ✅ Homelab IP access: `http://1.4.168.1.100:5555`
- ✅ Single domain production: `https://geopulse.yourdomain.com`
- ✅ Any deployment where nginx proxies both frontend and backend (standard GeoPulse setup)

**Why keep it empty?**
- Browser automatically handles cookies for same-origin requests
- More secure (cookies won't leak to other subdomains)
- Simpler configuration with fewer potential issues

**When to set GEOPULSE_COOKIE_DOMAIN (rare scenarios):**
- ❌ Only if deploying WITHOUT nginx proxy AND using separate subdomains
- ❌ Example: Frontend at `app.yourdomain.com`, Backend at `api.yourdomain.com`
- ❌ In this case, set `GEOPULSE_COOKIE_DOMAIN=.yourdomain.com`
- ⚠️ **Warning**: This is NOT a standard GeoPulse deployment and requires additional configuration changes

**Scenario-based examples:**

- **Localhost-only access:**
  ```env
  GEOPULSE_UI_URL=http://localhost:5555
  GEOPULSE_COOKIE_DOMAIN=           # Leave empty - nginx proxies everything
  GEOPULSE_AUTH_SECURE_COOKIES=false
  ```

- **Homelab access (via IP address and/or local domain):**
  ```env
  # Allows access via localhost, a local network IP, and a local domain
  GEOPULSE_UI_URL=http://localhost:5555,http://1.4.168.1.100:5555,http://geopulse.local
  GEOPULSE_COOKIE_DOMAIN=           # Leave empty - nginx proxies everything
  GEOPULSE_AUTH_SECURE_COOKIES=false
  ```

- **Production access (with a domain and HTTPS):**
  ```env
  GEOPULSE_UI_URL=https://geopulse.yourdomain.com
  GEOPULSE_COOKIE_DOMAIN=           # Leave empty - nginx proxies everything
  GEOPULSE_AUTH_SECURE_COOKIES=true
  ```

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

### 3. Start GeoPulse

Once you have configured your `.env` file, you can start the application.

```bash
docker compose up -d
```

**Access:**

- Frontend: http://localhost:5555 (or your configured URL)
- API: http://localhost:8080/api

---

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

## Troubleshooting

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
- See the "Understanding GEOPULSE_COOKIE_DOMAIN" section above for detailed cookie configuration

**Network/Proxy issues:**

- API requests failing: Check that frontend nginx proxy is configured correctly
- Multiple access methods not working: Ensure `GEOPULSE_UI_URL` includes all required URLs (comma-separated)
- CORS errors: Verify backend `GEOPULSE_UI_URL` matches your access URLs
- Development proxy issues: Ensure Vite dev server is running with proxy configuration

**Configuration issues:**

- Check `.env` file is properly formatted and readable by containers
- Verify `GEOPULSE_BACKEND_URL` uses correct container name (`http://geopulse-backend:8080` for Docker)
- For homelab: Use your actual IP addresses in `GEOPULSE_UI_URL`

**Key generation issues:**

- JWT keys and AI encryption keys are automatically generated in the `keys/` directory on first startup
- If you see key-related errors, check that the `geopulse-keygen` service completed successfully:
  `docker compose logs geopulse-keygen`
- Keys are persistent - they won't be regenerated if they already exist
- To regenerate keys: remove the `keys/` directory and restart with `docker compose up -d`
