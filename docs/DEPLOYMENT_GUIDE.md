<div align="center">
  <p><img src="../frontend/public/geopulse-logo.svg" alt="GeoPulse Logo" width="80"/></p>
  <h2>GeoPulse Deployment Guide</h2>
</div>

#  

## 🚀 Quick Start

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

### 📔 LocalStorage Mode (For Localhost & Dev)

* ❗ Tokens stored in browser `localStorage` (XSS-vulnerable)
* ✅ Works across different domains
* 🧪 Ideal for local dev or self-hosted environments

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

### 📜 Create `.env` File

Set your environment variables depending on the auth mode.
Set correct UI and BACKEND urls, credentials to postgres database.

#### For `cookie` based authentication (with reverse proxy and domain). Preferable for production

```env
# Domain Configuration (update UI and backend URLs and root domain accordingly)
GEOPULSE_UI_URL=https://geopulse.yourdomain.com
GEOPULSE_BACKEND_URL=https://geopulse-api.yourdomain.com
GEOPULSE_COOKIE_DOMAIN=.yourdomain.com

# Database (don't change the URL, change only username and password).
GEOPULSE_POSTGRES_URL=jdbc:postgresql://geopulse-postgres:5432/geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password
```

#### For `cookie` based authentication for `localhost` only.

```env
# Domain Configuration
GEOPULSE_UI_URL=http://localhost:5555
GEOPULSE_BACKEND_URL=http://localhost:8080
GEOPULSE_COOKIE_DOMAIN=.localhost

# Database (don't change the URL, change only username and password).
GEOPULSE_POSTGRES_URL=jdbc:postgresql://geopulse-postgres:5432/geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password
```

#### For `localStorage` authentication (reverse proxy with domain is used)

```env
# Domain Configuration (update UI and backend URLs and root domain accordingly)
GEOPULSE_UI_URL=https://geopulse.yourdomain.com
GEOPULSE_BACKEND_URL=https://geopulse-api.yourdomain.com
GEOPULSE_COOKIE_DOMAIN=""

# Database (don't change the URL, change only username and password).
GEOPULSE_POSTGRES_URL=jdbc:postgresql://geopulse-postgres:5432/geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password

# Auth settings
GEOPULSE_AUTH_MODE=localStorage
GEOPULSE_AUTH_SECURE_COOKIES=false
GEOPULSE_JWT_HEADER=Authorization
GEOPULSE_JWT_COOKIE=""
```

#### For local setup (no domain, no reverse proxy):

```env
GEOPULSE_UI_URL=http://localhost:5555
GEOPULSE_BACKEND_URL=http://localhost:8080
GEOPULSE_COOKIE_DOMAIN=""

# Database (don't change the URL, change only username and password).
GEOPULSE_POSTGRES_URL=jdbc:postgresql://geopulse-postgres:5432/geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password

# Auth settings
GEOPULSE_AUTH_MODE=localStorage
GEOPULSE_AUTH_SECURE_COOKIES=false
GEOPULSE_JWT_HEADER=Authorization
GEOPULSE_JWT_COOKIE=""
```

---

### 📦 Create `docker-compose.yml`

```yaml
services:
  geopulse-backend:
    image: tess1o/geopulse-backend:1.0.0-rc.1
    container_name: geopulse-backend
    restart: unless-stopped
    env_file:
      - .env
    ports:
      - 8080:8080  # Change left port to customize external access
    volumes:
      - ./keys:/app/keys
    depends_on:
      geopulse-postgres:
        condition: service_healthy

  geopulse-ui:
    image: tess1o/geopulse-ui:1.0.0-rc.1
    container_name: geopulse-ui
    restart: unless-stopped
    ports:
      - 5555:80  # Change left port to customize external access
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
      POSTGRES_DB: geopulse
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U ${GEOPULSE_POSTGRES_USERNAME} -d geopulse" ]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
```

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

* 💻 **Local setup**:

    * Frontend: `http://localhost:5555`
    * API: `http://localhost:8080/api`

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

* 🔐 Use strong, unique passwords for `GEOPULSE_POSTGRES_PASSWORD`
* 🛡️ Never expose port `5432` (PostgreSQL) to the internet
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
docker compose exec geopulse-postgres pg_isready -U ${GEOPULSE_POSTGRES_USERNAME} -d geopulse
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
docker compose exec geopulse-postgres pg_dump -U ${GEOPULSE_POSTGRES_USERNAME} geopulse > backup.sql

# Restore backup
docker compose exec -T geopulse-postgres psql -U ${GEOPULSE_POSTGRES_USERNAME} geopulse < backup.sql
```

### Configuration Backup

```bash
# Backup your configuration
tar -czf geopulse-backup.tar.gz .env docker-compose.yml keys/
```