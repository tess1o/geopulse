<div align="center">
  <p><img src="../frontend/public/geopulse-logo.svg" alt="GeoPulse Logo" width="80"/></p>
  <h2>GeoPulse Deployment Guide</h2>
</div>

---

**← Back to:** **[GeoPulse Overview](../README.md)**

---

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

There is one time service "geopulse-keygen" that creates JWT keys in the "keys" folder.

---

## Setup

### 1. Download Configuration Files

#### Step 1: Download `.env` configuration and key generation script

**Using wget:**

```bash
wget -O .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
wget -O generate-keys.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/generate-keys.sh
chmod +x generate-keys.sh
```

**Using curl:**

```bash
curl -L -o .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
curl -L -o generate-keys.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/generate-keys.sh
chmod +x generate-keys.sh
```

#### Step 2: Choose your deployment type

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

#### Step 3: Configure environment variables

**Step 3.1:** If you chose Option B (MQTT broker), enable MQTT integration in `.env`:

```env
GEOPULSE_MQTT_ENABLED=true
```

**Step 3.2:** Change database and MQTT passwords (optional, but recommended):

```env
GEOPULSE_POSTGRES_PASSWORD=your-secure-database-password
GEOPULSE_MQTT_PASSWORD=your-secure-mqtt-password
```

### 2. Start GeoPulse

```bash
docker compose up -d
```

**Access:**

- Frontend: http://localhost:5555
- API: http://localhost:8080/api

---

## Homelab Deployment

GeoPulse is designed to work seamlessly across multiple access methods in homelab environments:

### Multiple Access Methods Support

Update your `.env` file to support multiple UI URLs:

```env
# Support multiple access methods (comma-separated)
GEOPULSE_UI_URL=http://localhost:5555,http://192.168.1.100:5555,http://your-tailscale-ip:5555
```

This allows you to access GeoPulse via:

- **Localhost**: `http://localhost:5555`
- **Local Network**: `http://192.168.1.100:5555`
- **Tailscale**: `http://your-tailscale-ip:5555`
- **Any combination** of the above

---

## Production Deployment

For server deployment (like VPS) with your domain:

1. **Complete setup steps above**

2. **Edit `.env` file:**

Update the following values:

```env
GEOPULSE_UI_URL=https://geopulse.yourdomain.com
GEOPULSE_COOKIE_DOMAIN=.yourdomain.com
GEOPULSE_AUTH_SECURE_COOKIES=true
```

By setting `GEOPULSE_AUTH_SECURE_COOKIES=true` the `access_token` and `refresh_token` cookies will use flag `Secure`.

3. **Configure reverse proxy** (Nginx/Caddy/Traefik) for HTTPS

You must terminate HTTPS at a reverse proxy and forward traffic to the GeoPulse containers. Here is a basic Nginx
configuration. You will also need to set up SSL certificates (e.g., using Let's Encrypt).

**Example of nginx configuration**

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
- For local deployment: `GEOPULSE_COOKIE_DOMAIN=""` and `GEOPULSE_AUTH_SECURE_COOKIES=false`
- For production: set `GEOPULSE_COOKIE_DOMAIN=.yourdomain.com` and `GEOPULSE_AUTH_SECURE_COOKIES=true`
- Verify JWT keys exist in `keys/` directory

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

- JWT keys are automatically generated in the `keys/` directory on first startup
- If you see key-related errors, check that the `geopulse-keygen` service completed successfully:
  `docker compose logs geopulse-keygen`
- Keys are persistent - they won't be regenerated if they already exist
- To regenerate keys: remove the `keys/` directory and restart with `docker compose up -d`

---

## Performance Optimization

<details>
<summary><strong>🚀 Advanced Performance Tuning (Click to expand)</strong></summary>

### Memory Configuration by Use Case

GeoPulse automatically optimizes JVM memory usage based on container limits. Here are recommended configurations:

#### Minimal Deployment (1-2 users)
```yaml
# docker-compose.yml
services:
  geopulse-backend:
    deploy:
      resources:
        limits:
          memory: 512Mi
        requests:
          memory: 256Mi
    # Uses ~384MB heap automatically (75% of 512MB)
```

#### Standard Deployment (3-10 users)
```yaml
# docker-compose.yml  
services:
  geopulse-backend:
    deploy:
      resources:
        limits:
          memory: 1Gi
        requests:
          memory: 512Mi
    # Uses ~768MB heap automatically (75% of 1GB)
```

#### Production Deployment (10+ users)
```yaml
# docker-compose.yml
services:
  geopulse-backend:
    deploy:
      resources:
        limits:
          memory: 2Gi
        requests:
          memory: 1Gi
    environment:
      - JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:G1HeapRegionSize=16m -XX:+FlightRecorder -XX:StartFlightRecording=duration=0,filename=/app/logs/performance.jfr
    volumes:
      - ./logs:/app/logs
      - ./dumps:/app/dumps
```

### Custom JVM Tuning

#### Override Default Settings
```yaml
# docker-compose.yml
services:
  geopulse-backend:
    environment:
      # Example: Use 80% of container memory instead of default 75%
      - JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=80 -XX:+UseG1GC
```

#### Development Monitoring Setup
```yaml
# docker-compose-dev.yml (already configured)
services:
  geopulse-backend-dev:
    ports:
      - "9999:9999"  # JMX monitoring port
    environment:
      - JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false
    volumes:
      - ./logs:/app/logs  # Performance recordings
```

</details>