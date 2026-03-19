# Apprise Notifications

GeoPulse can send geofence alerts to external channels through [Apprise](https://github.com/caronc/apprise-api).

Apprise is optional. Geofence events are always stored in-app.

For the full geofence user workflow, see [Geofences](../../user-guide/core-features/geofences).

## 1. Install Apprise (Docker Compose Overlay)

GeoPulse does not add Apprise to main compose bundles by default.

Use an overlay compose file (recommended):

```yaml
services:
  apprise-api:
    image: caronc/apprise:latest
    container_name: geopulse-apprise
    restart: unless-stopped
    ports:
      - "8000:8000"
```

Start with overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.apprise.yml up -d
```

See full deployment context in [Docker Compose Deployment](../../getting-started/deployment/docker-compose#optional-add-apprise-geofence-external-notifications).

## 2. Choose the Correct Apprise API URL

Set `system.notifications.apprise.api-url` (Admin UI) based on where backend runs:

- **Backend + Apprise in same Docker Compose network:** `http://apprise-api:8000`
- **Backend running on host machine, Apprise exposed on host port 8000:** `http://localhost:8000`
- **Backend in another environment/network:** use reachable URL for that backend (for example `http://<apprise-host>:8000`)

## 3. Configure Admin Settings

Go to:

`Admin Dashboard > System Settings > Notifications`

Configure:

| Setting Key | Description |
|---|---|
| `system.notifications.apprise.enabled` | Master switch for external Apprise delivery |
| `system.notifications.apprise.api-url` | Base URL of Apprise API |
| `system.notifications.apprise.auth-token` | Optional token/key (encrypted at rest) |
| `system.notifications.apprise.timeout-ms` | HTTP timeout in milliseconds |
| `system.notifications.apprise.verify-tls` | TLS certificate verification toggle |

Use **Test Apprise** to verify reachability/auth before enabling production delivery.

## 4. User Templates vs Admin Transport

Apprise config is split between admin and users:

- **Admin (global transport):** endpoint/auth/timeout/TLS
- **User templates (per user):** destination URLs + message templates

Template destination rules:

- One Apprise URL per line
- Leave destination empty for in-app only template

## 5. Delivery + In-App Behavior

- Every geofence event is persisted in-app.
- Apprise delivery is additional (never replaces in-app events).
- Delivery status is metadata on event records:
  - `PENDING`
  - `SENT`
  - `FAILED`
  - `SKIPPED`

## 6. Geofence Event Cleanup Settings

In the same Notifications tab:

| Setting Key | Description |
|---|---|
| `system.notifications.geofence-events.cleanup.enabled` | Enable/disable automatic cleanup |
| `system.notifications.geofence-events.retention-days` | Delete events older than this number of days |

Cleanup scheduler cadence is environment/property-based and requires backend restart:

- `geopulse.notifications.geofence-events.cleanup.scheduler-cadence` (default `12h`)

## Troubleshooting

- **Test fails with 401/403:** verify auth token and endpoint.
- **No external messages:** verify `apprise.enabled`, API URL, and template destinations.
- **Events are `SKIPPED`:** destination missing/disabled template/non-deliverable path for that event.
- **Events are `FAILED`:** inspect delivery error details and Apprise service logs.
