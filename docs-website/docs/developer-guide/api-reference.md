---
title: API Reference
description: REST API documentation for GeoPulse.
---

# API Reference

:::info Coming Soon
This documentation is currently being written. Check back soon for updates.
:::

## What This Will Cover

- **Authentication endpoints** - login, registration, token refresh
- **Location data endpoints** - submitting GPS points from various sources
- **Timeline endpoints** - querying stays, trips, and timeline events
- **Favorites and places** - managing favorite locations
- **User management** - profile and settings APIs
- **Statistics endpoints** - travel analytics and insights

## In the Meantime

GeoPulse provides a REST API for programmatic access. Key endpoints include:

- `/api/owntracks` - OwnTracks location submission
- `/api/overland` - Overland location submission
- `/api/dawarich` - Dawarich-compatible endpoints
- `/api/timeline` - Timeline query endpoints
- `/api/prometheus/metrics` - Prometheus metrics (if enabled)

For authentication, most endpoints require JWT tokens obtained via the login endpoint.

## Stay Updated

Track progress on this documentation: [GitHub Issues](https://github.com/tess1o/geopulse/issues)
