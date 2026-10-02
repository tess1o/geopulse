---
title: Logging
description: Production logging levels, correlation, retention, and safe troubleshooting for GeoPulse.
---

# Logging

GeoPulse writes application, Nginx, and Mosquitto logs to container stdout/stderr. It does not create or rotate application log files.

## Backend output

Production backend records are JSON by default. Request-scoped application records carry `requestId`, and problem records also carry `errorId`; both are emitted as top-level JSON fields. The Quarkus access record is one formatted text value inside the JSON record's `message`, not a set of top-level HTTP fields.

Access records contain only method, query-free path, status, bytes, duration, and request ID. Health, metrics, invitation-token, and public share-token paths are excluded. Nginx logs only 4xx and 5xx responses and uses `$uri`, so query strings and headers are never included.

## Levels

Set `GEOPULSE_LOG_LEVEL` to `ERROR`, `WARN`, `INFO`, or `DEBUG`. An Admin override under **System Settings > Observability** takes precedence; use **Use environment/default** to remove it. Invalid environment values are ignored with one warning. TRACE is unavailable in production.

DEBUG can substantially increase volume. Enable it only while diagnosing an incident, reproduce the issue, then reset the Admin override or restore INFO. GPS payloads, coordinates, credentials, AI prompts/results, request headers, and provider response bodies are intentionally excluded at every level.

## Retention

Production Compose files use Docker's `json-file` driver with three 10 MB files per long-running service. Kubernetes installations rely on kubelet or the platform logging stack; configure collection and retention there. Mosquitto writes to stdout only. Upgrading the Helm chart removes the former Mosquitto log PVC from the rendered release, so preserve any historical broker logs before upgrading if they are required.

## Security after upgrade

The database migration removes secret values from current audit rows, but it cannot recall values copied into old backups, container log files, or support threads. Rotate affected Google Maps, Mapbox, Geoapify, ChibiGeo, Open-Meteo, Pirate Weather, backup, and Apprise credentials, including credentials embedded in Apprise destination URLs.

The settings backup export still includes `backup.health.apprise.destination` in plaintext because that setting remains a string in this release. Treat settings exports as sensitive. Changing its storage type and backup format is a separate compatibility decision.
