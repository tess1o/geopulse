# GeoPulse Deployment Guide

## Quick Start

1. **Generate JWT Keys**
2. **Customize Environment Variables** 
3. **Deploy with Docker Compose**

---

## 1. Generate JWT Keys

Create JWT signing keys (required for both development and production):

```bash
# Create keys directory
mkdir -p keys

# Generate JWT keys using Docker
docker run --rm -v "$(pwd)/keys:/keys" alpine:latest sh -c "
  apk add --no-cache openssl && 
  openssl genpkey -algorithm RSA -out /keys/jwt-private-key.pem && 
  openssl rsa -pubout -in /keys/jwt-private-key.pem -out /keys/jwt-public-key.pem && 
  chmod 644 /keys/jwt-*.pem
"
```

This creates:
- `keys/jwt-private-key.pem` - Private key for signing tokens
- `keys/jwt-public-key.pem` - Public key for verifying tokens

---

## 2. Production Deployment

### Create Environment File

Create `.env` file with your configuration:

```bash
# Domain Configuration (CHANGE THESE)
GEOPULSE_UI_URL=https://geopulse.yourdomain.com
GEOPULSE_BACKEND_URL=https://geopulse-api.yourdomain.com
GEOPULSE_AUTH_COOKIE_DOMAIN=.yourdomain.com

# Database Configuration  
GEOPULSE_POSTGRES_USERNAME=geopulse-user
GEOPULSE_POSTGRES_PASSWORD=my-good-postgres-password

# Security, change to 32+ symbols random string
GEOPULSE_CSRF_SECRET=uxU1Qq6HJblt4OquzsICDZWViPQDnKwZUbhiAVAI/XE=
```

### Create Docker Compose File

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  geopulse-backend:
    image: tess1o/geopulse-backend:0.0.28
    container_name: geopulse-backend
    volumes:
      - ./keys:/app/keys
    depends_on:
      geopulse-postgres:
        condition: service_healthy
    environment:
      # Database Configuration
      - GEOPULSE_POSTGRES_URL=jdbc:postgresql://geopulse-postgres:5432/geopulse
      - GEOPULSE_POSTGRES_USERNAME=${GEOPULSE_POSTGRES_USERNAME}
      - GEOPULSE_POSTGRES_PASSWORD=${GEOPULSE_POSTGRES_PASSWORD}
      
      # Domain Configuration  
      - GEOPULSE_UI_URL=${GEOPULSE_UI_URL}
      - GEOPULSE_BACKEND_URL=${GEOPULSE_BACKEND_URL}
      
      # Authentication (Secure Cookie Mode. For localstorage mode see details below)
      - GEOPULSE_AUTH_MODE=cookies
      - GEOPULSE_AUTH_SECURE_COOKIES=true
      - GEOPULSE_AUTH_COOKIE_DOMAIN=${GEOPULSE_AUTH_COOKIE_DOMAIN}
      
      # Security
      - GEOPULSE_JWT_COOKIE=access_token
      - GEOPULSE_CSRF_SECRET=${GEOPULSE_CSRF_SECRET}

  geopulse-ui:
    image: tess1o/geopulse-ui:0.0.28
    container_name: geopulse-ui
    depends_on:
      - geopulse-backend
    environment:
      - API_BASE_URL=${GEOPULSE_BACKEND_URL}/api

  geopulse-postgres:
    image: postgis/postgis:17-3.5
    container_name: geopulse-postgres
    environment:
      POSTGRES_USER: ${GEOPULSE_POSTGRES_USERNAME}
      POSTGRES_PASSWORD: ${GEOPULSE_POSTGRES_PASSWORD}
      POSTGRES_DB: geopulse
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${GEOPULSE_POSTGRES_USERNAME} -d geopulse"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
```

### Required Customizations

**🌐 Edit your `.env` file and update these values:**
```bash
# Your actual domains
GEOPULSE_UI_URL=https://geopulse.yourdomain.com
GEOPULSE_BACKEND_URL=https://geopulse-api.yourdomain.com  
GEOPULSE_AUTH_COOKIE_DOMAIN=.yourdomain.com              # Note the dot prefix

# Optionally change database username
GEOPULSE_POSTGRES_USERNAME=geopulse-user
```
---

## 3. Start the Application

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Check service status
docker-compose ps
```

**Access your application:**
- Frontend: `https://your-frontend-domain.com`
- API: `https://your-api-domain.com/api`

---

## 4. Domain & Cookie Configuration

### ✅ Cookie Mode (Recommended for Production)

Works when frontend and API share the same root domain:

| Frontend | API | Cookie Domain | Status |
|----------|-----|---------------|--------|
| `https://app.mydomain.com` | `https://api.mydomain.com` | `.mydomain.com` | ✅ **Works** |
| `https://mydomain.com` | `https://mydomain.com/api` | `.mydomain.com` | ✅ **Works** |
| `https://mydomain.com` | `https://mydomain.com:8080` | `mydomain.com` | ✅ **Works** |

### ❌ Cross-Domain Setup (localStorage mode required)

When domains are completely different:

| Frontend | API | Cookie Domain | Auth Mode |
|----------|-----|---------------|-----------|
| `https://myapp.com` | `https://api.different.net` | N/A | `localStorage` |
| `https://mydomain.com` | `https://mydomain.org` | N/A | `localStorage` |

**For cross-domain setups, update these environment variables:**
```yaml
environment:
  - GEOPULSE_AUTH_MODE=localStorage                    # ← Use localStorage instead
  - GEOPULSE_AUTH_SECURE_COOKIES=false                # ← Not applicable
  - GEOPULSE_AUTH_COOKIE_DOMAIN=""                    # ← Empty string
```

---

## 5. Development Mode

For local development with less security restrictions:

```yaml
# Add to geopulse-backend environment:
environment:
  - GEOPULSE_AUTH_MODE=localStorage                    # ← localStorage for development
  - GEOPULSE_AUTH_SECURE_COOKIES=false                # ← Allow HTTP cookies  
  - GEOPULSE_AUTH_COOKIE_DOMAIN=.localhost            # ← Local domain
```

**Development vs Production:**

| Feature | Development | Production |
|---------|-------------|------------|
| **Auth Mode** | `localStorage` | `cookies` |
| **Security** | Relaxed | Strict |
| **Database** | Exposed port | Internal only |
| **Logging** | Verbose | Essential only |
| **HTTPS** | Optional | Required |

---

## 6. Authentication Modes Explained

### Cookie Mode (Production Recommended)
- **Security**: HttpOnly cookies (XSS protection)
- **CSRF Protection**: Required for state-changing operations  
- **Domain Requirement**: Same root domain only
- **Setup**: Secure, automatic token management

### localStorage Mode (Development/Cross-Domain)
- **Security**: Tokens in browser storage (XSS vulnerable)
- **CSRF Protection**: Optional
- **Domain Requirement**: Works across any domains
- **Setup**: Simple, manual token management

GeoPulse **automatically detects** which mode to use based on server configuration.


---

## 7. Updating GeoPulse

```bash
# Pull latest images
docker-compose pull

# Restart with new images  
docker-compose up -d

# View startup logs
docker-compose logs -f geopulse-backend
```

**Database migrations are handled automatically** on startup.

---