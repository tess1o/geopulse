---
title: Manual Installation Guide
sidebar_label: Manual Installation Guide
description: Complete reference for deploying and configuring GeoPulse on bare metal servers or VMs without Docker/Kubernetes.
---

# Manual Installation Guide

This guide provides step-by-step instructions for manually deploying GeoPulse on bare metal servers or VMs without Docker/Kubernetes.

## Prerequisites

- Root access (`sudo`)
- Linux system (Ubuntu, Debian, RHEL, Rocky Linux, AlmaLinux, or Fedora)
- Internet connection
- Basic command-line knowledge

:::tip Running in Containers or as Root
If you're running in a minimal container (LXC, Docker, etc.) or already operating as root:

- **Skip `sudo`**: If you're root, run commands without `sudo` prefix
  ```bash
  # Instead of: sudo apt-get install ...
  # Use: apt-get install ...
  ```

- **PostgreSQL commands**: Replace `sudo -u postgres` with one of these:
  ```bash
  # Option 1: Switch user (then run psql commands)
  su - postgres

  # Option 2: Use runuser (from root, single command)
  runuser -u postgres -- psql -d geopulse -c "SELECT 1;"
  ```

- **Install sudo (optional)**: For consistency with these instructions:
  ```bash
  apt-get install -y sudo  # Debian/Ubuntu
  dnf install -y sudo      # RHEL/Rocky/Fedora
  ```
:::

---

## Table of Contents

**Installation Steps:**
1. [Choose Your Backend Type](#1-choose-your-backend-type)
2. [Determine Your CPU Architecture](#2-determine-your-cpu-architecture)
3. [Install System Dependencies](#3-install-system-dependencies)
4. [Create System User](#4-create-system-user)
5. [Create Directory Structure](#5-create-directory-structure)
6. [Configure PostgreSQL](#6-configure-postgresql)
7. [Generate Security Keys](#7-generate-security-keys)
8. [Download GeoPulse Artifacts](#8-download-geopulse-artifacts)
9. [Install Backend](#9-install-backend)
10. [Install Frontend](#10-install-frontend)
11. [Create Configuration File](#11-create-configuration-file)
12. [Create Systemd Service](#12-create-systemd-service)
13. [Configure Nginx Web Server](#13-configure-nginx-web-server)
14. [Start and Verify Services](#14-start-and-verify-services)

**Post-Installation:**
- [Create First Admin User](#create-first-admin-user)
- [Configuration Management](#configuration-management)
- [Monitoring and Logs](#monitoring-and-logs)
- [Upgrading GeoPulse](#upgrading-geopulse)
- [Troubleshooting](#troubleshooting)

---

## 1. Choose Your Backend Type

GeoPulse offers two backend options:

### Native Backend (Recommended)

- ✅ No Java required
- ✅ Lower memory usage (50-100 MB)
- ✅ Faster startup (milliseconds)
- ✅ Better performance
- ❌ Architecture-specific binary

:::info Best Use Cases
Production deployments, resource-constrained environments, Raspberry Pi
:::

### JVM Backend

- ✅ Platform-independent (works on any Java 25+ system)
- ✅ Easier debugging
- ❌ Requires Java 25+ runtime
- ❌ Higher memory usage (512-1024 MB)
- ❌ Slower startup

:::info Best Use Cases
Development, testing, cross-platform compatibility
:::

---

## 2. Determine Your CPU Architecture

Before downloading, identify your CPU architecture and capabilities to select the correct binary.

### Check Architecture

```bash
uname -m
```

**Output meanings:**

- `x86_64` → AMD64 (Intel/AMD processors)
- `aarch64` or `arm64` → ARM64 (ARM processors, Raspberry Pi, AWS Graviton)

### Check CPU Features (AMD64 only)

For AMD64 systems, check if your CPU supports modern instruction sets:

```bash
# Check for x86-64-v3 support (AVX2, BMI2, FMA)
grep -q avx2 /proc/cpuinfo && grep -q bmi2 /proc/cpuinfo && grep -q fma /proc/cpuinfo && echo "Modern CPU (x86-64-v3)" || echo "Older CPU (x86-64-v2)"
```

**Result interpretation:**

- **"Modern CPU (x86-64-v3)"**: Use `geopulse-backend-native-amd64-{version}`
  CPUs from ~2013+: Intel Haswell, AMD Zen or newer
- **"Older CPU (x86-64-v2)"**: Use `geopulse-backend-native-amd64-compat-{version}`
  Older Intel/AMD CPUs, VPS with older hypervisors

### Check CPU Model (ARM64 only)

For ARM64 systems, check if it's a Raspberry Pi:

```bash
cat /proc/cpuinfo | grep -i "raspberry\|bcm" && echo "Raspberry Pi - use compat build" || echo "Modern ARM64 - use optimized build"
```

**Result interpretation:**

- **"Raspberry Pi - use compat build"**: Use `geopulse-backend-native-arm64-compat-{version}`
  Raspberry Pi 3, 4, or similar
- **"Modern ARM64 - use optimized build"**: Use `geopulse-backend-native-arm64-{version}`
  AWS Graviton, Ampere Altra, Apple Silicon (if running Linux)

### Summary Table

| Architecture | CPU Type | Binary to Download |
|--------------|----------|-------------------|
| AMD64 | Modern (2013+) | `geopulse-backend-native-amd64-{version}` |
| AMD64 | Older (pre-2013) | `geopulse-backend-native-amd64-compat-{version}` |
| ARM64 | Modern | `geopulse-backend-native-arm64-{version}` |
| ARM64 | Raspberry Pi 3/4 | `geopulse-backend-native-arm64-compat-{version}` |
| Any | JVM Backend | `geopulse-backend-jvm-{version}.tar.gz` |

---

## 3. Install System Dependencies

### Ubuntu / Debian

```bash
# Update package list
sudo apt-get update

# Install core dependencies (including timezone data and lsb-release for repository setup)
sudo apt-get install -y curl wget tar openssl ca-certificates tzdata lsb-release

# Configure timezone (required for PostgreSQL)
sudo ln -sf /usr/share/zoneinfo/UTC /etc/localtime
sudo dpkg-reconfigure -f noninteractive tzdata

# Install PostgreSQL 17 + PostGIS
sudo apt-get install -y postgresql-17 postgresql-17-postgis-3

# Install Nginx
sudo apt-get install -y nginx

# If using JVM backend, install Java 25
# sudo apt-get install -y openjdk-25-jre-headless
```

:::note PostgreSQL 17 Repository
If PostgreSQL 17 is not available in your distribution's repositories, you can use PostgreSQL's official APT repository:
:::

```bash
# Ensure lsb-release is installed (needed for release detection)
sudo apt-get install -y lsb-release

# Add PostgreSQL APT repository key (modern method)
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | \
  sudo gpg --dearmor -o /usr/share/keyrings/postgresql-keyring.gpg

# Add PostgreSQL repository with signed-by keyring
sudo sh -c 'echo "deb [signed-by=/usr/share/keyrings/postgresql-keyring.gpg] https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'

# Update package lists
sudo apt-get update

# Install PostgreSQL 17
sudo apt-get install -y postgresql-17 postgresql-17-postgis-3
```

### RHEL / Rocky Linux / AlmaLinux

```bash
# Install core dependencies (including timezone data)
sudo dnf install -y curl wget tar openssl ca-certificates tzdata

# Configure timezone (required for PostgreSQL)
sudo ln -sf /usr/share/zoneinfo/UTC /etc/localtime

# Install PostgreSQL 17 + PostGIS
sudo dnf install -y postgresql17-server postgresql17-contrib postgis35_17

# Install Nginx (optional)
sudo dnf install -y nginx

# If using JVM backend, install Java 25
# sudo dnf install -y java-25-openjdk-headless
```

### Fedora

```bash
# Install core dependencies (including timezone data)
sudo dnf install -y curl wget tar openssl ca-certificates tzdata

# Configure timezone (required for PostgreSQL)
sudo ln -sf /usr/share/zoneinfo/UTC /etc/localtime

# Install PostgreSQL 17 + PostGIS
sudo dnf install -y postgresql-server postgresql-contrib postgis

# Install Nginx (optional)
sudo dnf install -y nginx

# If using JVM backend, install Java 25
# sudo dnf install -y java-25-openjdk-headless
```

---

## 4. Create System User

This user will own all GeoPulse files and run the backend service. It cannot log in interactively, making it secure.

```bash
sudo useradd -r -s /bin/false -d /opt/geopulse -c "GeoPulse Service" geopulse
```

---

## 5. Create Directory Structure

```bash
# Application directory (backend, keys)
sudo mkdir -p /opt/geopulse/backend
sudo mkdir -p /opt/geopulse/keys

# Configuration directory
sudo mkdir -p /etc/geopulse

# Frontend directory (for Nginx deployment)
sudo mkdir -p /var/www/geopulse

# Data directory (dumps, backups)
sudo mkdir -p /var/lib/geopulse/dumps

# Logs directory
sudo mkdir -p /var/log/geopulse/backend
sudo mkdir -p /var/log/geopulse/nginx

# Set ownership and permissions
sudo chown -R geopulse:geopulse /opt/geopulse
sudo chown -R geopulse:geopulse /var/lib/geopulse
sudo chown -R geopulse:geopulse /var/log/geopulse

# Secure the keys directory
sudo chmod 750 /opt/geopulse/keys
```

:::info Directory Structure

- `/opt/geopulse` - Application installation
- `/etc/geopulse` - Configuration files
- `/var/www/geopulse` - Frontend static files (Nginx)
- `/var/lib/geopulse` - Runtime data, backups
- `/var/log/geopulse` - Application logs
:::

---

## 6. Configure PostgreSQL

### Initialize and Start PostgreSQL

**Ubuntu/Debian:**

```bash
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**RHEL/Rocky/AlmaLinux:**

```bash
# Initialize database cluster (first time only)
sudo /usr/pgsql-17/bin/postgresql-17-setup initdb

# Start and enable
sudo systemctl start postgresql-17
sudo systemctl enable postgresql-17
```

### Create Database and User

```bash
sudo -u postgres psql << 'EOF'
-- Create user
CREATE USER geopulse WITH PASSWORD 'your_secure_password_here';

-- Create database
CREATE DATABASE geopulse OWNER geopulse;

-- Connect to geopulse database
\c geopulse

-- Enable PostGIS extensions (must be done as superuser)
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE geopulse TO geopulse;
GRANT ALL ON SCHEMA public TO geopulse;
GRANT ALL ON ALL TABLES IN SCHEMA public TO geopulse;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO geopulse;
EOF
```

:::warning Important
Replace `'your_secure_password_here'` with a strong, random password. Save this password - you'll need it in step 11.
:::

### Verify PostGIS Installation

```bash
sudo -u postgres psql -d geopulse -c "SELECT PostGIS_Version();"
```

You should see PostGIS version information.

---

## 7. Generate Security Keys

GeoPulse requires two types of cryptographic keys:
1. **JWT RSA Key Pair** - For user authentication tokens
2. **AI Encryption Key** - For encrypting AI provider API keys

```bash
# Generate JWT RSA key pair (2048-bit)
sudo openssl genpkey -algorithm RSA -out /opt/geopulse/keys/jwt-private-key.pem
sudo openssl rsa -pubout -in /opt/geopulse/keys/jwt-private-key.pem -out /opt/geopulse/keys/jwt-public-key.pem

# Generate AI encryption key (256-bit)
sudo bash -c 'openssl rand -base64 32 > /opt/geopulse/keys/ai-encryption-key.txt'

# Set secure permissions
sudo chmod 640 /opt/geopulse/keys/jwt-private-key.pem /opt/geopulse/keys/jwt-public-key.pem /opt/geopulse/keys/ai-encryption-key.txt
sudo chown geopulse:geopulse /opt/geopulse/keys/jwt-private-key.pem /opt/geopulse/keys/jwt-public-key.pem /opt/geopulse/keys/ai-encryption-key.txt

# Verify files were created
sudo ls -lh /opt/geopulse/keys/
```

:::danger Security Notice
These keys are critical. Never commit them to version control or share them. The private key should only be readable by the `geopulse` user.
:::

---

## 8. Download GeoPulse Artifacts

:::tip Version Selection
Set your desired version (check [GitHub Releases](https://github.com/tess1o/geopulse/releases) for the latest version):
:::

```bash
VERSION=1.16.2
```

Create a temporary download directory:

```bash
mkdir -p /tmp/geopulse-install
cd /tmp/geopulse-install
```

### Download Frontend (Required)

The frontend is architecture-independent:

```bash
wget https://github.com/tess1o/geopulse/releases/download/v${VERSION}/geopulse-frontend-${VERSION}.tar.gz
```

### Download Backend (Choose ONE Option)

Based on your CPU architecture determined in [Step 2](#2-determine-your-cpu-architecture):

**Option A: Native AMD64 (Modern CPUs - Intel Haswell 2013+, AMD Zen 2017+)**

```bash
wget https://github.com/tess1o/geopulse/releases/download/v${VERSION}/geopulse-backend-native-amd64-${VERSION}
```

**Option B: Native AMD64 Compatible (Older CPUs, x86-64-v2)**

```bash
wget https://github.com/tess1o/geopulse/releases/download/v${VERSION}/geopulse-backend-native-amd64-compat-${VERSION}
```

**Option C: Native ARM64 (Modern ARM64)**

```bash
wget https://github.com/tess1o/geopulse/releases/download/v${VERSION}/geopulse-backend-native-arm64-${VERSION}
```

**Option D: Native ARM64 Compatible (Raspberry Pi 3/4)**

```bash
wget https://github.com/tess1o/geopulse/releases/download/v${VERSION}/geopulse-backend-native-arm64-compat-${VERSION}
```

**Option E: JVM Backend (Any platform with Java 25+)**

```bash
wget https://github.com/tess1o/geopulse/releases/download/v${VERSION}/geopulse-backend-jvm-${VERSION}.tar.gz
```

### Download and Verify Checksums

:::tip Security Best Practice
Always verify checksums to ensure downloaded files haven't been tampered with.
:::

```bash
# Download checksums
wget https://github.com/tess1o/geopulse/releases/download/v${VERSION}/SHA256SUMS

# Verify checksums
sha256sum -c SHA256SUMS --ignore-missing
```

You should see:
```
geopulse-frontend-1.16.2.tar.gz: OK
geopulse-backend-native-amd64-1.16.2: OK
```

:::caution Checksum Verification
If verification fails, **do not proceed**. Re-download the files.
:::

---

## 9. Install Backend

### For Native Backend

Choose the command that matches your architecture from step 2:

**AMD64 Modern:**

```bash
# Copy binary and set permissions
sudo cp geopulse-backend-native-amd64-${VERSION} /opt/geopulse/backend/geopulse-backend
sudo chmod +x /opt/geopulse/backend/geopulse-backend
sudo chown geopulse:geopulse /opt/geopulse/backend/geopulse-backend
```

**AMD64 Compatible:**

```bash
# Copy binary and set permissions
sudo cp geopulse-backend-native-amd64-compat-${VERSION} /opt/geopulse/backend/geopulse-backend
sudo chmod +x /opt/geopulse/backend/geopulse-backend
sudo chown geopulse:geopulse /opt/geopulse/backend/geopulse-backend
```

**ARM64 Modern:**

```bash
# Copy binary and set permissions
sudo cp geopulse-backend-native-arm64-${VERSION} /opt/geopulse/backend/geopulse-backend
sudo chmod +x /opt/geopulse/backend/geopulse-backend
sudo chown geopulse:geopulse /opt/geopulse/backend/geopulse-backend
```

**ARM64 Compatible (Raspberry Pi):**

```bash
# Copy binary and set permissions
sudo cp geopulse-backend-native-arm64-compat-${VERSION} /opt/geopulse/backend/geopulse-backend
sudo chmod +x /opt/geopulse/backend/geopulse-backend
sudo chown geopulse:geopulse /opt/geopulse/backend/geopulse-backend
```

**Verify installation:**

```bash
ls -lh /opt/geopulse/backend/geopulse-backend
# Should show: -rwxr-xr-x 1 geopulse geopulse [size] [date] geopulse-backend
```

### For JVM Backend

```bash
# Extract quarkus-app folder
sudo tar -xzf geopulse-backend-jvm-${VERSION}.tar.gz -C /opt/geopulse/backend

# Set permissions
sudo chown -R geopulse:geopulse /opt/geopulse/backend

# Verify installation
ls -la /opt/geopulse/backend/quarkus-app/
```

You should see: `quarkus-run.jar`, `lib/`, `app/`, `quarkus/`

---

## 10. Install Frontend

Extract frontend to Nginx web directory and set permissions:

**Ubuntu / Debian:**

```bash
# Extract frontend files
sudo tar -xzf geopulse-frontend-${VERSION}.tar.gz -C /var/www/geopulse

# Set permissions for Nginx
sudo chown -R www-data:www-data /var/www/geopulse

# Verify extraction
ls -la /var/www/geopulse/
```

**RHEL / Rocky Linux / AlmaLinux / Fedora:**

```bash
# Extract frontend files
sudo tar -xzf geopulse-frontend-${VERSION}.tar.gz -C /var/www/geopulse

# Set permissions for Nginx
sudo chown -R nginx:nginx /var/www/geopulse

# Verify extraction
ls -la /var/www/geopulse/
```

You should see: `index.html`, `assets/`, `geopulse-logo.svg`, etc.

---

## 11. Create Configuration File

Create `/etc/geopulse/geopulse.env`:

```bash
sudo tee /etc/geopulse/geopulse.env > /dev/null << 'EOF'
# GeoPulse Configuration

# ⚠️ REQUIRED: Frontend URL - MUST be updated or the application will not work!
# Use your server's IP address or domain name. For multiple domains, separate with commas.
# Examples:
#   Single domain: GEOPULSE_UI_URL=http://192.168.1.100
#   Multiple:      GEOPULSE_UI_URL=http://192.168.1.100,http://example.com,https://example.com
GEOPULSE_UI_URL=http://your-server-ip

# Backend port
QUARKUS_HTTP_PORT=8080

# Database connection (update password from step 6)
GEOPULSE_POSTGRES_URL=jdbc:postgresql://localhost:5432/geopulse
GEOPULSE_POSTGRES_HOST=localhost
GEOPULSE_POSTGRES_PORT=5432
GEOPULSE_POSTGRES_DB=geopulse
GEOPULSE_POSTGRES_USERNAME=geopulse
GEOPULSE_POSTGRES_PASSWORD=your_secure_password_here

# JWT Keys (must use file: URI scheme)
GEOPULSE_JWT_PRIVATE_KEY_LOCATION=file:/opt/geopulse/keys/jwt-private-key.pem
GEOPULSE_JWT_PUBLIC_KEY_LOCATION=file:/opt/geopulse/keys/jwt-public-key.pem

# AI Encryption (must use file: URI scheme)
GEOPULSE_AI_ENCRYPTION_KEY_LOCATION=file:/opt/geopulse/keys/ai-encryption-key.txt

# Logging
QUARKUS_LOG_FILE_ENABLE=true
QUARKUS_LOG_FILE_PATH=/var/log/geopulse/backend/geopulse.log
QUARKUS_LOG_FILE_ROTATION_MAX_FILE_SIZE=10M
QUARKUS_LOG_FILE_ROTATION_MAX_BACKUP_INDEX=5

EOF
```

:::danger CRITICAL: Update Required Configuration Values

Before continuing, you **MUST** edit the configuration file and update these values:

```bash
sudo nano /etc/geopulse/geopulse.env
```

**Required changes:**

1. **GEOPULSE_UI_URL** (line 8) - Replace `http://your-server-ip` with your actual server IP or domain
   - Without this, you will get **"Request failed with status code 403"** CORS errors
   - Example: `GEOPULSE_UI_URL=http://192.168.1.100`

2. **GEOPULSE_POSTGRES_PASSWORD** (line 21) - Replace `your_secure_password_here` with the password you set in step 6
:::

**Set secure permissions:**

```bash
sudo chmod 640 /etc/geopulse/geopulse.env
sudo chown geopulse:geopulse /etc/geopulse/geopulse.env
```

---

## 12. Create Systemd Service

### For Native Backend

Create `/etc/systemd/system/geopulse-backend.service`:

```bash
sudo tee /etc/systemd/system/geopulse-backend.service > /dev/null << 'EOF'
[Unit]
Description=GeoPulse Backend Service (Native)
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=geopulse
Group=geopulse
WorkingDirectory=/opt/geopulse/backend
EnvironmentFile=/etc/geopulse/geopulse.env

# Start the native binary with GC options
ExecStart=/opt/geopulse/backend/geopulse-backend -Dquarkus.http.host=0.0.0.0 -XX:MaximumHeapSizePercent=70 -XX:MaximumYoungGenerationSizePercent=15

# Restart policy
Restart=on-failure
RestartSec=10

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/geopulse /var/log/geopulse /opt/geopulse/keys

# Logging
StandardOutput=append:/var/log/geopulse/backend/geopulse-stdout.log
StandardError=append:/var/log/geopulse/backend/geopulse-stderr.log

[Install]
WantedBy=multi-user.target
EOF
```

### For JVM Backend

Create `/etc/systemd/system/geopulse-backend.service`:

```bash
sudo tee /etc/systemd/system/geopulse-backend.service > /dev/null << 'EOF'
[Unit]
Description=GeoPulse Backend Service (JVM)
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=geopulse
Group=geopulse
WorkingDirectory=/opt/geopulse/backend/quarkus-app
EnvironmentFile=/etc/geopulse/geopulse.env

# JVM options
Environment="JAVA_OPTS=-Xmx512m -Xms128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/lib/geopulse/dumps/"

# Start the JVM
ExecStart=/usr/bin/java ${JAVA_OPTS} -jar /opt/geopulse/backend/quarkus-app/quarkus-run.jar

# Restart policy
Restart=on-failure
RestartSec=10

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/geopulse /var/log/geopulse /opt/geopulse/keys

# Logging
StandardOutput=append:/var/log/geopulse/backend/geopulse-stdout.log
StandardError=append:/var/log/geopulse/backend/geopulse-stderr.log

[Install]
WantedBy=multi-user.target
EOF
```

### Reload Systemd and Enable Service

```bash
sudo systemctl daemon-reload
sudo systemctl enable geopulse-backend
```

:::note Service Startup
Don't start the service yet - we'll do that in step 14 after configuring the web server.
:::

---

## 13. Configure Nginx Web Server

### Create OSM Tile Cache Directory

GeoPulse can cache OpenStreetMap tiles for better performance. Create the cache directory before configuring Nginx:

**Ubuntu/Debian:**

```bash
sudo mkdir -p /var/cache/nginx/osm_tiles
sudo chown -R www-data:www-data /var/cache/nginx
```

**RHEL/Rocky/Fedora:**

```bash
sudo mkdir -p /var/cache/nginx/osm_tiles
sudo chown -R nginx:nginx /var/cache/nginx
```

### Create Nginx Configuration

**Ubuntu/Debian:**

```bash
sudo tee /etc/nginx/sites-available/geopulse > /dev/null << 'EOF'
# OSM tile cache configuration
proxy_cache_path /var/cache/nginx/osm_tiles levels=1:2 keys_zone=osm_cache:100m max_size=10g inactive=30d use_temp_path=off;

# Map for OSM tile subdomain selection
map $uri $osm_subdomain {
    ~^/osm/tiles/a/ "a";
    ~^/osm/tiles/b/ "b";
    ~^/osm/tiles/c/ "c";
    default "a";
}

server {
    listen 80;
    server_name _;

    root /var/www/geopulse;
    index index.html;

    # Maximum upload size (for GPX files, location imports, etc.)
    client_max_body_size 100M;

    # Gzip compression for better performance
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    gzip_comp_level 6;
    gzip_min_length 1000;

    # Cache static assets (but exclude OSM tiles path)
    location ~* ^/(?!osm/).*\.(jpg|jpeg|png|gif|ico|css|js)$ {
        expires 1y;
        add_header Cache-Control "public, max-age=31536000";
    }

    # OSM tiles proxy with caching
    location ^~ /osm/tiles/ {
        resolver 8.8.8.8 valid=300s;
        resolver_timeout 10s;

        # Rewrite: /osm/tiles/a/19/299420/178830.png -> /19/299420/178830.png
        rewrite ^/osm/tiles/[abc]/(.*)$ /$1 break;

        proxy_pass https://$osm_subdomain.tile.openstreetmap.org;
        proxy_cache osm_cache;
        proxy_cache_key "$scheme$proxy_host$uri";
        proxy_cache_valid 200 30d;
        proxy_cache_valid 404 1m;
        proxy_cache_valid 502 503 504 1m;
        proxy_ignore_headers Cache-Control Expires Set-Cookie;
        proxy_cache_use_stale error timeout updating http_500 http_502 http_503 http_504;
        proxy_cache_background_update on;
        proxy_cache_lock on;

        proxy_set_header Cookie "";
        proxy_set_header Authorization "";
        proxy_set_header User-Agent "GeoPulse/1.16";
        proxy_set_header Host $osm_subdomain.tile.openstreetmap.org;
        proxy_http_version 1.1;
        proxy_set_header Connection "";

        proxy_connect_timeout 10s;
        proxy_read_timeout 10s;

        expires 30d;
        add_header Cache-Control "public, immutable";
        add_header X-Cache-Status $upstream_cache_status always;
    }

    # Backend API - reverse proxy with long timeouts for bulk operations
    location /api/ {
        proxy_pass http://localhost:8080/api/;

        # Long timeouts for GPX imports and bulk operations (60 minutes)
        proxy_connect_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_read_timeout 3600s;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Handle all routes for SPA (Single Page Application)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Logging
    access_log /var/log/geopulse/nginx/access.log;
    error_log /var/log/geopulse/nginx/error.log;
}
EOF

# Enable the site
sudo ln -s /etc/nginx/sites-available/geopulse /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# Test configuration
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
sudo systemctl enable nginx
```

**RHEL/Rocky/Fedora:**

```bash
sudo tee /etc/nginx/conf.d/geopulse.conf > /dev/null << 'EOF'
# OSM tile cache configuration
proxy_cache_path /var/cache/nginx/osm_tiles levels=1:2 keys_zone=osm_cache:100m max_size=10g inactive=30d use_temp_path=off;

# Map for OSM tile subdomain selection
map $uri $osm_subdomain {
    ~^/osm/tiles/a/ "a";
    ~^/osm/tiles/b/ "b";
    ~^/osm/tiles/c/ "c";
    default "a";
}

server {
    listen 80;
    server_name _;

    root /var/www/geopulse;
    index index.html;

    # Maximum upload size (for GPX files, location imports, etc.)
    client_max_body_size 100M;

    # Gzip compression for better performance
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    gzip_comp_level 6;
    gzip_min_length 1000;

    # Cache static assets (but exclude OSM tiles path)
    location ~* ^/(?!osm/).*\.(jpg|jpeg|png|gif|ico|css|js)$ {
        expires 1y;
        add_header Cache-Control "public, max-age=31536000";
    }

    # OSM tiles proxy with caching
    location ^~ /osm/tiles/ {
        resolver 8.8.8.8 valid=300s;
        resolver_timeout 10s;

        # Rewrite: /osm/tiles/a/19/299420/178830.png -> /19/299420/178830.png
        rewrite ^/osm/tiles/[abc]/(.*)$ /$1 break;

        proxy_pass https://$osm_subdomain.tile.openstreetmap.org;
        proxy_cache osm_cache;
        proxy_cache_key "$scheme$proxy_host$uri";
        proxy_cache_valid 200 30d;
        proxy_cache_valid 404 1m;
        proxy_cache_valid 502 503 504 1m;
        proxy_ignore_headers Cache-Control Expires Set-Cookie;
        proxy_cache_use_stale error timeout updating http_500 http_502 http_503 http_504;
        proxy_cache_background_update on;
        proxy_cache_lock on;

        proxy_set_header Cookie "";
        proxy_set_header Authorization "";
        proxy_set_header User-Agent "GeoPulse/1.16";
        proxy_set_header Host $osm_subdomain.tile.openstreetmap.org;
        proxy_http_version 1.1;
        proxy_set_header Connection "";

        proxy_connect_timeout 10s;
        proxy_read_timeout 10s;

        expires 30d;
        add_header Cache-Control "public, immutable";
        add_header X-Cache-Status $upstream_cache_status always;
    }

    # Backend API - reverse proxy with long timeouts for bulk operations
    location /api/ {
        proxy_pass http://localhost:8080/api/;

        # Long timeouts for GPX imports and bulk operations (60 minutes)
        proxy_connect_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_read_timeout 3600s;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Handle all routes for SPA (Single Page Application)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Logging
    access_log /var/log/geopulse/nginx/access.log;
    error_log /var/log/geopulse/nginx/error.log;
}
EOF

# Test configuration
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
sudo systemctl enable nginx
```

:::info Configuration Details

**Port changes**: If you change the backend port from 8080, update `proxy_pass http://localhost:8080/api/` in the Nginx configuration above. The backend port is set via `QUARKUS_HTTP_PORT` in `/etc/geopulse/geopulse.env`

**OSM tile caching**: The configuration includes OpenStreetMap tile caching (up to 10GB) for better performance. Tiles are cached for 30 days in `/var/cache/nginx/osm_tiles`

**Upload size**: `client_max_body_size` is set to 100M to support GPX file uploads and bulk location imports

**Timeouts**: API timeouts are set to 60 minutes (3600s) to support long-running operations like bulk imports
:::

---

## 14. Start and Verify Services

### Start the Backend

```bash
sudo systemctl start geopulse-backend
```

### Check Backend Status

```bash
sudo systemctl status geopulse-backend
```

You should see: `Active: active (running)`

### Monitor Backend Logs

```bash
# Watch logs in real-time
sudo journalctl -u geopulse-backend -f

# Or check log file
sudo tail -f /var/log/geopulse/backend/geopulse.log
```

### Test Backend Health Endpoint

```bash
curl http://localhost:8080/api/health
```

**Expected response:**

```json
{"status":"success","message":null,"data":{"database":"UP","status":"UP"}}
```

### Verify Database Connection

```bash
# Check if Flyway migrations ran successfully
sudo journalctl -u geopulse-backend | grep -i flyway
```

You should see successful migration messages.

### Access the Frontend

Open your browser and navigate to: **http://your-server-ip**

You should see the GeoPulse login page.

---

## Post-Installation

### Create First Admin User

:::tip Admin User Setup
Follow these steps to create your first administrator account:
:::

1. Set admin email environment variable:
   ```bash
   sudo bash -c 'echo "GEOPULSE_ADMIN_EMAIL=admin@example.com" >> /etc/geopulse/geopulse.env'
   sudo systemctl restart geopulse-backend
   ```

2. Open GeoPulse in browser
3. Register with the admin email
4. Your account will have admin privileges

---

## Configuration Management

### Applying Configuration Changes

After modifying `/etc/geopulse/geopulse.env`:

```bash
# 1. Restart the backend service to apply changes
sudo systemctl restart geopulse-backend

# 2. Verify the service started successfully
sudo systemctl status geopulse-backend

# 3. Check logs if there are issues
sudo journalctl -u geopulse-backend -n 50
```

:::warning Configuration Changes
Changes to environment variables require a service restart. Frontend changes (Nginx config) require `sudo systemctl restart nginx`.
:::

### Common Configuration Changes

#### Change Backend Port

1. Update `QUARKUS_HTTP_PORT` in `/etc/geopulse/geopulse.env`
2. Update `proxy_pass http://localhost:8080` in Nginx config to match new port
3. Restart both services:
   ```bash
   sudo systemctl restart geopulse-backend
   sudo systemctl restart nginx
   ```

#### Add Allowed Origins (CORS)

1. Update `GEOPULSE_UI_URL` in `/etc/geopulse/geopulse.env` (separate multiple domains with commas)
2. Restart backend: `sudo systemctl restart geopulse-backend`

---

## Monitoring and Logs

### Backend Logs

**Systemd journal (recommended for troubleshooting):**

```bash
# View recent logs
sudo journalctl -u geopulse-backend -n 100

# Follow logs in real-time
sudo journalctl -u geopulse-backend -f

# View logs since last boot
sudo journalctl -u geopulse-backend -b

# View logs for specific time period
sudo journalctl -u geopulse-backend --since "1 hour ago"
```

**Application log files:**

```bash
# Main application log
sudo tail -f /var/log/geopulse/backend/geopulse.log

# Standard output
sudo tail -f /var/log/geopulse/backend/geopulse-stdout.log

# Standard error (startup errors)
sudo tail -f /var/log/geopulse/backend/geopulse-stderr.log
```

### Frontend/Nginx Logs

```bash
# Nginx access log (requests)
sudo tail -f /var/log/geopulse/nginx/access.log

# Nginx error log (errors, proxy issues)
sudo tail -f /var/log/geopulse/nginx/error.log

# Nginx status
sudo systemctl status nginx

# Test Nginx configuration
sudo nginx -t
```

---

## Upgrading GeoPulse

### Manual Upgrade Process

#### 1. Download New Version

```bash
# Set new version
NEW_VERSION=1.17.0

# Create temporary directory
mkdir -p /tmp/geopulse-upgrade
cd /tmp/geopulse-upgrade

# Download new artifacts
wget https://github.com/tess1o/geopulse/releases/download/v${NEW_VERSION}/geopulse-frontend-${NEW_VERSION}.tar.gz
wget https://github.com/tess1o/geopulse/releases/download/v${NEW_VERSION}/geopulse-backend-native-arm64-${NEW_VERSION}  # or your architecture
wget https://github.com/tess1o/geopulse/releases/download/v${NEW_VERSION}/SHA256SUMS

# Verify checksums
sha256sum -c SHA256SUMS --ignore-missing
```

:::note Architecture Selection
Adjust download links to match your architecture (arm64, amd64, compatible images, jvm, etc).
:::

#### 2. Stop Services

```bash
sudo systemctl stop geopulse-backend
```

#### 3. Upgrade Backend

```bash
# For native backend
sudo cp geopulse-backend-native-arm64-${NEW_VERSION} /opt/geopulse/backend/geopulse-backend
sudo chmod +x /opt/geopulse/backend/geopulse-backend
sudo chown geopulse:geopulse /opt/geopulse/backend/geopulse-backend

# For JVM backend
sudo rm -rf /opt/geopulse/backend/quarkus-app
sudo tar -xzf geopulse-backend-jvm-${NEW_VERSION}.tar.gz -C /opt/geopulse/backend
sudo chown -R geopulse:geopulse /opt/geopulse/backend
```

:::note Architecture Selection
Adjust filename (`geopulse-backend-native-arm64-${NEW_VERSION}`) to match your architecture (arm64, amd64, compatible images, jvm, etc).
:::

#### 4. Upgrade Frontend

```bash
# Remove old frontend files
sudo rm -rf /var/www/geopulse/*

# Extract new frontend
sudo tar -xzf geopulse-frontend-${NEW_VERSION}.tar.gz -C /var/www/geopulse

# Set permissions
sudo chown -R www-data:www-data /var/www/geopulse  # Ubuntu/Debian
# OR
sudo chown -R nginx:nginx /var/www/geopulse  # RHEL/Rocky/Fedora
```

#### 5. Start Services

```bash
# Start backend
sudo systemctl start geopulse-backend

# Check status
sudo systemctl status geopulse-backend

# Verify health
curl http://localhost:8080/api/health
```

#### 6. Verify Upgrade

```bash
# Check logs for successful startup
sudo journalctl -u geopulse-backend -n 50

# Access frontend in browser
# Version should be visible in UI (bottom right corner or About page)
```

#### 7. Cleanup

```bash
rm -rf /tmp/geopulse-upgrade
```

---

## Troubleshooting

### Backend Won't Start

**Check service status:**

```bash
sudo systemctl status geopulse-backend
```

**Check logs for errors:**

```bash
# Recent logs
sudo journalctl -u geopulse-backend -n 100

# Startup errors
sudo cat /var/log/geopulse/backend/geopulse-stderr.log
```

### Common Issues

#### 1. Database Connection Failed

- Verify PostgreSQL is running: `sudo systemctl status postgresql`
- Check credentials in `/etc/geopulse/geopulse.env`
- Test connection: `sudo -u postgres psql -d geopulse -c "SELECT 1;"`

#### 2. Port Already in Use

- Change `QUARKUS_HTTP_PORT` in `/etc/geopulse/geopulse.env`
- Update Nginx `proxy_pass` port to match
- Restart services

#### 3. Permission Denied (Binary Not Executable)

```bash
sudo chmod +x /opt/geopulse/backend/geopulse-backend
sudo chown geopulse:geopulse /opt/geopulse/backend/geopulse-backend
```

#### 4. Keys Not Found

- Verify files exist: `sudo ls -la /opt/geopulse/keys/`
- Check paths in `/etc/geopulse/geopulse.env` use `file:` prefix
- Regenerate if needed (see Step 7)

#### 5. Timezone Error

Error: `could not stat file "/usr/share/zoneinfo/localtime"`

```bash
sudo apt-get install -y tzdata  # Ubuntu/Debian
# or: sudo dnf install -y tzdata  # RHEL/Rocky/Fedora
sudo ln -sf /usr/share/zoneinfo/UTC /etc/localtime
sudo systemctl restart postgresql geopulse-backend
```

#### 6. Extension Permission Denied

Error: `permission denied to create extension "postgis_topology"`

```bash
sudo -u postgres psql -d geopulse -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"
sudo systemctl restart geopulse-backend
```

### Frontend Not Loading

**Check Nginx:**

```bash
# Nginx status
sudo systemctl status nginx

# Test configuration
sudo nginx -t

# Restart if needed
sudo systemctl restart nginx
```

**Verify files:**

```bash
# Check frontend files exist
ls -la /var/www/geopulse/
# Should show: index.html, assets/, etc.

# Check permissions
sudo chown -R www-data:www-data /var/www/geopulse  # Ubuntu/Debian
# OR
sudo chown -R nginx:nginx /var/www/geopulse  # RHEL/Rocky/Fedora
```

**Check Nginx logs:**

```bash
sudo tail -50 /var/log/geopulse/nginx/error.log
```

### Request Failed with Status Code 403 (CORS Error)

This error means the backend is rejecting requests from your frontend URL due to CORS policy.

:::tip How to Fix

1. Add your domain/IP to `GEOPULSE_UI_URL` in `/etc/geopulse/geopulse.env`:
   ```bash
   # Single domain
   GEOPULSE_UI_URL=http://your-server-ip

   # Multiple domains (separate with commas)
   GEOPULSE_UI_URL=http://192.168.1.100,http://example.com,https://example.com
   ```

2. Restart backend:
   ```bash
   sudo systemctl restart geopulse-backend
   ```

3. Clear browser cache and reload
:::

### API Calls Failing / Cannot Connect to Backend

**Test backend directly:**

```bash
curl http://localhost:8080/api/health
```

**If backend responds but frontend can't connect:**

1. Check Nginx proxy configuration has correct backend port
2. Check Nginx logs: `sudo tail -f /var/log/geopulse/nginx/error.log`
3. Verify Nginx is running: `sudo systemctl status nginx`

**If backend doesn't respond:**

1. Check backend is running: `sudo systemctl status geopulse-backend`
2. Check backend logs: `sudo journalctl -u geopulse-backend -n 50`
3. Verify backend port matches Nginx proxy_pass

---
