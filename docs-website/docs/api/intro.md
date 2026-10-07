---
id: intro
title: REST API
description: Use GeoPulse's REST API for integrations, scripts, and automation.
---

GeoPulse exposes its backend REST API for integrations, scripts, reporting jobs, and automation that need to work with
the same data available in the web application.

## API structure

The reference is generated from the backend OpenAPI specification and is grouped by what you want to do:

| Group                 | What it covers                                                                                              |
|-----------------------|-------------------------------------------------------------------------------------------------------------|
| **Authentication**    | Sign-in with tokens, mobile sign-in, registration, and API tokens.                                          |
| **Location Data**     | GPS tracker ingest (OwnTracks, Overland, Traccar, and others), GPS points, GPS sources, import, and export. |
| **Timeline & Places** | The timeline, manual timeline corrections, timeline labels, places, favorites, geocoding, and notes.        |
| **Insights**          | Statistics, digests, journey insights, location analytics, and coverage.                                    |
| **Trips**             | Trips and trip planning.                                                                                    |
| **Friends & Sharing** | Friends, share links, and the public share-link viewer.                                                     |
| **Alerts**            | Geofences and notifications.                                                                                |
| **Integrations**      | Immich, Memos, the AI assistant, places to visit, and weather.                                              |
| **Account & App**     | Your profile and preferences, and server health and version.                                                |
| **Administration**    | User management, invitations, backups, system settings, and other administrator tools.                      |

Each tag page starts with an overview of the concepts involved, such as how the timeline is built or how imports run
as background jobs.

The reference covers the endpoints meant for integrations, scripts, and automation. The web app also uses internal
endpoints, such as browser sign-in, chunked uploads, and connection tests. They are listed in the OpenAPI specification
with `x-internal: true`, are not shown here, and can change without notice.

## Who can call an endpoint

Every endpoint description ends with an **Access** line:

| Access                          | Meaning                                                                                     |
|---------------------------------|---------------------------------------------------------------------------------------------|
| Public                          | No authentication. For example sign-in, registration, health, and version.                  |
| Any signed-in user              | A browser session or a user API token. The request acts as that user.                       |
| Administrators only             | A session or API token of a user with the `ADMIN` role.                                     |
| GPS source credentials          | The username and password, or token, of a GPS source configured in GeoPulse.                |
| Anyone with the share link      | A short-lived access token issued for a share link. No GeoPulse account needed.             |

## Authentication options

Browser users authenticate through the normal GeoPulse login flow with secure cookies and JWTs.

External API clients should use a user API token. A token acts as the user that created it and can call the same
authenticated REST endpoints that user can access, including administrator endpoints when the user is an administrator.
See [API Tokens](./api-tokens.md) for creation and usage details.

GPS tracking apps do not use API tokens. They send data to the **GPS Tracker Ingest** endpoints with the credentials of
a GPS source, configured in GeoPulse under **GPS Sources**.

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

REST endpoints are versioned under `/api/v1`, for example `http://localhost:8080/api/v1/timeline`.

## Code samples

Each endpoint page has a curl sample. It reads the server address from `GEOPULSE_URL` and your
API token from `GEOPULSE_API_TOKEN`, so set both before running it:

```bash
export GEOPULSE_URL=http://localhost:8080
export GEOPULSE_API_TOKEN=<your-api-token>
```

Samples for the **GPS Tracker Ingest** endpoints use the GPS source's credentials instead: `GPS_SOURCE_USERNAME` and
`GPS_SOURCE_PASSWORD`, or `GPS_SOURCE_TOKEN`. Samples for the public share-link endpoints use
`SHARE_LINK_ACCESS_TOKEN`.

## OpenAPI specification

The API reference in this section is generated from the GeoPulse OpenAPI specification. You can also use the
specification directly to generate clients, import the API into tools such as Postman or Insomnia, or check request and
response schemas:

- [openapi.json](https://github.com/tess1o/geopulse/blob/main/docs/openapi/openapi.json)
- [openapi.yaml](https://github.com/tess1o/geopulse/blob/main/docs/openapi/openapi.yaml)

## Responses

Successful responses are not wrapped in a common envelope. Each endpoint returns its own resource directly: a JSON
object, a JSON array, a paged list, a file download, or an empty `204 No Content` response. Check the endpoint page in
the generated reference or the OpenAPI specification for the exact request and response schema.

Paginated list endpoints return the items together with paging metadata, for example:

```json
{
  "items": [],
  "page": 1,
  "size": 50,
  "totalElements": 1234,
  "totalPages": 25
}
```

GPS integration ingest endpoints (OwnTracks, Overland, Dawarich, and others) respond in the format their client app
expects.

## Errors

All errors use one format: [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457), produced by
[quarkus-http-problem](https://github.com/quarkiverse/quarkus-http-problem) and returned with the
`application/problem+json` content type. GeoPulse adds a few stable fields to the standard ones:

```json
{
  "type": "urn:geopulse:error:EXPORT_NOT_FOUND",
  "title": "Not Found",
  "status": 404,
  "detail": "Export job not found",
  "instance": "/api/v1/exports/0b9f6c1e-1d2a-4a8e-9c55-6a3e1f7d2b10",
  "code": "EXPORT_NOT_FOUND",
  "errorId": "5f1c2a7e-8e0b-4f43-b2a4-7d9f0c3e6a21",
  "requestId": "c7d1e5f2-3a4b-4c6d-8e9f-0a1b2c3d4e5f"
}
```

| Field        | Description                                                                                       |
|--------------|---------------------------------------------------------------------------------------------------|
| `type`       | URI that identifies the error type, always `urn:geopulse:error:<code>`.                           |
| `title`      | Short HTTP status description.                                                                    |
| `status`     | HTTP status code.                                                                                 |
| `detail`     | Human-readable explanation. Do not parse it; it may change.                                       |
| `instance`   | Request path that produced the error.                                                             |
| `code`       | Stable, machine-readable error code. Use this field to handle specific errors in scripts.         |
| `errorId`    | Unique ID of this error occurrence. Also returned in the `X-Error-Id` response header.            |
| `requestId`  | Request correlation ID. Also returned in the `X-Request-Id` response header.                      |
| `parameters` | Optional. Values related to the error, such as limits or the offending field.                     |
| `violations` | Optional. Present on validation errors (`code: VALIDATION_FAILED`), one entry per invalid field. |

A validation error lists every invalid field:

```json
{
  "type": "urn:geopulse:error:VALIDATION_FAILED",
  "title": "Bad Request",
  "status": 400,
  "instance": "/api/v1/registrations",
  "code": "VALIDATION_FAILED",
  "errorId": "9a3e7c51-2b6d-4f0e-8a1c-4d5e6f7a8b9c",
  "requestId": "1e2d3c4b-5a69-4788-9abc-def012345678",
  "violations": [
    {
      "field": "password",
      "in": "body",
      "code": "SIZE",
      "parameters": { "min": 3, "max": 128 },
      "detail": {
        "key": "validation.size",
        "parameters": { "min": 3, "max": 128 },
        "fallback": "Password must be between 3 and 128 characters"
      }
    }
  ]
}
```

When reporting a problem, include the `errorId` or `requestId`; administrators can use them to find the matching entry in
the backend logs.

## Examples

See [API Examples](./examples.md) for `curl` examples that read the timeline, fetch raw GPS points, and import or export
GPX and OwnTracks files.
