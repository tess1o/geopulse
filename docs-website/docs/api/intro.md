---
id: intro
title: REST API
description: Use GeoPulse's REST API for integrations, scripts, and automation.
---

GeoPulse exposes its backend REST API for integrations, scripts, reporting jobs, and automation that need to work with
the same data available in the web application.

## API structure

The reference is generated from the backend OpenAPI specification and is grouped by audience:

- **Public API** - endpoints that do not require a logged-in GeoPulse session, such as login, registration, health,
  shared links, and GPS integration ingest endpoints.
- **User API** - endpoints available to authenticated users and user-owned API tokens.
- **Admin API** - endpoints that require an administrator account.

## Authentication options

Browser users authenticate through the normal GeoPulse login flow with secure cookies and JWTs.

External API clients should use a user API token. A token acts as the user that created it and can call the same
authenticated REST endpoints that user can access. See [API Tokens](./api-tokens.md) for creation and usage details.

Send a token with either header:

```http
X-API-Key: <your-api-token>
```

or

```http
Authorization: Bearer <your-api-token>
```

Use `X-API-Key` for scripts and service clients when possible. `Authorization: Bearer` is also supported for tools that
expect bearer-token authentication.

## Base URL

In a local development environment the API is usually available at:

```text
http://localhost:8080
```

For deployed environments, use the public URL of your GeoPulse backend or reverse proxy.

## Response format

Most JSON endpoints return GeoPulse's standard API response envelope:

```json
{
  "status": "success",
  "message": null,
  "data": {}
}
```

Some compatibility endpoints, downloads, and integration ingest endpoints intentionally use formats required by the
client they support. Check each endpoint page in the generated reference for its request and response details.
