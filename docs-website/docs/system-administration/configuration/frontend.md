---
title: Frontend Nginx Configuration
description: Configure upload limits and DNS resolvers for the GeoPulse frontend Nginx service.
---

# Frontend Nginx Configuration

This page describes the available environment variables for customizing the Nginx frontend used by GeoPulse.

---

## 🧩 Overview

The GeoPulse frontend uses **Nginx** as a reverse proxy and static file server.  
You can modify its behavior through environment variables, especially for upload size limits and DNS resolution for
OpenStreetMap (OSM) tiles.

---

## 📦 Max Upload File Size

By default, Nginx limits the maximum upload size to **200 MB**.  
To increase this limit (for example, when importing large GPX or JSON files), set the following environment variable in
your container configuration:

```bash
CLIENT_MAX_BODY_SIZE=500M
```

You can specify size units such as:

- M → megabytes
- G → gigabytes

After updating the variable, restart the Nginx container to apply the change.

💡 Tip: Increasing this limit may be necessary when handling large GPS data imports or timeline backups.

## 🌐 OSM Resolver DNS Servers

GeoPulse uses OpenStreetMap (OSM) tiles for map rendering.
The `OSM_RESOLVER` environment variable defines which DNS servers Nginx should use to resolve OSM tile subdomains:

```shell
a.tile.openstreetmap.org  
b.tile.openstreetmap.org  
c.tile.openstreetmap.org
```

Default value: `OSM_RESOLVER="127.0.0.11 8.8.8.8"`
It will try to resolve correct DNS names from `/etc/resolv.conf` file for both Docker and Kubernetes deployments and
both IpV4 and Ipv6.

## 📝 Notes

- `CLIENT_MAX_BODY_SIZE` affects all upload endpoints served through Nginx.
- Ensure that your specified DNS servers are reachable from within the container environment.
- After modifying environment variables, restart or redeploy the Nginx service for changes to take effect.

## Kubernetes / Helm Configuration

Configure frontend nginx settings in `values.yaml`:

```yaml
frontend:
  nginx:
    clientMaxBodySize: "500M"  # Increase upload limit
    osmResolver: "127.0.0.11 8.8.8.8"  # DNS resolvers
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-configuration-guide).