# Apprise Notifications

GeoPulse can deliver geofence alerts to external channels through [Apprise](https://github.com/caronc/apprise-api).

This integration is optional. GeoPulse always stores geofence events in-app; Apprise adds external delivery.

## What is configured where

- **Admin (global transport settings):** Apprise API endpoint, auth token, timeout, TLS behavior.
- **User (per-template destinations):** destination URL(s) and message templates.

## Admin UI Configuration

Go to:

`Admin Dashboard > System Settings > Notifications`

Configure these settings:

| Setting Key | Description |
|---|---|
| `system.notifications.apprise.enabled` | Master switch for Apprise delivery |
| `system.notifications.apprise.api-url` | Base URL of Apprise API (for example `http://apprise-api:8000`) |
| `system.notifications.apprise.auth-token` | Optional API token/key (encrypted) |
| `system.notifications.apprise.timeout-ms` | Request timeout in milliseconds |
| `system.notifications.apprise.verify-tls` | TLS certificate verification toggle |

Use **Test Apprise** to validate connectivity. You can optionally provide a destination to send a live test notification.

## Geofence Event Cleanup

In the same Notifications tab, you can configure retention cleanup for in-app geofence events:

| Setting Key | Description |
|---|---|
| `system.notifications.geofence-events.cleanup.enabled` | Enable/disable automatic cleanup job |
| `system.notifications.geofence-events.cleanup.interval-days` | How often cleanup runs (in days) |
| `system.notifications.geofence-events.retention-days` | Delete events older than this number of days |

## User Template Configuration

On the Geofences page, users create templates with:

- Destination URL(s)
- Optional title template
- Optional body template
- Default flags for enter/leave

Geofence rules can select dedicated enter/leave templates, or rely on user defaults.

## Delivery Behavior

- Geofence events are persisted in-app.
- If a matching enabled template has destination URL(s), event delivery is queued to Apprise.
- Delivery status is tracked per event: `PENDING`, `SENT`, `FAILED`, `SKIPPED`.
- Failed deliveries retry until max attempts is reached.

## Troubleshooting

- **Test fails with 401/403:** verify API token and endpoint.
- **Events remain `SKIPPED`:** check template enabled flag and destination URL(s).
- **Events move to `FAILED`:** inspect event delivery error text and Apprise logs.
- **No external delivery at all:** ensure `system.notifications.apprise.enabled=true` and API URL is configured.
