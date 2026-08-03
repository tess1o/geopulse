---
title: Unraid Installation
sidebar_label: Unraid
description: Install GeoPulse on Unraid with Compose Manager Plus and Unraid-specific Docker Compose files.
---

# Unraid Installation

Unraid is a NAS and homelab operating system with a web interface for storage, VMs, and Docker containers. GeoPulse uses several containers, so the recommended Unraid setup is Docker Compose through a plugin such as [Compose Manager Plus](https://github.com/mstrhakr/compose_plugin).

GeoPulse provides separate Unraid compose files so existing Docker Compose, Kubernetes, Proxmox, and manual installations are not affected.

## Prerequisites

- An Unraid server with Docker enabled.
- The [Community Applications](https://docs.unraid.net/community-applications/) plugin.
- Compose Manager Plus installed from Community Applications.
- Enough appdata storage for GeoPulse data, database files, keys, and imports.

## Image Defaults

The Unraid compose files use conservative defaults for homelab hardware:

| Component | Default image | Reason |
|-----------|---------------|--------|
| Backend | `tess1o/geopulse-backend:${GEOPULSE_VERSION}-native-compat` | Safest default for older Intel/AMD Unraid systems. |
| Frontend | `tess1o/geopulse-ui:${GEOPULSE_VERSION}` | Same UI image used by Docker Compose installs. |
| Postgres/PostGIS | `postgis/postgis:17-3.5` | Unraid runs on x86_64, so the standard PostGIS image is the right default. |

Use `native-compat` unless you know your CPU supports the optimized native image. For modern CPUs, edit the backend image in the Unraid compose file:

```yaml
image: tess1o/geopulse-backend:${GEOPULSE_VERSION}-native
```

Do not use the ARM64 Postgres image shown in the regular Docker guide for a normal Unraid installation.

## Install Without MQTT

Use this option if you do not need OwnTracks over MQTT.

1. Open the Unraid terminal or use a file manager on the appdata share.

2. Create the GeoPulse appdata directory:

   ```bash
   mkdir -p /mnt/user/appdata/geopulse
   cd /mnt/user/appdata/geopulse
   ```

3. Download the environment file and Unraid compose file:

   ```bash
   curl -L -o .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
   curl -L -o docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.unraid.yml
   ```

4. Edit `.env` before first start:

   ```env
   GEOPULSE_POSTGRES_PASSWORD=replace-with-a-secure-password
   GEOPULSE_PUBLIC_BASE_URL=http://<unraid-ip>:5555
   GEOPULSE_AUTH_SECURE_COOKIES=false
   ```

   If GeoPulse will be served through HTTPS by a reverse proxy, set `GEOPULSE_PUBLIC_BASE_URL` to the public HTTPS URL and set `GEOPULSE_AUTH_SECURE_COOKIES=true`.

5. In Compose Manager Plus, create a new stack named `geopulse` using `/mnt/user/appdata/geopulse/docker-compose.yml`.

6. Start the stack.

7. Open GeoPulse:

   ```text
   http://<unraid-ip>:5555
   ```

The first registered user becomes admin unless `GEOPULSE_ADMIN_EMAIL` is set before registration. Continue with the [Initial Setup Guide](../../system-administration/initial-setup).

## Install With MQTT

Use this option for OwnTracks MQTT support.

1. Follow the same steps as the non-MQTT install, but download the MQTT-enabled Unraid compose file:

   ```bash
   curl -L -o docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.unraid-complete.yml
   ```

2. Edit `.env` and enable MQTT:

   ```env
   GEOPULSE_MQTT_ENABLED=true
   GEOPULSE_MQTT_PASSWORD=replace-with-a-secure-mqtt-password
   ```

3. Start the stack in Compose Manager Plus.

The MQTT broker listens on port `1883`. Configure OwnTracks with the MQTT credentials created in GeoPulse.

## Storage Layout

By default, GeoPulse stores persistent files under:

```text
/mnt/user/appdata/geopulse
```

The compose files create these paths:

| Path | Purpose |
|------|---------|
| `postgres/` | PostgreSQL/PostGIS database files. |
| `keys/` | JWT keys and AI encryption key generated on first start. |
| `import-drop/` | Optional server-side import drop folder. |
| `mosquitto/` | MQTT config, data, and logs when using the complete compose file. |

To use another appdata path, set `GEOPULSE_APPDATA` before starting the stack:

```env
GEOPULSE_APPDATA=/mnt/user/appdata/geopulse
```

## Reverse Proxy

Expose your reverse proxy to the GeoPulse UI port only:

```text
http://<unraid-ip>:5555
```

The backend port `8080` is intentionally private. The frontend container proxies `/api` requests to the backend inside the Docker network.

Recommended HTTPS settings:

```env
GEOPULSE_PUBLIC_BASE_URL=https://geopulse.example.com
GEOPULSE_AUTH_SECURE_COOKIES=true
GEOPULSE_CORS_ENABLED=false
GEOPULSE_COOKIE_DOMAIN=""
```

For advanced authentication and cookie behavior, see [Authentication Configuration](../../system-administration/configuration/authentication.md).

## Updating

1. Back up `/mnt/user/appdata/geopulse`.
2. Edit `.env` and update `GEOPULSE_VERSION`.
3. In Compose Manager Plus, pull the latest images for the stack.
4. Recreate or restart the stack.

For general upgrade guidance, see [Upgrading GeoPulse](../../system-administration/maintenance/updating).

## Backup and Restore

Back up the full appdata directory:

```text
/mnt/user/appdata/geopulse
```

At minimum, keep `postgres/` and `keys/` together. The database and JWT keys must match for existing user sessions and encrypted settings to continue working.

For database-aware backup and restore procedures, see [Backup & Restore](../../system-administration/maintenance/backup-restore).

## Troubleshooting

**Cannot open the web UI**

- Confirm the stack is running in Compose Manager Plus.
- Check that no other Unraid app uses port `5555`.
- If you changed `GEOPULSE_UI_PORT`, open `http://<unraid-ip>:<port>`.

**Backend crashes immediately**

The Unraid files already use `native-compat`. If you changed to `native`, switch back to:

```yaml
image: tess1o/geopulse-backend:${GEOPULSE_VERSION}-native-compat
```

**Database does not start**

- Confirm `/mnt/user/appdata/geopulse/postgres` is writable.
- Keep the standard `postgis/postgis:17-3.5` image on Unraid.
- Do not switch to the ARM64 PostGIS image unless you are running outside normal Unraid hardware.

**Keys are missing**

Check the `geopulse-keygen` logs in Compose Manager Plus. Keys are generated once into:

```text
/mnt/user/appdata/geopulse/keys
```

To regenerate keys, stop the stack, remove the `keys/` directory, and start the stack again. Existing sessions and encrypted AI settings may need to be recreated.
