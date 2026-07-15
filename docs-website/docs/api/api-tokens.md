---
id: api-tokens
title: API Tokens
description: Create and use GeoPulse API tokens for scripts, automation, and service clients.
---

API tokens let external clients call GeoPulse REST APIs without using a browser session. They are useful for scripts,
MCP clients, dashboards, scheduled jobs, and other automation owned by a GeoPulse user.

## What an API token can do

An API token acts as the user who created it:

- It can call authenticated **User API** endpoints available to that user.
- If an administrator creates a token for their own account, that token has the administrator's API permissions.
- It is separate from browser login cookies and does not require CSRF handling.
- It records usage metadata such as last-used time and last-used IP.

Treat API tokens like passwords. Anyone with the token can use it until it expires or is revoked.

## Create a token

1. Sign in to GeoPulse.
2. Open **Profile**.
3. Select the **Security** tab.
4. Find **API Tokens**.
5. Click **Create Token**.
6. Enter a clear name, such as `Home dashboard` or `Nightly export job`.
7. Optionally choose an expiration date.
8. Click **Create**.
9. Copy the token immediately and store it securely.

GeoPulse shows the token secret only once. After closing the creation dialog, the app only shows a short preview, status,
expiration, and usage metadata.

## Use a token

Send the token on every API request with `X-API-Key`:

```bash
curl -H "X-API-Key: <your-api-token>" \
  http://localhost:8080/api/users/me
```

Or use a bearer token header:

```bash
curl -H "Authorization: Bearer <your-api-token>" \
  http://localhost:8080/api/users/me
```

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

- View token name, preview, status, expiration, last-used time, and last-used IP.
- Edit the token name or expiration.
- Revoke a token.

Revoking a token takes effect immediately. Any automation using that token will stop authenticating.

## Admin visibility

Administrators can review and revoke user API tokens from the Admin API and admin UI. This is intended for audit,
support, and incident response. Administrators cannot recover the full secret after creation; only the original one-time
token value can be used by clients.

## Troubleshooting

If an API request returns `401 Unauthorized`, check that:

- The token was copied before the creation dialog was closed.
- The token is sent in `X-API-Key` or as `Authorization: Bearer <token>`.
- The token has not expired or been revoked.
- There are no extra spaces or quotes around the token value.

If a request returns `403 Forbidden`, the token is valid but the user does not have permission for that endpoint.
