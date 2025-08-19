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

## Quick Start

1. Generate JWT keys
2. Download configuration files
3. Start with Docker Compose

---

## Setup

### 1. Generate JWT Keys

These keys are required to sign and verify JWT tokens on the server side.

```bash
mkdir -p keys
docker run --rm -v "$(pwd)/keys:/keys" alpine:latest sh -c "
  apk add --no-cache openssl &&
  openssl genpkey -algorithm RSA -out /keys/jwt-private-key.pem &&
  openssl rsa -pubout -in /keys/jwt-private-key.pem -out /keys/jwt-public-key.pem &&
  chmod 644 /keys/jwt-*.pem
"
```

### 2. Download Configuration and docker-compose

Download `.env` configuration

```bash
wget -O .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
```

If you do not want to use MQTT broker (for OwnTracks app), download this `docker-compose.yml`:

```bash
wget -O docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.yml
```

If you want to use MQTT broker (for OwnTracks app), download this `docker-compose.yml` and mosquitto entrypoint script.
The entrypoint script will be executed automatically by docker container and will configure MQTT broker: create config,
setup integration with GeoPulse database for custom authentication, setup admin user.

```bash
wget -O docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose-complete.yml
wget -O mosquitto_entrypoint.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/mosquitto_entrypoint.sh
chmod +x mosquitto_entrypoint.sh
```

Update `.env` file:
If you want to use MQTT broker, enable MQTT integration:

```env
GEOPULSE_MQTT_ENABLED=true
```

Change database and MQTT passwords (recommended)

```bash
GEOPULSE_POSTGRES_PASSWORD=change-this-secure-password
GEOPULSE_MQTT_PASSWORD=change-this-mqtt-admin-password
```

### 3. Start GeoPulse

```bash
docker compose up -d
```

**Access:**

- Frontend: http://localhost:5555
- API: http://localhost:8080/api

---

## Production Deployment

For server deployment with your domain:

1. **Complete setup steps above**

2. **Edit `.env` file:**
   ```bash
   nano .env
   ```
   
   Update the following values:
   ```env
   GEOPULSE_UI_URL=https://geopulse.yourdomain.com
   GEOPULSE_BACKEND_URL=https://geopulse-api.yourdomain.com
   GEOPULSE_COOKIE_DOMAIN=.yourdomain.com
   GEOPULSE_AUTH_SECURE_COOKIES=true
   ```

3. **Configure reverse proxy** (Nginx/Caddy/Traefik) for HTTPS

You must terminate HTTPS at a reverse proxy and forward traffic to the GeoPulse containers. Here is a basic Nginx
configuration. You will also need to set up SSL certificates (e.g., using Let's Encrypt).

```nginx
# /etc/nginx/sites-available/geopulse.conf

server {
    listen 80;
    server_name geopulse.yourdomain.com geopulse-api.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name geopulse.yourdomain.com;

    # SSL certs
    ssl_certificate /path/to/your/fullchain.pem;
    ssl_certificate_key /path/to/your/privkey.pem;

    location / {
        proxy_pass http://localhost:5555;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 443 ssl;
    server_name geopulse-api.yourdomain.com;

    # SSL certs (can be the same)
    ssl_certificate /path/to/your/fullchain.pem;
    ssl_certificate_key /path/to/your/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
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

---

## Security

- Use strong passwords for database and MQTT broker
- Never expose PostgreSQL/MQTT port externally
- Use HTTPS in production
- Keep JWT keys secure