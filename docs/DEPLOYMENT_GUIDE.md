<div align="center">
  <p><img src="../frontend/public/geopulse-logo.svg" alt="GeoPulse Logo" width="80"/></p>
  <h2>GeoPulse Deployment Guide</h2>
</div>

---

**← Back to:** **[GeoPulse Overview](../README.md)**

---

## 📖 Comprehensive Deployment Guide

This detailed guide covers all deployment options, customizations, and troubleshooting. Use this if:
- You need custom configuration beyond the examples  
- You want to understand all the options available
- You're troubleshooting deployment issues
- You prefer manual configuration

### Quick Start Overview

1. 🔐 **Choose Authorization Mode** (`cookies` or `localStorage`)
2. 🔑 **Generate JWT Keys**
3. 🐳 **Configure Docker Compose**
4. ▶️ **Start the Application**
5. ♻️ **Update GeoPulse**

---

## 🛠 Prerequisites

* ✅ [Docker](https://www.docker.com/) (version 20.10+) and [Docker Compose](https://docs.docker.com/compose/) (version
  2.0+) installed
* ✅ Ports `8080` (backend) and `5555` (frontend) must be available
    * 📝 **Note**: These ports can be changed in the `docker-compose.yml` file
    * 🛡️ **Security**: Remove port mappings if using a reverse proxy to avoid exposing services directly to the internet
* 🌐 (For production) A domain and reverse proxy (e.g., Nginx, Traefik, or Caddy)
* 💾 At least 1GB of available disk space for Docker images and database

---

## 1. 🔐 Choose Authorization Mode

GeoPulse uses **JWT tokens** (access + refresh) for authentication. You can store these tokens on the frontend using
either:

* 🧁 `HttpOnly Cookies` (recommended for production)
* 📔 `LocalStorage` (simpler for local setups)

### 🔒 Cookie Mode (Recommended for Production)

* ✅ Secure via **HttpOnly cookies** (XSS-protected)
* 🌐 Requires same root domain (e.g., `geopulse.yourdomain.com` and `geopulse-api.yourdomain.com`)
* 🛡️ Requires a reverse proxy for HTTPS

⚠️ **Domain Requirements for Cookie Mode**:

- Both frontend and backend must share the same root domain
- Example: `app.example.com` (frontend) and `api.example.com` (backend) ✅
- Example: `example.com` (frontend) and `different.com` (backend) ❌

### 📔 LocalStorage Mode (For Local Machine & Dev)

* ❗ Tokens stored in browser `localStorage` (XSS-vulnerable)
* ✅ Works across different domains
* 🧪 Ideal for local machine or self-hosted environments

⚠️ **Important**: GeoPulse does **not** serve HTTPS directly. You must use a reverse proxy (e.g., Nginx Proxy Manager
with Let's Encrypt, Traefik, or Caddy) to handle TLS/SSL termination.

---

## 2. 🔑 Generate JWT Keys

Run the following to create the signing keys:

```bash
# Create keys directory
mkdir -p keys

# Generate RSA private/public key pair using Docker
docker run --rm -v "$(pwd)/keys:/keys" alpine:latest sh -c "
  apk add --no-cache openssl &&
  openssl genpkey -algorithm RSA -out /keys/jwt-private-key.pem &&
  openssl rsa -pubout -in /keys/jwt-private-key.pem -out /keys/jwt-public-key.pem &&
  chmod 644 /keys/jwt-*.pem
"
```

This will create:

* 🔐 `keys/jwt-private-key.pem` – used to **sign** JWTs
* 🔓 `keys/jwt-public-key.pem` – used to **verify** JWTs

---

## 3. ⚙️ Docker Compose Configuration

### 📜 Environment Configuration

Download pre-configured .env examples:
```bash
# For local machine (no external domain)
wget -O .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example.localhost

# For local machine + MQTT support (OwnTracks MQTT mode)
wget -O .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example.localhost_mqtt

# For server with domain (reverse proxy required)
wget -O .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example.cookies

# For server with domain + MQTT support (OwnTracks MQTT mode)
wget -O .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example.cookies_mqtt
```

Then edit the downloaded `.env` file to update passwords and domains as needed.

<details>
<summary>📋 Manual .env Configuration Examples (click to expand)</summary>

If you prefer to create the `.env` file manually, here are the configuration templates:

#### For `cookie` based authentication (with reverse proxy and domain). Preferable for server deployments

```env
# Domain Configuration (update UI and backend URLs and root domain accordingly)
GEOPULSE_UI_URL=https://geopulse.yourdomain.com
GEOPULSE_BACKEND_URL=https://geopulse-api.yourdomain.com
GEOPULSE_COOKIE_DOMAIN=.yourdomain.com

# Database Configuration
GEOPULSE_POSTGRES_HOST=geopulse-postgres
GEOPULSE_POSTGRES_PORT=5432
GEOPULSE_POSTGRES_DB=geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password
```

#### For `cookie` based authentication for local machine only.

```env
# Domain Configuration
GEOPULSE_UI_URL=http://localhost:5555
GEOPULSE_BACKEND_URL=http://localhost:8080
GEOPULSE_COOKIE_DOMAIN=.localhost

# Database Configuration
GEOPULSE_POSTGRES_HOST=geopulse-postgres
GEOPULSE_POSTGRES_PORT=5432
GEOPULSE_POSTGRES_DB=geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password
```

#### For local machine + MQTT support

```env
# Domain Configuration
GEOPULSE_UI_URL=http://localhost:5555
GEOPULSE_BACKEND_URL=http://localhost:8080
GEOPULSE_COOKIE_DOMAIN=""

# Database Configuration
GEOPULSE_POSTGRES_HOST=geopulse-postgres
GEOPULSE_POSTGRES_PORT=5432
GEOPULSE_POSTGRES_DB=geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password

# Auth settings
GEOPULSE_AUTH_MODE=localStorage
GEOPULSE_AUTH_SECURE_COOKIES=false
GEOPULSE_JWT_HEADER=Authorization
GEOPULSE_JWT_COOKIE=""

# MQTT Configuration
GEOPULSE_MQTT_ENABLED=true
GEOPULSE_MQTT_BROKER_HOST=geopulse-mosquitto
GEOPULSE_MQTT_BROKER_PORT=1883
GEOPULSE_MQTT_USERNAME=geopulse_mqtt_admin
GEOPULSE_MQTT_PASSWORD=my-mqtt-admin-password
```

#### For `localStorage` authentication (reverse proxy with domain is used)

```env
# Domain Configuration (update UI and backend URLs and root domain accordingly)
GEOPULSE_UI_URL=https://geopulse.yourdomain.com
GEOPULSE_BACKEND_URL=https://geopulse-api.yourdomain.com
GEOPULSE_COOKIE_DOMAIN=""

# Database Configuration
GEOPULSE_POSTGRES_HOST=geopulse-postgres
GEOPULSE_POSTGRES_PORT=5432
GEOPULSE_POSTGRES_DB=geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password

# Auth settings
GEOPULSE_AUTH_MODE=localStorage
GEOPULSE_AUTH_SECURE_COOKIES=false
GEOPULSE_JWT_HEADER=Authorization
GEOPULSE_JWT_COOKIE=""
```

#### For local machine setup (no domain, no reverse proxy):

```env
GEOPULSE_UI_URL=http://localhost:5555
GEOPULSE_BACKEND_URL=http://localhost:8080
GEOPULSE_COOKIE_DOMAIN=""

# Database Configuration
GEOPULSE_POSTGRES_HOST=geopulse-postgres
GEOPULSE_POSTGRES_PORT=5432
GEOPULSE_POSTGRES_DB=geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password

# Auth settings
GEOPULSE_AUTH_MODE=localStorage
GEOPULSE_AUTH_SECURE_COOKIES=false
GEOPULSE_JWT_HEADER=Authorization
GEOPULSE_JWT_COOKIE=""
```

</details>

---

### 📦 Docker Compose Configuration

Download pre-configured docker-compose files:
```bash
# Basic services (backend + UI + postgres)  
wget -O docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.yml

# Complete setup with MQTT (backend + UI + postgres + mosquitto)
wget -O docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose-complete.yml
```

**If you selected MQTT support:** Download the MQTT entrypoint script (required for MQTT):
```bash
wget -O mosquitto_entrypoint.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/mosquitto_entrypoint.sh
chmod +x mosquitto_entrypoint.sh
```

> 💡 **What is MQTT?** MQTT allows OwnTracks to send GPS data in real-time with lower battery usage. If you're unsure, start without MQTT - you can always add it later.

<details>
<summary>🐳 Manual Docker Compose Example (click to expand)</summary>

If you need customizations, here's the basic template:

```yaml
services:
  geopulse-backend:
    image: tess1o/geopulse-backend:1.0.0-rc.2
    container_name: geopulse-backend
    restart: unless-stopped
    env_file:
      - .env
    environment:
      - GEOPULSE_POSTGRES_URL=jdbc:postgresql://${GEOPULSE_POSTGRES_HOST}:${GEOPULSE_POSTGRES_PORT}/${GEOPULSE_POSTGRES_DB}
    ports:
      - 8080:8080
    volumes:
      - ./keys:/app/keys
    depends_on:
      geopulse-postgres:
        condition: service_healthy

  geopulse-ui:
    image: tess1o/geopulse-ui:1.0.0-rc.2
    container_name: geopulse-ui
    restart: unless-stopped
    ports:
      - 5555:80
    depends_on:
      - geopulse-backend
    environment:
      - API_BASE_URL=${GEOPULSE_BACKEND_URL}/api

  geopulse-postgres:
    image: postgis/postgis:17-3.5
    container_name: geopulse-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${GEOPULSE_POSTGRES_USERNAME}
      POSTGRES_PASSWORD: ${GEOPULSE_POSTGRES_PASSWORD}
      POSTGRES_DB: ${GEOPULSE_POSTGRES_DB}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U ${GEOPULSE_POSTGRES_USERNAME} -d ${GEOPULSE_POSTGRES_DB}" ]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
```

</details>

---

## 4. ▶️ Start the Application

```bash
# Start all services in background
docker compose up -d

# Tail logs
docker compose logs -f

# Check status
docker compose ps

# Stop all services
docker compose down
```

### 🔗 Access GeoPulse

* 🗰 **With reverse proxy**:

    * Frontend: `https://geopulse.yourdomain.com`
    * API: `https://geopulse-api.yourdomain.com/api`
    * MQTT Broker: `your-mqtt-domain.com:1883` (if MQTT enabled)

* 💻 **Local machine**:

    * Frontend: `http://localhost:5555`
    * API: `http://localhost:8080/api`  
    * MQTT Broker: `localhost:1883` (if MQTT enabled)

---

## 5. 🔄 Updating GeoPulse

```bash
# Pull latest backend & frontend images
docker compose pull

# Restart with new versions
docker compose up -d

# Watch logs for migrations and readiness
docker compose logs -f geopulse-backend
```

📦 **Note**: Database migrations are applied automatically on backend startup.

---


## 🔒 Security Recommendations

* 🔐 Use strong, unique passwords for `GEOPULSE_POSTGRES_PASSWORD` and `GEOPULSE_MQTT_PASSWORD` (if using MQTT)
* 🛡️ Never expose port `5432` (PostgreSQL) or `1883` (MQTT) directly to the internet
* 🌐 Always use HTTPS in production with proper SSL certificates
* 🔑 Keep your JWT keys secure and never commit them to version control
* 🚫 Remove port mappings in production when using reverse proxy

---

## 🏥 Health Checks

### Verify Installation

```bash
# Check all services are running
docker compose ps

# Test backend API (adjust URL based on your setup)
curl ${GEOPULSE_BACKEND_URL}/api/health

# Test frontend (adjust URL based on your setup)
curl -I ${GEOPULSE_UI_URL}
```

---

## 🔧 Troubleshooting

### Common Issues

**Port conflicts:**

```bash
# Check if ports are in use
netstat -tulpn | grep -E ':(8080|5555)'
# Or use Docker to check
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

**Permission issues with keys:**

```bash
# Fix key permissions if needed
sudo chown -R $USER:$USER keys/
chmod 600 keys/jwt-private-key.pem
chmod 644 keys/jwt-public-key.pem
```

**Database connection issues:**

```bash
# Check database health
docker compose logs geopulse-postgres
# Verify database is ready
docker compose exec geopulse-postgres pg_isready -U ${GEOPULSE_POSTGRES_USERNAME} -d ${GEOPULSE_POSTGRES_DB}
```

**Service not accessible through reverse proxy:**

```bash
# Check if services are running on Docker network
docker compose exec geopulse-backend curl http://localhost:8080/api/health
docker compose exec geopulse-ui curl http://localhost:80
```

---

## 💾 Backup & Recovery

### Database Backup

```bash
# Create backup
docker compose exec geopulse-postgres pg_dump -U ${GEOPULSE_POSTGRES_USERNAME} ${GEOPULSE_POSTGRES_DB} > backup.sql

# Restore backup
docker compose exec -T geopulse-postgres psql -U ${GEOPULSE_POSTGRES_USERNAME} ${GEOPULSE_POSTGRES_DB} < backup.sql
```

### Configuration Backup

```bash
# Backup your configuration
tar -czf geopulse-backup.tar.gz .env docker-compose.yml keys/
```