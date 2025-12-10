---
title: How to Update GeoPulse
description: Update your GeoPulse installation to the latest version.
---

# How to Update GeoPulse

:::info Coming Soon
This documentation is currently being written. Check back soon for updates.
:::

## What This Will Cover

- **Updating Docker Compose deployments** - pulling new images and restarting services
- **Updating Kubernetes/Helm deployments** - helm upgrade procedures
- **Database migrations** - handling schema changes safely
- **Rollback procedures** - reverting to previous versions if needed
- **Version compatibility** - understanding breaking changes between versions

## In the Meantime

For Docker Compose deployments:
```bash
docker-compose pull
docker-compose up -d
```

For Helm deployments:
```bash
helm upgrade geopulse ./helm/geopulse -f your-values.yaml
```

Always check the [GitHub Releases](https://github.com/tess1o/geopulse/releases) for release notes and breaking changes before updating.

## Stay Updated

Track progress on this documentation: [GitHub Issues](https://github.com/tess1o/geopulse/issues)
