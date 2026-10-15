---
id: api-tokens
title: API Tokens
description: Create and use GeoPulse API tokens for scripts, automation, and service clients.
---

API tokens let external clients call GeoPulse REST APIs without using a browser session. They are useful for scripts,
MCP clients, dashboards, scheduled jobs, and other automation owned by a GeoPulse user.

## What an API token can do

An API token acts as the user who created it, limited to the **scopes** chosen when it was created:

- With full access, it can call every authenticated endpoint available to that user. If an administrator creates a
  full-access token for their own account, that token has the administrator's API permissions.
- With narrower scopes, it can only do what those scopes allow (see [Scopes](#scopes)).
- It is separate from browser login cookies and does not require CSRF handling.
- It records usage metadata such as last-used time and last-used IP.

Treat API tokens like passwords. Anyone with the token can use it until it expires or is revoked. Give each client the
narrowest access it needs.

## Scopes

| Scope | Allows |
|---|---|
| `all` | Everything the user can do, including managing API tokens and, for administrators, the admin API. |
| `read` | Reading data: every `GET` request on user endpoints, a few read-only `POST` requests (previews and searches), and the [MCP server](./mcp.md). |
| `write` | Changing or deleting data and settings (`POST`, `PUT`, `PATCH` and `DELETE` requests). |
| `gps:ingest` | Sending GPS points with the token, for example to `POST /api/v1/gps/ingest/home-assistant`. |
| `export` | Creating, checking and downloading exports. |
| `import` | Uploading files and running imports that add data. An import that replaces existing data (`clearDataBeforeImport`) also needs `write`. |

The create dialog offers presets for common cases:

| Preset | Scopes | Typical use |
|---|---|---|
| Full access | `all` | Personal scripts you fully trust. |
| Read only | `read` | Dashboards, MCP clients, reporting. |
| Home Assistant | `read`, `gps:ingest` | The [GeoPulse integration for Home Assistant](https://github.com/tess1o/geopulse-homeassistant): sends locations and reads your status. |
| Backup | `read`, `export` | Scheduled backups. |
| Custom | any combination | Anything else. |

A few things to know:

- **Scopes are fixed.** You can rename a token or change its expiration, but not its scopes. To change access, create
  a new token and revoke the old one.
- **Tokens created before scopes existed have full access** (`all`), so existing automation keeps working.
- **Some endpoints always need full access**, even for reading: API token management (`/api/v1/api-tokens`), password
  changes, sign-in sessions, and the admin API. A leaked limited token cannot create new tokens or reach the admin
  area.
- **A client can check its own token** with `GET /api/v1/api-tokens/current`. It works with any scope and returns the
  token's id, owner (`userId`), name, scopes and expiration.
- **Tokens without full access never see stored credentials of other integrations.** Source tokens in
  `GET /api/v1/gps/sources` and notification destinations (Apprise URLs) in `GET /api/v1/geofences/templates` are left
  out (`null`).

When a token lacks a scope, GeoPulse answers `403 Forbidden` with the code `API_TOKEN_SCOPE_REQUIRED`. The response
names the missing scope in `requiredScope` and in the `X-GeoPulse-Required-Scope` header:

```json
{
  "type": "urn:geopulse:error:API_TOKEN_SCOPE_REQUIRED",
  "title": "Forbidden",
  "status": 403,
  "detail": "This API token does not have the 'write' scope.",
  "code": "API_TOKEN_SCOPE_REQUIRED",
  "requiredScope": "write"
}
```

## Create a token

1. Sign in to GeoPulse.
2. Open **Profile**.
3. In the **Personal** group, select **Security**. You can also go straight to `/app/profile?tab=security`.
4. Scroll to the **API tokens** section.
5. Click **Create Token**.
6. Enter a clear name, such as `Home dashboard` or `Nightly export job`.
7. Optionally choose an expiration date.
8. Under **Access**, choose a preset or **Custom** permissions.
9. Click **Create**.
10. Copy the token immediately and store it securely.

GeoPulse shows the token secret only once. After closing the creation dialog, the app only shows a short preview, status,
expiration, and usage metadata.

## Use a token

Send the token on every API request with `X-API-Key`:

```bash
curl -H "X-API-Key: <your-api-token>" \
  http://localhost:8080/api/v1/users/me
```

Or use a bearer token header:

```bash
curl -H "Authorization: Bearer <your-api-token>" \
  http://localhost:8080/api/v1/users/me
```

Either request returns the profile of the user who owns the token. The response (abbreviated) looks like:

```json
{
  "userId": "3f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f",
  "email": "user@example.com",
  "fullName": "Jane Doe",
  "role": "USER",
  "timezone": "Europe/Kyiv",
  "avatar": "/avatars/avatar3.png",
  "hasPassword": true,
  "demoMode": false,
  "canViewAdmin": false,
  "adminReadOnly": false,
  "uiPreferences": {},
  "timelineDisplay": {}
}
```

For more requests, such as reading the timeline or importing a GPX file, see [API Examples](./examples.md).

## Expiration and rotation

When creating or editing a token, you can set an expiration date. Tokens without an expiration remain valid until they
are revoked.

Good practice:

- Use one token per script, integration, or automation client.
- Give each token a name that explains where it is used.
- Set an expiration for temporary work.
- Rotate long-lived tokens periodically.
- Revoke tokens immediately when a client is retired or a token may have leaked.

## Manage existing tokens

In **Profile > Security > API Tokens**, you can:

- View token name, access, preview, status, expiration, last-used time, and last-used IP.
- Edit the token name or expiration (access cannot be changed).
- Revoke a token.

Revoking a token takes effect immediately. Any automation using that token will stop authenticating.

## Admin visibility

Administrators can review and revoke user API tokens from the Administration API and admin UI. This is intended for audit,
support, and incident response. Administrators cannot recover the full secret after creation; only the original one-time
token value can be used by clients.

## Troubleshooting

If an API request returns `401 Unauthorized`, check that:

- The token was copied before the creation dialog was closed.
- The token is sent in `X-API-Key` or as `Authorization: Bearer <token>`.
- The token has not expired or been revoked.
- There are no extra spaces or quotes around the token value.

If a request returns `403 Forbidden`, the token is valid but not allowed to make that request:

- With the code `API_TOKEN_SCOPE_REQUIRED`, the token is missing the scope named in `requiredScope`. Create a token with
  that scope.
- Otherwise, the user does not have permission for that endpoint (for example, a regular user calling the admin API).
